package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.domain.model.Availability
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.result.LineupResult
import com.carlesso.goalfather.test.makeClub
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SaveLineupServiceTest {

    private val clubRepo: ClubRepository = mockk()
    private val service = SaveLineupService(clubRepo)

    private val owner = 7L

    @Test
    fun `salva escalacao valida e retorna Success com clube atualizado`() = runTest {
        val club = makeClub(squadSize = 15, ownerId = owner)
        val ids = club.squad.take(11).map { it.id }
        coEvery { clubRepo.findById(ClubId(1)) } returns club
        val saved = slot<Club>()
        coEvery { clubRepo.save(capture(saved)) } answers { saved.captured }

        val result = service.execute(ClubId(1), owner, Formation.F_4_4_2, ids)

        assertIs<LineupResult.Success>(result)
        assertEquals(11, saved.captured.lineup?.players?.size)
        assertEquals(Formation.F_4_4_2, saved.captured.lineup?.formation)
        // Ordem preservada
        assertEquals(ids, saved.captured.lineup?.players?.map { it.id })
    }

    @Test
    fun `clube inexistente retorna ClubNotFound`() = runTest {
        coEvery { clubRepo.findById(ClubId(999)) } returns null

        val result = service.execute(ClubId(999), owner, Formation.F_4_4_2, emptyList())

        assertIs<LineupResult.ClubNotFound>(result)
        assertEquals(ClubId(999), result.clubId)
        coVerify(exactly = 0) { clubRepo.save(any()) }
    }

    @Test
    fun `solicitante que nao e dono retorna NotOwner sem persistir`() = runTest {
        val club = makeClub(squadSize = 15, ownerId = owner)
        coEvery { clubRepo.findById(ClubId(1)) } returns club

        val result = service.execute(ClubId(1), owner + 1, Formation.F_4_4_2, club.squad.take(11).map { it.id })

        assertIs<LineupResult.NotOwner>(result)
        assertEquals(ClubId(1), result.clubId)
        coVerify(exactly = 0) { clubRepo.save(any()) }
    }

    @Test
    fun `menos de 11 retorna IncompleteLineup`() = runTest {
        val club = makeClub(squadSize = 15, ownerId = owner)
        coEvery { clubRepo.findById(ClubId(1)) } returns club

        val result = service.execute(ClubId(1), owner, Formation.F_4_4_2, club.squad.take(5).map { it.id })

        assertIs<LineupResult.IncompleteLineup>(result)
        assertEquals(11, result.expected)
        assertEquals(5, result.actual)
    }

    @Test
    fun `IDs fora do elenco retornam PlayersNotInSquad`() = runTest {
        val club = makeClub(squadSize = 11, ownerId = owner)
        coEvery { clubRepo.findById(ClubId(1)) } returns club

        val foreignIds = (100L..110L).map { PlayerId(it) }
        val result = service.execute(ClubId(1), owner, Formation.F_4_4_2, foreignIds)

        assertIs<LineupResult.PlayersNotInSquad>(result)
        assertEquals((100L..110L).toList(), result.missingIds)
    }

    @Test
    fun `escalar lesionado retorna InjuredPlayers sem persistir`() = runTest {
        // Issue #54: lesionado não entra em campo enquanto o afastamento durar.
        val base = makeClub(squadSize = 15, ownerId = owner)
        val club = base.copy(
            squad = base.squad.mapIndexed { i, p ->
                if (i == 3) p.copy(availability = Availability.Injured(2)) else p
            },
        )
        coEvery { clubRepo.findById(ClubId(1)) } returns club

        val result = service.execute(ClubId(1), owner, Formation.F_4_4_2, club.squad.take(11).map { it.id })

        assertIs<LineupResult.InjuredPlayers>(result)
        assertEquals(listOf(club.squad[3].id.value), result.playerIds)
        coVerify(exactly = 0) { clubRepo.save(any()) }
    }

    @Test
    fun `escalacao sem o lesionado continua valida`() = runTest {
        val base = makeClub(squadSize = 15, ownerId = owner)
        val club = base.copy(
            squad = base.squad.mapIndexed { i, p ->
                if (i == 3) p.copy(availability = Availability.Injured(2)) else p
            },
        )
        coEvery { clubRepo.findById(ClubId(1)) } returns club
        val saved = slot<Club>()
        coEvery { clubRepo.save(capture(saved)) } answers { saved.captured }

        val healthy = club.squad.filterNot { it.injured }.take(11).map { it.id }
        val result = service.execute(ClubId(1), owner, Formation.F_4_4_2, healthy)

        assertIs<LineupResult.Success>(result)
        assertEquals(healthy, saved.captured.lineup?.players?.map { it.id })
    }

    @Test
    fun `mistura de IDs validos e invalidos retorna PlayersNotInSquad com apenas os invalidos`() = runTest {
        val club = makeClub(squadSize = 11, ownerId = owner)
        coEvery { clubRepo.findById(ClubId(1)) } returns club

        val valid = club.squad.take(10).map { it.id }
        val invalid = listOf(PlayerId(999))
        val mixed = valid + invalid

        val result = service.execute(ClubId(1), owner, Formation.F_4_4_2, mixed)

        assertIs<LineupResult.PlayersNotInSquad>(result)
        assertEquals(listOf(999L), result.missingIds)
    }
}
