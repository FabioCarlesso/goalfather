package com.carlesso.goalfather.adapter.`in`.web.ws

import org.springframework.web.socket.WebSocketSession
import org.springframework.web.util.UriComponentsBuilder

/**
 * Velocidade da transmissão ao vivo (issue #115) — schema `StreamSpeed` do
 * contrato. Só muda a pausa entre frames: os eventos são os mesmos em qualquer
 * velocidade (a seed de cada partida é o `matchId`). Por isso mora no adapter
 * WS e não no domínio: o domínio devolve o `Flow` sem timing.
 *
 * `enum class` com propriedade no construtor: cada constante carrega o seu
 * intervalo, então não existe um `when` espalhado mapeando nome → ms.
 */
enum class StreamSpeed(val msPerMinute: Long) {
    SLOW(160),
    NORMAL(80),
    FAST(20),
    INSTANT(0),
    ;

    /**
     * Pausa antes de um evento no minuto [toMinute], vindo de [fromMinute].
     * Eventos fora de ordem (minuto menor) nunca geram pausa negativa.
     */
    fun gapMs(fromMinute: Int, toMinute: Int): Long =
        ((toMinute - fromMinute) * msPerMinute).coerceAtLeast(0)

    companion object {
        /** Valor do contrato quando o parâmetro falta ou é desconhecido. */
        val DEFAULT = NORMAL

        /**
         * Lê o query param `speed`. Ausente ou inválido → [DEFAULT], nunca
         * exception: velocidade é preferência de UI, não motivo para recusar o
         * handshake. Aceita qualquer caixa (`instant` = `INSTANT`).
         *
         * `entries` (Kotlin 1.9+) substitui o `values()` herdado do Java: é uma
         * lista imutável pronta, sem alocar um array novo a cada chamada.
         */
        fun fromQuery(raw: String?): StreamSpeed =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * Velocidade pedida no handshake (`?speed=`). Extension function: lê como um
 * método da `WebSocketSession` do Spring, sem classe utilitária estática — e
 * os dois handlers (rodada e partida) compartilham a mesma leitura.
 */
fun WebSocketSession.streamSpeed(): StreamSpeed {
    val raw = uri?.let { UriComponentsBuilder.fromUri(it).build().queryParams.getFirst("speed") }
    return StreamSpeed.fromQuery(raw)
}
