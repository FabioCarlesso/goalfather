package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.model.BoardStatus
import com.carlesso.goalfather.domain.model.ClubId

/**
 * Leitura do painel da diretoria (issue #62): a meta da temporada corrente e a
 * posição/confiança de agora. Porta de ENTRADA, e não o repositório direto no
 * controller, porque um save anterior à issue ainda não tem meta gravada e ela
 * precisa ser derivada pela regra de domínio (`boardExpectations`).
 */
interface BoardUseCase {
    /** Painel do clube, ou `null` se o clube não existe ou não está em tabela nenhuma. */
    suspend fun status(clubId: ClubId): BoardStatus?
}
