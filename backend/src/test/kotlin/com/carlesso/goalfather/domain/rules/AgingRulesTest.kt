package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.test.makePlayer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Evolução e regressão de atributos por idade (issue #55) — teste PURO de
 * domínio: sem Spring, sem I/O, com `Random(seed)` fixa. Cobre os critérios de
 * aceite da issue: determinismo, faixas por idade, invariante de `overall` e
 * saída do aposentado.
 */
class AgingRulesTest {

    /** Seeds usadas nas varreduras de propriedade — bordas em vários sorteios. */
    private val seeds = (0..300)

    // ─── Idade ────────────────────────────────────────────────────────────

    @Test
    fun `todo jogador ganha um ano na virada`() {
        for (age in 18..35) {
            val outcome = makePlayer(1, age = age).ageOneSeason(Random(age))
            assertEquals(age + 1, outcome.player.age, "jogador de $age anos deveria virar ${age + 1}")
        }
    }

    // ─── Faixas etárias ───────────────────────────────────────────────────

    @Test
    fun `jovem nunca regride mais do que o piso da faixa`() {
        // Critério de aceite: "jovem nunca regride mais do que X".
        val floor = AgeBand.YOUNG.overallDelta.first
        for (seed in seeds) {
            val before = makePlayer(1, overall = 70, age = 19)
            val after = before.ageOneSeason(Random(seed)).player

            assertTrue(
                after.overall - before.overall >= floor,
                "jovem caiu ${after.overall - before.overall}, além do piso $floor (seed $seed)",
            )
        }
    }

    @Test
    fun `veterano nunca evolui mais do que o teto da faixa`() {
        // Critério de aceite: "veterano nunca evolui mais do que Y".
        val ceiling = AgeBand.VETERAN.overallDelta.last
        for (seed in seeds) {
            val before = makePlayer(1, overall = 75, age = 33)
            val after = before.ageOneSeason(Random(seed)).player

            assertTrue(
                after.overall - before.overall <= ceiling,
                "veterano subiu ${after.overall - before.overall}, além do teto $ceiling (seed $seed)",
            )
        }
    }

    @Test
    fun `jovem tende a evoluir e veterano a regredir no agregado`() {
        // A faixa é sorteada, então o teste é sobre a TENDÊNCIA (o valor
        // esperado de cada faixa), não sobre um sorteio isolado.
        val youngGain = seeds.sumOf { deltaOf(age = 19, seed = it) }
        val peakGain = seeds.sumOf { deltaOf(age = 26, seed = it) }
        val veteranGain = seeds.sumOf { deltaOf(age = 32, seed = it) }

        assertTrue(youngGain > 0, "jovens deveriam evoluir no agregado, somaram $youngGain")
        assertTrue(veteranGain < 0, "veteranos deveriam regredir no agregado, somaram $veteranGain")
        assertTrue(
            youngGain > peakGain && peakGain > veteranGain,
            "as faixas deveriam ficar ordenadas: jovem $youngGain > auge $peakGain > veterano $veteranGain",
        )
    }

    @Test
    fun `a faixa vale para a idade NOVA — quem faz 24 ja entra no auge`() {
        // Aos 23 ainda é jovem; ao virar 24 na temporada nova, passa ao auge.
        assertEquals(AgeBand.YOUNG, AgeBand.of(YOUNG_MAX_AGE))
        assertEquals(AgeBand.PEAK, AgeBand.of(YOUNG_MAX_AGE + 1))
        assertEquals(AgeBand.PEAK, AgeBand.of(PEAK_MAX_AGE))
        assertEquals(AgeBand.VETERAN, AgeBand.of(PEAK_MAX_AGE + 1))

        // Um jogador de 23 é sorteado na faixa do AUGE, não na de jovem.
        val ceiling = AgeBand.PEAK.overallDelta.last
        for (seed in seeds) {
            assertTrue(
                deltaOf(age = YOUNG_MAX_AGE, seed = seed) <= ceiling,
                "aos ${YOUNG_MAX_AGE + 1} o sorteio já é o do auge (seed $seed)",
            )
        }
    }

    @Test
    fun `atributos acompanham a variacao do overall`() {
        val before = makePlayer(1, overall = 70, age = 19)
        val after = before.ageOneSeason(Random(3)).player
        val delta = after.overall - before.overall

        assertEquals(before.pace + delta, after.pace)
        assertEquals(before.shooting + delta, after.shooting)
        assertEquals(before.passing + delta, after.passing)
        assertEquals(before.defending + delta, after.defending)
    }

    // ─── Invariante do Player ─────────────────────────────────────────────

    @Test
    fun `overall nunca estoura o invariante 0 a 99 do Player`() {
        // O `require` do Player já explodiria; a asserção é a rede de segurança
        // explícita do critério de aceite. Craque no teto e perna-de-pau no chão.
        for (seed in seeds) {
            val prodigy = makePlayer(1, overall = 99, age = 18).ageOneSeason(Random(seed)).player
            val journeyman = makePlayer(2, overall = 0, age = 33).ageOneSeason(Random(seed)).player

            assertTrue(prodigy.overall in 0..99, "overall ${prodigy.overall} fora de 0..99")
            assertTrue(journeyman.overall in 0..99, "overall ${journeyman.overall} fora de 0..99")
        }
    }

    @Test
    fun `carreira inteira simulada nunca quebra o invariante de idade`() {
        // 40 viradas seguidas: sem o teto duro de carreira, `age` estouraria o
        // `require(age in 15..50)` do Player no meio do caminho.
        var outcome: AgingOutcome = AgingOutcome.Steady(makePlayer(1, overall = 90, age = 18))
        for (season in 1..40) {
            val current = outcome
            if (current is AgingOutcome.Retired) break
            outcome = current.player.ageOneSeason(Random(season))
        }

        val career = outcome
        assertIs<AgingOutcome.Retired>(career, "toda carreira deveria terminar em aposentadoria")
        assertTrue(career.player.age <= FORCED_RETIREMENT_AGE)
    }

    // ─── Aposentadoria ────────────────────────────────────────────────────

    @Test
    fun `veterano de overall baixo se aposenta`() {
        val outcome = makePlayer(1, overall = RETIREMENT_MAX_OVERALL - 10, age = RETIREMENT_MIN_AGE)
            .ageOneSeason(Random(1))

        assertIs<AgingOutcome.Retired>(outcome)
    }

    @Test
    fun `veterano que ainda rende segue jogando`() {
        // Overall alto o bastante: a idade sozinha não aposenta.
        for (seed in seeds) {
            val outcome = makePlayer(1, overall = 90, age = RETIREMENT_MIN_AGE + 1).ageOneSeason(Random(seed))
            assertTrue(outcome !is AgingOutcome.Retired, "craque de 37 não deveria se aposentar (seed $seed)")
        }
    }

    @Test
    fun `jogador de overall baixo mas jovem nao se aposenta`() {
        for (seed in seeds) {
            val outcome = makePlayer(1, overall = 40, age = 22).ageOneSeason(Random(seed))
            assertTrue(outcome !is AgingOutcome.Retired, "reserva jovem não se aposenta (seed $seed)")
        }
    }

    @Test
    fun `o teto duro de carreira aposenta ate quem ainda rende`() {
        val outcome = makePlayer(1, overall = 99, age = FORCED_RETIREMENT_AGE).ageOneSeason(Random(1))

        assertIs<AgingOutcome.Retired>(outcome)
        // Não envelhece mais: quem bate o teto sai com a idade que tinha.
        assertEquals(FORCED_RETIREMENT_AGE, outcome.player.age)
    }

    @Test
    fun `aposentados saem do elenco e levam a folha junto`() {
        val squad = listOf(
            makePlayer(1, overall = 80, age = 25, salary = 30_000),
            makePlayer(2, overall = 50, age = 38, salary = 12_000), // aposenta
            makePlayer(3, overall = 70, age = 20, salary = 8_000),
        )

        val outcomes = ageSquadOneSeason(squad, Random(9))
        val remaining = outcomes.remainingSquad()

        assertEquals(listOf(1L, 3L), remaining.map { it.id.value })
        assertEquals(
            38_000,
            remaining.sumOf { it.salary },
            "a folha deveria perder o salário do aposentado",
        )
        assertEquals(1, outcomes.count { it is AgingOutcome.Retired })
    }

    // ─── Resultado como sealed (o `when` conta a história) ────────────────

    @Test
    fun `a variante do resultado corresponde a variacao de overall`() {
        for (seed in seeds) {
            val before = makePlayer(1, overall = 75, age = 26)
            val outcome = before.ageOneSeason(Random(seed))
            val delta = outcome.player.overall - before.overall

            when (outcome) {
                is AgingOutcome.Evolved -> assertEquals(delta, outcome.overallDelta)
                is AgingOutcome.Regressed -> assertEquals(delta, outcome.overallDelta)
                is AgingOutcome.Steady -> assertEquals(0, delta)
                is AgingOutcome.Retired -> error("jogador de 26 anos não se aposenta (seed $seed)")
            }
        }
    }

    // ─── Determinismo ─────────────────────────────────────────────────────

    @Test
    fun `mesma seed produz exatamente a mesma virada de temporada`() {
        val squad = (1L..20L).map { makePlayer(it, overall = 60 + it.toInt(), age = 18 + it.toInt()) }

        val a = ageSquadOneSeason(squad, Random(2026))
        val b = ageSquadOneSeason(squad, Random(2026))

        assertEquals(a, b)
    }

    @Test
    fun `seeds diferentes produzem viradas diferentes`() {
        val squad = (1L..20L).map { makePlayer(it, overall = 70, age = 20) }

        val a = ageSquadOneSeason(squad, Random(1)).remainingSquad().map { it.overall }
        val b = ageSquadOneSeason(squad, Random(2)).remainingSquad().map { it.overall }

        assertNotEquals(a, b, "seeds distintas deveriam divergir — se falhar, o RNG não é consumido")
    }

    @Test
    fun `seed do envelhecimento nao colide entre pares de temporada e clube`() {
        val computed = (2020..2060).flatMap { season ->
            (0L..2_000L).map { agingSeed(season, ClubId(it)) }
        }

        assertEquals(computed.size, computed.toSet().size, "toda combinação deveria dar uma seed única")
        assertEquals(agingSeed(2027, ClubId(3)), agingSeed(2027, ClubId(3)), "deve ser estável")
    }

    @Test
    fun `seed do envelhecimento nao coincide com a do desgaste`() {
        // Sem o salt, temporada 7/clube 3 e rodada 7/clube 3 sorteariam a MESMA
        // sequência — envelhecimento e fadiga andariam correlacionados.
        assertNotEquals(agingSeed(7, ClubId(3)), fitnessSeed(7, ClubId(3)))
    }

    private fun deltaOf(age: Int, seed: Int): Int {
        val before = makePlayer(1, overall = 75, age = age)
        return before.ageOneSeason(Random(seed)).player.overall - before.overall
    }
}
