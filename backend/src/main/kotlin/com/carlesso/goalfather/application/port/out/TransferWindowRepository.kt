package com.carlesso.goalfather.application.port.out

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.world.TransferWindow
import java.time.LocalDate

/**
 * Port de saída das janelas de transferência do save (issue #137, V24).
 * Gravadas quando o jogo nasce do pack, como o catálogo de competições: mudar
 * o pack não mexe num jogo em andamento.
 */
interface TransferWindowRepository {
    /** Todas as janelas do save, de todas as federações. */
    suspend fun findAll(): List<TransferWindow>

    companion object {
        /** Save sem janelas: só para testes que montam serviços à mão. */
        val NONE: TransferWindowRepository = object : TransferWindowRepository {
            override suspend fun findAll(): List<TransferWindow> = emptyList()
        }
    }
}

/**
 * Port de saída dos registros de jogador (issue #137, V24): cada vez que um
 * jogador entra num clube (compra no mercado ou proposta aceita), uma linha.
 * É o que conta o limite de "1 por janela, 2 por temporada".
 */
interface PlayerRegistrationRepository {
    /** As datas (do relógio do jogo) dos registros do jogador, em qualquer ordem. */
    suspend fun datesOf(playerId: PlayerId): List<LocalDate>

    /** Registra [playerId] em [clubId] na data [date]. */
    suspend fun record(playerId: PlayerId, clubId: ClubId, date: LocalDate)

    companion object {
        /** Sem memória: nada conta para o limite. Para testes montados à mão. */
        val NONE: PlayerRegistrationRepository = object : PlayerRegistrationRepository {
            override suspend fun datesOf(playerId: PlayerId): List<LocalDate> = emptyList()

            override suspend fun record(playerId: PlayerId, clubId: ClubId, date: LocalDate) = Unit
        }
    }
}
