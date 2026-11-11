package com.carlesso.goalfather.adapter.out.worlddata

import com.carlesso.goalfather.adapter.out.worlddata.dto.CalendarDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.ClubDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.CompetitionDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.CountryDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.FormatDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.KnockoutStageDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.PlayerDto
import com.carlesso.goalfather.application.port.out.PackLoadError
import com.carlesso.goalfather.domain.model.Competition
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.Confederation
import com.carlesso.goalfather.domain.model.Country
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.FifaWindow
import com.carlesso.goalfather.domain.model.KnockoutStage
import com.carlesso.goalfather.domain.model.Legs
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.Tiebreaker
import com.carlesso.goalfather.domain.model.world.ClubProfile
import com.carlesso.goalfather.domain.model.world.CompetitionOwner
import com.carlesso.goalfather.domain.model.world.LeagueStrength
import com.carlesso.goalfather.domain.model.world.PlayerProfile
import com.carlesso.goalfather.domain.model.world.Reputation
import com.carlesso.goalfather.domain.model.world.Stadium
import com.carlesso.goalfather.domain.model.world.TransferWindow
import com.carlesso.goalfather.domain.model.world.WorldCalendar
import com.carlesso.goalfather.domain.model.world.WorldClubId
import com.carlesso.goalfather.domain.model.world.WorldCompetition
import com.carlesso.goalfather.domain.model.world.WorldPlayerId
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * A camada anticorrupção do pack (issue #126): traduz os DTOs externos para o
 * domínio, um item por vez, e **acumula** os problemas em [errors] em vez de
 * parar no primeiro.
 *
 * Cada função devolve o objeto de domínio ou `null` (com o erro registrado).
 * É uma versão pragmática do padrão "Validation" da programação funcional:
 * `T?` + lista de erros cobre o caso sem trazer biblioteca (Arrow) nem
 * empilhar `Result` aninhado. O estado mutável é LOCAL a uma carga — o
 * [WorldPackLoader] cria um mapper novo a cada `load`.
 *
 * Os `init`/`require` do domínio continuam valendo: aqui eles são capturados
 * na BORDA e viram [PackLoadError.InvalidValue]. Dentro do domínio uma
 * violação é bug (exception); vinda de dado externo, é erro esperado (valor).
 */
internal class PackMapper(private val errors: MutableList<PackLoadError>) {

    fun report(error: PackLoadError) {
        errors += error
    }

    /**
     * Roda [block] e converte as exceptions de VALIDAÇÃO (o `require` do
     * domínio lança `IllegalArgumentException`; data inválida lança
     * `DateTimeParseException`) em erro do recurso. `inline` evita alocar o
     * lambda a cada item; `private` porque um `inline` só enxerga membros
     * tão visíveis quanto ele (aqui, o [errors] privado).
     */
    private inline fun <T> attempt(resource: String, block: () -> T): T? =
        try {
            block()
        } catch (e: IllegalArgumentException) {
            report(PackLoadError.InvalidValue(resource, e.message ?: e.toString()))
            null
        } catch (e: DateTimeParseException) {
            report(PackLoadError.InvalidValue(resource, "data inválida '${e.parsedString}'"))
            null
        }

    // ── Países ────────────────────────────────────────────────────────────

    fun countries(resource: String, dtos: List<CountryDto>): Map<CountryCode, Country> {
        val mapped = dtos.mapNotNull { dto ->
            attempt(resource) {
                Country(
                    code = CountryCode(dto.code),
                    name = dto.name.also { require(it.isNotBlank()) { "país ${dto.code} sem nome" } },
                    confederation = enumValue<Confederation>(dto.confederation, "confederação"),
                    isoAlpha2 = dto.isoAlpha2,
                )
            }
        }
        mapped.groupBy { it.code }.filterValues { it.size > 1 }.keys.forEach {
            report(PackLoadError.InvalidValue(resource, "país '${it.value}' repetido"))
        }
        return mapped.associateBy { it.code }
    }

    /** Resolve um código de país do pack; inexistente vira [PackLoadError.UnknownCountry]. */
    fun country(resource: String, code: String, countries: Map<CountryCode, Country>): Country? {
        val parsed = attempt(resource) { CountryCode(code) } ?: return null
        return countries[parsed] ?: run {
            report(PackLoadError.UnknownCountry(resource, parsed))
            null
        }
    }

    // ── Competições ───────────────────────────────────────────────────────

    fun competition(
        resource: String,
        dto: CompetitionDto,
        countries: Map<CountryCode, Country>,
        clubIds: List<WorldClubId>,
    ): WorldCompetition? {
        val owner = owner(resource, dto, countries) ?: return null
        return attempt(resource) {
            WorldCompetition(
                competition = Competition(
                    id = CompetitionId(dto.id),
                    name = dto.name,
                    format = format(dto.format),
                    tiebreakers = dto.tiebreakers?.map { enumValue<Tiebreaker>(it, "critério de desempate") }
                        ?: Competition.DEFAULT_TIEBREAKERS,
                ),
                owner = owner,
                tier = dto.tier?.also { require(it >= 1) { "tier deve ser >= 1: $it" } },
                strength = dto.strength?.let(::LeagueStrength),
                clubIds = clubIds,
            )
        }
    }

    /**
     * Exatamente um dono: país (liga/copa nacional) OU confederação
     * (continental). `when` sobre o PAR de nulos deixa os quatro casos à vista.
     */
    private fun owner(resource: String, dto: CompetitionDto, countries: Map<CountryCode, Country>): CompetitionOwner? =
        when {
            dto.country != null && dto.confederation == null ->
                country(resource, dto.country, countries)?.let(CompetitionOwner::National)
            dto.country == null && dto.confederation != null ->
                attempt(resource) {
                    CompetitionOwner.Continental(enumValue<Confederation>(dto.confederation, "confederação"))
                }
            else -> {
                report(
                    PackLoadError.InvalidValue(
                        resource,
                        "competição '${dto.id}' precisa de exatamente um entre country e confederation",
                    ),
                )
                null
            }
        }

    /**
     * DTO → formato sealed do domínio. `when` exaustivo sobre o `sealed` do
     * DTO: um formato novo no pack que o código não conhece nem compila até
     * ser tratado aqui. As regras de consistência (a conta das fases) são dos
     * `init` do domínio — o mapper não as duplica.
     */
    private fun format(dto: FormatDto): CompetitionFormat = when (dto) {
        is FormatDto.RoundRobin -> CompetitionFormat.RoundRobin(dto.legs)
        is FormatDto.Knockout -> knockout(dto.stages)
        is FormatDto.GroupsThenKnockout -> CompetitionFormat.GroupsThenKnockout(
            groups = dto.groups,
            perGroup = dto.perGroup,
            qualifiersPerGroup = dto.qualifiersPerGroup,
            knockout = knockout(dto.knockout),
            groupLegs = dto.groupLegs,
        )
        is FormatDto.SwissLeague -> CompetitionFormat.SwissLeague(
            teams = dto.teams,
            matchesPerTeam = dto.matchesPerTeam,
            directQualifiers = dto.directQualifiers,
            playoffRange = if (dto.playoffFrom != null && dto.playoffTo != null) {
                dto.playoffFrom..dto.playoffTo
            } else {
                IntRange.EMPTY
            },
        )
    }

    private fun knockout(stages: List<KnockoutStageDto>): CompetitionFormat.Knockout =
        CompetitionFormat.Knockout(
            stages.map {
                KnockoutStage(
                    name = it.name,
                    teams = it.teams,
                    legs = Legs.entries.firstOrNull { legs -> legs.matches == it.legs }
                        ?: throw IllegalArgumentException("fase ${it.name}: legs deve ser 1 ou 2, veio ${it.legs}"),
                    newEntrants = it.newEntrants,
                )
            },
        )

    // ── Clubes e jogadores ────────────────────────────────────────────────

    fun club(
        resource: String,
        dto: ClubDto,
        countries: Map<CountryCode, Country>,
        squad: List<PlayerProfile>,
    ): ClubProfile? {
        // Definição completa exige os quatro campos; `?: return` sai cedo com o erro.
        val name = dto.name ?: return missingField(resource, dto.id, "name")
        val countryCode = dto.country ?: return missingField(resource, dto.id, "country")
        val reputation = dto.reputation ?: return missingField(resource, dto.id, "reputation")
        val stadium = dto.stadium ?: return missingField(resource, dto.id, "stadium")
        val country = country(resource, countryCode, countries) ?: return null
        return attempt(resource) {
            ClubProfile(
                id = WorldClubId(dto.id),
                name = name,
                country = country,
                stadium = Stadium(stadium.name, stadium.capacity),
                reputation = Reputation(reputation),
                squad = squad,
            )
        }
    }

    private fun missingField(resource: String, clubId: String, field: String): Nothing? {
        report(PackLoadError.InvalidValue(resource, "clube '$clubId' sem o campo '$field'"))
        return null
    }

    fun player(resource: String, dto: PlayerDto, countries: Map<CountryCode, Country>): PlayerProfile? {
        val nationality = country(resource, dto.nationality, countries)
        val second = dto.secondNationality?.let { country(resource, it, countries) }
        // Nacionalidade desconhecida já foi reportada; segunda idem (se veio e falhou).
        if (nationality == null || (dto.secondNationality != null && second == null)) return null
        return attempt(resource) {
            PlayerProfile(
                id = WorldPlayerId(dto.id),
                name = dto.name,
                birthDate = LocalDate.parse(dto.birthDate),
                nationality = nationality,
                position = position(dto.position),
                secondNationality = second,
                displayName = dto.displayName,
            )
        }
    }

    /**
     * Posição externa → as quatro do jogo. É o exemplo mais claro de
     * anticorrupção: a fonte (Wikidata) fala `DF` para "defensor", o jogo
     * fala `CB`. Se a fonte mudar o vocabulário, muda só aqui.
     */
    private fun position(raw: String): Position = when (raw.uppercase()) {
        "GK", "GOALKEEPER" -> Position.GK
        "DF", "CB", "DEFENDER" -> Position.CB
        "MF", "MIDFIELDER" -> Position.MF
        "FW", "FORWARD", "ATTACKER" -> Position.FW
        else -> throw IllegalArgumentException("posição desconhecida '$raw'")
    }

    // ── Calendário ────────────────────────────────────────────────────────

    fun calendar(resource: String, expectedYear: Int, dto: CalendarDto, countries: Map<CountryCode, Country>): WorldCalendar? {
        if (dto.year != expectedYear) {
            report(PackLoadError.InvalidValue(resource, "year ${dto.year} ≠ ano do arquivo $expectedYear"))
            return null
        }
        val fifaWindows = dto.fifaWindows.mapNotNull {
            attempt(resource) { FifaWindow(LocalDate.parse(it.start), LocalDate.parse(it.end)) }
        }
        val transferWindows = dto.transferWindows.mapNotNull { window ->
            country(resource, window.country, countries)?.let { country ->
                attempt(resource) { TransferWindow(country, LocalDate.parse(window.start), LocalDate.parse(window.end)) }
            }
        }
        val allMapped = fifaWindows.size == dto.fifaWindows.size && transferWindows.size == dto.transferWindows.size
        return if (allMapped) WorldCalendar(dto.year, fifaWindows, transferWindows) else null
    }
}

/**
 * `enumValueOf` com mensagem nossa. `reified` (só em função `inline`) deixa
 * o tipo genérico disponível em runtime — o que permite `enumValueOf<T>`
 * sem passar `Class<T>` como em Java (`Enum.valueOf(Confederation.class, s)`).
 */
private inline fun <reified T : Enum<T>> enumValue(raw: String, what: String): T =
    enumValues<T>().firstOrNull { it.name == raw.uppercase() }
        ?: throw IllegalArgumentException("$what desconhecido(a) '$raw'")
