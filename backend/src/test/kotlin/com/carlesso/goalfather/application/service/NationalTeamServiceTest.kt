package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.WindowStatus
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.NationalWindowRepository
import com.carlesso.goalfather.domain.model.Availability
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.GameWeek
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.NationalMatchKind
import com.carlesso.goalfather.domain.model.NationalWindow
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.Slot
import com.carlesso.goalfather.domain.rules.interimFifaWindows
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.makePlayer
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * O relógio das datas FIFA no serviço (issue #134): entra na janela →
 * convoca; sai → joga e devolve. Ports com MockK (o clube "grava" num mapa) e
 * uma janela em memória; sem Spring.
 */
class NationalTeamServiceTest {

    private val march = interimFifaWindows(2026).first() // 23–31/03: semanas 13 e 14

    /** Clube brasileiro de 30 jogadores (ids 1001..1030), os primeiros com overall maior. */
    private val brazilClub: Club = makeClub(id = 1, name = "Flamengo").copy(
        squad = (1..30).map { i ->
            makePlayer(1_000L + i, position = Position.entries[i % 4], overall = 90 - i).copy(nationality = Countries.BRAZIL)
        },
    )

    /** Clube inglês só de ingleses (abaixo do corte de 11: ninguém convocado). */
    private val englishClub: Club = makeClub(id = 2, name = "Arsenal").copy(
        country = Countries.ENGLAND,
        squad = (1..10).map { i -> makePlayer(2_000L + i, overall = 80).copy(nationality = Countries.ENGLAND) },
    )

    private val clubs = mutableMapOf(brazilClub.id to brazilClub, englishClub.id to englishClub)
    private val windows = mutableMapOf<LocalDate, NationalWindow>()
    private var now: Matchday = Matchday(GameWeek(2026, 12), Slot.WEEKEND)

    private val clubRepo: ClubRepository = mockk {
        coEvery { findAll() } answers { clubs.values.toList() }
        // `value class` chega ao MockK desembrulhada (o `Long` cru): remonta o id.
        coEvery { findById(any()) } answers { clubs[ClubId(firstArg<Long>())] }
        coEvery { save(any()) } answers {
            val club = firstArg<Club>()
            clubs[club.id] = club
            ClubSaveResult.Saved(club)
        }
    }
    private val leagueRepo: LeagueRepository = mockk {
        coEvery { findLatest() } answers { Round(now, 2026, matches = emptyList()) }
    }
    private val windowRepo = object : NationalWindowRepository {
        override suspend fun findAll() = windows.values.sortedBy { it.start }
        override suspend fun find(start: LocalDate) = windows[start]
        override suspend fun save(window: NationalWindow) {
            windows[window.start] = window
        }
    }

    private val service = NationalTeamService(clubRepo, windowRepo, leagueRepo)

    private fun onDuty(): List<Long> =
        clubs.values.flatMap { it.squad }.filter { it.onInternationalDuty }.map { it.id.value }

    private suspend fun advanceTo(to: Matchday) {
        service.advance(now, to)
        now = to
    }

    @Test
    fun `antes da janela o tecnico ve a previa e ninguem sai`() = runTest {
        val preview = assertNotNull(service.clubCallUps(brazilClub.id))
        val window = assertNotNull(preview.window)
        assertEquals(WindowStatus.UPCOMING, window.status)
        assertEquals(march, window.window)
        assertTrue(preview.players.size in 23..26, "${preview.players.size}")
        assertTrue(preview.players.all { it.country.code == Countries.BRAZIL.code })
        assertEquals(emptyList(), onDuty())
        assertEquals(emptyList(), assertNotNull(service.clubCallUps(englishClub.id)).players)
    }

    @Test
    fun `entrar na janela convoca a previa e sair joga e devolve`() = runTest {
        val preview = assertNotNull(service.clubCallUps(brazilClub.id)).players.map { it.player.playerId.value }

        advanceTo(Matchday(GameWeek(2026, 13), Slot.MIDWEEK))
        assertEquals(preview.sorted(), onDuty().sorted(), "a convocação é a prévia")
        assertIs<NationalWindow.Called>(windows[march.start])
        val during = assertNotNull(service.clubCallUps(brazilClub.id))
        assertEquals(WindowStatus.CALLED, assertNotNull(during.window).status)
        val duty = clubs.getValue(brazilClub.id).squad.first { it.onInternationalDuty }.availability
        assertEquals(Availability.OnInternationalDuty(Countries.BRAZIL.code, march.end), duty)

        // Ainda dentro (semana 14): nada muda.
        advanceTo(Matchday(GameWeek(2026, 14), Slot.WEEKEND))
        assertEquals(preview.size, onDuty().size)

        // Saiu da janela: jogos disputados, todos de volta.
        advanceTo(Matchday(GameWeek(2026, 15), Slot.MIDWEEK))
        val played = assertIs<NationalWindow.Played>(windows[march.start])
        assertEquals(emptyList(), onDuty())
        assertTrue(played.matches.isNotEmpty())
        assertEquals(10, played.matches.count { it.kind == NationalMatchKind.QUALIFIER }, "rodadas 1 e 2: 5 jogos cada")
        // Quem jogou pela seleção voltou cansado.
        assertTrue(clubs.getValue(brazilClub.id).squad.any { it.stamina < 100 })

        val overview = service.overview()
        assertEquals(2, overview.qualifiers.roundsPlayed)
        assertEquals(10, overview.qualifiers.table.size)
        assertTrue(overview.ranking.isNotEmpty())
        assertEquals(march, overview.lastWindow?.window)
        assertEquals(interimFifaWindows(2026)[1], overview.nextWindow?.window)
        assertEquals(WindowStatus.PLAYED, overview.windows.first().status)
        assertEquals(8, overview.windows.size, "as datas de 2026 e 2027")
    }

    @Test
    fun `um salto por cima da janela inteira convoca e joga na mesma virada`() = runTest {
        advanceTo(Matchday(GameWeek(2026, 16), Slot.WEEKEND))
        assertIs<NationalWindow.Played>(windows[march.start])
        assertEquals(emptyList(), onDuty())
    }

    @Test
    fun `a janela e convocada e jogada uma vez so`() = runTest {
        advanceTo(Matchday(GameWeek(2026, 13), Slot.MIDWEEK))
        val called = windows.getValue(march.start)
        service.advance(Matchday(GameWeek(2026, 12), Slot.WEEKEND), now) // replay da mesma virada
        assertEquals(called, windows.getValue(march.start))

        advanceTo(Matchday(GameWeek(2026, 15), Slot.MIDWEEK))
        val played = windows.getValue(march.start)
        service.advance(Matchday(GameWeek(2026, 14), Slot.WEEKEND), now)
        assertEquals(played, windows.getValue(march.start))
    }

    @Test
    fun `clube inexistente nao tem convocados`() = runTest {
        assertEquals(null, service.clubCallUps(ClubId(999)))
    }
}
