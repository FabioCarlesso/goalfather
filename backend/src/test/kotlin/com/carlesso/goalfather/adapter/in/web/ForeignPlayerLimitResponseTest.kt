package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.ErrorResponse
import com.carlesso.goalfather.adapter.`in`.web.dto.LineupRequest
import com.carlesso.goalfather.application.port.`in`.SaveLineupUseCase
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.result.LineupResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tradução do limite de estrangeiros (issue #130) na borda HTTP: o use case
 * devolve o erro como VALOR e o controller o mapeia em 400
 * `TOO_MANY_FOREIGN_PLAYERS`, com o limite e os ids no `details`. Use case
 * MockK e handler `suspend` chamado direto — sem contexto Spring, como em
 * [ClubWriteConflictResponseTest].
 */
class ForeignPlayerLimitResponseTest {

    private val saveLineup: SaveLineupUseCase = mockk()

    private val clubs = ClubController(
        clubRepo = mockk(),
        saveLineup = saveLineup,
        claimClub = mockk(),
        treatSquad = mockk(),
        setTrainingFocus = mockk(),
        setTicketPrice = mockk(),
        expandStadium = mockk(),
        board = mockk(),
        json = Json { classDiscriminator = "type" },
    )

    @Test
    fun `escalacao acima do limite de estrangeiros responde 400 TOO_MANY_FOREIGN_PLAYERS`() = runTest {
        coEvery { saveLineup.execute(ClubId(1), 7L, any(), any(), any()) } returns
            LineupResult.TooManyForeignPlayers(limit = 9, playerIds = (1L..10L).toList())

        val response = clubs.saveLineup(1, LineupRequest(Formation.F_4_4_2, List(11) { PlayerId(it + 1L) }), 7L)

        assertEquals(400, response.statusCode.value())
        val error = assertIs<ErrorResponse>(response.body)
        assertEquals("TOO_MANY_FOREIGN_PLAYERS", error.code)
        assertEquals("9", error.details["limit"])
        assertEquals("1,2,3,4,5,6,7,8,9,10", error.details["playerIds"])
    }
}
