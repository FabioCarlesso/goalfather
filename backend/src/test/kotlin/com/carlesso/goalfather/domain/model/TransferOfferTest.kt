package com.carlesso.goalfather.domain.model

import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Máquina de estados da proposta (issue #63) — sem Spring. */
class TransferOfferTest {

    private fun offer(roundsLeft: Int = 3, status: OfferStatus = OfferStatus.Pending) = TransferOffer(
        id = OfferId(1),
        buyerClubId = ClubId(1),
        buyerClubName = "Comprador",
        sellerClubId = ClubId(2),
        sellerClubName = "Vendedor",
        playerId = PlayerId(10),
        playerName = "Craque",
        amount = 500_000_00,
        roundsLeft = roundsLeft,
        status = status,
    )

    private val terminals = listOf(
        OfferStatus.Accepted,
        OfferStatus.Rejected,
        OfferStatus.Withdrawn,
        OfferStatus.Expired(ExpiryReason.DEADLINE),
    )

    @Test
    fun `pendente aceita, recusa, retira e expira`() {
        assertEquals(OfferStatus.Accepted, offer().accept()?.status)
        assertEquals(OfferStatus.Rejected, offer().reject()?.status)
        assertEquals(OfferStatus.Withdrawn, offer().withdraw()?.status)
        assertEquals(
            OfferStatus.Expired(ExpiryReason.PLAYER_TRANSFERRED),
            offer().expire(ExpiryReason.PLAYER_TRANSFERRED)?.status,
        )
    }

    @Test
    fun `estado terminal nao tem transicao de saida`() {
        for (status in terminals) {
            val closed = offer(status = status)
            assertNull(closed.accept(), "accept a partir de $status")
            assertNull(closed.reject(), "reject a partir de $status")
            assertNull(closed.withdraw(), "withdraw a partir de $status")
            assertNull(closed.expire(ExpiryReason.DEADLINE), "expire a partir de $status")
        }
    }

    @Test
    fun `cada rodada consome uma rodada do prazo`() {
        val after = offer(roundsLeft = 3).afterRound()

        assertEquals(2, after.roundsLeft)
        assertEquals(OfferStatus.Pending, after.status)
    }

    @Test
    fun `a ultima rodada do prazo expira a proposta por DEADLINE`() {
        val expired = offer(roundsLeft = 3).afterRound().afterRound().afterRound()

        assertEquals(0, expired.roundsLeft)
        assertEquals(OfferStatus.Expired(ExpiryReason.DEADLINE), expired.status)
    }

    @Test
    fun `proposta resolvida nao anda no relogio`() {
        for (status in terminals) {
            val closed = offer(roundsLeft = 2, status = status)
            assertEquals(closed, closed.afterRound(), "afterRound a partir de $status")
        }
    }

    @Test
    fun `virada expira a proposta cujo jogador saiu do vendedor (revisao PR 94)`() {
        val gone = offer(roundsLeft = 3).advance(playerStillWithSeller = false)
        assertEquals(OfferStatus.Expired(ExpiryReason.PLAYER_TRANSFERRED), gone.status)

        // Com o jogador no elenco, é só o relógio andando.
        assertEquals(offer(roundsLeft = 3).afterRound(), offer(roundsLeft = 3).advance(playerStillWithSeller = true))
        // Resolvida não muda, saia o jogador ou não.
        val accepted = offer(status = OfferStatus.Accepted)
        assertEquals(accepted, accepted.advance(playerStillWithSeller = false))
    }

    @Test
    fun `involves reconhece os dois lados`() {
        assertEquals(true, offer().involves(ClubId(1)))
        assertEquals(true, offer().involves(ClubId(2)))
        assertEquals(false, offer().involves(ClubId(3)))
    }

    @Test
    fun `invariantes - valor positivo e clubes distintos`() {
        assertThrows<IllegalArgumentException> { offer().copy(amount = 0) }
        assertThrows<IllegalArgumentException> { offer().copy(sellerClubId = ClubId(1)) }
    }
}
