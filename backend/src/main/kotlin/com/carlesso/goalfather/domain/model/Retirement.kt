package com.carlesso.goalfather.domain.model

import kotlinx.serialization.Serializable

/**
 * Um jogador pendurou as chuteiras na virada de temporada e a base do clube
 * assumiu a vaga (issue #55). Espelha `Retirement` no `contract/openapi.yaml`.
 *
 * O par `retired` + `promoted` é o que dá a notícia por inteiro — "Fulano, 37,
 * se aposentou; entra Sicrano, 18". Sem ele o técnico só perceberia a troca
 * abrindo o elenco e comparando com a memória.
 *
 * A promoção é 1:1 por decisão de regra: elenco que só encolhe acaba em time
 * incompleto (ver `AgingRules`), então quem sai é sempre substituído.
 */
@Serializable
data class Retirement(
    val clubId: ClubId,
    val retired: Player,
    val promoted: Player,
)
