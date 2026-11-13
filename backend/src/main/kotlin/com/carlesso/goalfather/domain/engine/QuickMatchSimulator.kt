package com.carlesso.goalfather.domain.engine

import com.carlesso.goalfather.domain.engine.MatchSimulator.Companion.EVENT_RATE
import com.carlesso.goalfather.domain.engine.MatchSimulator.Companion.P_CARD
import com.carlesso.goalfather.domain.engine.MatchSimulator.Companion.P_GOAL
import com.carlesso.goalfather.domain.engine.MatchSimulator.Companion.P_MISS
import com.carlesso.goalfather.domain.engine.MatchSimulator.Companion.P_RED_CARD
import com.carlesso.goalfather.domain.engine.MatchSimulator.Companion.P_SAVE
import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.event.MatchStats
import com.carlesso.goalfather.domain.event.TeamStats
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.attackFactor
import com.carlesso.goalfather.domain.model.defenseFactor
import com.carlesso.goalfather.domain.model.teamStrength
import com.carlesso.goalfather.domain.rules.drawInjuryDuration
import com.carlesso.goalfather.domain.rules.drawShooter
import com.carlesso.goalfather.domain.rules.penaltyShootout
import kotlin.math.ln
import kotlin.random.Random

/**
 * Resultado de uma partida simulada pela [QuickMatchSimulator] (issue #129):
 * tudo que tabela, artilharia, cartões e lesões precisam, sem o feed minuto a
 * minuto.
 *
 * [highlights] guarda só os lances que mexem na ficha de alguém — gol, cartão
 * e lesão —, em ordem cronológica. Reusar os próprios tipos de [MatchEvent]
 * (em vez de um `ScorerEntry` paralelo) é de propósito: quem aplica os efeitos
 * da rodada já sabe ler `Goal`/`Card`/`Injury`, então jogo rápido e jogo
 * completo passam pelo MESMO código de efeitos, sem um segundo caminho que
 * pudesse divergir. Defesas e chutes para fora não viram objeto: entram só nas
 * contagens de [stats].
 */
data class QuickMatchResult(
    val homeGoals: Int,
    val awayGoals: Int,
    val highlights: List<MatchEvent>,
    val shootout: MatchEvent.PenaltyShootout?,
    val stats: MatchStats,
) {
    init {
        // Invariante barato que protege quem lê: o feed de destaques só tem os
        // três lances que a ficha do jogador consome.
        require(highlights.all { it is MatchEvent.Goal || it is MatchEvent.Card || it is MatchEvent.Injury }) {
            "Destaques de jogo rápido só têm Goal, Card e Injury"
        }
    }

    /** Apito final no mesmo formato da engine completa — o placar dos 90 minutos. */
    val fullTime: MatchEvent.FullTime
        get() = MatchEvent.FullTime(minute = 90, homeGoals = homeGoals, awayGoals = awayGoals, stats = stats)

    /**
     * A "súmula" do jogo rápido: destaques, disputa de pênaltis (se houve) e o
     * apito final — a mesma ordem de cauda da engine completa.
     * `listOfNotNull` evita o `if (shootout != null) add(...)`.
     */
    fun summary(): List<MatchEvent> = highlights + listOfNotNull(shootout) + fullTime
}

/**
 * Simulação rápida de partida (issue #129), para jogos SEM técnico humano: não
 * há ninguém assistindo, então não há `Flow`, nem `KickOff`, nem objeto para
 * cada defesa — só o placar e o que alimenta os efeitos da rodada.
 *
 * **Mesmo modelo, outro jeito de sortear.** A engine completa testa 90 minutos,
 * cada um com chance [EVENT_RATE] de ter lance. Aqui pulamos direto para o
 * próximo minuto com lance: o número de minutos "vazios" antes de um sucesso
 * numa sequência de Bernoullis segue uma distribuição GEOMÉTRICA, que se
 * sorteia com um único `nextDouble()` por inversão (ver [quietMinutes]). São
 * ~11 sorteios de minuto em vez de 90, e a distribuição dos lances é
 * exatamente a mesma. Daí em diante, cada lance segue as mesmas fatias de
 * probabilidade, a mesma divisão de gols por força × tática e os mesmos
 * sorteios de autor/lesão — reusados das regras de domínio, não copiados.
 *
 * Consequência: mesma seed NÃO dá o mesmo jogo da engine completa (o RNG é
 * consumido em outra ordem), mas a distribuição de resultados é a mesma — o
 * que o teste estatístico de 10 mil jogos fixa. E mesma seed dá sempre o mesmo
 * jogo rápido: a classe é pura sobre `MatchSetup` + `Random`.
 *
 * Sem estado: uma instância serve a todas as partidas, inclusive em paralelo
 * (cada chamada recebe o próprio `Random`).
 */
class QuickMatchSimulator {

    fun simulate(setup: MatchSetup, rng: Random = Random.Default): QuickMatchResult {
        val odds = MatchOdds.of(setup)

        // Estado mutável LOCAL de um laço curto: não escapa da função, e o
        // resultado devolvido é imutável. Quem fica fora de campo (vermelho ou
        // lesão) sai da lista do seu lado — recalculada só quando alguém sai,
        // em vez de filtrar o elenco a cada lance como a engine completa.
        var homeOnPitch: List<Player> = setup.home.players
        var awayOnPitch: List<Player> = setup.away.players
        fun onPitch(home: Boolean) = if (home) homeOnPitch else awayOnPitch
        fun sendOff(home: Boolean, id: PlayerId) {
            if (home) homeOnPitch = homeOnPitch.filterNot { it.id == id }
            else awayOnPitch = awayOnPitch.filterNot { it.id == id }
        }

        val home = SideTally()
        val away = SideTally()
        fun tally(isHome: Boolean) = if (isHome) home else away
        val highlights = mutableListOf<MatchEvent>()

        var minute = 0
        while (true) {
            minute += 1 + quietMinutes(rng)
            if (minute > 90) break

            val roll = rng.nextDouble()
            when {
                roll < odds.goalP -> {
                    val isHome = rng.nextDouble() < odds.homeRatio
                    val scorer = onPitch(isHome).drawShooter(rng) ?: continue
                    tally(isHome).apply { goals++; shots++; shotsOnTarget++ }
                    highlights += MatchEvent.Goal(minute = minute, scorerId = scorer.id, home = isHome)
                }
                roll < odds.goalP + odds.saveP -> {
                    // Mesma leitura da engine: a finalização vem de um lado e a
                    // defesa conta para o goleiro do OUTRO.
                    val shooterIsHome = rng.nextDouble() < odds.homeRatio
                    tally(shooterIsHome).apply { shots++; shotsOnTarget++ }
                    tally(!shooterIsHome).saves++
                }
                roll < odds.goalP + odds.saveP + P_MISS -> {
                    val isHome = rng.nextDouble() < odds.homeRatio
                    // O autor não é registrado, mas o sorteio fica: sem ninguém
                    // apto a chutar o lance não acontece, como na engine.
                    onPitch(isHome).drawShooter(rng) ?: continue
                    tally(isHome).shots++
                }
                roll < odds.goalP + odds.saveP + P_MISS + P_CARD -> {
                    val isHome = rng.nextDouble() < 0.5
                    val squad = onPitch(isHome)
                    if (squad.isEmpty()) continue
                    val player = squad[rng.nextInt(squad.size)]
                    val red = rng.nextDouble() < P_RED_CARD
                    if (red) {
                        sendOff(isHome, player.id)
                        tally(isHome).redCards++
                    } else {
                        tally(isHome).yellowCards++
                    }
                    highlights += MatchEvent.Card(minute = minute, playerId = player.id, red = red, home = isHome)
                }
                else -> {
                    val isHome = rng.nextDouble() < 0.5
                    val squad = onPitch(isHome)
                    if (squad.isEmpty()) continue
                    val player = squad[rng.nextInt(squad.size)]
                    sendOff(isHome, player.id)
                    highlights += MatchEvent.Injury(
                        minute = minute,
                        playerId = player.id,
                        roundsOut = drawInjuryDuration(rng),
                    )
                }
            }
        }

        // Mata-mata: a mesma regra de domínio da engine, com o mesmo RNG, já
        // depois do minuto 90.
        val shootout = if (setup.knockout && home.goals == away.goals) {
            val result = penaltyShootout(homeOnPitch, awayOnPitch, rng)
            MatchEvent.PenaltyShootout(
                minute = 90,
                homeScore = result.score.home,
                awayScore = result.score.away,
                kicks = result.kicks,
            )
        } else {
            null
        }

        return QuickMatchResult(
            homeGoals = home.goals,
            awayGoals = away.goals,
            highlights = highlights,
            shootout = shootout,
            stats = MatchStats(home.toTeamStats(), away.toTeamStats()),
        )
    }

    /**
     * Quantos minutos seguidos passam SEM lance antes do próximo — amostra de
     * uma geométrica de parâmetro [EVENT_RATE] por inversão da CDF:
     * `P(K ≥ k) = (1-p)^k` ⇒ `K = ⌊ln(U) / ln(1-p)⌋` com `U` uniforme em (0, 1].
     * `1 - nextDouble()` troca o intervalo `[0, 1)` por `(0, 1]` e evita `ln(0)`.
     */
    private fun quietMinutes(rng: Random): Int =
        (ln(1.0 - rng.nextDouble()) / LN_NO_EVENT).toInt()

    /**
     * Contadores de um lado. `var` dentro de uma classe privada e local ao
     * sorteio — o `apply { goals++; shots++ }` lê melhor que um `copy()` por
     * lance, e nada mutável sai daqui: [toTeamStats] devolve o valor imutável.
     */
    private class SideTally {
        var goals = 0
        var shots = 0
        var shotsOnTarget = 0
        var saves = 0
        var yellowCards = 0
        var redCards = 0

        fun toTeamStats() = TeamStats(
            shots = shots,
            shotsOnTarget = shotsOnTarget,
            saves = saves,
            yellowCards = yellowCards,
            redCards = redCards,
        )
    }

    private companion object {
        /** `ln(1 - EVENT_RATE)`, calculado uma vez: é o denominador da geométrica. */
        val LN_NO_EVENT: Double = ln(1.0 - EVENT_RATE)
    }
}

/**
 * As probabilidades de um lance, função pura do `MatchSetup` (força × tática).
 * É a MESMA conta que o [MatchSimulator] faz no início de cada partida — com os
 * mesmos fatores de domínio (`teamStrength`, `attackFactor`, `defenseFactor`)
 * e as mesmas constantes do companion dele. A engine completa não foi tocada
 * pela issue #129 (fora de escopo); o teste estatístico rápida × completa é o
 * que garante que as duas contas não se afastem.
 */
internal data class MatchOdds(
    val goalP: Double,
    val saveP: Double,
    val homeRatio: Double,
) {
    companion object {
        fun of(setup: MatchSetup): MatchOdds {
            val homeGoalWeight = setup.home.attackFactor() * setup.away.defenseFactor()
            val awayGoalWeight = setup.away.attackFactor() * setup.home.defenseFactor()
            val goalP = (P_GOAL * (homeGoalWeight + awayGoalWeight) / 2).coerceIn(0.0, P_GOAL + P_SAVE)
            val homeShare = setup.home.teamStrength() * homeGoalWeight
            val awayShare = setup.away.teamStrength() * awayGoalWeight
            return MatchOdds(
                goalP = goalP,
                saveP = P_GOAL + P_SAVE - goalP,
                homeRatio = if (homeShare + awayShare > 0) homeShare / (homeShare + awayShare) else 0.5,
            )
        }
    }
}

/**
 * Apito inicial de um setup, no mesmo formato que a engine completa emite.
 * Serve ao drill-down de um jogo rápido (`PlayMatchService`), que precisa de um
 * `KickOff` para a tela montar o placar mesmo sem o feed minuto a minuto.
 */
fun MatchSetup.kickOff(): MatchEvent.KickOff = MatchEvent.KickOff(
    minute = 0,
    homeClubName = homeName,
    awayClubName = awayName,
    homeStrength = home.teamStrength(),
    awayStrength = away.teamStrength(),
    homePosture = home.tactics.posture,
    awayPosture = away.tactics.posture,
)
