package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Confederation
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.world.TransferWindow
import com.carlesso.goalfather.domain.model.world.TransferWindowScope
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Janelas de transferência (issue #137) — regras puras, datas nas bordas.
 * Janelas da CBF de 2026 (as da issue) e da FA.
 */
class TransferWindowRulesTest {

    private val bra = Countries.BRAZIL
    private val eng = Countries.ENGLAND
    private val windows = listOf(
        TransferWindow(bra, day(2026, 1, 5), day(2026, 3, 3)),
        TransferWindow(bra, day(2026, 3, 4), day(2026, 3, 27), TransferWindowScope.DOMESTIC_ONLY),
        TransferWindow(bra, day(2026, 7, 20), day(2026, 9, 11)),
        TransferWindow(bra, day(2027, 1, 4), day(2027, 3, 2)),
        TransferWindow(bra, day(2027, 7, 19), day(2027, 9, 10)),
        TransferWindow(eng, day(2026, 1, 1), day(2026, 2, 2)),
        TransferWindow(eng, day(2026, 6, 15), day(2026, 9, 1)),
    )

    private fun day(y: Int, m: Int, d: Int): LocalDate = LocalDate.of(y, m, d)

    @Test
    fun `bordas da janela - abre no primeiro dia e fecha depois do ultimo`() {
        assertEquals(WindowStatus.Closed(day(2026, 1, 5)), windowStatus(bra.code, day(2026, 1, 4), windows))
        assertEquals(
            WindowStatus.Open(day(2026, 3, 3), TransferWindowScope.INTERNATIONAL),
            windowStatus(bra.code, day(2026, 1, 5), windows),
        )
        assertEquals(
            WindowStatus.Open(day(2026, 3, 3), TransferWindowScope.INTERNATIONAL),
            windowStatus(bra.code, day(2026, 3, 3), windows),
        )
        // Dia seguinte: a janela só doméstica da CBF.
        assertEquals(
            WindowStatus.Open(day(2026, 3, 27), TransferWindowScope.DOMESTIC_ONLY),
            windowStatus(bra.code, day(2026, 3, 4), windows),
        )
        assertEquals(WindowStatus.Closed(day(2026, 7, 20)), windowStatus(bra.code, day(2026, 3, 28), windows))
    }

    @Test
    fun `depois da ultima janela do ano a proxima abertura e a do ano seguinte`() {
        assertEquals(WindowStatus.Closed(day(2027, 1, 4)), windowStatus(bra.code, day(2026, 9, 12), windows))
    }

    @Test
    fun `ano sem dado repete o ultimo ano conhecido da federacao`() {
        // A FA só tem 2026: em 2028 vale o padrão de 2026 no mesmo dia e mês.
        assertEquals(
            WindowStatus.Open(day(2028, 9, 1), TransferWindowScope.INTERNATIONAL),
            windowStatus(eng.code, day(2028, 7, 1), windows),
        )
        assertEquals(WindowStatus.Closed(day(2029, 1, 1)), windowStatus(eng.code, day(2028, 9, 2), windows))
    }

    @Test
    fun `federacao sem janela nenhuma fica fechada e sem data`() {
        assertEquals(WindowStatus.Closed(null), windowStatus(CountryCode("ARG"), day(2026, 1, 10), windows))
    }

    @Test
    fun `janela so domestica recusa transferencia entre paises e aceita dentro do pais`() {
        val domestic = day(2026, 3, 10)
        // De outro país: barrada, com a próxima abertura INTERNACIONAL (pula nenhuma doméstica).
        assertEquals(
            TransferBlock.WindowClosed(day(2026, 7, 20)),
            windowBlocker(bra.code, origin = eng.code, date = domestic, windows = windows),
        )
        assertNull(windowBlocker(bra.code, origin = bra.code, date = domestic, windows = windows))
        // Do mercado (sem clube de origem): aceita.
        assertNull(windowBlocker(bra.code, origin = null, date = domestic, windows = windows))
    }

    @Test
    fun `vale a janela do comprador`() {
        // 10/06: CBF fechada, FA fechada até 15/06; 20/06: a FA abriu e a CBF segue fechada.
        val june = day(2026, 6, 20)
        assertNull(windowBlocker(eng.code, origin = bra.code, date = june, windows = windows))
        assertEquals(TransferBlock.WindowClosed(day(2026, 7, 20)), windowBlocker(bra.code, eng.code, june, windows))
    }

    @Test
    fun `jogador registrado na janela nao pode ser registrado de novo nela`() {
        val previous = listOf(day(2026, 1, 10))
        assertEquals(
            TransferBlock.LimitReached(RegistrationLimit.ONE_PER_WINDOW),
            registrationBlocker(bra, origin = null, date = day(2026, 2, 1), windows = windows, previous = previous),
        )
        // Na janela seguinte (a doméstica), pode — é outra janela.
        assertNull(registrationBlocker(bra, bra.code, day(2026, 3, 10), windows, previous))
    }

    @Test
    fun `dois registros na temporada esgotam o limite`() {
        val previous = listOf(day(2026, 1, 10), day(2026, 3, 10))
        assertEquals(
            TransferBlock.LimitReached(RegistrationLimit.TWO_PER_SEASON),
            registrationBlocker(bra, null, day(2026, 7, 25), windows, previous),
        )
        // Temporada nova (ano civil no Brasil): zera.
        assertNull(registrationBlocker(bra, null, day(2027, 1, 10), windows, previous))
    }

    @Test
    fun `janela fechada vem antes do limite`() {
        assertEquals(
            TransferBlock.WindowClosed(day(2026, 7, 20)),
            registrationBlocker(bra, null, day(2026, 5, 1), windows, previous = listOf(day(2026, 1, 10), day(2026, 3, 10))),
        )
    }

    @Test
    fun `temporada de registro da UEFA vai de junho a maio`() {
        assertEquals(day(2026, 6, 1)..day(2027, 5, 31), registrationSeason(Confederation.UEFA, day(2027, 1, 15)))
        assertEquals(day(2026, 6, 1)..day(2027, 5, 31), registrationSeason(Confederation.UEFA, day(2026, 6, 15)))
        assertEquals(day(2025, 6, 1)..day(2026, 5, 31), registrationSeason(Confederation.UEFA, day(2026, 5, 31)))
        assertEquals(day(2026, 1, 1)..day(2026, 12, 31), registrationSeason(Confederation.CONMEBOL, day(2026, 7, 1)))
    }
}
