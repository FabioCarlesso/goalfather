package com.carlesso.goalfather.adapter.`in`.web.dto

import com.carlesso.goalfather.domain.model.Availability
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.Posture
import com.carlesso.goalfather.domain.model.TrainingFocus
import com.carlesso.goalfather.domain.result.TransferResult
import com.carlesso.goalfather.domain.rules.DEFAULT_TICKET_PRICE_CENTS
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

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
    val salary: Int,
    val age: Int,
    val goals: Int,
    val yellowCards: Int,
    val redCards: Int,
    val availability: Availability,
    /** Derivado no domínio (`overall >= 82`); materializado aqui para viajar no JSON. */
    val star: Boolean,
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
    val lineup: LineupDto? = null,
    val ownerId: Long? = null,
    /** Foco de treino em vigor (issue #58) — o card da Dashboard o pré-seleciona. */
    val trainingFocus: TrainingFocus = TrainingFocus.DEFAULT,
    /** Preço do ingresso em vigor, em centavos (issue #59). */
    val ticketPriceCents: Long = DEFAULT_TICKET_PRICE_CENTS,
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
}

// ─── Mapeamento domínio → DTO ─────────────────────────────────────────────

fun Player.toDto(): PlayerDto = PlayerDto(
    id = id.value,
    name = name,
    position = position,
    overall = overall,
    pace = pace,
    shooting = shooting,
    passing = passing,
    defending = defending,
    stamina = stamina,
    salary = salary,
    age = age,
    goals = goals,
    yellowCards = yellowCards,
    redCards = redCards,
    availability = availability,
    star = star,
)

fun Lineup.toDto(): LineupDto = LineupDto(
    formation = formation,
    playerIds = players.map { it.id.value },
    posture = tactics.posture,
)

fun Club.toDto(): ClubDto = ClubDto(
    id = id.value,
    name = name,
    cash = cash,
    stadiumCapacity = stadiumCapacity,
    squad = squad.map { it.toDto() },
    lineup = lineup?.toDto(),
    ownerId = ownerId,
    trainingFocus = trainingFocus,
    ticketPriceCents = ticketPriceCents,
)

fun MarketEntry.toDto(): MarketEntryDto = MarketEntryDto(player = player.toDto(), price = price)

/**
 * `when` exaustivo sobre o sealed de domínio — variante nova em
 * `TransferResult` quebra a compilação aqui, que é o ponto.
 */
fun TransferResult.toDto(): TransferResultDto = when (this) {
    is TransferResult.Success ->
        TransferResultDto.Success(club = club.toDto(), player = player.toDto())

    is TransferResult.InsufficientFunds ->
        TransferResultDto.InsufficientFunds(available = available, required = required)

    is TransferResult.SquadFull ->
        TransferResultDto.SquadFull(maxSize = maxSize)

    is TransferResult.PlayerNotAvailable ->
        TransferResultDto.PlayerNotAvailable(playerId = playerId.value)
}
