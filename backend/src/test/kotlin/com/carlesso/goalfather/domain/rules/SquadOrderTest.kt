package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.Position.CB
import com.carlesso.goalfather.domain.model.Position.FW
import com.carlesso.goalfather.domain.model.Position.GK
import com.carlesso.goalfather.domain.model.Position.MF
import com.carlesso.goalfather.test.makePlayer
import kotlin.test.Test
import kotlin.test.assertEquals

class SquadOrderTest {

    private var nextId = 0L
    private fun squadOf(vararg players: Pair<Position, Int>) = players.map { (position, overall) ->
        makePlayer(id = ++nextId, position = position, overall = overall)
    }

    @Test
    fun `os 11 primeiros sao o melhor 4-4-2, mesmo com o pack listando os goleiros primeiro`() {
        // Ordem do pack: 3 goleiros, 8 zagueiros, 7 meias, 5 atacantes.
        val squad = squadOf(
            GK to 70, GK to 75, GK to 60,
            CB to 71, CB to 72, CB to 73, CB to 74, CB to 65, CB to 66, CB to 67, CB to 68,
            MF to 80, MF to 79, MF to 78, MF to 77, MF to 60, MF to 61, MF to 62,
            FW to 85, FW to 70, FW to 84, FW to 50, FW to 51,
        )
        val ordered = squad.inStartingOrder()

        assertEquals(Formation.F_4_4_2.slots, ordered.take(11).map { it.position })
        assertEquals(listOf(75, 74, 73, 72, 71, 80, 79, 78, 77, 85, 84), ordered.take(11).map { it.overall })
        assertEquals(squad.size, ordered.size)
        assertEquals(squad.toSet(), ordered.toSet())
    }

    @Test
    fun `vaga sem jogador da posicao fica com o melhor que sobrou`() {
        // Só um atacante: a 2ª vaga de ataque vai para o melhor que sobrou — um
        // meia de 70 (o de 76 já é titular no meio), não o goleiro reserva.
        val squad = squadOf(
            GK to 70, CB to 70, CB to 70, CB to 70, CB to 70,
            MF to 70, MF to 70, MF to 70, MF to 70, MF to 76, FW to 80, GK to 60,
        )
        val starters = squad.inStartingOrder().take(11)

        assertEquals(1, starters.count { it.position == GK })
        assertEquals(76, starters.filter { it.position == MF }.maxOf { it.overall })
        assertEquals(MF to 70, starters.last().let { it.position to it.overall })
    }

    @Test
    fun `ordem e deterministica`() {
        val squad = squadOf(GK to 70, GK to 70, CB to 70, CB to 70, MF to 70, FW to 70)
        assertEquals(squad.inStartingOrder(), squad.shuffled(kotlin.random.Random(7)).inStartingOrder())
    }
}
