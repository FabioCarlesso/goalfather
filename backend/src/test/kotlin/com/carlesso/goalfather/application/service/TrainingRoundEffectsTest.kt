package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.RoundReadinessRepository
import com.carlesso.goalfather.domain.event.RoundEvent
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.domain.model.TrainingFocus
import com.carlesso.goalfather.domain.rules.STAMINA_MATCH_FLOOR
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.makePlayer
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O treino da semana (issue #58) aplicado na virada da rodada: uma vez só,
 * com o foco de cada clube, e reportado no `RoundFinished`.
 *
 * Sem `@SpringBootTest` — o service é construído com ports mockados, como o
 * resto de `application/service`.
 */
class TrainingRoundEffectsTest {

    private val clubRepo: ClubRepository = mockk()
    private val leagueRepo: LeagueRepository = mockk()
    private val readinessRepo: RoundReadinessRepository = mockk(relaxed = true)
    private val service =
        PlayRoundService(clubRepo, leagueRepo, readinessRepo, mockk(relaxed = true), mockk(relaxed = true))

    // Elenco jovem e cansado: o foco FISICO tem o que recuperar, e a chance de
    // evolução da faixa YOUNG é a mais alta — o efeito aparece sem depender de
    // uma seed "de sorte".
    private val home = makeClub(id = 1, name = "Home FC", ownerId = 7)
        .copy(
            squad = (1L..11L).map { makePlayer(it, overall = 70, age = 20, stamina = 50) },
            trainingFocus = TrainingFocus.FISICO,
        )
    private val away = makeClub(id = 2, name = "Away FC")
        .copy(squad = (101L..111L).map { makePlayer(it, overall = 70, age = 20, stamina = 50) })

    private val round = Round(
        number = 1,
        season = 2026,
        matches = listOf(RoundMatch(1001, ClubId(1), ClubId(2), "Home FC", "Away FC")),
    )

    private val standings = Standings(
        season = 2026,
        round = 0,
        rows = listOf(
            StandingRow(1, ClubId(1), "Home FC"),
            StandingRow(2, ClubId(2), "Away FC"),
        ),
    )

    private fun stubRound(status: RoundStatus = RoundStatus.Scheduled) {
        coEvery { leagueRepo.findRound(1) } returns round.copy(status = status)
        coEvery { leagueRepo.currentStandings() } returns listOf(standings)
        coEvery { clubRepo.findById(ClubId(1)) } returns home
        coEvery { clubRepo.findById(ClubId(2)) } returns away
        // 4 clubes → temporada de 3 rodadas: a rodada 1 não vira temporada,
        // senão a pré-temporada zeraria a forma física e mascararia o treino.
        coEvery { clubRepo.findAll() } returns listOf(
            home,
            away,
            makeClub(id = 3, name = "C3"),
            makeClub(id = 4, name = "C4"),
        )
        coEvery { leagueRepo.saveRound(any()) } just Runs
        coEvery { leagueRepo.finishRound(any()) } returns true
        coEvery { leagueRepo.saveStandings(any()) } just Runs
    }

    @Test
    fun `rodada aplica o foco de cada clube e reporta no RoundFinished`() = runTest {
        stubRound()
        val savedClubs = mutableListOf<Club>()
        coEvery { clubRepo.save(capture(savedClubs)) } answers { firstArg() }

        val finished = service.stream(1).toList()
            .filterIsInstance<RoundEvent.RoundFinished>()
            .single()

        assertEquals(
            listOf(ClubId(1), ClubId(2), ClubId(3), ClubId(4)),
            finished.training.map { it.clubId },
            "todo clube ganha relatório de treino, mesmo sem eventos",
        )
        assertEquals(TrainingFocus.FISICO, finished.training.first().focus)
        assertTrue(
            finished.training.drop(1).all { it.focus == TrainingFocus.DESCANSO },
            "clube da IA treina no default",
        )
    }

    @Test
    fun `recuperacao do foco entra por cima do desgaste da rodada`() = runTest {
        stubRound()
        val savedClubs = mutableListOf<Club>()
        coEvery { clubRepo.save(capture(savedClubs)) } answers { firstArg() }

        service.stream(1).toList()

        val trained = savedClubs.last { it.id == ClubId(1) }
        val rival = savedClubs.last { it.id == ClubId(2) }
        // Os dois elencos entraram com 50 e são 11 titulares: qualquer sorteio
        // de desgaste (10..25) leva ao piso de partida, então o que sobra é
        // exatamente o piso + a recuperação do foco. Determinístico sem
        // depender da seed, e mostra o treino entrando POR CIMA do desgaste.
        for (player in trained.squad) {
            assertEquals(STAMINA_MATCH_FLOOR + TrainingFocus.FISICO.staminaRecovery, player.stamina)
        }
        for (player in rival.squad) {
            assertEquals(STAMINA_MATCH_FLOOR + TrainingFocus.DESCANSO.staminaRecovery, player.stamina)
        }
    }

    @Test
    fun `o que o relatorio anuncia esta no elenco gravado`() = runTest {
        // Regressão da review do PR #74: o relatório sai de `train(...)` e o
        // `save` recebe o elenco treinado — mas eram duas leituras
        // independentes. Se o save voltasse a persistir o elenco PRÉ-treino,
        // todo o resto do arquivo continuaria verde e o técnico veria uma
        // evolução que o elenco não teria.
        stubRound()
        val savedClubs = mutableListOf<Club>()
        coEvery { clubRepo.save(capture(savedClubs)) } answers { firstArg() }

        val finished = service.stream(1).toList()
            .filterIsInstance<RoundEvent.RoundFinished>()
            .single()

        val report = finished.training.single { it.clubId == ClubId(1) }
        assertTrue(
            report.events.isNotEmpty(),
            "seed fixa da rodada 1/clube 1 deveria render eventos — sem eles o teste não prova nada",
        )

        val persisted = savedClubs.last { it.id == ClubId(1) }.squad.associateBy { it.id }
        for (event in report.events) {
            val onSquad = persisted.getValue(event.player.id)
            assertEquals(event.player, onSquad, "evento anunciou estado que não foi gravado")
        }
    }

    @Test
    fun `replay de rodada ja encerrada nao treina de novo`() = runTest {
        // Reconexão ao WS (issue #46): a rodada já consta `Finished`, então os
        // efeitos não são reaplicados — nem o treino.
        stubRound(status = RoundStatus.Finished)

        val finished = service.stream(1).toList()
            .filterIsInstance<RoundEvent.RoundFinished>()
            .single()

        assertTrue(finished.training.isEmpty())
        coVerify(exactly = 0) { clubRepo.save(any()) }
    }
}
