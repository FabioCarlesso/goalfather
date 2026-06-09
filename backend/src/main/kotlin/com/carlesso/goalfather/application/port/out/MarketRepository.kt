package com.carlesso.goalfather.application.port.out

import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position

interface MarketRepository {
    suspend fun findAll(position: Position? = null, maxPrice: Long? = null): List<MarketEntry>
    suspend fun findEntry(playerId: PlayerId): MarketEntry?
    suspend fun add(entry: MarketEntry)

    /**
     * Remove a entrada do mercado de forma atômica e resolve a corrida de
     * compra (issue #21): retorna `true` se ESTE chamador removeu a entrada
     * (venceu), `false` se o jogador já não estava disponível — porque outro
     * comprador venceu a corrida (lock otimista) ou já o havia comprado.
     *
     * O caller só deve debitar caixa / mover o jogador para o elenco quando
     * receber `true`; assim o perdedor jamais altera o clube.
     */
    suspend fun claim(playerId: PlayerId): Boolean
}
