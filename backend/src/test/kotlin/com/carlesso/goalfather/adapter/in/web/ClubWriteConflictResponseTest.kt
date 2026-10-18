package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.BuyPlayerRequest
import com.carlesso.goalfather.adapter.`in`.web.dto.ErrorResponse
import com.carlesso.goalfather.adapter.`in`.web.dto.ExpandStadiumRequest
import com.carlesso.goalfather.adapter.`in`.web.dto.LineupRequest
import com.carlesso.goalfather.application.port.`in`.BuyPlayerUseCase
import com.carlesso.goalfather.application.port.`in`.ExpandStadiumUseCase
import com.carlesso.goalfather.application.port.`in`.SaveLineupUseCase
import com.carlesso.goalfather.application.port.`in`.SetTicketPriceUseCase
import com.carlesso.goalfather.application.port.`in`.SetTrainingFocusUseCase
import com.carlesso.goalfather.application.port.`in`.TreatSquadUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.result.ExpandStadiumResult
import com.carlesso.goalfather.domain.result.LineupResult
import com.carlesso.goalfather.domain.result.MedicalResult
import com.carlesso.goalfather.domain.result.TransferResult
import com.carlesso.goalfather.test.makeClub
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.http.ResponseEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tradução do conflito de lock otimista do clube (issue #89) na borda HTTP.
 * Provocar a corrida de verdade via MockMvc não é determinístico — a corrida é
 * coberta em `ClubOptimisticLockIntegrationTest`; aqui os use cases são MockK
 * e os handlers `suspend` são chamados direto, sem subir contexto Spring.
 *
 * Critério de aceite: nenhum controller recebe exception de lock otimista — o
 * conflito chega como VALOR e sai como 409 `CLUB_WRITE_CONFLICT` (comandos do
 * clube) ou como a variante `Conflict` do `TransferResult` (mercado).
 */
class ClubWriteConflictResponseTest {

    private val clubId = ClubId(1)
    private val owner = 7L
    private val conflict = 409

    private val saveLineup: SaveLineupUseCase = mockk()
    private val treatSquad: TreatSquadUseCase = mockk()
    private val setTrainingFocus: SetTrainingFocusUseCase = mockk()
    private val setTicketPrice: SetTicketPriceUseCase = mockk()
    private val expandStadium: ExpandStadiumUseCase = mockk()
    private val clubRepo: ClubRepository = mockk()
    private val json = Json { classDiscriminator = "type" }

    private val clubs = ClubController(
        clubRepo = clubRepo,
        saveLineup = saveLineup,
        claimClub = mockk(),
        treatSquad = treatSquad,
        setTrainingFocus = setTrainingFocus,
        setTicketPrice = setTicketPrice,
        expandStadium = expandStadium,
        json = json,
    )

    private fun assertClubWriteConflict(response: ResponseEntity<Any>) {
        assertEquals(conflict, response.statusCode.value())
        assertEquals("CLUB_WRITE_CONFLICT", assertIs<ErrorResponse>(response.body).code)
    }

    @Test
    fun `comandos read-modify-write do clube traduzem Conflict em 409 CLUB_WRITE_CONFLICT`() = runTest {
        coEvery { saveLineup.execute(clubId, owner, any(), any(), any()) } returns LineupResult.Conflict(clubId)
        coEvery { treatSquad.execute(clubId, owner) } returns MedicalResult.Conflict(clubId)
        coEvery { expandStadium.execute(clubId, owner, any()) } returns ExpandStadiumResult.Conflict(clubId)

        assertClubWriteConflict(
            clubs.saveLineup(1, LineupRequest(Formation.F_4_4_2, List(11) { PlayerId(it + 1L) }), owner),
        )
        assertClubWriteConflict(clubs.treatSquad(1, owner))
        assertClubWriteConflict(clubs.expandStadium(1, ExpandStadiumRequest(1_000), owner))
    }

    @Test
    fun `compra com conflito no clube sai como variante Conflict do TransferResult`() = runTest {
        val buy: BuyPlayerUseCase = mockk()
        coEvery { clubRepo.findById(clubId) } returns makeClub(ownerId = owner)
        coEvery { buy.execute(clubId, PlayerId(99)) } returns TransferResult.Conflict(clubId)
        val market = MarketController(
            marketRepo = mockk(),
            clubRepo = clubRepo,
            buyUseCase = buy,
            sellUseCase = mockk(),
            meterRegistry = SimpleMeterRegistry(),
            json = json,
        )

        val response = market.buy(BuyPlayerRequest(clubId = 1, playerId = 99), owner)

        assertEquals(200, response.statusCode.value())
        val body = json.parseToJsonElement(assertIs<String>(response.body)).jsonObject
        assertEquals("Conflict", body.getValue("type").jsonPrimitive.content)
        assertEquals(1L, body.getValue("clubId").jsonPrimitive.content.toLong())
    }
}
