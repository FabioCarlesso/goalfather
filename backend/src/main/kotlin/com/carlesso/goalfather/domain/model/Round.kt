package com.carlesso.goalfather.domain.model

import com.carlesso.goalfather.domain.serialization.CompetitionIdSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
enum class RoundStatus { Scheduled, InProgress, Finished }

/**
 * Uma partida do encaixe. Desde o calendário semanal (issue #125) um encaixe
 * junta jogos de VÁRIAS competições — liga e copa podem cair na mesma quarta —,
 * então o que se disputa ([stage]) e de qual competição ([competition]) é dado
 * de CADA partida, não da rodada.
 *
 * O nome da competição é uma FOTO, como os nomes dos clubes: a partida
 * continua legível na história mesmo que a competição mude de nome.
 *
 * Os defaults de [stage]/[competition] descrevem a liga da divisão — o que
 * toda partida era antes da copa (issue #61). Produção sempre os preenche
 * pelos geradores; o default só poupa os testes de liga de repeti-los.
 */
@Serializable
data class RoundMatch(
    val matchId: Long,
    val homeClubId: ClubId,
    val awayClubId: ClubId,
    val homeClubName: String,
    val awayClubName: String,
    val status: RoundStatus = RoundStatus.Scheduled,
    val homeGoals: Int = 0,
    val awayGoals: Int = 0,
    val minute: Int = 0,
    // Divisão da partida (issue #47). Default = elite.
    val division: Division = Division.FIRST,
    // Disputa de pênaltis (issue #61) — só existe em jogo de copa que
    // terminou empatado. Nulo no resto.
    val penalties: PenaltyScore? = null,
    val stage: RoundStage = RoundStage.League(1),
    // Um default pode ler os parâmetros ANTERIORES do mesmo construtor: a
    // competição padrão é a liga da [division] declarada acima.
    @Serializable(with = CompetitionIdSerializer::class)
    val competition: CompetitionId = SeasonCompetitions.leagueId(division),
    val competitionName: String = SeasonCompetitions.leagueName(division),
) {
    /** Jogo de mata-mata? Empate nele não existe: decide nos pênaltis. */
    val isKnockout: Boolean
        get() = when (stage) {
            is RoundStage.League -> false
            is RoundStage.Cup -> true
        }

    /** [clubId] está em campo neste jogo? */
    fun involves(clubId: ClubId): Boolean = homeClubId == clubId || awayClubId == clubId
}

/**
 * O que uma partida disputa (issue #61): rodada de LIGA ou fase de COPA. As
 * duas passam pelo MESMO fluxo de prontidão, claim e stream do encaixe
 * (issues #20/#46/#125) — o que muda é só o que se faz com o resultado.
 *
 * `sealed` porque cada variante carrega um dado diferente e os pontos de
 * consumo PRECISAM tratar as duas: aplicar à tabela, cobrar folha, decidir
 * empate nos pênaltis. Um `when` exaustivo sem `else` faz o compilador
 * apontar cada um desses lugares — com um `isCup: Boolean` qualquer `if`
 * esquecido passaria em silêncio.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface RoundStage {

    /**
     * Rodada de pontos corridos. `matchday` é a rodada DA LIGA (1..N-1 do
     * Berger) — o encaixe do calendário em que ela cai é outra coisa
     * ([Round.matchday]).
     */
    @Serializable
    @SerialName("League")
    data class League(val matchday: Int) : RoundStage {
        init {
            require(matchday >= 1) { "Rodada de liga deve ser >= 1: $matchday" }
        }
    }

    /** Fase da copa disputada nesta data — jogo único, empate vai aos pênaltis. */
    @Serializable
    @SerialName("Cup")
    data class Cup(val phase: KnockoutPhase) : RoundStage
}

/**
 * Os jogos de UM encaixe do calendário (issue #125): todas as partidas de
 * todas as competições marcadas para [matchday]. É o que o técnico joga de uma
 * vez — prontidão, claim `finishRound` e WebSocket são por encaixe.
 *
 * O nome `Round` ficou da época da rodada global: era o que o resto do código
 * (repositório, stream, eventos) já chamava assim, e "a rodada do encaixe" é
 * como o jogo a apresenta. A identidade, porém, é o [matchday] — semana +
 * encaixe —, que nunca se repete entre temporadas (a rodada "1" se repetia).
 *
 * [season] é o ano da temporada do mundo que o encaixe disputa (o ano civil
 * no Brasil).
 */
@Serializable
data class Round(
    val matchday: Matchday,
    val season: Int,
    val status: RoundStatus = RoundStatus.Scheduled,
    val matches: List<RoundMatch>,
) {
    val isFinished: Boolean
        get() = matches.all { it.status == RoundStatus.Finished }

    /**
     * Liberado pelo fluxo de início (issue #151): só `RoundReadinessService.start`
     * tira o encaixe de `Scheduled`, e só com todos os técnicos prontos ou o
     * timeout vencido. Antes disso, nenhum stream — rodada ou drill-down — pode
     * simular as partidas: o resultado é determinístico (seed = `matchId`), e
     * vê-lo antes seria trocar a escalação até ganhar.
     */
    val isReleased: Boolean
        get() = status != RoundStatus.Scheduled

    /** Clubes em campo neste encaixe — quem "joga" para lesões, folha e diretoria. */
    val clubsInAction: Set<ClubId>
        get() = matches.flatMapTo(HashSet()) { listOf(it.homeClubId, it.awayClubId) }
}
