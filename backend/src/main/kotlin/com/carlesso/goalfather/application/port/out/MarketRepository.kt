package com.carlesso.goalfather.application.port.out

import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.PlayerId

interface MarketRepository {
    /** O pool inteiro. A busca do técnico filtra e pagina em cima dele (`search`). */
    suspend fun findAll(): List<MarketEntry>
    suspend fun findEntry(playerId: PlayerId): MarketEntry?
    suspend fun add(entry: MarketEntry)

    /**
     * Regrava preço e temporadas sem clube de entradas que JÁ estão no mercado
     * (issue #139, envelhecimento do pool). Não mexe no jogador — esse vai por
     * `PlayerRepository.saveFreeAgents` — e ignora quem já saiu.
     */
    suspend fun updateAll(entries: List<MarketEntry>)

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
