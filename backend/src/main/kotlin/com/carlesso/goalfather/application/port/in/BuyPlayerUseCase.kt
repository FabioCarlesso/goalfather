package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.result.TransferResult

/**
 * Port de entrada (use case) — interface que controllers/CLI/testes
 * usam para acionar "comprar jogador". O resultado é `TransferResult`
 * sealed: sucesso ou variantes de falha de negócio explícitas.
 *
 * Note o pacote `in` em backticks: `in` é palavra-chave em Kotlin
 * (modificador de variância). Usar como nome de pacote requer escape.
 */
interface BuyPlayerUseCase {
    suspend fun execute(clubId: ClubId, playerId: PlayerId): TransferResult
}
