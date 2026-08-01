package com.carlesso.goalfather.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Disponibilidade de um jogador para ser escalado (issue #54).
 *
 * Substitui o antigo `injured: Boolean`. O par `injured` + "quantas rodadas
 * falta" permitiria estados sem sentido — `injured = false` com 3 rodadas de
 * afastamento, ou `injured = true` sem duração nenhuma. Com um `sealed
 * interface` esses estados simplesmente **não existem**: a duração só é
 * representável DENTRO de [Injured], e [Available] não tem onde guardá-la.
 * É o idioma Kotlin para "torne estados inválidos irrepresentáveis" — o
 * `when` sobre as variantes é exaustivo, sem `else` nem checagem defensiva.
 *
 * `data object` (Kotlin 1.9+) para [Available]: sem estado, uma instância só,
 * e ainda ganha `toString()`/`equals()` decentes — melhor que `object` cru em
 * modelo de domínio comparado por valor.
 *
 * Serializa como `{"type": "Available"}` / `{"type": "Injured", "roundsOut": 3}`,
 * espelhando o `oneOf` + `discriminator` do `contract/openapi.yaml` — mesma
 * convenção de `MatchEvent` e `TransferResult`.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface Availability {

    /** Apto a jogar. */
    @Serializable
    @SerialName("Available")
    data object Available : Availability

    /**
     * Afastado por lesão pelas próximas [roundsOut] rodadas. Cada rodada
     * jogada decrementa o contador (ver `FitnessRules.applyRoundFitness`);
     * ao chegar a zero o jogador volta a [Available] — por isso `>= 1`:
     * "lesionado por zero rodadas" é [Available], não uma lesão.
     */
    @Serializable
    @SerialName("Injured")
    data class Injured(val roundsOut: Int) : Availability {
        init {
            require(roundsOut >= 1) { "roundsOut de uma lesão deve ser >= 1: $roundsOut" }
        }
    }
}
