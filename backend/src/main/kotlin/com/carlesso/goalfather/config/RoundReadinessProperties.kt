package com.carlesso.goalfather.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Configuração do escape hatch para técnico ausente (issue #45).
 *
 * `timeout` é o tempo, contado a partir do PRIMEIRO técnico que sinaliza
 * pronto, após o qual a rodada destrava mesmo com pendentes (ausentes entram
 * com a última escalação salva). Mesmo padrão tipado do rate limiting
 * (`@ConfigurationProperties` + `Duration`, ver [RoundReadinessProperties]
 * irmão `RateLimitProperties`): Spring converte `2m`, `120s` ou `PT2M`.
 *
 * Default folgado (2 min) para dar a todos tempo de escalar; sobrescreva com
 * `APP_ROUND_READINESS_TIMEOUT` (ex.: `30s` em dev/E2E). O timeout é avaliado
 * por processo (cada instância corre o próprio relógio); a transição que ele
 * destrava, essa sim, é atômica entre instâncias via lock otimista (issue #46).
 */
@ConfigurationProperties("app.round-readiness")
data class RoundReadinessProperties(
    val timeout: Duration = Duration.ofMinutes(2),
)
