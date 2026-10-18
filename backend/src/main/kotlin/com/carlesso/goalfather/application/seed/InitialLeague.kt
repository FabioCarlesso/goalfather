package com.carlesso.goalfather.application.seed

import com.carlesso.goalfather.application.seed.dsl.DivisionBuilder
import com.carlesso.goalfather.application.seed.dsl.League
import com.carlesso.goalfather.application.seed.dsl.league
import com.carlesso.goalfather.application.seed.dsl.syntheticSquad
import com.carlesso.goalfather.domain.model.Position
import kotlin.random.Random

/**
 * Estado inicial do jogo descrito declarativamente via DSL de seed.
 *
 * Vivia dentro do `DataInitializer` (config/Spring); foi extraído para cá
 * (issue #88) por ser puro — assim o seed REAL pode ser testado sem subir
 * contexto Spring (ver `InitialLeagueTest`). O runner só persiste o [League].
 */
fun initialLeague(): League {
    // Nomes já usados na liga: compartilhado entre os clubes de IA para que
    // o mesmo "Fulano Tal" não apareça em dois elencos. Mutável, mas local a
    // esta função — não escapa.
    val takenNames = mutableSetOf<String>()

    return league(season = 2026) {
        division(1) {
            club(1, "Goal Father FC") {
                cash = 800_000_00
                stadium = 15_000
                player(1, "Marcos Figueiredo") { position = Position.GK; overall = 78; salary = 25_000_00; age = 32 }
                player(2, "Rodrigo Alves") { position = Position.CB; overall = 75; salary = 18_000_00; age = 27 }
                player(3, "Túlio Mendes") { position = Position.CB; overall = 72; salary = 15_000_00; age = 24 }
                player(4, "Caio Bernardes") { position = Position.CB; overall = 70; salary = 13_000_00; age = 22 }
                player(5, "Pedro Henrique") { position = Position.CB; overall = 73; salary = 16_000_00; age = 26 }
                player(6, "Felipe Costa") { position = Position.MF; overall = 80; salary = 28_000_00; age = 28 }
                player(7, "Diego Lobato") { position = Position.MF; overall = 76; salary = 22_000_00; age = 25 }
                player(8, "Igor Vasques") { position = Position.MF; overall = 74; salary = 19_000_00; age = 23 }
                player(9, "Carlos Almeida") { position = Position.MF; overall = 71; salary = 14_000_00; age = 21 }
                player(10, "Renato Silva") { position = Position.FW; overall = 88; salary = 55_000_00; age = 29 }
                player(11, "João Faria") { position = Position.FW; overall = 79; salary = 26_000_00; age = 26 }
            }

            // Clubes da IA (id 2..6): elenco sintético de 11 jogadores com
            // nomes plausíveis e overall em torno da força do clube (média
            // exata = força). O bloco `club { }` é código normal, então dá
            // para popular o elenco com um `for` — a DSL não obriga repetição.
            for ((id, name, strength) in AI_CLUBS_DIV1) {
                aiClub(id, name, strength, takenNames)
            }
        }

        // Divisão 2 (issue #47): 6 clubes IA, elencos um degrau mais fracos.
        division(2) {
            for ((id, name, strength) in AI_CLUBS_DIV2) {
                aiClub(id, name, strength, takenNames)
            }
        }

        market {
            player(101, "Lucas Vidigal", price = 450_000_00) { position = Position.FW; overall = 81; salary = 32_000_00; age = 25 }
            player(102, "André Pacheco", price = 220_000_00) { position = Position.MF; overall = 77; salary = 23_000_00; age = 24 }
            player(103, "Bruno Aparício", price = 300_000_00) { position = Position.CB; overall = 79; salary = 25_000_00; age = 27 }
            player(104, "Eduardo Ramires", price = 180_000_00) { position = Position.GK; overall = 76; salary = 21_000_00; age = 28 }
        }

        // Rodada 1 — Berger com 6 times por divisão:
        // divisão 1: (1×6), (2×5), (3×4); divisão 2: (7×12), (8×11), (9×10).
        round(1) {
            match(homeId = 1, awayId = 6)
            match(homeId = 2, awayId = 5)
            match(homeId = 3, awayId = 4)
            match(homeId = 7, awayId = 12)
            match(homeId = 8, awayId = 11)
            match(homeId = 9, awayId = 10)
        }
    }
}

/**
 * Clube IA padrão: caixa/estádio fixos e elenco sintético de 11 jogadores
 * (issue #88). `Random(id)` torna o elenco função só do id do clube — o
 * mesmo banco recriado nasce com os mesmos nomes e overalls.
 */
private fun DivisionBuilder.aiClub(id: Long, name: String, strength: Int, takenNames: MutableSet<String>) {
    val squad = Random(id).syntheticSquad(strength = strength, size = 11, taken = takenNames)
    takenNames += squad.map { it.name }
    club(id, name) {
        cash = 500_000_00
        stadium = 12_000
        // `withIndex()` entrega (índice, valor) sem contador manual; o slot
        // é 1-based para manter os ids `id * 1000 + slot` de antes.
        for ((index, generated) in squad.withIndex()) {
            val slot = index + 1
            player(id * 1000 + slot, generated.name) {
                position = positionForSlot(slot)
                overall = generated.overall
                salary = 10_000_00
                age = ageForSlot(slot)
            }
        }
    }
}

/**
 * Idades espalhadas entre 21 e 32 (issue #55). Com o elenco inteiro na
 * mesma idade — era 25 para todo mundo — os onze cruzariam a barreira da
 * aposentadoria na MESMA virada de temporada, e o clube trocaria de elenco
 * inteiro de uma vez. Espalhar transforma isso numa renovação gradual, uma
 * ou duas saídas por temporada.
 */
private fun ageForSlot(slot: Int): Int = 21 + (slot * 5) % 12

private fun positionForSlot(slot: Int): Position = when (slot) {
    1 -> Position.GK
    in 2..5 -> Position.CB
    in 6..9 -> Position.MF
    else -> Position.FW
}

/** Clubes controlados pela IA: (id, nome, força = overall médio do elenco). */
internal val AI_CLUBS_DIV1 = listOf(
    Triple(2L, "Atlético Bonsucesso", 76),
    Triple(3L, "Esporte Clube Vargem", 72),
    Triple(4L, "Tupinambás FC", 70),
    Triple(5L, "Independente Sul", 74),
    Triple(6L, "Real Capela", 73),
)

/** Divisão 2 (issue #47): mesmos moldes, força um degrau abaixo. */
internal val AI_CLUBS_DIV2 = listOf(
    Triple(7L, "Ferroviária da Serra", 68),
    Triple(8L, "Operário do Vale", 66),
    Triple(9L, "Náutico Boa Vista", 67),
    Triple(10L, "Comercial de Ouro Fino", 64),
    Triple(11L, "Juventude Alvorada", 65),
    Triple(12L, "União Primavera", 63),
)
