package com.carlesso.goalfather.domain.result

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId

/**
 * Resultado de uma transferência (compra ou venda).
 *
 * Erro de negócio modelado como valor — não como exception. Cada
 * variante carrega exatamente o contexto que o caller precisa para
 * reagir (e.g. `available`/`required` em `InsufficientFunds`).
 *
 * Espelha o schema `TransferResult` no `contract/openapi.yaml`
 * (oneOf + discriminator `type`). Consumidores devem usar
 * `when (result) is ...` exaustivo — o compilador recusa código
 * que esquece uma variante.
 */
sealed interface TransferResult {
    data class Success(val club: Club, val player: Player) : TransferResult
    data class InsufficientFunds(val available: Long, val required: Long) : TransferResult
    data class SquadFull(val maxSize: Int) : TransferResult
    data class PlayerNotAvailable(val playerId: PlayerId) : TransferResult
}
