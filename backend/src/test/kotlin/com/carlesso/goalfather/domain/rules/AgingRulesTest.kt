package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.Position
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
        // Ganha o ano como todo mundo: TODA variante carrega o jogador na idade
        // com que terminou a virada, para o extrato de fim de temporada não
        // mentir a idade de quem parou.
        assertEquals(FORCED_RETIREMENT_AGE + 1, outcome.player.age)
    }

    @Test
    fun `nem o jogador no topo do invariante de idade quebra o Player`() {
        // Seed absurdo (alguém já em 50 anos): a virada não pode estourar o
        // `require(age in 15..50)` só para incrementar o ano.
        val outcome = makePlayer(1, overall = 60, age = Player.AGE_RANGE.last).ageOneSeason(Random(1))

        assertIs<AgingOutcome.Retired>(outcome)
        assertEquals(Player.AGE_RANGE.last, outcome.player.age)
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

    // ─── Promoção da base (elenco não pode só encolher) ───────────────────

    @Test
    fun `a base repoe o aposentado e o elenco nao encolhe`() {
        val squad = listOf(
            makePlayer(1, position = Position.GK, overall = 62, age = 38), // aposenta
            makePlayer(2, position = Position.CB, overall = 80, age = 25),
            makePlayer(3, position = Position.FW, overall = 55, age = 39), // aposenta
        )

        val turn = ageSquadForSeason(squad, ClubId(7), season = 2030)

        assertEquals(squad.size, turn.squad.size, "o elenco deveria manter o tamanho")
        assertEquals(2, turn.retirements.size)
        // O garoto entra na MESMA posição do veterano — o time não fica sem goleiro.
        assertEquals(
            listOf(Position.GK, Position.FW),
            turn.retirements.map { it.promoted.position },
        )
        assertTrue(
            turn.retirements.all { it.promoted.age in YOUTH_AGE },
            "promovido deveria vir da faixa de base $YOUTH_AGE",
        )
        assertTrue(
            turn.retirements.all { it.promoted.overall < it.retired.overall },
            "o garoto entra abaixo do veterano — é aposta, não reposição imediata",
        )
        assertTrue(
            turn.retirements.all { it.clubId == ClubId(7) },
            "a aposentadoria carrega o clube, para o evento saber de quem falar",
        )
    }

    @Test
    fun `elenco sem aposentadoria passa intacto em tamanho e ordem`() {
        val squad = (1L..11L).map { makePlayer(it, overall = 75, age = 24) }

        val turn = ageSquadForSeason(squad, ClubId(1), season = 2027)

        assertTrue(turn.retirements.isEmpty())
        assertEquals(squad.map { it.id }, turn.squad.map { it.id })
    }

    @Test
    fun `virada do elenco e deterministica por temporada e clube`() {
        val squad = listOf(makePlayer(1, overall = 60, age = 38), makePlayer(2, overall = 70, age = 22))

        assertEquals(
            ageSquadForSeason(squad, ClubId(3), 2028),
            ageSquadForSeason(squad, ClubId(3), 2028),
        )
        assertNotEquals(
            ageSquadForSeason(squad, ClubId(3), 2028).squad,
            ageSquadForSeason(squad, ClubId(4), 2028).squad,
            "clubes distintos não deveriam viver a mesma temporada",
        )
    }

    @Test
    fun `id do promovido e unico por clube temporada e vaga`() {
        val ids = (2026..2050).flatMap { season ->
            (1L..30L).flatMap { club ->
                (1..5).map { slot -> youthPlayerId(ClubId(club), season, slot) }
            }
        }

        assertEquals(ids.size, ids.toSet().size, "cada garoto precisa de um id só seu")
        // Faixa alta e disjunta: não colide com o seed (`clube * 1000 + slot`)
        // nem com o mercado, e cabe no `number` do JavaScript (2^53).
        assertTrue(ids.all { it.value > 1_000_000L && it.value < (1L shl 53) })
    }

    /**
     * Regressão do achado da review: elencos da IA nasciam TODOS com 25 anos e
     * cruzavam a barreira da aposentadoria na mesma virada — o clube ficava
     * vazio por volta da temporada 11. Com a base repondo 1:1, o tamanho do
     * elenco não cai por mais longa que seja a simulação.
     */
    @Test
    fun `vinte temporadas seguidas nao esvaziam o elenco`() {
        var squad = (1L..11L).map { makePlayer(it, overall = 70, age = 25) }

        for (season in 2026..2045) {
            val turn = ageSquadForSeason(squad, ClubId(1), season)
            squad = turn.squad
            assertEquals(11, squad.size, "elenco encolheu na temporada $season")
        }

        // E o elenco realmente girou: ninguém sobrevive 20 temporadas.
        assertTrue(squad.none { it.id.value in 1L..11L }, "o elenco deveria ter se renovado")
    }

    // ─── Seed do mercado ──────────────────────────────────────────────────

    @Test
    fun `seed do mercado nao coincide com a de nenhum clube na mesma temporada`() {
        val season = 2031
        val clubSeeds = (0L..2_000L).map { agingSeed(season, ClubId(it)) }.toSet()

        assertTrue(marketAgingSeed(season) !in clubSeeds)
        assertNotEquals(marketAgingSeed(season), marketAgingSeed(season + 1))
    }

    private fun deltaOf(age: Int, seed: Int): Int {
        val before = makePlayer(1, overall = 75, age = age)
        return before.ageOneSeason(Random(seed)).player.overall - before.overall
    }
}
