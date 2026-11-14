package com.carlesso.goalfather.domain.model

import com.carlesso.goalfather.domain.serialization.CompetitionIdSerializer
import com.carlesso.goalfather.domain.serialization.PlayerIdSerializer
import kotlinx.serialization.Serializable

/**
 * Identificador tipado de jogador.
 *
 * `value class` (inline) — zero overhead em runtime, mas o compilador
 * impede passar Long cru onde um PlayerId é esperado. Substitui o
 * "primitive obsession" do mundo Java.
 *
 * No JSON, sai como número simples (via `PlayerIdSerializer`).
 */
@JvmInline
@Serializable(with = PlayerIdSerializer::class)
value class PlayerId(val value: Long)

/**
 * Jogador imutável. Mutações produzem novas instâncias via `copy()`,
 * nunca via setters.
 *
 * `isStar` é derivado (computed property) — não armazenado, recalculado
 * sob demanda. Idioma Kotlin: comportamento perto do dado, sem getter
 * verboso à la Java.
 *
 * `stamina` (0..100) é consumida a cada rodada pelos titulares e recuperada
 * pelos reservas; `availability` diz se o jogador pode ser escalado. Ambas
 * são movidas exclusivamente pelas regras puras de `domain/rules/FitnessRules`
 * (issue #54).
 */
@Serializable
data class Player(
    val id: PlayerId,
    val name: String,
    val position: Position,
    val overall: Int,
    val pace: Int,
    val shooting: Int,
    val passing: Int,
    val defending: Int,
    val stamina: Int = 100,
    val salary: Money,
    val age: Int,
    val goals: Int = 0,
    val yellowCards: Int = 0,
    val redCards: Int = 0,
    val availability: Availability = Availability.Available,
    /**
     * Nacionalidade esportiva (issue #123): a seleção que o jogador pode
     * defender. Default = [Countries.DEFAULT] só para o seed e os testes
     * anteriores à issue; o pack do mundo (issue #126) sempre informa.
     */
    val nationality: Country = Countries.DEFAULT,
    /**
     * Segunda nacionalidade, se houver. Não muda a seleção — só conta para a
     * regra de estrangeiro (`isForeignIn`): um argentino com passaporte
     * brasileiro não ocupa vaga de estrangeiro no Brasil.
     */
    val secondNationality: Country? = null,
    /**
     * Amarelos ainda não "pagos" com suspensão, POR competição (issue #130):
     * um amarelo no Brasileirão não conta na copa. Diferente de [yellowCards],
     * que é estatística da temporada e nunca zera ao cumprir gancho.
     *
     * A anotação no ARGUMENTO de tipo (`Map<@Serializable(with = …) K, V>`) é
     * como o kotlinx aceita um serializer custom só para a chave do mapa.
     */
    val pendingYellows: Map<@Serializable(with = CompetitionIdSerializer::class) CompetitionId, Int> = emptyMap(),
    /** Suspensões em vigor, no máximo uma por competição (issue #130). */
    val suspensions: List<Suspension> = emptyList(),
) {
    val star: Boolean get() = overall >= 82

    /**
     * Atalho de leitura para as checagens de "pode escalar?". Derivado de
     * [availability] — não é um campo, então não há como divergir dela.
     */
    val injured: Boolean get() = availability is Availability.Injured

    /** A suspensão em vigor em [competition], se houver — ausência no tipo. */
    fun suspensionIn(competition: CompetitionId): Suspension? =
        suspensions.firstOrNull { it.competitionId == competition }

    fun isSuspendedIn(competition: CompetitionId): Boolean = suspensionIn(competition) != null

    /**
     * Pode entrar em campo num jogo de [competition]? `null` = sem competição
     * em jogo (partida avulsa, telas): só a lesão conta.
     */
    fun canPlayIn(competition: CompetitionId?): Boolean =
        !injured && (competition == null || !isSuspendedIn(competition))

    init {
        require(overall in OVERALL_RANGE) { "overall fora de $OVERALL_RANGE: $overall" }
        require(stamina in 0..100) { "stamina fora de 0..100: $stamina" }
        require(age in AGE_RANGE) { "age fora de $AGE_RANGE: $age" }
        require(secondNationality != nationality) { "segunda nacionalidade repete a primeira: ${nationality.code}" }
        require(pendingYellows.values.all { it >= 1 }) { "amarelos pendentes devem ser >= 1 (zero sai do mapa): $pendingYellows" }
        require(suspensions.distinctBy { it.competitionId }.size == suspensions.size) {
            "mais de uma suspensão na mesma competição: $suspensions"
        }
    }

    /**
     * Os invariantes viram constantes nomeadas para que as REGRAS possam
     * truncar nos mesmos limites (ver `domain/rules/AgingRules.kt`) em vez de
     * repetir os literais e torcer para não divergirem. O `require` continua
     * sendo a última linha de defesa, não a primeira.
     */
    companion object {
        val OVERALL_RANGE: IntRange = 0..99
        val AGE_RANGE: IntRange = 15..50
    }
}
