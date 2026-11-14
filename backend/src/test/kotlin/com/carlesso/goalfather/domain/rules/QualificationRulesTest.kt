package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.LegScore
import com.carlesso.goalfather.domain.model.Legs
import com.carlesso.goalfather.domain.model.Pairing
import com.carlesso.goalfather.domain.model.PenaltyScore
import com.carlesso.goalfather.domain.model.QualificationRule
import com.carlesso.goalfather.domain.model.SeasonCompetitions
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.domain.model.TableZone
import com.carlesso.goalfather.domain.model.TieResult
import com.carlesso.goalfather.domain.model.TwoLeggedTieResult
import com.carlesso.goalfather.domain.model.WorldQualificationRules
import com.carlesso.goalfather.domain.model.WorldQualificationRules.BRA_COPA_DO_BRASIL
import com.carlesso.goalfather.domain.model.WorldQualificationRules.BRA_SERIE_A
import com.carlesso.goalfather.domain.model.WorldQualificationRules.BRA_SERIE_B
import com.carlesso.goalfather.domain.model.WorldQualificationRules.CONMEBOL_LIBERTADORES
import com.carlesso.goalfather.domain.model.WorldQualificationRules.LIBERTADORES_GROUP_STAGE
import com.carlesso.goalfather.domain.model.WorldQualificationRules.LIBERTADORES_SECOND_STAGE
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Vagas, acesso, rebaixamento e playoff (issue #128) — testes PUROS de domínio
 * com tabelas fabricadas. A primeira seção são os casos da regra antiga de
 * promoção/rebaixamento (issue #47), mantidos como casos da regra nova.
 */
class QualificationRulesTest {

    /** Tabela de divisão com clubes já na ordem final (posição 1..N). */
    private fun table(division: Int, vararg clubIds: Long) = Standings(
        season = 2026,
        round = 5,
        division = Division(division),
        rows = clubIds.mapIndexed { i, id ->
            StandingRow(position = i + 1, clubId = ClubId(id), clubName = "Club $id")
        },
    )

    /** Tabela final com os clubes [ids] nessa ordem. */
    private fun ranked(ids: LongRange): Standings = table(1, *ids.toList().toLongArray())

    private fun resolved(transition: SeasonTransition): SeasonTransition.Resolved = assertIs(transition)

    // ─── Pirâmide do jogo (casos da issue #47) ────────────────────────────

    @Test
    fun `ultimos da divisao de cima trocam com os primeiros da de baixo`() {
        val next = applyPromotionRelegation(
            listOf(
                table(1, 1, 2, 3, 4, 5, 6),
                table(2, 7, 8, 9, 10, 11, 12),
            ),
        )

        // 5 e 6 caem; 7 e 8 sobem; os demais ficam onde estão.
        assertEquals(Division(2), next[ClubId(5)])
        assertEquals(Division(2), next[ClubId(6)])
        assertEquals(Division(1), next[ClubId(7)])
        assertEquals(Division(1), next[ClubId(8)])
        assertEquals(Division(1), next[ClubId(1)])
        assertEquals(Division(2), next[ClubId(9)])
        assertEquals(Division(2), next[ClubId(12)])

        // Balanceado: cada divisão continua com 6 clubes.
        assertEquals(6, next.values.count { it == Division(1) })
        assertEquals(6, next.values.count { it == Division(2) })
    }

    @Test
    fun `tres divisoes encadeiam as trocas entre pares adjacentes`() {
        val next = applyPromotionRelegation(
            listOf(
                table(1, 1, 2, 3, 4),
                table(2, 5, 6, 7, 8),
                table(3, 9, 10, 11, 12),
            ),
        )

        assertEquals(Division(2), next[ClubId(3)]) // caiu da 1ª
        assertEquals(Division(2), next[ClubId(4)])
        assertEquals(Division(1), next[ClubId(5)]) // subiu para a 1ª
        assertEquals(Division(1), next[ClubId(6)])
        assertEquals(Division(3), next[ClubId(7)]) // caiu da 2ª
        assertEquals(Division(3), next[ClubId(8)])
        assertEquals(Division(2), next[ClubId(9)]) // subiu para a 2ª
        assertEquals(Division(2), next[ClubId(10)])
        assertEquals(Division(3), next[ClubId(11)])
    }

    @Test
    fun `divisao unica nao move ninguem`() {
        val next = applyPromotionRelegation(listOf(table(1, 1, 2, 3, 4)))
        assertEquals(setOf(Division.FIRST), next.values.toSet())
    }

    @Test
    fun `mesma tabela final produz sempre a mesma recomposicao (determinismo)`() {
        val tables = listOf(table(1, 1, 2, 3, 4, 5, 6), table(2, 7, 8, 9, 10, 11, 12))
        // Ordem de entrada das tabelas não importa.
        assertEquals(applyPromotionRelegation(tables), applyPromotionRelegation(tables.reversed()))
    }

    @Test
    fun `divisao duplicada e rejeitada`() {
        assertThrows<IllegalArgumentException> {
            applyPromotionRelegation(listOf(table(1, 1, 2), table(1, 3, 4)))
        }
    }

    @Test
    fun `buraco na piramide (tiers nao contiguos) e rejeitado`() {
        // Divisões 1 e 3 sem a 2: erro de seed — a regra não deve "pontear".
        assertThrows<IllegalArgumentException> {
            applyPromotionRelegation(listOf(table(1, 1, 2), table(3, 3, 4)))
        }
    }

    @Test
    fun `a piramide vira um par Relegation + Promotion por degrau`() {
        val d1 = SeasonCompetitions.leagueId(Division(1))
        val d2 = SeasonCompetitions.leagueId(Division(2))
        assertEquals(
            listOf(
                QualificationRule.Relegation(from = d1, bottomN = 2, to = d2),
                QualificationRule.Promotion(from = d2, topN = 2, to = d1),
            ),
            pyramidRules(listOf(table(2, 7, 8, 9), table(1, 1, 2, 3))),
        )
        // Divisão menor que as vagas: troca só o que cabe, como a regra antiga.
        assertEquals(1, assertIs<QualificationRule.Relegation>(pyramidRules(listOf(table(1, 1), table(2, 2)))[0]).bottomN)
    }

    @Test
    fun `vagas e faixas da zona seguem a posicao da divisao na piramide`() {
        val (elite, second) = listOf(table(1, 1, 2, 3, 4, 5, 6), table(2, 7, 8, 9, 10, 11, 12)).withPyramidZones()

        assertEquals(0, elite.promotionSpots)
        assertEquals(PROMOTION_RELEGATION_SPOTS, elite.relegationSpots)
        assertEquals(PROMOTION_RELEGATION_SPOTS, second.promotionSpots)
        assertEquals(0, second.relegationSpots)
        assertEquals(
            List(4) { TableZone.NONE } + List(2) { TableZone.RELEGATION },
            elite.rows.map { it.zone },
        )
        assertEquals(
            List(2) { TableZone.PROMOTION } + List(4) { TableZone.NONE },
            second.rows.map { it.zone },
        )
        // Divisão única: sem zona nenhuma.
        val (alone) = listOf(table(1, 1, 2, 3, 4)).withPyramidZones()
        assertEquals(0, alone.relegationSpots)
        assertEquals(setOf(TableZone.NONE), alone.rows.map { it.zone }.toSet())
    }

    // ─── Brasil: Série A/B e Libertadores ─────────────────────────────────

    private val serieA = ranked(1L..20L)
    private val serieB = ranked(21L..40L)
    private val brazilTables = mapOf(BRA_SERIE_A to serieA, BRA_SERIE_B to serieB)

    @Test
    fun `Serie A e B trocam os 4 ultimos e os 4 primeiros na virada`() {
        val transition = resolved(
            applyQualificationRules(WorldQualificationRules.BRAZIL, brazilTables, mapOf(BRA_COPA_DO_BRASIL to ClubId(30))),
        )
        val next = transition.memberships

        (17L..20L).forEach { assertEquals(BRA_SERIE_B, next[ClubId(it)], "clube $it cai") }
        (21L..24L).forEach { assertEquals(BRA_SERIE_A, next[ClubId(it)], "clube $it sobe") }
        assertEquals(BRA_SERIE_A, next[ClubId(16)])
        assertEquals(BRA_SERIE_B, next[ClubId(25)])
        // Mapa total e balanceado: 20 + 20.
        assertEquals(40, next.size)
        assertEquals(20, next.values.count { it == BRA_SERIE_A })
    }

    @Test
    fun `campeao da copa fora do G-6 soma vaga ao G-6`() {
        val qualifications = resolved(
            applyQualificationRules(WorldQualificationRules.BRAZIL, brazilTables, mapOf(BRA_COPA_DO_BRASIL to ClubId(10))),
        ).qualifications

        assertEquals(listOf(10L, 1, 2, 3, 4, 5, 6), qualifications.map { it.club.value })
        assertEquals(setOf(CONMEBOL_LIBERTADORES), qualifications.map { it.competition }.toSet())
        assertEquals(
            List(5) { LIBERTADORES_GROUP_STAGE } + List(2) { LIBERTADORES_SECOND_STAGE },
            qualifications.map { it.entryStage },
        )
    }

    @Test
    fun `cascata - campeao da copa no G-6 faz a vaga descer para o 7o`() {
        // O 3º colocado levou a Copa do Brasil: entra pela copa na fase de grupos,
        // e a tabela segue do 4º em diante — o 7º herda a vaga da segunda fase.
        val qualifications = resolved(
            applyQualificationRules(WorldQualificationRules.BRAZIL, brazilTables, mapOf(BRA_COPA_DO_BRASIL to ClubId(3))),
        ).qualifications

        assertEquals(listOf(3L, 1, 2, 4, 5, 6, 7), qualifications.map { it.club.value })
        val byClub = qualifications.associateBy { it.club.value }
        assertIs<QualificationRule.Champion>(byClub.getValue(3).via)
        assertEquals(LIBERTADORES_GROUP_STAGE, byClub.getValue(5).entryStage) // subiu para o G-4 efetivo
        assertEquals(LIBERTADORES_SECOND_STAGE, byClub.getValue(7).entryStage)
    }

    @Test
    fun `campeao ja classificado por outro titulo deixa a vaga para o proximo da tabela`() {
        val cupB = CompetitionId("copa-b")
        val rules = listOf(
            QualificationRule.Champion(from = BRA_COPA_DO_BRASIL, to = CONMEBOL_LIBERTADORES, entryStage = "G"),
            QualificationRule.Champion(from = cupB, to = CONMEBOL_LIBERTADORES, entryStage = "G"),
            QualificationRule.TopN(from = BRA_SERIE_A, n = 2, to = CONMEBOL_LIBERTADORES, entryStage = "G"),
        )
        val qualifications = resolved(
            applyQualificationRules(
                rules,
                mapOf(BRA_SERIE_A to serieA),
                champions = mapOf(BRA_COPA_DO_BRASIL to ClubId(30), cupB to ClubId(30)),
            ),
        ).qualifications

        // Duas copas, um campeão só: a 2ª vaga de título cai na tabela (1, 2 e 3).
        assertEquals(listOf(30L, 1, 2, 3), qualifications.map { it.club.value })
    }

    @Test
    fun `campeao de liga sem copa informada e o lider da tabela`() {
        val rules = listOf(QualificationRule.Champion(from = BRA_SERIE_A, to = CONMEBOL_LIBERTADORES, entryStage = "G"))
        val qualifications = resolved(applyQualificationRules(rules, mapOf(BRA_SERIE_A to serieA))).qualifications
        assertEquals(listOf(ClubId(1)), qualifications.map { it.club })
    }

    @Test
    fun `copa sem campeao informado e erro de configuracao`() {
        assertThrows<IllegalArgumentException> {
            applyQualificationRules(WorldQualificationRules.BRAZIL, brazilTables)
        }
    }

    @Test
    fun `faixas do Brasileirao - G-6 continental e Z-4`() {
        val zones = tableZones(BRA_SERIE_A, 20, WorldQualificationRules.BRAZIL)
        assertEquals(
            List(6) { TableZone.QUALIFICATION } + List(10) { TableZone.NONE } + List(4) { TableZone.RELEGATION },
            zones,
        )
        assertEquals(List(4) { TableZone.PROMOTION } + List(16) { TableZone.NONE }, tableZones(BRA_SERIE_B, 20, WorldQualificationRules.BRAZIL))
    }

    @Test
    fun `liga europeia do MVP e fechada - sem faixa e ninguem se move`() {
        val premier = CompetitionId("eng-premier-league")
        assertEquals(setOf(TableZone.NONE), tableZones(premier, 20, WorldQualificationRules.EUROPE).toSet())
        val next = resolved(applyQualificationRules(WorldQualificationRules.EUROPE, mapOf(premier to serieA))).memberships
        assertEquals(setOf(premier), next.values.toSet())
    }

    @Test
    fun `faixas sobrepostas ou fora da tabela sao rejeitadas`() {
        val league = CompetitionId("liga")
        val other = CompetitionId("outra")
        assertThrows<IllegalArgumentException> {
            tableZones(
                league,
                4,
                listOf(
                    QualificationRule.TopN(from = league, n = 3, to = other, entryStage = "G"),
                    QualificationRule.Relegation(from = league, bottomN = 2, to = other),
                ),
            )
        }
        assertThrows<IllegalArgumentException> {
            tableZones(league, 4, listOf(QualificationRule.Relegation(from = league, bottomN = 5, to = other)))
        }
    }

    // ─── Playoff (Bundesliga: 16º da elite × 3º da segunda) ───────────────

    private val bundesliga = CompetitionId("ger-bundesliga")
    private val zweite = CompetitionId("ger-2-bundesliga")
    private val playoff = QualificationRule.Playoff(
        upper = bundesliga, upperPosition = 16, lower = zweite, lowerPosition = 3, format = Legs.TWO_LEGGED,
    )
    private val germanRules = listOf(
        QualificationRule.Relegation(from = bundesliga, bottomN = 2, to = zweite),
        QualificationRule.Promotion(from = zweite, topN = 2, to = bundesliga),
        playoff,
    )
    private val germanTables = mapOf(bundesliga to ranked(1L..18L), zweite to ranked(21L..38L))

    @Test
    fun `playoff pendente gera os jogos de ida e volta`() {
        val pending = assertIs<SeasonTransition.PlayoffsPending>(applyQualificationRules(germanRules, germanTables))
        val tie = pending.ties.single()

        assertEquals(ClubId(16), tie.upperClub)
        assertEquals(ClubId(23), tie.lowerClub)
        // Ida na casa do 3º da segunda divisão; a elite decide em casa.
        assertEquals(
            listOf(listOf(Pairing(ClubId(23), ClubId(16))), listOf(Pairing(ClubId(16), ClubId(23)))),
            tie.matches(),
        )
        assertEquals(TableZone.PLAYOFF, tableZones(bundesliga, 18, germanRules)[15])
        assertEquals(TableZone.PLAYOFF, tableZones(zweite, 18, germanRules)[2])
    }

    @Test
    fun `playoff vencido pelo clube de baixo troca os dois de liga`() {
        val tie = assertIs<SeasonTransition.PlayoffsPending>(applyQualificationRules(germanRules, germanTables)).ties.single()
        // 23 vence a ida em casa por 2 × 0 e perde a volta por 1 × 0: agregado 2 × 1.
        val winner = tie.winnerOf(TwoLeggedTieResult(ClubId(23), ClubId(16), LegScore(2, 0), LegScore(1, 0)))
        assertEquals(ClubId(23), winner)

        val next = resolved(applyQualificationRules(germanRules, germanTables, playoffWinners = mapOf(playoff to winner))).memberships
        assertEquals(bundesliga, next[ClubId(23)])
        assertEquals(zweite, next[ClubId(16)])
        assertEquals(18, next.values.count { it == bundesliga })
    }

    @Test
    fun `playoff vencido pelo clube de cima mantem os dois onde estao`() {
        val tie = assertIs<SeasonTransition.PlayoffsPending>(applyQualificationRules(germanRules, germanTables)).ties.single()
        val single = tie.copy(rule = playoff.copy(format = Legs.SINGLE))
        assertEquals(listOf(listOf(Pairing(ClubId(23), ClubId(16)))), single.matches())
        // Jogo único empatado, o visitante (o de cima) leva nos pênaltis.
        val winner = single.winnerOf(TieResult(1, 1, PenaltyScore(home = 3, away = 4)))
        assertEquals(ClubId(16), winner)

        val next = resolved(applyQualificationRules(germanRules, germanTables, playoffWinners = mapOf(playoff to winner))).memberships
        assertEquals(bundesliga, next[ClubId(16)])
        assertEquals(zweite, next[ClubId(23)])
    }

    @Test
    fun `vencedor que nao disputou o playoff e rejeitado`() {
        assertThrows<IllegalArgumentException> {
            applyQualificationRules(germanRules, germanTables, playoffWinners = mapOf(playoff to ClubId(1)))
        }
        val tie = assertIs<SeasonTransition.PlayoffsPending>(applyQualificationRules(germanRules, germanTables)).ties.single()
        assertThrows<IllegalArgumentException> {
            // Mando invertido: não é a súmula deste playoff.
            tie.winnerOf(TwoLeggedTieResult(ClubId(16), ClubId(23), LegScore(1, 0), LegScore(0, 0)))
        }
    }

    @Test
    fun `duas regras sobre a mesma posicao sao rejeitadas`() {
        val rules = germanRules + QualificationRule.Relegation(from = bundesliga, bottomN = 1, to = CompetitionId("outra"))
        assertThrows<IllegalArgumentException> { applyQualificationRules(rules, germanTables) }
    }
}
