package com.carlesso.goalfather.domain.model

import kotlinx.serialization.Serializable

/**
 * Resumo financeiro de um clube em uma rodada — receita de bilheteria
 * (apenas para o mandante) e folha salarial paga (em rodadas de "mês").
 *
 * Valores em centavos (Long), mesma convenção de `Club.cash`. Emitido no
 * `RoundEvent.RoundFinished` para que o frontend mostre o balanço da rodada.
 */
@Serializable
data class RoundFinance(
    val clubId: ClubId,
    val ticketRevenue: Long,
    val salariesPaid: Long,
)
