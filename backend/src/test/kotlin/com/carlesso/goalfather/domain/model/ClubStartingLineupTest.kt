package com.carlesso.goalfather.domain.model

import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.makePlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Club.startingLineup()` — o que o time DE FATO leva a campo (issue #54).
 *
 * A escalação salva é uma foto que envelhece: reidratar pelo id e revalidar
 * disponibilidade aqui é o que impede a foto de mentir no apito inicial.
 */
class ClubStartingLineupTest {

    private fun clubWithSavedLineup(squadSize: Int = 14): Club {
        val base = makeClub(squadSize = squadSize)
        return base.copy(lineup = Lineup(players = base.squad.take(11), formation = Formation.F_4_4_2))
    }

    private fun Club.injure(vararg playerIds: Long, rounds: Int = 3): Club = copy(
        squad = squad.map {
            if (it.id.value in playerIds) it.copy(availability = Availability.Injured(rounds)) else it
        },
    )

    @Test
    fun `lesionado numa escalacao ja salva NAO entra em campo`() {
        // Regressão: o guard de lesão só existia no SaveLineupService, que roda
        // ao SALVAR. Quem se machucava depois seguia titular na rodada seguinte.
        val club = clubWithSavedLineup().injure(1L)

        val onField = club.startingLineup().players

        assertTrue(onField.none { it.injured }, "nenhum lesionado deveria entrar em campo")
        assertTrue(onField.none { it.id == PlayerId(1) }, "o lesionado #1 deveria estar fora")
    }

    @Test
    fun `vaga do lesionado e preenchida por reserva apto`() {
        val club = clubWithSavedLineup(squadSize = 14).injure(1L)

        val onField = club.startingLineup().players

        assertEquals(11, onField.size, "reserva apto deveria completar o time")
        // O primeiro reserva do elenco (id 12) entra no lugar do lesionado.
        assertTrue(onField.any { it.id == PlayerId(12) })
    }

    @Test
    fun `sem reservas aptos o time entra desfalcado`() {
        // Elenco de 11 com 2 lesionados: não há de onde tirar substituto.
        val club = clubWithSavedLineup(squadSize = 11).injure(1L, 2L)

        val onField = club.startingLineup().players

        assertEquals(9, onField.size, "time deveria entrar com 9, não completar com lesionado")
        assertTrue(onField.none { it.injured })
    }

    @Test
    fun `substituto nao duplica quem ja esta em campo`() {
        val club = clubWithSavedLineup(squadSize = 14).injure(1L, 2L)

        val onField = club.startingLineup().players

        assertEquals(onField.size, onField.map { it.id }.toSet().size, "não pode repetir jogador")
    }

    @Test
    fun `fallback sem escalacao salva tambem ignora lesionados`() {
        val base = makeClub(squadSize = 14)
        val club = base.copy(lineup = null).injure(1L)

        val onField = club.startingLineup().players

        assertEquals(11, onField.size)
        assertTrue(onField.none { it.injured })
    }

    @Test
    fun `titulares sao reidratados com o estado atual do elenco`() {
        // A foto salva tem stamina 100; o elenco já cansou. Vale o elenco.
        val base = makeClub(squadSize = 11, stamina = 100)
        val saved = Lineup(players = base.squad.take(11), formation = Formation.F_4_4_2)
        val club = base.copy(
            lineup = saved,
            squad = base.squad.map { it.copy(stamina = 45) },
        )

        assertTrue(
            club.startingLineup().players.all { it.stamina == 45 },
            "escalação deveria refletir a stamina atual, não a do dia da escalação",
        )
    }

    @Test
    fun `jogador vendido depois da escalacao some do time`() {
        val club = clubWithSavedLineup(squadSize = 14).let { c ->
            c.copy(squad = c.squad.filterNot { it.id == PlayerId(1) })
        }

        val onField = club.startingLineup().players

        assertTrue(onField.none { it.id == PlayerId(1) })
        assertEquals(11, onField.size, "reserva apto assume a vaga do vendido")
    }

    @Test
    fun `ordem da escalacao salva e preservada`() {
        val club = clubWithSavedLineup(squadSize = 14)

        val ids = club.startingLineup().players.map { it.id.value }

        assertEquals((1L..11L).toList(), ids)
    }

    @Test
    fun `formacao salva e preservada ao substituir`() {
        val base = makeClub(squadSize = 14)
        val club = base
            .copy(lineup = Lineup(players = base.squad.take(11), formation = Formation.F_3_5_2))
            .injure(1L)

        assertEquals(Formation.F_3_5_2, club.startingLineup().formation)
    }

    @Test
    fun `elenco inteiro lesionado resulta em time vazio, nao em lesionado escalado`() {
        val base = makeClub(squadSize = 11)
        val club = base
            .copy(lineup = Lineup(players = base.squad.take(11), formation = Formation.F_4_4_2))
            .injure(*(1L..11L).toList().toLongArray())

        val onField = club.startingLineup().players

        assertTrue(onField.isEmpty(), "melhor 0 em campo que 11 lesionados jogando")
        // Domínio robusto: força cai para o default, sem estourar.
        assertEquals(60.0, club.startingLineup().teamStrength())
    }

    @Test
    fun `jogador que se recupera volta a ser escalavel`() {
        val club = clubWithSavedLineup().injure(1L, rounds = 1)
        assertTrue(club.startingLineup().players.none { it.id == PlayerId(1) })

        // Lesão cumprida: volta ao time titular na rodada seguinte.
        val healed = club.copy(
            squad = club.squad.map {
                if (it.id == PlayerId(1)) it.copy(availability = Availability.Available) else it
            },
        )

        assertTrue(healed.startingLineup().players.any { it.id == PlayerId(1) })
    }

    @Test
    fun `substituto entra apto mesmo com o elenco parcialmente lesionado`() {
        // Reserva 12 lesionado: quem assume é o 13, não ele.
        val club = clubWithSavedLineup(squadSize = 14).injure(1L, 12L)

        val onField = club.startingLineup().players

        assertEquals(11, onField.size)
        assertTrue(onField.none { it.id == PlayerId(12) }, "reserva lesionado não pode assumir")
        assertTrue(onField.any { it.id == PlayerId(13) })
    }

    @Test
    fun `elenco sao mantem o time inalterado`() {
        val club = clubWithSavedLineup(squadSize = 14)
        val expected = makePlayer(1).id

        val onField = club.startingLineup().players

        assertEquals(11, onField.size)
        assertEquals(expected, onField.first().id)
    }
}
