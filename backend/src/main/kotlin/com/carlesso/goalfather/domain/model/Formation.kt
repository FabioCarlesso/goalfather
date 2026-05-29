package com.carlesso.goalfather.domain.model

import com.carlesso.goalfather.domain.model.Position.CB
import com.carlesso.goalfather.domain.model.Position.FW
import com.carlesso.goalfather.domain.model.Position.GK
import com.carlesso.goalfather.domain.model.Position.MF

/**
 * Esquema tático. `label` é o nome convencional ("4-4-2") que casa com
 * o enum `Formation` no contrato. `slots` é a sequência de posições
 * esperada — propriedade comportamental anexada ao valor do enum
 * (idioma Kotlin: enums com propriedades).
 *
 * Nota: o frontend (UI-side) mantém esta mesma estrutura em
 * `frontend/src/domain/formations.ts` enquanto o contrato OpenAPI não
 * expõe `slots` por formação. Quando expuser, o frontend remove o
 * mapeamento de lá e usa o do contrato (gerado).
 */
enum class Formation(val label: String, val slots: List<Position>) {
    F_4_4_2("4-4-2", listOf(GK, CB, CB, CB, CB, MF, MF, MF, MF, FW, FW)),
    F_4_3_3("4-3-3", listOf(GK, CB, CB, CB, CB, MF, MF, MF, FW, FW, FW)),
    F_3_5_2("3-5-2", listOf(GK, CB, CB, CB, MF, MF, MF, MF, MF, FW, FW)),
    F_5_3_2("5-3-2", listOf(GK, CB, CB, CB, CB, CB, MF, MF, MF, FW, FW)),
    ;

    companion object {
        /** Resolve do `label` (vindo do contrato) para o enum. */
        fun fromLabel(label: String): Formation =
            entries.firstOrNull { it.label == label }
                ?: throw IllegalArgumentException("Formação desconhecida: $label")
    }
}
