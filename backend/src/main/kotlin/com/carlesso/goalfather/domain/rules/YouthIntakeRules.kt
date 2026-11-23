package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.world.LeagueStrength
import com.carlesso.goalfather.domain.model.world.Reputation
import kotlin.random.Random

/**
 * Jovens da base a cada virada de temporada (issue #139). Funções PURAS, com o
 * `Random` semeado por temporada + clube ([youthIntakeSeed]): reprocessar a
 * mesma virada gera os mesmos garotos.
 *
 * Substitui a reposição 1:1 da #55 ("Cria da base N" no lugar de cada
 * aposentado). Agora todo clube recebe de 2 a 4 garotos por ano, aposente-se
 * alguém ou não, e quem segura o tamanho do elenco é a renovação da IA, que
 * libera o excedente ao mercado (`contractTurnover`). Sem isso o mundo só
 * cresceria.
 */

/**
 * Fonte de nomes plausíveis de um país. `fun interface` (interface de um
 * método só) aceita lambda no lugar de classe — o `@FunctionalInterface` do
 * Java, mas sem precisar de `new`. Mora no domínio porque é a regra da base
 * que pede os nomes; as listas (dado) ficam em `application/seed/dsl/NamePools`.
 */
fun interface NamePool {
    /** Fluxo infinito de nomes distintos entre si e fora de [taken]. */
    fun names(random: Random, taken: Set<String>): Sequence<String>
}

/** Quantos garotos sobem por clube e temporada. */
val YOUTH_INTAKE_SIZE: IntRange = 2..4

/** Idade de quem sobe: 16 a 18 anos. */
val YOUTH_INTAKE_AGES: IntRange = 16..18

/**
 * Os garotos que sobem para o elenco de [club] na temporada [season].
 *
 * - **Quantos:** de 2 a 4, sorteados; com [maxSquadSize] (o clube de técnico
 *   humano, que não tem quem libere o excedente por ele), nunca além do teto.
 * - **Posição:** a mais carente do elenco em relação à proporção ideal
 *   ([IDEAL_SQUAD_SHARE]); assim a base não forma o 5º goleiro de um time sem
 *   atacante.
 * - **Nacionalidade e nome:** do país do clube, pelo [names].
 * - **Atributos:** pelo POTENCIAL. O [AttributeGenerator] da #126 sorteia o
 *   jogador no auge ([POTENTIAL_AGE]) com a força da liga e a reputação
 *   derivada do elenco ([reputationOf]) — garoto de clube grande promete mais
 *   —, e o garoto nasce abaixo disso o que o envelhecimento ainda vai devolver
 *   (`AgeBand.YOUNG`: +1 por ano em média). Gerá-lo direto na idade dele, pela
 *   curva do gerador (−12 aos 17), deixaria o mundo mais fraco a cada geração:
 *   a curva sobe ~1,7 por ano, o envelhecimento só ~1.
 * - **Contrato:** o da base (3 temporadas, salário de base), na janela da liga.
 */
fun youthIntake(
    club: Club,
    season: Int,
    strength: LeagueStrength,
    names: NamePool,
    maxSquadSize: Int? = null,
): List<Player> {
    val rng = Random(youthIntakeSeed(season, club.id))
    val drawn = YOUTH_INTAKE_SIZE.random(rng)
    val count = maxSquadSize?.let { drawn.coerceAtMost((it - club.squad.size).coerceAtLeast(0)) } ?: drawn
    if (count == 0) return emptyList()

    val reputation = reputationOf(club.squad, strength)
    val generator = AttributeGenerator(rng)
    val contract = youthContract(club.id, season, club.contractSpan)
    val taken = club.squad.mapTo(HashSet()) { it.name }
    // `iterator()` sobre a Sequence preguiçosa: cada `next()` sorteia só o
    // próximo nome inédito, sem materializar a lista infinita.
    val nameStream = names.names(rng, taken).iterator()

    // `fold` acumula o elenco que vai crescendo: a posição do 2º garoto já
    // conta o 1º. É o `reduce` com valor inicial — sem `var` nem `for`.
    return (1..count).fold(emptyList<Player>()) { promoted, slot ->
        val position = neediestPosition(club.squad + promoted)
        val age = YOUTH_INTAKE_AGES.random(rng)
        val attributes = generator.generate(position, POTENTIAL_AGE, strength, reputation)
            .grownDown(by = (POTENTIAL_AGE - age) * EXPECTED_YOUTH_GROWTH)
        promoted + Player(
            id = youthPlayerId(club.id, season, slot),
            name = nameStream.next(),
            position = position,
            overall = attributes.overall,
            pace = attributes.pace,
            shooting = attributes.shooting,
            passing = attributes.passing,
            defending = attributes.defending,
            employment = contract,
            age = age,
            nationality = club.country,
        )
    }
}

/** A idade do auge em que o potencial do garoto é sorteado. */
const val POTENTIAL_AGE: Int = 24

/** Quanto o envelhecimento devolve por ano ao jovem, em média (`AgeBand.YOUNG` = −1..3). */
private const val EXPECTED_YOUTH_GROWTH: Int = 1

/** Os atributos [by] pontos abaixo, dentro da faixa do jogador. */
private fun PlayerAttributes.grownDown(by: Int): PlayerAttributes = PlayerAttributes(
    overall = (overall - by).coerceIn(Player.OVERALL_RANGE),
    pace = (pace - by).coerceIn(Player.OVERALL_RANGE),
    shooting = (shooting - by).coerceIn(Player.OVERALL_RANGE),
    passing = (passing - by).coerceIn(Player.OVERALL_RANGE),
    defending = (defending - by).coerceIn(Player.OVERALL_RANGE),
)

/**
 * Reputação de um clube na escala do pack (1..100), derivada do elenco — o
 * save não guarda a `Reputation` do pack. É a conta do [AttributeGenerator]
 * de trás para frente: lá, cada 5 pontos de reputação acima da média valem 1
 * de overall acima da força da liga; aqui, a média dos 11 melhores acima da
 * força da liga vira 5 pontos de reputação por ponto. Um clube que se reforça
 * forma garotos melhores.
 */
fun reputationOf(squad: List<Player>, strength: LeagueStrength): Reputation {
    val aboveLeague = squadReputation(squad) - strength.value
    return Reputation((Reputation.AVERAGE.value + aboveLeague * REPUTATION_PER_OVERALL).coerceIn(Reputation.RANGE))
}

private const val REPUTATION_PER_OVERALL: Int = 5

/**
 * Proporção ideal de cada posição num elenco: 3 goleiros, 8 zagueiros, 8 meias
 * e 6 atacantes num elenco de 25. A mesma régua vale para qualquer tamanho.
 */
val IDEAL_SQUAD_SHARE: Map<Position, Double> = mapOf(
    Position.GK to 3 / 25.0,
    Position.CB to 8 / 25.0,
    Position.MF to 8 / 25.0,
    Position.FW to 6 / 25.0,
)

/**
 * A posição com o maior déficit em relação à proporção ideal, contando o garoto
 * que vai entrar. Empate: a ordem do enum (goleiro primeiro), para o resultado
 * não depender de sorteio.
 */
internal fun neediestPosition(squad: List<Player>): Position {
    val size = squad.size + 1
    val counts = squad.groupingBy { it.position }.eachCount()
    return Position.entries.maxBy { position ->
        IDEAL_SQUAD_SHARE.getValue(position) * size - (counts[position] ?: 0)
    }
}

/**
 * Seed da base de um clube numa temporada. Mesmo empacotamento de [agingSeed]
 * com outro salt: sem ele, o garoto repetiria a sequência do envelhecimento.
 */
fun youthIntakeSeed(season: Int, clubId: ClubId): Long =
    ((season.toLong() shl 32) xor clubId.value) xor YOUTH_SEED_SALT

/** "YOUTH" em hexa. */
private const val YOUTH_SEED_SALT: Long = 0x594F_5554_48L
