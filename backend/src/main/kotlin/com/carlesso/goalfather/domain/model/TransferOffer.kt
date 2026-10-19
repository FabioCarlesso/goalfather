package com.carlesso.goalfather.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/** Identificador tipado de proposta (issue #63) — mesmo idioma de [PlayerId]/[ClubId]. */
@JvmInline
value class OfferId(val value: Long)

/**
 * Por que uma proposta expirou (issue #63). Duas causas com a mesma
 * consequência — a proposta morreu sem resposta —, mas a UI explica cada uma
 * de um jeito: "o prazo acabou" não é "o jogador já foi vendido a outro".
 */
@Serializable
enum class ExpiryReason {
    /** Passaram as rodadas do prazo sem o vendedor responder. */
    DEADLINE,

    /** O vendedor aceitou outra proposta pelo mesmo jogador. */
    PLAYER_TRANSFERRED,
}

/**
 * Ciclo de vida de uma proposta (issue #63) — a máquina de estados da issue.
 *
 * `sealed` porque só [Expired] carrega dado (o motivo): com um enum + um
 * `expiryReason` solto, "aceita com motivo de expiração" seria representável.
 * Aqui não é — mesmo raciocínio de [Availability].
 *
 * Só [Pending] tem saída: os outros quatro são TERMINAIS. A regra mora nas
 * transições de [TransferOffer] (`when` exaustivo), não espalhada em `if`s.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface OfferStatus {

    /** Aguardando o vendedor. */
    @Serializable
    @SerialName("Pending")
    data object Pending : OfferStatus

    /** Aceita: o jogador mudou de clube e o dinheiro, de mãos. */
    @Serializable
    @SerialName("Accepted")
    data object Accepted : OfferStatus

    /** Recusada pelo vendedor. */
    @Serializable
    @SerialName("Rejected")
    data object Rejected : OfferStatus

    /** Retirada pelo comprador antes da resposta. */
    @Serializable
    @SerialName("Withdrawn")
    data object Withdrawn : OfferStatus

    /** Morreu sem resposta — ver [ExpiryReason]. */
    @Serializable
    @SerialName("Expired")
    data class Expired(val reason: ExpiryReason) : OfferStatus
}

/**
 * Proposta em dinheiro de um técnico humano por um jogador do elenco de outro
 * (issue #63) — o agregado novo da issue, imutável como o resto do domínio.
 *
 * Os nomes (jogador e clubes) são uma FOTO do momento da proposta, como os
 * nomes de clube da `RoundMatch`: o histórico de propostas continua legível
 * mesmo depois que o jogador muda de clube, se aposenta ou sai do banco.
 *
 * [roundsLeft] é o prazo em rodadas, contado para baixo a cada rodada encerrada
 * ([afterRound]). Contador em vez de "rodada de vencimento" porque o número da
 * rodada recomeça a cada temporada — "rodada 3" não diz se é desta ou da próxima.
 *
 * Valores em centavos, como todo dinheiro do contrato. Sem `@Serializable`: a
 * API fala por um DTO (`TransferOfferDto`); só o [OfferStatus] cruza a
 * fronteira como está, porque a forma JSON dele já é a do contrato.
 */
data class TransferOffer(
    val id: OfferId,
    val buyerClubId: ClubId,
    val buyerClubName: String,
    val sellerClubId: ClubId,
    val sellerClubName: String,
    val playerId: PlayerId,
    val playerName: String,
    val amount: Long,
    val roundsLeft: Int,
    val status: OfferStatus = OfferStatus.Pending,
) {
    init {
        require(amount > 0) { "amount deve ser positivo: $amount" }
        require(roundsLeft >= 0) { "roundsLeft não pode ser negativo: $roundsLeft" }
        require(buyerClubId != sellerClubId) { "Clube não faz proposta a si mesmo: ${buyerClubId.value}" }
    }

    val isPending: Boolean get() = status == OfferStatus.Pending

    // ─── Transições ──────────────────────────────────────────────────────
    // Devolvem `null` quando a proposta já não está pendente: a transição não
    // existe a partir de um estado terminal. O chamador traduz o `null` no erro
    // de negócio certo olhando o `status` atual — ausência no tipo, sem exception.

    fun accept(): TransferOffer? = resolve(OfferStatus.Accepted)

    fun reject(): TransferOffer? = resolve(OfferStatus.Rejected)

    fun withdraw(): TransferOffer? = resolve(OfferStatus.Withdrawn)

    fun expire(reason: ExpiryReason): TransferOffer? = resolve(OfferStatus.Expired(reason))

    /**
     * Uma rodada a menos no prazo; a última rodada expira a proposta por
     * [ExpiryReason.DEADLINE]. Proposta já resolvida não muda — o relógio só
     * corre para quem ainda espera resposta.
     */
    fun afterRound(): TransferOffer = when (status) {
        OfferStatus.Pending ->
            if (roundsLeft <= 1) copy(roundsLeft = 0, status = OfferStatus.Expired(ExpiryReason.DEADLINE))
            else copy(roundsLeft = roundsLeft - 1)
        OfferStatus.Accepted, OfferStatus.Rejected, OfferStatus.Withdrawn, is OfferStatus.Expired -> this
    }

    /**
     * O coração da máquina de estados: só [OfferStatus.Pending] sai do lugar.
     * `when` exaustivo SEM `else` — um estado novo no `sealed` quebra a
     * compilação aqui até alguém decidir se ele é terminal ou não.
     */
    private fun resolve(next: OfferStatus): TransferOffer? = when (status) {
        OfferStatus.Pending -> copy(status = next)
        OfferStatus.Accepted, OfferStatus.Rejected, OfferStatus.Withdrawn, is OfferStatus.Expired -> null
    }
}
