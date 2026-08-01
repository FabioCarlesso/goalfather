package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.domain.model.Availability
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.result.MedicalResult
import com.carlesso.goalfather.domain.rules.MEDICAL_DEPARTMENT_COST_CENTS
import com.carlesso.goalfather.domain.rules.MEDICAL_STAMINA_RECOVERY
import com.carlesso.goalfather.test.makeClub
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Departamento médico (issue #54). */
class TreatSquadServiceTest {

    private val clubRepo: ClubRepository = mockk()
    private val service = TreatSquadService(clubRepo)

    private val owner = 7L

    @Test
    fun `debita a taxa fixa e recupera o elenco`() = runTest {
        val club = makeClub(cash = 100_000_00, ownerId = owner, stamina = 50)
        coEvery { clubRepo.findById(ClubId(1)) } returns club
        val saved = slot<Club>()
        coEvery { clubRepo.save(capture(saved)) } answers { saved.captured }

        val result = service.execute(ClubId(1), owner)

        assertIs<MedicalResult.Success>(result)
        assertEquals(100_000_00 - MEDICAL_DEPARTMENT_COST_CENTS, saved.captured.cash)
        assertEquals(
            50 + MEDICAL_STAMINA_RECOVERY,
            saved.captured.squad.first().stamina,
        )
    }

    @Test
    fun `encurta a lesao em uma rodada`() = runTest {
        val club = makeClub(cash = 100_000_00, ownerId = owner).let {
            it.copy(squad = it.squad.mapIndexed { i, p ->
                if (i == 0) p.copy(availability = Availability.Injured(3)) else p
            })
        }
        coEvery { clubRepo.findById(ClubId(1)) } returns club
        val saved = slot<Club>()
        coEvery { clubRepo.save(capture(saved)) } answers { saved.captured }

        service.execute(ClubId(1), owner)

        assertEquals(Availability.Injured(2), saved.captured.squad.first().availability)
    }

    @Test
    fun `caixa insuficiente retorna erro como valor e nao persiste`() = runTest {
        // Erro de negócio é VALOR, não exception (CLAUDE.md).
        val club = makeClub(cash = MEDICAL_DEPARTMENT_COST_CENTS - 1, ownerId = owner)
        coEvery { clubRepo.findById(ClubId(1)) } returns club

        val result = service.execute(ClubId(1), owner)

        assertIs<MedicalResult.InsufficientFunds>(result)
        assertEquals(MEDICAL_DEPARTMENT_COST_CENTS - 1, result.available)
        assertEquals(MEDICAL_DEPARTMENT_COST_CENTS, result.required)
        coVerify(exactly = 0) { clubRepo.save(any()) }
    }

    @Test
    fun `caixa exatamente igual a taxa e suficiente`() = runTest {
        val club = makeClub(cash = MEDICAL_DEPARTMENT_COST_CENTS, ownerId = owner)
        coEvery { clubRepo.findById(ClubId(1)) } returns club
        val saved = slot<Club>()
        coEvery { clubRepo.save(capture(saved)) } answers { saved.captured }

        val result = service.execute(ClubId(1), owner)

        assertIs<MedicalResult.Success>(result)
        assertEquals(0, saved.captured.cash)
    }

    @Test
    fun `clube inexistente retorna ClubNotFound`() = runTest {
        coEvery { clubRepo.findById(ClubId(9)) } returns null

        assertIs<MedicalResult.ClubNotFound>(service.execute(ClubId(9), owner))
        coVerify(exactly = 0) { clubRepo.save(any()) }
    }

    @Test
    fun `solicitante que nao e dono retorna NotOwner sem cobrar`() = runTest {
        val club = makeClub(cash = 100_000_00, ownerId = owner)
        coEvery { clubRepo.findById(ClubId(1)) } returns club

        assertIs<MedicalResult.NotOwner>(service.execute(ClubId(1), owner + 1))
        coVerify(exactly = 0) { clubRepo.save(any()) }
    }
}
