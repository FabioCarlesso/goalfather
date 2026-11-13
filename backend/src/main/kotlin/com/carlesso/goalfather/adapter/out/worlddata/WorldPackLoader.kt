package com.carlesso.goalfather.adapter.out.worlddata

import com.carlesso.goalfather.adapter.out.worlddata.dto.CalendarDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.ClubDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.CompetitionDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.CountryDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.EnvelopeDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.EnvelopeHeaderDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.SquadDto
import com.carlesso.goalfather.application.port.out.PackLoadError
import com.carlesso.goalfather.application.port.out.PackLoadResult
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.Country
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.world.ClubProfile
import com.carlesso.goalfather.domain.model.world.PlayerProfile
import com.carlesso.goalfather.domain.model.world.WorldClubId
import com.carlesso.goalfather.domain.model.world.WorldPack
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Lê um pack recurso a recurso, como um cliente de API REST, e o entrega
 * validado ao domínio (issue #126).
 *
 * Não sabe de onde os bytes vêm: recebe `read`, uma função `suspend` que dado
 * o caminho lógico (`clubs/bra-flamengo/squad.json`) devolve o texto ou
 * `null` se o recurso não existe. O adapter de arquivo passa "ler do disco";
 * um adapter HTTP passaria "GET na URL" — e reaproveitaria este loader e o
 * [PackMapper] inteiros. Função como parâmetro (tipo `suspend (String) -> String?`)
 * no lugar de uma interface `ResourceReader` de um método só: em Kotlin,
 * função é tipo de primeira classe.
 */
class WorldPackLoader(private val json: Json = PACK_JSON) {

    suspend fun load(read: suspend (String) -> String?, calendarYears: List<Int>): PackLoadResult {
        val errors = mutableListOf<PackLoadError>()
        val mapper = PackMapper(errors)

        // Países e competições são a raiz: sem eles não há o que validar.
        val countryDtos = fetch(COUNTRIES, ListSerializer(CountryDto.serializer()), read, mapper)
        val competitionDtos = fetch(COMPETITIONS, ListSerializer(CompetitionDto.serializer()), read, mapper)
        if (countryDtos == null || competitionDtos == null) return PackLoadResult.Invalid(errors)
        val countries = mapper.countries(COUNTRIES, countryDtos)

        // 1) Clubes de cada competição. Ids viram caminho de recurso, então
        //    precisam ser seguros ANTES de qualquer leitura (nada de "../").
        val clubsByCompetition: Map<CompetitionDto, List<ClubDto>> = competitionDtos
            .filter { checkId(COMPETITIONS, it.id, mapper) }
            .associateWith { competition ->
                val resource = clubsResource(competition.id)
                fetch(resource, ListSerializer(ClubDto.serializer()), read, mapper)
                    .orEmpty()
                    .filter { checkId(resource, it.id, mapper) }
            }

        // 2) Definição de cada clube: a primeira completa vale; outra
        //    completa diferente é conflito. `LinkedHashMap` (o default do
        //    `mutableMapOf`) mantém a ordem do pack — o resultado é estável.
        val definitions = mutableMapOf<String, ClubDefinition>()
        clubsByCompetition.forEach { (competition, clubs) ->
            clubs.filterNot { it.isReference }.forEach { dto ->
                val previous = definitions.putIfAbsent(dto.id, ClubDefinition(clubsResource(competition.id), dto))
                if (previous != null && previous.dto != dto) {
                    mapper.report(PackLoadError.ConflictingClubDefinition(WorldClubId(dto.id)))
                }
            }
        }

        // 3) Referências soltas: competição cita clube que ninguém define.
        clubsByCompetition.forEach { (competition, clubs) ->
            clubs.filter { it.id !in definitions }.distinctBy { it.id }.forEach {
                mapper.report(PackLoadError.UnknownClubReference(CompetitionId(competition.id), WorldClubId(it.id)))
            }
        }

        // 4) Elenco de cada clube definido.
        val clubs = definitions.values.mapNotNull { loadClub(it, read, mapper, countries) }
        reportDuplicatePlayers(clubs, mapper)

        // 5) Competições já com os ids dos clubes resolvidos.
        val competitions = clubsByCompetition.mapNotNull { (dto, clubDtos) ->
            val clubIds = clubDtos.map { it.id }.distinct().filter { it in definitions }.map(::WorldClubId)
            mapper.competition(COMPETITIONS, dto, countries, clubIds)
        }

        // 6) Calendário por ano.
        val calendars = calendarYears.sorted().mapNotNull { year ->
            val resource = "calendar/$year.json"
            fetch(resource, CalendarDto.serializer(), read, mapper)?.let { mapper.calendar(resource, year, it, countries) }
        }

        return if (errors.isEmpty()) {
            PackLoadResult.Loaded(WorldPack(countries.values.toList(), competitions, clubs, calendars))
        } else {
            PackLoadResult.Invalid(errors.toList())
        }
    }

    /** Onde o clube foi definido por completo (para a mensagem de erro apontar o arquivo certo). */
    private data class ClubDefinition(val resource: String, val dto: ClubDto)

    private fun clubsResource(competitionId: String) = "competitions/$competitionId/clubs.json"

    private suspend fun loadClub(
        definition: ClubDefinition,
        read: suspend (String) -> String?,
        mapper: PackMapper,
        countries: Map<CountryCode, Country>,
    ): ClubProfile? {
        val resource = "clubs/${definition.dto.id}/squad.json"
        val squadDto = fetch(resource, SquadDto.serializer(), read, mapper)
        val squad = squadDto?.players.orEmpty().mapNotNull { mapper.player(resource, it, countries) }
        val club = mapper.club(definition.resource, definition.dto, countries, squad) ?: return null
        // Só acusa "sem goleiro" se o elenco foi lido por inteiro: um goleiro
        // que falhou no mapeamento já gerou o erro dele, e um segundo erro
        // ("sem goleiro") apontaria a causa errada.
        val squadComplete = squadDto != null && squad.size == squadDto.players.size
        if (squadComplete && !club.hasGoalkeeper) mapper.report(PackLoadError.SquadWithoutGoalkeeper(club.id))
        return club
    }

    private fun reportDuplicatePlayers(clubs: List<ClubProfile>, mapper: PackMapper) {
        // `flatMap` em pares (jogador, clube) → agrupa por jogador → quem tem mais de um.
        clubs.flatMap { club -> club.squad.map { player: PlayerProfile -> player.id to club.id } }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size > 1 }
            .forEach { (player, inClubs) -> mapper.report(PackLoadError.DuplicatePlayer(player, inClubs)) }
    }

    private fun checkId(resource: String, id: String, mapper: PackMapper): Boolean =
        SAFE_ID.matches(id).also { safe ->
            if (!safe) mapper.report(PackLoadError.InvalidValue(resource, "id '$id' fora do padrão ${SAFE_ID.pattern}"))
        }

    /**
     * Lê um recurso em duas passadas: primeiro só o cabeçalho (a versão),
     * depois o envelope completo com o `data` tipado. Qualquer falha vira erro
     * registrado e `null`.
     *
     * A exception do kotlinx (`SerializationException`) é capturada AQUI, na
     * borda: dado externo malformado é um caso esperado, não excepcional.
     * (`SerializationException` é subclasse de `IllegalArgumentException`, que
     * também cobre o JSON sintaticamente inválido.)
     */
    private suspend fun <T> fetch(
        resource: String,
        dataSerializer: KSerializer<T>,
        read: suspend (String) -> String?,
        mapper: PackMapper,
    ): T? {
        val text = read(resource) ?: run {
            mapper.report(PackLoadError.MissingResource(resource))
            return null
        }
        return try {
            val version = json.decodeFromString(EnvelopeHeaderDto.serializer(), text).schemaVersion
            if (version !in SUPPORTED_SCHEMA_VERSIONS) {
                mapper.report(PackLoadError.UnsupportedSchemaVersion(resource, version, SUPPORTED_SCHEMA_VERSIONS))
                return null
            }
            json.decodeFromString(EnvelopeDto.serializer(dataSerializer), text).data
        } catch (e: IllegalArgumentException) {
            mapper.report(PackLoadError.MalformedResource(resource, e.message?.lineSequence()?.first() ?: e.toString()))
            null
        }
    }

    companion object {
        const val COUNTRIES = "countries.json"
        const val COMPETITIONS = "competitions.json"

        /** Versões do schema que este código entende. Um `v2` incompatível ganharia seu próprio mapeamento. */
        val SUPPORTED_SCHEMA_VERSIONS: Set<Int> = setOf(1)

        /** Ids viram pedaço de caminho/URL: só minúsculas, dígitos e hífen. */
        val SAFE_ID: Regex = Regex("[a-z0-9][a-z0-9-]*")

        /**
         * `Json` PRÓPRIO do adapter, não o bean do Spring: o formato externo
         * do pack evolui por conta própria e não deve herdar ajustes feitos
         * para a API REST. `ignoreUnknownKeys = true` é o que deixa um pack
         * com campos novos ser lido por código antigo (forward compatibility).
         */
        val PACK_JSON: Json = Json { ignoreUnknownKeys = true }
    }
}
