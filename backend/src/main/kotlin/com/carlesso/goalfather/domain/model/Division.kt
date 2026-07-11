package com.carlesso.goalfather.domain.model

import com.carlesso.goalfather.domain.serialization.DivisionSerializer
import kotlinx.serialization.Serializable

/**
 * Divisão (tier) do campeonato — 1 é a elite, números maiores são divisões
 * inferiores. `@JvmInline value class` como os demais IDs tipados do domínio:
 * zero overhead em runtime e impossível confundir com um `Int` qualquer
 * (rodada, temporada, posição...) em tempo de compilação.
 *
 * `Comparable` permite ordenar divisões diretamente (`sortedBy { it.division }`),
 * o que os fluxos de tabela e de virada de temporada usam o tempo todo.
 */
@JvmInline
@Serializable(with = DivisionSerializer::class)
value class Division(val value: Int) : Comparable<Division> {
    init {
        require(value >= 1) { "Divisão deve ser >= 1: $value" }
    }

    override fun compareTo(other: Division): Int = value.compareTo(other.value)

    companion object {
        /** Divisão de elite — o padrão para ligas de divisão única. */
        val FIRST = Division(1)
    }
}
