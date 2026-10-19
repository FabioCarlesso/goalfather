package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.result.OfferResult
import kotlinx.serialization.Serializable

/**
 * Regras das propostas entre técnicos humanos (issue #63). Funções PURAS,
 * testáveis sem Spring — o use case só busca os clubes e grava o desfecho.
 */

/** Rodadas que o vendedor tem para responder antes de a proposta expirar. */
const val OFFER_DEADLINE_ROUNDS: Int = 3

/** Menor proposta aceita, em centavos (R$ 1.000) — proposta simbólica não é negociação. */
const val MIN_OFFER_CENTS: Long = 1_000_00

/** Menor elenco com que um clube pode ficar depois de vender: um time em campo. */
const val MIN_SQUAD_SIZE: Int = 11

/** Maior elenco que um clube pode ter — o mesmo teto da compra no mercado. */
const val MAX_SQUAD_SIZE: Int = 25

/**
 * Por que o elenco do vendedor ficaria inválido sem o jogador. Enum (e não
 * só um `Boolean`) porque a UI explica cada caso de um jeito.
 */
@Serializable
enum class SquadViolation {
    /** Ficaria com menos de [MIN_SQUAD_SIZE] jogadores. */
    TOO_FEW_PLAYERS,

    /** Venderia o último goleiro. */
    NO_GOALKEEPER,
}

/**
 * O que a venda de [playerId] faria com [squad], ou `null` se o elenco que
 * sobra continua jogável.
 *
 * O goleiro só conta quando é ELE que sai: um elenco que já não tinha goleiro
 * não fica "mais inválido" vendendo um atacante, e travar essa venda puniria
 * o técnico por um estado que a proposta não criou.
 */
fun squadViolationAfterSale(squad: List<Player>, playerId: PlayerId): SquadViolation? {
    val sold = squad.firstOrNull { it.id == playerId }
    val remaining = squad.filterNot { it.id == playerId }
    return when {
        remaining.size < MIN_SQUAD_SIZE -> SquadViolation.TOO_FEW_PLAYERS
        sold?.position == Position.GK && remaining.none { it.position == Position.GK } ->
            SquadViolation.NO_GOALKEEPER
        else -> null
    }
}

/**
 * Primeiro motivo de negócio que impede [buyer] de levar [playerId] de
 * [seller] por [amount], ou `null` se a transferência pode acontecer. Os dois
 * lados precisam ter técnico humano: proposta é negociação entre técnicos.
 *
 * Roda DUAS vezes na vida de uma proposta: ao criar (feedback imediato ao
 * comprador) e ao aceitar — a checagem que vale, porque entre uma e outra o
 * caixa do comprador e o elenco dos dois clubes podem ter mudado.
 *
 * A ordem é a de quem lê a mensagem: primeiro o que invalida a proposta em si
 * (valor, jogador, vendedor), depois o lado do comprador, por fim o do vendedor.
 */
fun offerBlocker(
    buyer: Club,
    seller: Club,
    playerId: PlayerId,
    amount: Long,
    maxSquadSize: Int = MAX_SQUAD_SIZE,
): OfferResult.Rejection? {
    val inSellerSquad = seller.squad.any { it.id == playerId }
    val violation = squadViolationAfterSale(seller.squad, playerId)
    return when {
        amount < MIN_OFFER_CENTS -> OfferResult.InvalidAmount(minimum = MIN_OFFER_CENTS)
        buyer.id == seller.id || !inSellerSquad -> OfferResult.PlayerNotAvailable(playerId)
        seller.ownerId == null -> OfferResult.SellerNotHuman(seller.id)
        buyer.ownerId == null -> OfferResult.BuyerNotHuman(buyer.id)
        buyer.cash < amount -> OfferResult.InsufficientFunds(available = buyer.cash, required = amount)
        buyer.squad.size >= maxSquadSize -> OfferResult.SquadFull(maxSize = maxSquadSize)
        violation != null -> OfferResult.SellerSquadInvalid(violation, MIN_SQUAD_SIZE)
        else -> null
    }
}
