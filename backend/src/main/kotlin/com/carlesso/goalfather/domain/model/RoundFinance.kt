package com.carlesso.goalfather.domain.model

import kotlinx.serialization.Serializable

/**
 * Resumo financeiro de um clube em uma rodada — receita de bilheteria
 * (apenas para o mandante) e folha salarial paga (em rodadas de "mês").
 *
 * Valores em centavos (Long), mesma convenção de `Club.cash`. Emitido no
 * `RoundEvent.RoundFinished` para que o frontend mostre o balanço da rodada.
 *
 * `deficit` é a parte da folha que o caixa não cobriu: como `Club.cash` não
 * pode ser negativo (invariante do agregado), o saldo é truncado em zero e o
 * rombo seria perdido silenciosamente. Registramos aqui para sinalizar "no
 * vermelho" na UI (issue #23). `deficit > 0` ⇒ clube ficou sem caixa.
 *
 * `ticketPrice` e `attendance` (issue #59) abrem a conta da bilheteria: com o
 * preço definido pelo técnico, `ticketRevenue` sozinho não diz se a rodada
 * rendeu por estádio cheio ou por ingresso caro — e é exatamente essa leitura
 * que o técnico precisa para corrigir o preço na rodada seguinte. O preço é o
 * do clube mesmo quando ele jogou FORA (é o que ele cobra em casa); nesse caso
 * `attendance` e `ticketRevenue` são zero, porque não houve bilheteria dele.
 */
@Serializable
data class RoundFinance(
    val clubId: ClubId,
    val ticketRevenue: Long,
    val salariesPaid: Long,
    val deficit: Long = 0,
    val ticketPrice: Long = 0,
    val attendance: Int = 0,
)
