package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.event.TrainingEvent
import com.carlesso.goalfather.domain.model.Availability
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.TrainedAttribute
import com.carlesso.goalfather.domain.model.TrainingFocus
import kotlin.random.Random

/**
 * Treino semanal com foco escolhido pelo técnico (issue #58). Funções PURAS —
 * sem Spring, sem I/O — com a aleatoriedade INJETADA por parâmetro: a mesma
 * seed produz sempre a mesma semana, o que permite testar cada foco sem subir
 * contexto.
 *
 * A regra é o que transforma o intervalo entre rodadas numa decisão: até aqui
 * o técnico só escalava e apertava "jogar". Agora a semana é um trade-off —
 * evoluir o elenco custa forma física e risco de lesão; poupá-lo devolve
 * stamina, mas ninguém melhora.
 *
 * Os efeitos entram na partida pelos ATRIBUTOS e pela STAMINA (que a
 * `FitnessRules.effectiveOverall` já desconta) — a engine não sabe que treino
 * existe, e não precisou mudar.
 */

/** Quanto uma sessão bem-sucedida acrescenta ao atributo do foco. */
const val TRAINING_ATTRIBUTE_GAIN: Int = 1

/** Duração da lesão leve de treino, em rodadas — o susto, não a temporada. */
const val TRAINING_INJURY_ROUNDS: Int = 1

/**
 * Chance de um jogador ganhar [TRAINING_ATTRIBUTE_GAIN] na semana, por faixa
 * etária. Jovem aproveita muito mais o treino — mesma história que
 * `AgeBand.overallDelta` conta na virada de temporada, aqui na escala da
 * rodada.
 *
 * **Extension property** sobre a [AgeBand] de `AgingRules`, em vez de mais um
 * campo no enum: a faixa etária é conceito do envelhecimento e não deveria
 * carregar constantes de treino. Idioma Kotlin — comportamento perto do dado,
 * sem acoplar o dono do dado a quem o usa (e sem classe utilitária estática).
 */
val AgeBand.trainingUpgradeChance: Double
    get() = when (this) {
        AgeBand.YOUNG -> 0.25
        AgeBand.PEAK -> 0.12
        AgeBand.VETERAN -> 0.05
    }

/** Elenco depois da semana + o que aconteceu nela. */
data class TrainingOutcome(
    val squad: List<Player>,
    val events: List<TrainingEvent> = emptyList(),
)

/**
 * Aplica UMA semana de treino ao elenco.
 *
 * Ordem por jogador: primeiro a recuperação de stamina do foco (que vale para
 * todo mundo, inclusive quem está no departamento médico), depois — e só para
 * os aptos, em treino que desenvolve atributo — os sorteios de evolução e de
 * lesão.
 *
 * **Lesionado não treina:** a semana dele é de recuperação, então nem evolui
 * nem se machuca de novo. Quem faz a lesão andar é `applyRoundFitness`; aqui
 * ninguém mexe no contador, senão a mesma rodada descontaria duas vezes.
 *
 * O RNG é consumido em DOIS sorteios por jogador apto, sempre na mesma ordem
 * (evolução, depois lesão) mesmo quando o primeiro já decidiu o desfecho: com
 * consumo fixo, a sequência depende só do elenco e do foco — dois elencos
 * iguais com a mesma seed produzem exatamente a mesma semana. Um foco sem
 * atributo a treinar ([TrainingFocus.DESCANSO]) não sorteia nada, porque não
 * há o que sortear — e o `init` de [TrainingFocus] recusa um foco desses com
 * risco de lesão declarado, para o risco não virar letra morta aqui.
 */
fun train(squad: List<Player>, focus: TrainingFocus, rng: Random): TrainingOutcome {
    // Uma semana POR JOGADOR e depois a agregação — em vez de acumular eventos
    // mutando uma lista de dentro do `map`. Mesmo desenho de `AgingRules`, que
    // devolve um `AgingOutcome` por jogador e junta em `ageSquadForSeason`: a
    // função por jogador fica pura e os eventos saem do resultado, não de um
    // efeito colateral do mapeamento.
    val weeks = squad.map { it.trainOneWeek(focus, rng) }
    return TrainingOutcome(
        squad = weeks.map { it.player },
        events = weeks.flatMap { it.events },
    )
}

/** Jogador depois da semana + o que ela rendeu a ele. */
private data class PlayerWeek(val player: Player, val events: List<TrainingEvent> = emptyList())

/**
 * A semana de UM jogador. Os eventos são montados no fim, a partir do estado
 * FINAL: quem evolui e se machuca na mesma semana gera dois eventos que
 * concordam entre si — o contrato promete que cada variante carrega o jogador
 * já com o efeito aplicado, e um `Improved` com a foto pré-lesão faria um
 * cliente que atualiza o elenco por evento ressuscitar o lesionado.
 */
private fun Player.trainOneWeek(focus: TrainingFocus, rng: Random): PlayerWeek {
    val rested = copy(stamina = (stamina + focus.staminaRecovery).coerceAtMost(MAX_STAMINA))

    val attribute = focus.trains
    if (attribute == null || injured) return PlayerWeek(rested)

    val improves = rng.nextDouble() < AgeBand.of(age).trainingUpgradeChance
    val hurts = rng.nextDouble() < focus.injuryRisk

    // Quem já está no teto do invariante não "evolui": sem a comparação, um
    // craque de 99 geraria um evento de evolução que não mudou nada.
    val better = if (improves) rested.improve(attribute) else rested
    val evolved = better != rested
    val result =
        if (hurts) better.copy(availability = Availability.Injured(TRAINING_INJURY_ROUNDS))
        else better

    return PlayerWeek(
        player = result,
        events = buildList {
            if (evolved) add(TrainingEvent.Improved(result, attribute))
            if (hurts) add(TrainingEvent.Injured(result, TRAINING_INJURY_ROUNDS))
        },
    )
}

/**
 * Seed determinística do treino de um clube numa rodada.
 *
 * Mesmo empacotamento em faixas disjuntas do `Long` de [fitnessSeed] (somar
 * rodada e clube colidiria assim que um id de clube crescesse), mais um salt
 * próprio: sem ele, treino e desgaste da MESMA rodada/clube sorteariam a
 * mesma sequência — quem cansasse mais evoluiria mais, por acidente.
 */
fun trainingSeed(roundNumber: Int, clubId: ClubId): Long =
    ((roundNumber.toLong() shl 32) xor clubId.value) xor TRAINING_SEED_SALT

/** "TRAIN" em hexa — só para descolar o stream do treino dos demais. */
private const val TRAINING_SEED_SALT: Long = 0x5452_4149_4EL

/** Teto de forma física — mesmo do invariante `stamina in 0..100` do `Player`. */
private const val MAX_STAMINA: Int = 100

/**
 * Aplica o ganho ao atributo do foco E ao `overall`.
 *
 * O `overall` entra porque é ELE que a engine lê (`Lineup.teamStrength()` é a
 * média dos overalls efetivos): um ganho que só mexesse em `shooting` seria
 * decorativo, e a issue pede que o efeito do treino chegue à partida sem
 * tocar na engine. Mesma decisão de `AgingRules`, onde `overall` e atributos
 * andam juntos — quem trunca no invariante `0..99` é a regra, o `require` do
 * `Player` é a última linha de defesa.
 */
private fun Player.improve(attribute: TrainedAttribute): Player {
    val bumped = when (attribute) {
        TrainedAttribute.SHOOTING -> copy(shooting = grow(shooting))
        TrainedAttribute.DEFENDING -> copy(defending = grow(defending))
        TrainedAttribute.PACE -> copy(pace = grow(pace))
    }
    return bumped.copy(overall = grow(overall))
}

private fun grow(value: Int): Int =
    (value + TRAINING_ATTRIBUTE_GAIN).coerceAtMost(Player.OVERALL_RANGE.last)
