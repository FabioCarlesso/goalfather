package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.TrainingFocus
import com.carlesso.goalfather.domain.result.TrainingFocusResult
import com.carlesso.goalfather.test.makeClub
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Escolha do foco de treino da semana (issue #58). */
class SetTrainingFocusServiceTest {

    private val clubRepo: ClubRepository = mockk()
    private val service = SetTrainingFocusService(clubRepo)

    private val owner = 7L

    @Test
    fun `grava o foco escolhido no clube`() = runTest {
        val club = makeClub(ownerId = owner)
        coEvery { clubRepo.findById(ClubId(1)) } returns club
        val saved = slot<Club>()
        coEvery { clubRepo.save(capture(saved)) } answers { saved.captured }

        val result = service.execute(ClubId(1), owner, TrainingFocus.ATAQUE)

        assertIs<TrainingFocusResult.Success>(result)
        assertEquals(TrainingFocus.ATAQUE, saved.captured.trainingFocus)
        assertEquals(TrainingFocus.ATAQUE, result.club.trainingFocus)
    }

    @Test
    fun `clube novo comeca em DESCANSO`() = runTest {
        assertEquals(TrainingFocus.DESCANSO, makeClub().trainingFocus)
    }

    @Test
    fun `repetir o foco atual nao grava de novo`() = runTest {
        val club = makeClub(ownerId = owner).copy(trainingFocus = TrainingFocus.FISICO)
        coEvery { clubRepo.findById(ClubId(1)) } returns club

        val result = service.execute(ClubId(1), owner, TrainingFocus.FISICO)

        assertIs<TrainingFocusResult.Success>(result)
        coVerify(exactly = 0) { clubRepo.save(any()) }
    }

    @Test
    fun `clube inexistente retorna ClubNotFound`() = runTest {
        coEvery { clubRepo.findById(ClubId(9)) } returns null

        assertIs<TrainingFocusResult.ClubNotFound>(
            service.execute(ClubId(9), owner, TrainingFocus.DEFESA),
        )
        coVerify(exactly = 0) { clubRepo.save(any()) }
    }

    @Test
    fun `solicitante que nao e dono retorna NotOwner`() = runTest {
        coEvery { clubRepo.findById(ClubId(1)) } returns makeClub(ownerId = owner)

        assertIs<TrainingFocusResult.NotOwner>(
            service.execute(ClubId(1), owner + 1, TrainingFocus.DEFESA),
        )
        coVerify(exactly = 0) { clubRepo.save(any()) }
    }
}
