package com.carlesso.goalfather.domain.model.world

import com.carlesso.goalfather.domain.model.Competition
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.Confederation
import com.carlesso.goalfather.domain.model.Country
import com.carlesso.goalfather.domain.model.FifaWindow
import com.carlesso.goalfather.domain.model.Position
import java.time.LocalDate
import java.time.Period

/**
 * O mundo do jogo como o DOMÍNIO o enxerga (issue #126): países, competições,
 * clubes, elencos e calendário, já traduzidos do formato externo pela camada
 * anticorrupção (`adapter/out/worlddata`).
 *
 * Só carrega **fatos reais** (política de direitos em `docs/WORLD_DATA.md`):
 * nomes, nascimento, nacionalidade, posição, estádio e capacidade. Nada de
 * atributo, salário, valor ou caixa — isso é simulado por regra nossa
 * ([com.carlesso.goalfather.domain.rules.AttributeGenerator]) quando o mundo
 * vira jogo (issue #131).
 *
 * Os ids aqui são os do pack ([WorldClubId], [WorldPlayerId]), legíveis e
 * estáveis entre versões. Traduzi-los para os `ClubId`/`PlayerId` numéricos
 * do jogo é trabalho do seed (#131), não do provedor.
 */
data class WorldPack(
    val countries: List<Country>,
    val competitions: List<WorldCompetition>,
    val clubs: List<ClubProfile>,
    val calendars: List<WorldCalendar>,
) {
    private val clubsById: Map<WorldClubId, ClubProfile> by lazy { clubs.associateBy { it.id } }

    /** Clube pelo id do pack, ou `null` — ausência no tipo, sem exception. */
    fun club(id: WorldClubId): ClubProfile? = clubsById[id]

    /** Calendário de um ano, se o pack trouxer. */
    fun calendar(year: Int): WorldCalendar? = calendars.firstOrNull { it.year == year }

    /**
     * Força da liga em que o clube joga, insumo do [com.carlesso.goalfather.domain.rules.AttributeGenerator].
     *
     * Copas e continentais não têm força própria (`strength == null`): um clube
     * da Série B na Copa do Brasil continua sendo um clube da Série B. Se o clube
     * aparece em mais de uma liga (não deveria, mas o dado é externo), vale a
     * mais forte. `maxByOrNull` devolve `null` quando nenhuma liga tem o clube —
     * quem chama decide o default.
     */
    fun leagueStrengthOf(id: WorldClubId): LeagueStrength? =
        competitions
            .filter { id in it.clubIds }
            .mapNotNull { it.strength }
            .maxByOrNull { it.value }
}

/**
 * Id de clube no pack (`"bra-flamengo"`). `value class` pelo mesmo motivo de
 * `ClubId`: o compilador recusa um nome de clube ou id de jogador no lugar.
 * É `String`, não `Long`, porque vem de dado externo legível — o `ClubId`
 * numérico do jogo nasce no seed.
 */
@JvmInline
value class WorldClubId(val value: String) {
    init {
        require(value.isNotBlank()) { "WorldClubId não pode ser vazio" }
    }

    override fun toString(): String = value
}

/** Id de jogador no pack. Estável mesmo quando o nome exibido muda ([PlayerProfile.displayName]). */
@JvmInline
value class WorldPlayerId(val value: String) {
    init {
        require(value.isNotBlank()) { "WorldPlayerId não pode ser vazio" }
    }

    override fun toString(): String = value
}

/**
 * Força de uma liga: o overall típico de um titular de clube mediano dela
 * (Premier League ~78, Série B ~62). É um número NOSSO, não copiado de jogo
 * nenhum — calibra o [com.carlesso.goalfather.domain.rules.AttributeGenerator].
 */
@JvmInline
value class LeagueStrength(val value: Int) {
    init {
        require(value in RANGE) { "LeagueStrength fora de $RANGE: $value" }
    }

    companion object {
        val RANGE: IntRange = 1..99
    }
}

/**
 * Reputação do clube DENTRO da sua liga, de 1 a 100 (50 = mediano). Dado do
 * pack, decidido por nós a partir de fatos públicos (títulos, torcida) — não
 * é rating de terceiro.
 */
@JvmInline
value class Reputation(val value: Int) {
    init {
        require(value in RANGE) { "Reputation fora de $RANGE: $value" }
    }

    companion object {
        val RANGE: IntRange = 1..100
        val AVERAGE: Reputation = Reputation(50)
    }
}

/**
 * Quem organiza a competição. `sealed` porque são dois casos com dados
 * diferentes — liga/copa nacional tem país, continental tem confederação — e
 * um `when` sobre ele obriga quem consome (calendário, vagas) a tratar os dois.
 */
sealed interface CompetitionOwner {
    data class National(val country: Country) : CompetitionOwner

    data class Continental(val confederation: Confederation) : CompetitionOwner
}

/**
 * Uma competição do mundo: o [Competition] de domínio (formato e desempate,
 * issue #124) mais o que o pack sabe sobre ela.
 *
 * [tier] é a divisão em ligas nacionais (1 = Série A); `null` em copas e
 * continentais. [strength] é `null` onde não faz sentido (copas, continentais).
 */
data class WorldCompetition(
    val competition: Competition,
    val owner: CompetitionOwner,
    val tier: Int?,
    val strength: LeagueStrength?,
    val clubIds: List<WorldClubId>,
) {
    val id: CompetitionId get() = competition.id

    /** Liga de pontos corridos? (`is` com smart cast seria o `instanceof` do Java, sem o cast depois.) */
    val isLeague: Boolean get() = competition.format is CompetitionFormat.RoundRobin
}

/** Estádio: nome e capacidade são fatos públicos, por isso entram no pack. */
data class Stadium(val name: String, val capacity: Int) {
    init {
        require(name.isNotBlank()) { "Estádio sem nome" }
        require(capacity > 0) { "Capacidade do estádio deve ser positiva: $capacity" }
    }
}

/**
 * Clube como fato real: nome, país, estádio e reputação. Sem caixa, sem
 * escudo, sem uniforme (marcas registradas ficam fora do pack). [squad] é o
 * elenco real, só com fatos de cada jogador.
 */
data class ClubProfile(
    val id: WorldClubId,
    val name: String,
    val country: Country,
    val stadium: Stadium,
    val reputation: Reputation,
    val squad: List<PlayerProfile>,
) {
    init {
        require(name.isNotBlank()) { "Clube ${id.value} sem nome" }
    }

    /** `any` para em quem achar primeiro — o `stream().anyMatch` sem a cerimônia. */
    val hasGoalkeeper: Boolean get() = squad.any { it.position == Position.GK }
}

/**
 * Jogador como fato real. [displayName] troca o nome exibido por um simulado
 * (pedido de remoção, homônimo, jogador que não quer aparecer) SEM mudar o
 * [id] — saves, históricos e referências continuam apontando para o mesmo
 * jogador.
 */
data class PlayerProfile(
    val id: WorldPlayerId,
    val name: String,
    val birthDate: LocalDate,
    val nationality: Country,
    val position: Position,
    val secondNationality: Country? = null,
    val displayName: String? = null,
) {
    init {
        require(name.isNotBlank()) { "Jogador ${id.value} sem nome" }
        require(displayName == null || displayName.isNotBlank()) { "displayName vazio em ${id.value}" }
        require(secondNationality != nationality) { "Segunda nacionalidade repete a primeira em ${id.value}" }
    }

    /** O nome que o jogo mostra: o simulado, se houver; senão o real. */
    val shownName: String get() = displayName ?: name

    /** Idade completa em [date] — a idade do jogo é sempre relativa a uma data do calendário. */
    fun ageOn(date: LocalDate): Int = Period.between(birthDate, date).years
}

/**
 * Janela de transferência de UMA federação (aqui, o país: a CBF é o Brasil, a
 * FA é a Inglaterra). Só o dado; abrir e fechar o mercado por ela é a #137.
 */
data class TransferWindow(
    val country: Country,
    val start: LocalDate,
    val end: LocalDate,
) {
    init {
        require(!end.isBefore(start)) { "TransferWindow de ${country.code} termina ($end) antes de começar ($start)" }
    }

    /** `operator fun contains` habilita `date in window`, como em [FifaWindow]. */
    operator fun contains(date: LocalDate): Boolean = date in start..end
}

/** Datas FIFA e janelas de transferência de um ano (`calendar/{year}.json`). */
data class WorldCalendar(
    val year: Int,
    val fifaWindows: List<FifaWindow>,
    val transferWindows: List<TransferWindow>,
) {
    /** Janelas da federação [country] no ano. */
    fun transferWindowsOf(country: Country): List<TransferWindow> =
        transferWindows.filter { it.country == country }
}
