package com.carlesso.goalfather.tools.worldimport.assemble

import com.carlesso.goalfather.adapter.out.worlddata.dto.PlayerDto
import java.time.LocalDate
import kotlin.random.Random

/**
 * Completa um elenco curto com jogadores GERADOS até [minSize] jogadores e
 * [minGoalkeepers] goleiros (issue #127). O Wikidata traz elencos incompletos
 * — no Brasil, muitas vezes menos da metade — e o jogo precisa de elencos
 * completos para escalar.
 *
 * Determinístico: o `Random` de cada clube nasce de `seed + id.hashCode()`
 * (o `hashCode` de `String` é fixado pela especificação do Java, igual em
 * qualquer JVM), então o mesmo clube recebe os mesmos gerados em toda
 * execução, independentemente da ordem dos outros clubes.
 *
 * O gerado só tem os campos factuais do pack (nome, nascimento,
 * nacionalidade, posição) e é marcado com `generated = true`.
 */
class SquadFiller(
    private val seed: Long,
    private val referenceDate: LocalDate,
    private val minSize: Int,
    private val minGoalkeepers: Int,
) {
    /**
     * @param real o elenco que veio do Wikidata (+ overrides).
     * @param taken nomes que um gerado NÃO pode receber (todos os reais do pack,
     *   para não "clonar" um jogador de verdade).
     * @return só os gerados, já na ordem em que foram criados.
     */
    fun fill(clubId: String, clubCountry: String, real: List<PlayerDto>, taken: Set<String>): List<PlayerDto> {
        val random = Random(seed + clubId.hashCode())
        val positions = positionsToAdd(real.map { it.position })
        val names = namePoolFor(clubCountry).names(random, taken + real.map { it.name }).iterator()
        // `mapIndexed` dá o índice sem contador manual; o id `-gen-01` é estável
        // enquanto o elenco real do clube não mudar de tamanho por posição.
        return positions.mapIndexed { index, position ->
            PlayerDto(
                id = "$clubId-gen-${(index + 1).toString().padStart(2, '0')}",
                name = names.next(),
                birthDate = birthDate(random).toString(),
                nationality = clubCountry,
                position = position,
                generated = true,
            )
        }
    }

    /**
     * Quais posições faltam: primeiro goleiros até o mínimo; depois, até
     * [minSize], sempre a posição mais longe do [TEMPLATE] (empate resolvido
     * pela ordem GK, DF, MF, FW, a mesma do template).
     */
    fun positionsToAdd(current: List<String>): List<String> {
        val counts = TEMPLATE.keys.associateWith { position -> current.count { it == position } }.toMutableMap()
        val added = mutableListOf<String>()
        fun add(position: String) {
            added += position
            counts[position] = counts.getValue(position) + 1
        }
        while (counts.getValue("GK") < minGoalkeepers) add("GK")
        while (current.size + added.size < minSize) {
            // `maxBy` devolve o PRIMEIRO máximo: com o `LinkedHashMap` do
            // template, o empate cai na ordem GK, DF, MF, FW.
            add(TEMPLATE.keys.maxBy { TEMPLATE.getValue(it) - counts.getValue(it) })
        }
        return added
    }

    /** Idade de 18 a 32 anos na data de referência, dia do ano sorteado. */
    private fun birthDate(random: Random): LocalDate =
        referenceDate.minusYears(random.nextLong(18, 33)).minusDays(random.nextLong(0, 365))

    companion object {
        /** Elenco-modelo de 22: 3 goleiros, 7 defensores, 7 meias, 5 atacantes. */
        val TEMPLATE: Map<String, Int> = linkedMapOf("GK" to 3, "DF" to 7, "MF" to 7, "FW" to 5)
    }
}
