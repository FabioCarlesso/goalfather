package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.domain.model.BoardExpectation
import com.carlesso.goalfather.domain.model.BoardObjective
import com.carlesso.goalfather.domain.model.ClubId
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Meta da diretoria persistida no clube (issue #62) contra o H2 real, com a
 * migration V13 aplicada: o seed já nasce com meta e ela atravessa o `save`.
 */
@SpringBootTest
class ClubBoardPersistenceIntegrationTest {

    @Autowired private lateinit var clubRepo: ClubRepository

    @Test
    fun `seed nasce com a meta da diretoria de todos os clubes`() = runBlocking {
        val clubs = clubRepo.findAll()

        // Uma meta de título por divisão de 6 (o favorito de cada uma).
        val boards = clubs.map { assertNotNull(it.board, "clube ${it.id.value} sem meta") }
        assertEquals(clubs.map { it.division }.distinct().size, boards.count { it.objective == BoardObjective.TITLE })
    }

    @Test
    fun `meta e confianca atravessam o save e a releitura`() = runBlocking {
        // Clube da 2ª divisão (ids 7..12): fora do caminho dos testes de claim.
        val club = assertNotNull(clubRepo.findById(ClubId(12)))
        val board = BoardExpectation(season = 2031, objective = BoardObjective.TOP_HALF, targetPosition = 3, confidence = 17)

        assertIs<ClubSaveResult.Saved>(clubRepo.save(club.copy(board = board)))

        assertEquals(board, clubRepo.findById(ClubId(12))?.board)
        // Devolve a meta original para não vazar estado para outras suítes.
        val reloaded = assertNotNull(clubRepo.findById(ClubId(12)))
        assertIs<ClubSaveResult.Saved>(clubRepo.save(reloaded.copy(board = club.board)))
    }
}
