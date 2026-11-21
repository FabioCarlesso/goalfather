package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.ContractOffer
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.world.LeagueStrength
import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.math.roundToInt

/*
 * Termos pessoais (issue #138): o jogador aceita ou recusa uma oferta de
 * salário e duração. Regra PURA e DETERMINÍSTICA — sem `Random`: a mesma
 * oferta no mesmo estado dá sempre a mesma resposta, e a recusa diz o porquê
 * e quanto ele pede, para o técnico entender o que mudar.
 *
 * A exigência salarial sai de quatro coisas:
 * 1. o VALOR do jogador com contrato pleno na liga do clube que oferece
 *    (`fullContractValue`, #135), convertido em salário pela mesma régua do
 *    #136 (`salaryForValue`) — que já pesa overall, idade e liga;
 * 2. a IDADE outra vez, para o salário: o garoto aceita menos para jogar, e o
 *    veterano, cujo valor de transferência despenca, não aceita que o salário
 *    despenque junto;
 * 3. a REPUTAÇÃO do clube que oferece: clube grande paga em prestígio;
 * 4. o DEGRAU para baixo: sair de um clube muito mais forte, ou de uma liga
 *    muito mais forte, só por um salário bem acima.
 */

// ─── Reputação ──────────────────────────────────────────────────────────────

/** Titulares que medem a reputação do elenco: um time. */
private const val REPUTATION_SQUAD_SIZE: Int = 11

/**
 * Reputação de um clube para a negociação: a média de overall dos 11 melhores
 * do elenco (0 sem elenco). O save não guarda a `Reputation` do pack (só o
 * seed a usa para gerar atributos e caixa), e o elenco é o que o jogador
 * enxerga de fato — um clube que se reforça fica mais atraente.
 */
fun squadReputation(squad: List<Player>): Int {
    val best = squad.map { it.overall }.sortedDescending().take(REPUTATION_SQUAD_SIZE)
    return if (best.isEmpty()) 0 else best.average().roundToInt()
}

/**
 * O que o jogador enxerga de um clube ao negociar: quem é, o tamanho dele e a
 * força da liga em que joga. Um `data class` pequeno no lugar do `Club`
 * inteiro: a regra fica testável sem montar elenco e catálogo.
 */
data class ClubStature(val clubId: ClubId, val reputation: Int, val leagueStrength: LeagueStrength)

/** A estatura de [this] numa liga de força [leagueStrength]. */
fun Club.stature(leagueStrength: LeagueStrength): ClubStature =
    ClubStature(clubId = id, reputation = squadReputation(squad), leagueStrength = leagueStrength)

// ─── A resposta ─────────────────────────────────────────────────────────────

/** Por que o jogador recusou. Enum: nenhum motivo carrega dado próprio. */
@Serializable
enum class RefusalReason {
    /** Salário abaixo do que ele pede. */
    LOW_SALARY,

    /** Duração menor do que ele aceita. */
    TOO_SHORT,

    /** Duração maior do que ele aceita. */
    TOO_LONG,

    /** O clube que oferece é muito mais fraco que o dele, e o salário não compensa. */
    WEAKER_CLUB,

    /** A liga do clube que oferece é muito mais fraca que a dele, e o salário não compensa. */
    LOWER_LEAGUE,
}

/**
 * A resposta do jogador. `sealed`: só a recusa carrega dado — o motivo, o
 * salário que ele aceitaria NAQUELA proposta e as durações que ele topa.
 * `data object` para o aceite: não há o que carregar, e um singleton basta.
 */
sealed interface PlayerResponse {
    data object Accepts : PlayerResponse

    /**
     * `@Serializable` porque viaja dentro do `TransferResult` (que é
     * serializável). O kotlinx não serializa `IntRange`, então a faixa é
     * guardada em duas pontas, e [acceptedYears] a remonta. O construtor
     * SECUNDÁRIO (`constructor(...) : this(...)`) deixa a regra continuar
     * falando em faixa.
     */
    @Serializable
    data class Refuses(
        val reason: RefusalReason,
        val demandedSalary: Money,
        val minYears: Int,
        val maxYears: Int,
    ) : PlayerResponse {
        constructor(reason: RefusalReason, demandedSalary: Money, acceptedYears: IntRange) :
            this(reason, demandedSalary, acceptedYears.first, acceptedYears.last)

        val acceptedYears: IntRange get() = minYears..maxYears
    }
}

/**
 * Resposta de [player] a [offer] do clube [buyer]. [current] é o clube em que
 * ele está (`null` para o livre); quando é o próprio [buyer], é uma renovação.
 * O contrato atual vem no próprio [player] (`employment`): é dele que sai o
 * piso salarial da renovação.
 *
 * Ordem dos motivos: primeiro a duração (é o que ele recusa mesmo com salário
 * de sobra), depois o salário. Abaixo da exigência-base o motivo é
 * `LOW_SALARY`; acima dela mas abaixo do prêmio pelo degrau, é o degrau
 * (`WEAKER_CLUB` antes de `LOWER_LEAGUE`). Em toda recusa, [PlayerResponse.Refuses.demandedSalary]
 * é o salário que fecharia o negócio com aquela duração.
 */
fun playerResponse(
    player: Player,
    offer: ContractOffer,
    buyer: ClubStature,
    current: ClubStature?,
): PlayerResponse {
    val years = acceptedContractYears(player.age)
    val base = salaryDemand(player, buyer, current)
    val step = current?.takeIf { it.clubId != buyer.clubId }?.let { stepDown(it, buyer) }
    val demanded = step?.let { roundedSalary(base.cents * it.premium) } ?: base

    fun refuse(reason: RefusalReason) = PlayerResponse.Refuses(reason, demanded, years)
    return when {
        offer.years < years.first -> refuse(RefusalReason.TOO_SHORT)
        offer.years > years.last -> refuse(RefusalReason.TOO_LONG)
        offer.salary < base -> refuse(RefusalReason.LOW_SALARY)
        step != null && offer.salary < demanded -> refuse(step.reason)
        else -> PlayerResponse.Accepts
    }
}

/**
 * O salário que [player] pede ao clube [buyer], antes de qualquer degrau.
 * Numa renovação ([current] é o próprio [buyer]) ele não aceita ganhar menos
 * do que já ganha.
 */
fun salaryDemand(player: Player, buyer: ClubStature, current: ClubStature?): Money {
    val fair = salaryForValue(fullContractValue(player, buyer.leagueStrength))
    val demand = roundedSalary(fair.cents * salaryAgeFactor(player.age) * prestigeFactor(buyer.reputation))
    val renewal = current != null && current.clubId == buyer.clubId
    return if (renewal) maxOf(demand, player.salary) else demand
}

// ─── Duração ────────────────────────────────────────────────────────────────

/**
 * Temporadas que um jogador de [age] anos aceita assinar, sempre dentro do
 * permitido pela FIFA ([allowedContractSeasons]):
 *
 * | Idade | Aceita | Por quê |
 * |---|---|---|
 * | < 18 | 1–3 | o teto da FIFA para menor |
 * | 18–23 | 2–5 | quer estabilidade para se firmar: 1 temporada é pouco |
 * | 24–29 | 1–4 | no auge, não se prende por 5 temporadas |
 * | 30–32 | 2–4 | quer segurança, mas não até os 37 |
 * | 33+ | 1–2 | perto de parar |
 */
fun acceptedContractYears(age: Int): IntRange {
    val wanted = when {
        age < ADULT_AGE -> CONTRACT_SEASONS
        age <= 23 -> 2..5
        age <= 29 -> 1..4
        age <= 32 -> 2..4
        else -> 1..2
    }
    val allowed = allowedContractSeasons(age)
    return maxOf(wanted.first, allowed.first)..minOf(wanted.last, allowed.last)
}

// ─── Fatores do salário ─────────────────────────────────────────────────────

/** Até esta idade o jogador aceita menos para ter minutos. */
private const val YOUNG_DEMAND_MAX_AGE: Int = 21

private const val YOUNG_DEMAND_FACTOR: Double = 0.85

/** A partir desta idade cada ano a mais pede +12% sobre o salário do valor. */
private const val VETERAN_DEMAND_FROM_AGE: Int = 30

private const val VETERAN_DEMAND_PER_YEAR: Double = 0.12

private const val VETERAN_DEMAND_CAP: Double = 1.6

/**
 * Fator de idade SOBRE o salário que já sai do valor. O valor de um veterano
 * cai 18% ao ano (#135) — sem este fator o salário pedido cairia junto, e um
 * titular de 33 anos assinaria por um terço do que ganhava aos 27.
 */
internal fun salaryAgeFactor(age: Int): Double = when {
    age <= YOUNG_DEMAND_MAX_AGE -> YOUNG_DEMAND_FACTOR
    age < VETERAN_DEMAND_FROM_AGE -> 1.0
    else -> (1.0 + VETERAN_DEMAND_PER_YEAR * (age - VETERAN_DEMAND_FROM_AGE + 1)).coerceAtMost(VETERAN_DEMAND_CAP)
}

/** Reputação de referência: fator 1 (um elenco de média 70, como a liga de referência). */
private const val REFERENCE_REPUTATION: Int = 70

/** Cada ponto de reputação acima da referência tira 1,5% do pedido; abaixo, soma. */
private const val PRESTIGE_PER_POINT: Double = 0.015

private val PRESTIGE_RANGE: ClosedFloatingPointRange<Double> = 0.80..1.25

/** Fator de prestígio do clube que oferece: clube grande paga menos pelo mesmo jogador. */
internal fun prestigeFactor(reputation: Int): Double =
    (1.0 + PRESTIGE_PER_POINT * (REFERENCE_REPUTATION - reputation)).coerceIn(PRESTIGE_RANGE)

// ─── Degrau para baixo ──────────────────────────────────────────────────────

/** Diferença de reputação a partir da qual o clube é "muito mais fraco". */
const val WEAKER_CLUB_GAP: Int = 6

/** Prêmio sobre a exigência para descer a um clube muito mais fraco: +50%. */
const val WEAKER_CLUB_PREMIUM: Double = 1.5

/** Diferença de força de liga a partir da qual a liga é "muito mais fraca". */
const val LOWER_LEAGUE_GAP: Int = 8

/** Prêmio para descer a uma liga muito mais fraca: +30%. */
const val LOWER_LEAGUE_PREMIUM: Double = 1.3

/** Um degrau para baixo e o prêmio que ele cobra. */
private data class StepDown(val reason: RefusalReason, val premium: Double)

/**
 * O degrau de [from] para [to], se houver. Os dois prêmios se MULTIPLICAM
 * (clube e liga mais fracos ao mesmo tempo pedem 1,5 × 1,3); o motivo
 * informado é o do clube, que é o que o jogador sente primeiro.
 */
private fun stepDown(from: ClubStature, to: ClubStature): StepDown? {
    val weakerClub = from.reputation - to.reputation >= WEAKER_CLUB_GAP
    val lowerLeague = from.leagueStrength.value - to.leagueStrength.value >= LOWER_LEAGUE_GAP
    return when {
        weakerClub && lowerLeague -> StepDown(RefusalReason.WEAKER_CLUB, WEAKER_CLUB_PREMIUM * LOWER_LEAGUE_PREMIUM)
        weakerClub -> StepDown(RefusalReason.WEAKER_CLUB, WEAKER_CLUB_PREMIUM)
        lowerLeague -> StepDown(RefusalReason.LOWER_LEAGUE, LOWER_LEAGUE_PREMIUM)
        else -> null
    }
}

// ─── Esfriamento da renovação ───────────────────────────────────────────────

/** Semanas (do jogo) que o jogador não volta a conversar depois de uma oferta ofensiva. */
const val RENEWAL_COOLDOWN_WEEKS: Long = 4

/** Abaixo desta fração do que ele pediu, a oferta é ofensiva: 75%. */
const val OFFENSIVE_OFFER_PERCENT: Long = 75

/**
 * Esfriamento depois de uma renovação recusada (issue #138): a oferta é
 * OFENSIVA quando paga menos de [OFFENSIVE_OFFER_PERCENT]% do que ele pedia E
 * menos do que [currentSalary], o que ele já ganha. Aí o jogador não aceita
 * nova proposta de renovação por [RENEWAL_COOLDOWN_WEEKS] semanas a partir de
 * [today]. Manter o salário de hoje nunca ofende, mesmo longe do pedido: sem
 * isso, o veterano, cujo pedido sobe com a idade, esfriaria com a primeira
 * oferta "renova pelo mesmo" (achado no teste da PR #160). Uma recusa honesta
 * (perto do pedido, só pela duração ou pelo salário atual) não esfria: o
 * técnico ajusta e tenta de novo. Devolve até quando, ou `null`.
 */
fun renewalCooldown(
    offer: ContractOffer,
    refusal: PlayerResponse.Refuses,
    currentSalary: Money,
    today: LocalDate,
): LocalDate? =
    today.plusWeeks(RENEWAL_COOLDOWN_WEEKS).takeIf {
        // Divide o pedido em vez de multiplicar a oferta: o salário oferecido
        // vem do cliente e, multiplicado, poderia estourar o `Long`.
        offer.salary < currentSalary &&
            offer.salary.cents < refusal.demandedSalary.cents * OFFENSIVE_OFFER_PERCENT / 100
    }

/** O jogador ainda está "esfriado" em [today], com esfriamento até [until] (exclusive)? */
fun isCoolingDown(until: LocalDate?, today: LocalDate): Boolean = until != null && today.isBefore(until)
