package com.carlesso.goalfather.adapter.`in`.web.dto

import com.carlesso.goalfather.domain.model.Availability
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.Employment
import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.Posture
import com.carlesso.goalfather.domain.model.TrainingFocus
import com.carlesso.goalfather.domain.model.leagueOf
import com.carlesso.goalfather.domain.result.TransferResult
import com.carlesso.goalfather.domain.rules.DEFAULT_TICKET_PRICE_CENTS
import com.carlesso.goalfather.domain.rules.competitionOf
import com.carlesso.goalfather.domain.rules.isForeignAt
import com.carlesso.goalfather.domain.rules.isInFinalSeason
import com.carlesso.goalfather.domain.rules.startingLineupIn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import java.time.LocalDate

/**
 * DTOs de RESPOSTA — a fronteira que faltava entre o domínio e a API.
 *
 * Até aqui os controllers devolviam os tipos de domínio direto (o débito
 * declarado em `Requests.kt`), e o resultado era um contrato que descrevia uma
 * API que o backend não implementava. Duas divergências concretas viviam nessa
 * fresta:
 *
 * 1. **`Lineup`** — o domínio guarda os jogadores INTEIROS (`players`) e a
 *    tática ANINHADA (`tactics.posture`), porque é isso que a engine precisa;
 *    o contrato sempre falou `playerIds` + `posture` achatado, que é o que a
 *    UI precisa. Quem consumisse a API real recebia uma escalação que o tipo
 *    gerado do OpenAPI não descrevia — a `LineupPage` não pré-preenchia nem os
 *    titulares nem a postura.
 * 2. **`Player.star`** — é uma *computed property* do domínio (`overall >= 82`),
 *    e `kotlinx.serialization` não serializa getters. O campo existia no
 *    contrato, a UI o lia (`DashboardPage`), e ele nunca chegava.
 *
 * Nenhuma das duas aparecia no CI porque o E2E roda contra o MSW, que
 * implementa o contrato — não o backend.
 *
 * A regra daqui para frente: **nada de domínio cruza o controller**. O tipo
 * mora aqui, o mapeamento também, e o compilador passa a ser quem cobra a
 * fidelidade ao contrato.
 *
 * `Availability`, `Position`, `Formation` e `Posture` são reusados do domínio
 * de propósito: são enums/soma de tipos cuja forma JSON já é EXATAMENTE a do
 * contrato (`Availability` já serializa com o discriminador `type`). Duplicá-los
 * só criaria duas fontes de verdade para o mesmo conjunto fechado de valores.
 */
@Serializable
data class PlayerDto(
    val id: Long,
    val name: String,
    val position: Position,
    val overall: Int,
    val pace: Int,
    val shooting: Int,
    val passing: Int,
    val defending: Int,
    val stamina: Int,
    val salary: Long,
    val age: Int,
    val goals: Int,
    val yellowCards: Int,
    val redCards: Int,
    val availability: Availability,
    /** Derivado no domínio (`overall >= 82`); materializado aqui para viajar no JSON. */
    val star: Boolean,
    /** Nacionalidade esportiva (issue #123). */
    val nationality: CountryDto,
    /** Segunda nacionalidade — ausente (não `null`) quando não há, por `explicitNulls = false`. */
    val secondNationality: CountryDto? = null,
    /** Suspensões em vigor, por competição (issue #130). Vazia quando não há. */
    val suspensions: List<SuspensionDto> = emptyList(),
    /**
     * Ocupa vaga de estrangeiro NO CLUBE dele (issue #130)? Só faz sentido no
     * elenco de um clube — no mercado o jogador não tem clube de referência, e
     * o campo fica ausente (`null` + `explicitNulls = false`).
     */
    val foreign: Boolean? = null,
    /**
     * Contrato em vigor (issue #136) — ausente para o livre (mercado). O
     * salário continua em [salary], que é o do contrato.
     */
    val contract: ContractDto? = null,
)

/**
 * Contrato como a API o descreve (issue #136). Datas ISO (`2028-12-31`), como
 * o resto do contrato. [finalSeason] é a regra do domínio
 * (`isInFinalSeason`) materializada aqui, para a UI só destacar — ela não
 * sabe a data do jogo nem quando a temporada termina.
 */
@Serializable
data class ContractDto(
    val start: String,
    val end: String,
    /** Cláusula de rescisão, em centavos — ausente quando não há. */
    val releaseClause: Long? = null,
    val finalSeason: Boolean,
)

/** Suspensão como a API a descreve: com o nome da competição para a UI exibir. */
@Serializable
data class SuspensionDto(
    val competitionId: String,
    val competitionName: String,
    val matchesLeft: Int,
)

/**
 * Escalação como a API a descreve: só os IDs (a UI já tem o elenco completo em
 * `Club.squad`, então mandar os jogadores de novo seria duplicação) e a postura
 * achatada, sem o nível `tactics`.
 */
@Serializable
data class LineupDto(
    val formation: Formation,
    val playerIds: List<Long>,
    val posture: Posture,
)

@Serializable
data class ClubDto(
    val id: Long,
    val name: String,
    val cash: Long,
    val stadiumCapacity: Int,
    val squad: List<PlayerDto>,
    /** Escalação SALVA pelo técnico — a foto do momento em que ele escalou. */
    val lineup: LineupDto? = null,
    /**
     * Escalação EFETIVA (issue #87): o time que a engine põe em campo se a
     * rodada começar agora — `Club.startingLineup()`, já com o fallback 4-4-2
     * e a troca de lesionados/vendidos. Sempre presente, para a UI nunca
     * precisar recalcular a regra de seleção de titulares.
     */
    val effectiveLineup: LineupDto,
    /** `true` quando não há escalação salva e a efetiva é a automática. */
    val lineupIsDefault: Boolean,
    /**
     * `true` quando HÁ escalação salva mas a efetiva difere dela (lesionado
     * ou vendido substituído por reserva). Separado de `lineupIsDefault` para
     * a UI poder explicar o motivo de cada aviso.
     */
    val lineupAdjusted: Boolean,
    val ownerId: Long? = null,
    /** Foco de treino em vigor (issue #58) — o card da Dashboard o pré-seleciona. */
    val trainingFocus: TrainingFocus = TrainingFocus.DEFAULT,
    /** Preço do ingresso em vigor, em centavos (issue #59). */
    val ticketPriceCents: Long = DEFAULT_TICKET_PRICE_CENTS.cents,
    /** País do clube (issue #123). */
    val country: CountryDto,
    /**
     * Máximo de estrangeiros na escalação (issue #130) — o da liga do clube.
     * Ausente = sem limite.
     */
    val foreignPlayerLimit: Int? = null,
)

@Serializable
data class MarketEntryDto(
    val player: PlayerDto,
    val price: Long,
)

/**
 * Espelha o `oneOf` + `discriminator` de `TransferResult` no contrato. Existe
 * separado do sealed de domínio apenas porque as variantes carregam `Club` e
 * `Player` — que precisam virar DTO.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface TransferResultDto {

    @Serializable
    @SerialName("Success")
    data class Success(val club: ClubDto, val player: PlayerDto) : TransferResultDto

    @Serializable
    @SerialName("InsufficientFunds")
    data class InsufficientFunds(val available: Long, val required: Long) : TransferResultDto

    @Serializable
    @SerialName("SquadFull")
    data class SquadFull(val maxSize: Int) : TransferResultDto

    @Serializable
    @SerialName("PlayerNotAvailable")
    data class PlayerNotAvailable(val playerId: Long) : TransferResultDto

    /** Lock otimista do clube (issue #89) — contrato `TransferConflict`. */
    @Serializable
    @SerialName("Conflict")
    data class Conflict(val clubId: Long) : TransferResultDto
}

// ─── Mapeamento domínio → DTO ─────────────────────────────────────────────

/**
 * [club] é o clube de referência para o "estrangeiro?" — passado só pelo
 * elenco de um clube; o mercado chama sem ele. [competitions] (o catálogo do
 * save, issue #131) dá o nome das competições das suspensões. [today] é a data
 * do jogo (issue #136), para o "último ano de contrato?"; sem ela, `false`.
 */
fun Player.toDto(
    club: Club? = null,
    competitions: List<GameCompetition> = emptyList(),
    today: LocalDate? = null,
): PlayerDto = PlayerDto(
    id = id.value,
    name = name,
    position = position,
    overall = overall,
    pace = pace,
    shooting = shooting,
    passing = passing,
    defending = defending,
    stamina = stamina,
    salary = salary.cents,
    age = age,
    goals = goals,
    yellowCards = yellowCards,
    redCards = redCards,
    availability = availability,
    star = star,
    nationality = nationality.toDto(),
    secondNationality = secondNationality?.toDto(),
    suspensions = suspensions.map {
        SuspensionDto(
            competitionId = it.competitionId.value,
            // Id desconhecido do catálogo cai no próprio id: legível, nunca vazio.
            competitionName = competitions.competitionOf(it.competitionId)?.name ?: it.competitionId.value,
            matchesLeft = it.matchesLeft,
        )
    },
    foreign = club?.let { isForeignAt(it) },
    contract = contract?.toDto(today),
)

fun Employment.UnderContract.toDto(today: LocalDate?): ContractDto = ContractDto(
    start = start.toString(),
    end = end.toString(),
    releaseClause = releaseClause?.cents,
    finalSeason = today?.let { isInFinalSeason(it) } ?: false,
)

fun Lineup.toDto(): LineupDto = LineupDto(
    formation = formation,
    playerIds = players.map { it.id.value },
    posture = tactics.posture,
)

/**
 * Corpo de expressão com `run`: o bloco calcula `startingLineup()` UMA vez
 * (`effective`) e a última expressão vira o retorno — sem precisar trocar o
 * `= ClubDto(...)` por um corpo com chaves e `return`.
 */
fun Club.toDto(competitions: List<GameCompetition> = emptyList(), today: LocalDate? = null): ClubDto = run {
    val saved = lineup?.toDto()
    // A escalação efetiva é a do próximo jogo de LIGA (issue #130): sem os
    // suspensos dela e dentro do limite de estrangeiros dela — a liga do clube
    // no catálogo do save (issue #131). Sem catálogo, sem limite.
    val league = competitions.leagueOf(this)?.competition
    val effective = (league?.let(::startingLineupIn) ?: startingLineup()).toDto()
    ClubDto(
        id = id.value,
        name = name,
        cash = cash.cents,
        stadiumCapacity = stadiumCapacity,
        squad = squad.map { it.toDto(club = this, competitions = competitions, today = today) },
        lineup = saved,
        effectiveLineup = effective,
        lineupIsDefault = saved == null,
        // `data class` gera `equals` estrutural: compara formação, ids (em
        // ordem) e postura de uma vez. Só os ids mudam em `startingLineup()`,
        // mas comparar o DTO inteiro dispensa saber disso.
        lineupAdjusted = saved != null && saved != effective,
        ownerId = ownerId,
        trainingFocus = trainingFocus,
        ticketPriceCents = ticketPriceCents.cents,
        country = country.toDto(),
        foreignPlayerLimit = league?.foreignPlayerLimit,
    )
}

fun MarketEntry.toDto(): MarketEntryDto = MarketEntryDto(player = player.toDto(), price = price.cents)

/**
 * `when` exaustivo sobre o sealed de domínio — variante nova em
 * `TransferResult` quebra a compilação aqui, que é o ponto.
 */
fun TransferResult.toDto(
    competitions: List<GameCompetition> = emptyList(),
    today: LocalDate? = null,
): TransferResultDto = when (this) {
    is TransferResult.Success ->
        TransferResultDto.Success(club = club.toDto(competitions, today), player = player.toDto(today = today))

    is TransferResult.InsufficientFunds ->
        TransferResultDto.InsufficientFunds(available = available.cents, required = required.cents)

    is TransferResult.SquadFull ->
        TransferResultDto.SquadFull(maxSize = maxSize)

    is TransferResult.PlayerNotAvailable ->
        TransferResultDto.PlayerNotAvailable(playerId = playerId.value)

    is TransferResult.Conflict ->
        TransferResultDto.Conflict(clubId = clubId.value)
}
