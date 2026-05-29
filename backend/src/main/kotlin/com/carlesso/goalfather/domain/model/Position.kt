package com.carlesso.goalfather.domain.model

/**
 * Posição do jogador no campo.
 *
 * `abbr` é a sigla PT-BR usada na UI (espelha o `x-labels` no
 * `contract/openapi.yaml`). O nome do enum (GK/CB/MF/FW) bate com
 * o `enum Position` do contrato — não renomear sem atualizar lá.
 */
enum class Position(val abbr: String) {
    GK("GL"),
    CB("ZG"),
    MF("MC"),
    FW("AT"),
}
