package com.carlesso.goalfather.application.port.out

import com.carlesso.goalfather.domain.model.NationalWindow
import java.time.LocalDate

/**
 * Port de saída das datas FIFA já convocadas (issue #134). Só quem venceu o
 * claim do encaixe (issue #46) grava — a convocação na entrada da janela e os
 * placares na saída —, então o `save` é uma substituição simples, como a da
 * copa.
 */
interface NationalWindowRepository {
    /** Todas as janelas gravadas, da mais antiga para a mais recente. */
    suspend fun findAll(): List<NationalWindow>

    /** A janela que começa em [start], se já foi convocada. */
    suspend fun find(start: LocalDate): NationalWindow?

    /** Cria ou substitui a janela da mesma data de início. */
    suspend fun save(window: NationalWindow)
}
