package com.carlesso.goalfather.domain.model

import com.carlesso.goalfather.domain.rules.effectiveOverall
import kotlinx.serialization.Serializable

/**
 * Escalação de até 11 jogadores numa formação, com as instruções táticas
 * da partida (issue #56).
 *
 * `tactics` mora AQUI, e não no `Club`, porque é a mesma decisão: quem
 * entra em campo, em que desenho e com que postura. Consequência prática
 * boa — a escalação é persistida como JSON na coluna `lineup_json`, então
 * a postura viaja junto e fica gravada ANTES da rodada rodar, sem migração
 * e sem risco de duas réplicas simularem com inputs diferentes (issue #46).
 * Escalação antiga (sem o campo no JSON) desserializa para o default.
 *
 * Validação via `init { require(...) }` — falha rápido na construção,
 * não permite estado inválido. `copy()` herda a validação.
 */
@Serializable
data class Lineup(
    val players: List<Player>,
    val formation: Formation,
    val tactics: Tactics = Tactics.DEFAULT,
) {
    init {
        require(players.size <= 11) {
            "Escalação não pode ter mais que 11 jogadores (tem ${players.size})"
        }
    }

    val isComplete: Boolean get() = players.size == 11
}

/**
 * Quanto este time multiplica a PRÓPRIA chance de gol — postura e formação
 * compostas (issue #56). 1.0 = neutro (EQUILIBRADA em 4-4-2).
 */
fun Lineup.attackFactor(): Double = tactics.posture.attackMod * formation.attackMod

/**
 * Quanto este time multiplica a chance de gol DO ADVERSÁRIO. Abaixo de 1.0
 * o time se fecha; acima, deixa o jogo aberto.
 */
fun Lineup.defenseFactor(): Double = tactics.posture.defenseMod * formation.defenseMod

/**
 * Força agregada do time = média do overall EFETIVO dos escalados, isto é,
 * já descontada a fadiga (issue #54). Escalar um titular exausto passa a
 * custar força de time — é o que dá sentido a rodar o elenco.
 *
 * Extension function — lê como linguagem natural: `home.teamStrength()`.
 * Default 60.0 para lineup vazio mantém o domínio robusto sem precisar
 * de NPE-handling no caller.
 */
fun Lineup.teamStrength(): Double =
    if (players.isEmpty()) 60.0
    else players.sumOf { it.effectiveOverall() } / players.size
