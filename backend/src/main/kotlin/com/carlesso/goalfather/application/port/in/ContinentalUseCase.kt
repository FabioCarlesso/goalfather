package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.ContinentalCup
import com.carlesso.goalfather.domain.model.StandingRow

/**
 * Uma edição de continental pronta para a tela (issue #132): a edição e a
 * tabela de cada grupo, já ordenada pelos desempates da competição e com a
 * faixa de classificação — a UI só desenha, não recalcula regra.
 */
data class ContinentalEditionView(
    val edition: ContinentalCup,
    /** Tabela de cada grupo, pelo nome do grupo, na ordem dos grupos. */
    val tables: Map<String, List<StandingRow>>,
)

/** Leitura das continentais (issue #132). Só leitura — quem joga é o `PlayRoundUseCase`. */
interface ContinentalUseCase {
    /** A edição [season] de [competition] (sem [season], a mais recente), ou `null` se não houver. */
    suspend fun edition(competition: CompetitionId, season: Int?): ContinentalEditionView?
}
