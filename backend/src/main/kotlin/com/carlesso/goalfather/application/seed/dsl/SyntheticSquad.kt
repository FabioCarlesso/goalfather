package com.carlesso.goalfather.application.seed.dsl

import com.carlesso.goalfather.domain.model.Player
import kotlin.random.Random

/**
 * Geração de elencos sintéticos para os clubes de IA do seed (issue #88).
 *
 * Tudo aqui é **puro e determinístico**: a aleatoriedade entra por um
 * [Random] recebido como receiver (normalmente `Random(clubId)`), então a
 * mesma seed sempre produz os mesmos nomes e os mesmos overalls — dá para
 * testar com igualdade exata e o banco recriado nasce idêntico.
 *
 * Por que **extension functions sobre `Random`**: em Java isso seria uma
 * `NameUtils.randomName(random)` estática. Em Kotlin, `random.nextPlayerName()`
 * lê como se fosse um método da própria classe, sem herdar nem embrulhar o
 * `Random` — e continua sendo só uma função top-level resolvida
 * estaticamente (não há despacho dinâmico nem acesso a membros privados).
 */

/** Prenomes masculinos comuns no Brasil. */
private val FIRST_NAMES = listOf(
    "Adriano", "Alexandre", "Anderson", "Antônio", "Arthur", "Breno", "Bruno",
    "Caio", "Cássio", "Cláudio", "Cristiano", "Daniel", "Danilo", "Davi",
    "Douglas", "Edson", "Elias", "Emerson", "Everton", "Fábio", "Fernando",
    "Flávio", "Gabriel", "Gilberto", "Guilherme", "Gustavo", "Hélio", "Hugo",
    "Jefferson", "Jonas", "Jorge", "José", "Juliano", "Leandro", "Leonardo",
    "Luan", "Luiz", "Marcelo", "Márcio", "Mateus", "Maurício", "Murilo",
    "Nathan", "Otávio", "Paulo", "Rafael", "Raul", "Ricardo", "Roberto",
    "Rogério", "Samuel", "Sérgio", "Thiago", "Valdir", "Vinícius", "Wagner",
    "Wesley", "Willian",
)

/** Sobrenomes brasileiros comuns. */
private val LAST_NAMES = listOf(
    "Abreu", "Andrade", "Araújo", "Azevedo", "Barbosa", "Barros", "Batista",
    "Borges", "Brandão", "Cardoso", "Carvalho", "Castro", "Cavalcanti",
    "Coelho", "Correia", "Cunha", "Dias", "Duarte", "Farias", "Fernandes",
    "Ferreira", "Fonseca", "Freitas", "Gomes", "Gonçalves", "Guimarães",
    "Lima", "Lopes", "Macedo", "Machado", "Martins", "Medeiros", "Melo",
    "Monteiro", "Moraes", "Moreira", "Nascimento", "Nogueira", "Oliveira",
    "Pereira", "Pinheiro", "Queiroz", "Ramos", "Rezende", "Ribeiro", "Rocha",
    "Sampaio", "Santana", "Santos", "Siqueira", "Soares", "Souza", "Tavares",
    "Teixeira", "Toledo", "Valente", "Vieira", "Xavier",
)

/** Um nome "Prenome Sobrenome" sorteado das listas acima. */
fun Random.nextPlayerName(): String = "${FIRST_NAMES.random(this)} ${LAST_NAMES.random(this)}"

/**
 * Fluxo infinito e preguiçoso de nomes **distintos** entre si e fora de
 * [taken].
 *
 * - **`generateSequence { ... }`** cria uma `Sequence` infinita que chama o
 *   lambda sob demanda — nada é sorteado até alguém consumir. É o `Stream.generate`
 *   do Java, mas sem a cerimônia de `limit`/`collect`.
 * - **`distinct()` + `filterNot`** em `Sequence` também são preguiçosos: o
 *   sorteio continua até achar o próximo nome inédito. Com ~3.300 combinações
 *   e 11 nomes por clube, o descarte é raro; quem consome usa `take(n)`.
 */
fun Random.playerNames(taken: Set<String> = emptySet()): Sequence<String> =
    generateSequence { nextPlayerName() }
        .distinct()
        .filterNot { it in taken }

/**
 * [count] deltas inteiros em `-maxSpread..maxSpread` que **somam exatamente
 * zero**, embaralhados. Somados a uma força base, preservam a média.
 *
 * **`sequence { yield(...) }`** é um gerador (como `yield` em Python/C#): o
 * bloco é suspenso a cada `yield` e retomado quando o próximo item é pedido,
 * então o `while (true)` não trava — o `take(count)` corta o fluxo. Aqui ele
 * emite pares simétricos `+d, -d`; com [count] ímpar, um `0` inicial
 * mantém todos os pares completos, logo a soma é sempre zero.
 */
fun Random.balancedDeltas(count: Int, maxSpread: Int = 3): List<Int> {
    require(count >= 0) { "count negativo: $count" }
    require(maxSpread >= 1) { "maxSpread deve ser >= 1: $maxSpread" }
    return sequence {
        if (count % 2 == 1) yield(0)
        while (true) {
            for (d in 1..maxSpread) {
                yield(d)
                yield(-d)
            }
        }
    }.take(count).toList().shuffled(this)
}

/** Um jogador sintético ainda sem id/posição/idade — o resto vem do slot. */
data class SyntheticPlayer(val name: String, val overall: Int)

/**
 * Elenco sintético de [size] jogadores com nomes únicos (entre si e fora de
 * [taken]) e overall em torno de [strength], com média **exatamente** igual
 * a [strength]. Determinístico para o mesmo receiver/seed.
 */
fun Random.syntheticSquad(
    strength: Int,
    size: Int = 11,
    taken: Set<String> = emptySet(),
    maxSpread: Int = 3,
): List<SyntheticPlayer> {
    // Se algum overall saísse da faixa, o `Player` rejeitaria — e truncar
    // quebraria a média. Melhor falhar cedo com mensagem clara.
    require(strength - maxSpread in Player.OVERALL_RANGE && strength + maxSpread in Player.OVERALL_RANGE) {
        "strength $strength ± $maxSpread fora de ${Player.OVERALL_RANGE}"
    }
    val names = playerNames(taken).take(size).toList()
    val deltas = balancedDeltas(size, maxSpread)
    // `zip` casa as duas listas posição a posição — sem índices manuais.
    return names.zip(deltas) { name, delta -> SyntheticPlayer(name, strength + delta) }
}
