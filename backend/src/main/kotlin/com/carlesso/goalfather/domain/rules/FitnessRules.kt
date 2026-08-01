package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Availability
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import kotlin.random.Random

/**
 * Fadiga, recuperação e lesões com duração (issue #54). Funções PURAS —
 * sem Spring, sem I/O — com a aleatoriedade INJETADA por parâmetro, então a
 * mesma seed produz sempre o mesmo desgaste. É o que permite testar a virada
 * de rodada com uma tabela conhecida, sem subir contexto.
 *
 * Números herdados do protótipo (`prototype/goalfather-web.jsx`, a
 * especificação executável do CLAUDE.md): titular perde 10–25 de stamina por
 * partida com piso em 40, e o "departamento médico" custa R$ 30.000 e devolve
 * +30 de stamina.
 */

/** Perda de stamina de um titular por rodada — faixa do protótipo. */
val STARTER_STAMINA_LOSS: IntRange = 10..25

/**
 * Piso de stamina por desgaste de partida. Jogar nunca leva a zero: o
 * jogador fica ruim, não inutilizável (mesma escolha do protótipo).
 */
const val STAMINA_MATCH_FLOOR: Int = 40

/** Stamina que um reserva recupera por rodada em que não entrou em campo. */
const val BENCH_STAMINA_RECOVERY: Int = 12

/** Duração possível de uma lesão, em rodadas, sorteada com o RNG da partida. */
val INJURY_DURATION: IntRange = 1..4

/**
 * Acima deste nível de stamina o jogador rende 100%. Abaixo, o rendimento cai
 * proporcionalmente — ver [effectiveOverall].
 */
const val STAMINA_FULL_PERFORMANCE: Int = 70

/** Custo fixo de uma sessão do departamento médico, em centavos (R$ 30.000). */
const val MEDICAL_DEPARTMENT_COST_CENTS: Long = 30_000_00

/** Stamina devolvida a cada jogador por uma sessão do departamento médico. */
const val MEDICAL_STAMINA_RECOVERY: Int = 30

/** Rodadas de lesão que uma sessão do departamento médico encurta. */
const val MEDICAL_INJURY_ROUNDS_HEALED: Int = 1

/**
 * Overall EFETIVO do jogador — o quanto ele de fato rende hoje.
 *
 * Até [STAMINA_FULL_PERFORMANCE] de stamina não há penalidade (um time não
 * precisa estar com 100% para jogar bem); abaixo disso o rendimento escala
 * linearmente com a stamina. A função é contínua no piso (em exatamente 70 o
 * fator é 1.0), então não existe degrau artificial entre 70 e 69 — o técnico
 * sente a queda de forma gradual conforme o elenco cansa.
 */
fun Player.effectiveOverall(): Double =
    if (stamina >= STAMINA_FULL_PERFORMANCE) overall.toDouble()
    else overall * (stamina.toDouble() / STAMINA_FULL_PERFORMANCE)

/** Sorteia a duração de uma lesão. Usada pela engine com o RNG da partida. */
fun drawInjuryDuration(rng: Random): Int = INJURY_DURATION.random(rng)

/**
 * Seed determinística do desgaste de um clube numa rodada.
 *
 * Empacota os dois números em faixas DISJUNTAS do `Long` em vez de somá-los:
 * um `rodada * 1000 + clube` colide assim que um id de clube passa de 999
 * (rodada 1/clube 1000 daria a mesma seed que rodada 2/clube 0, e os dois
 * elencos cansariam de forma idêntica). O deslocamento de 32 bits é injetivo
 * para qualquer id que caiba num `Int` sem sinal.
 */
fun fitnessSeed(roundNumber: Int, clubId: ClubId): Long =
    (roundNumber.toLong() shl 32) xor clubId.value

/**
 * Aplica UMA rodada de desgaste ao elenco: titulares cansam, reservas
 * recuperam, lesões em curso andam uma rodada e as lesões novas da rodada
 * entram em vigor.
 *
 * Ordem importa: o decremento roda ANTES de aplicar [newInjuries], senão uma
 * lesão sofrida nesta mesma rodada já nasceria com uma rodada a menos. Uma
 * lesão nova nunca encurta a que estava em curso — fica valendo a maior das
 * durações (`maxOf`), que é o comportamento intuitivo de "machucou de novo".
 *
 * O RNG é consumido apenas para titulares, na ordem do elenco: dois elencos
 * iguais com a mesma seed produzem exatamente o mesmo resultado.
 *
 * @param starterIds quem entrou em campo — o resto do elenco descansa.
 * @param newInjuries lesões sofridas nesta rodada (id → duração sorteada).
 */
fun applyRoundFitness(
    squad: List<Player>,
    starterIds: Set<PlayerId>,
    rng: Random,
    newInjuries: Map<PlayerId, Int> = emptyMap(),
): List<Player> = squad.map { player ->
    val stamina =
        if (player.id in starterIds) {
            (player.stamina - STARTER_STAMINA_LOSS.random(rng)).coerceAtLeast(STAMINA_MATCH_FLOOR)
        } else {
            (player.stamina + BENCH_STAMINA_RECOVERY).coerceAtMost(100)
        }

    player.copy(
        stamina = stamina,
        availability = player.availability.advanceOneRound().withNewInjury(newInjuries[player.id]),
    )
}

/**
 * Trata o elenco no departamento médico: devolve stamina e encurta lesões em
 * curso. Pura — quem debita o caixa é o use case (mesmo padrão de
 * `FinanceRules`, que calcula mas não persiste).
 */
fun applyMedicalTreatment(squad: List<Player>): List<Player> = squad.map { player ->
    player.copy(
        stamina = (player.stamina + MEDICAL_STAMINA_RECOVERY).coerceAtMost(100),
        availability = player.availability.advanceOneRound(MEDICAL_INJURY_ROUNDS_HEALED),
    )
}

/**
 * Faz a lesão andar [rounds] rodadas. `when` exaustivo sobre o `sealed` —
 * sem `else`: se um dia surgir uma variante nova (suspensão por cartão, por
 * exemplo) o compilador aponta este ponto.
 */
private fun Availability.advanceOneRound(rounds: Int = 1): Availability = when (this) {
    is Availability.Available -> Availability.Available
    is Availability.Injured ->
        (roundsOut - rounds).let { left ->
            if (left >= 1) Availability.Injured(left) else Availability.Available
        }
}

/** Aplica a lesão sofrida na rodada, mantendo a mais longa das duas. */
private fun Availability.withNewInjury(roundsOut: Int?): Availability = when {
    roundsOut == null -> this
    this is Availability.Injured -> Availability.Injured(maxOf(this.roundsOut, roundsOut))
    else -> Availability.Injured(roundsOut)
}
