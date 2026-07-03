package com.carlesso.goalfather.application.metrics

/**
 * Nomes e tags das métricas customizadas (issue #44), centralizados para evitar
 * divergência entre quem publica (services/controllers) e quem lê (testes,
 * dashboards). O prefixo `goalfather.` é convertido pelo registry Prometheus em
 * `goalfather_...`; timers ganham o sufixo de unidade (`_seconds`) e counters o
 * `_total` automaticamente.
 *
 * Micrometer prega nomes com pontos e dimensões em *tags* (não no nome) —
 * `goalfather.market.transfers{result="success"}` em vez de
 * `goalfather.market.transfers.success`. Assim uma única série cobre todos os
 * desfechos e o agregado sai de graça.
 */
object GoalfatherMetrics {

    /** Timer da simulação de uma rodada inteira (todas as partidas). */
    const val ROUND_SIMULATION = "goalfather.round.simulation"

    /** Timer da simulação de uma única partida (drill-down do WS de match). */
    const val MATCH_SIMULATION = "goalfather.match.simulation"

    /** Counter de transferências no mercado, dimensionado por [TAG_RESULT]. */
    const val MARKET_TRANSFERS = "goalfather.market.transfers"

    /** Counter de tentativas de login, dimensionado por [TAG_RESULT]. */
    const val AUTH_LOGINS = "goalfather.auth.logins"

    /** Tag que dimensiona o desfecho (`success`, `conflict`, `failure`, ...). */
    const val TAG_RESULT = "result"
}
