package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.result.OfferResult
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.makePlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Regras das propostas entre técnicos (issue #63) — sem Spring. */
class TransferOfferRulesTest {

    /** Elenco com um goleiro (id 1) e [size] - 1 jogadores de linha. */
    private fun squad(size: Int, goalkeepers: Int = 1) = (1L..size.toLong()).map { id ->
        makePlayer(id, position = if (id <= goalkeepers) Position.GK else Position.MF)
    }

    // ─── Elenco do vendedor ─────────────────────────────────────────────

    @Test
    fun `vender com elenco de sobra e mais de um goleiro e valido`() {
        assertNull(squadViolationAfterSale(squad(size = 14, goalkeepers = 2), PlayerId(1)))
        assertNull(squadViolationAfterSale(squad(size = 12), PlayerId(5)))
    }

    @Test
    fun `vendedor nao fica com menos de 11 jogadores`() {
        assertEquals(SquadViolation.TOO_FEW_PLAYERS, squadViolationAfterSale(squad(size = 11), PlayerId(5)))
    }

    @Test
    fun `vendedor nao entrega o ultimo goleiro`() {
        assertEquals(SquadViolation.NO_GOALKEEPER, squadViolationAfterSale(squad(size = 14), PlayerId(1)))
    }

    @Test
    fun `elenco que ja nao tinha goleiro pode vender jogador de linha`() {
        // A proposta não cria o problema — travar a venda puniria o técnico por
        // um estado anterior a ela.
        assertNull(squadViolationAfterSale(squad(size = 14, goalkeepers = 0), PlayerId(3)))
    }

    // ─── offerBlocker ───────────────────────────────────────────────────

    private val buyer = makeClub(id = 1, cash = 1_000_000_00, squadSize = 0, ownerId = 10)
    private val seller = makeClub(id = 2, cash = 0, squadSize = 0, ownerId = 20)
        .copy(squad = squad(size = 13).map { it.copy(id = PlayerId(200 + it.id.value)) })
    private val target = PlayerId(205)

    @Test
    fun `proposta valida nao tem bloqueio`() {
        assertNull(offerBlocker(buyer, seller, target, amount = 500_000_00))
    }

    @Test
    fun `valor abaixo do minimo`() {
        val blocker = offerBlocker(buyer, seller, target, amount = MIN_OFFER_CENTS - 1)
        assertEquals(OfferResult.InvalidAmount(MIN_OFFER_CENTS), blocker)
    }

    @Test
    fun `jogador fora do elenco do vendedor ou proposta ao proprio clube`() {
        assertIs<OfferResult.PlayerNotAvailable>(offerBlocker(buyer, seller, PlayerId(999), 500_000_00))
        assertIs<OfferResult.PlayerNotAvailable>(offerBlocker(seller, seller, target, 500_000_00))
    }

    @Test
    fun `clube da IA nao negocia`() {
        val ai = seller.copy(ownerId = null)
        assertEquals(OfferResult.SellerNotHuman(ClubId(2)), offerBlocker(buyer, ai, target, 500_000_00))
    }

    @Test
    fun `comprador sem caixa`() {
        val poor = buyer.copy(cash = 100_000_00)
        assertEquals(
            OfferResult.InsufficientFunds(available = 100_000_00, required = 500_000_00),
            offerBlocker(poor, seller, target, 500_000_00),
        )
    }

    @Test
    fun `comprador com elenco cheio`() {
        val full = buyer.copy(squad = (1L..MAX_SQUAD_SIZE.toLong()).map { makePlayer(it) })
        assertEquals(OfferResult.SquadFull(MAX_SQUAD_SIZE), offerBlocker(full, seller, target, 500_000_00))
    }

    @Test
    fun `vendedor que ficaria com elenco invalido`() {
        val thin = seller.copy(squad = seller.squad.take(11))
        assertEquals(
            OfferResult.SellerSquadInvalid(SquadViolation.TOO_FEW_PLAYERS, MIN_SQUAD_SIZE),
            offerBlocker(buyer, thin, target, 500_000_00),
        )
    }
}
