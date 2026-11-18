package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.rules.WindowStatus

/**
 * Leitura da janela de transferência de um clube (issue #137): o selo "Janela
 * aberta até …" / "Abre em …" do mercado e do cabeçalho.
 */
interface TransferWindowUseCase {
    /** A janela da federação do clube na data do relógio; `null` se o clube não existe. */
    suspend fun statusOf(clubId: ClubId): WindowStatus?
}
