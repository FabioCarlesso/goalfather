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
 * O default de [stage] descreve uma rodada de liga — o que toda partida era
 * antes da copa (issue #61). Produção sempre o preenche pelos geradores.
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
    // terminou empatado no jogo único ou no AGREGADO da volta. Nulo no resto.
    val penalties: PenaltyScore? = null,
    val stage: RoundStage = RoundStage.League(1),
    // Sem default desde a issue #131: a competição sai do catálogo do save
    // (`bra-serie-a`, `eng-premier-league`…), e não existe mais uma "liga da
    // divisão" que sirva de palpite — a Série A e a Premier League são ambas
    // divisão 1.
    @Serializable(with = CompetitionIdSerializer::class)
    val competition: CompetitionId,
    val competitionName: String,
) {
    /**
     * Jogo de copa? Desde a ida e volta (issue #131) isso NÃO quer dizer "empate
     * vai aos pênaltis" — a ida pode empatar. Quem decide os pênaltis é
     * `decidesOnPenalties` (`CupRules.kt`), pela perna do [stage].
     */
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

    /**
     * Jogo de copa (issue #131): a fase pelo NOME (`"Quinta fase"`, `"Final"`
     * — as fases vêm do formato da competição, não de um enum) e a perna do
     * confronto, que carrega o que é preciso para decidir os pênaltis.
     */
    @Serializable
    @SerialName("Cup")
    data class Cup(val stageName: String, val leg: CupLeg) : RoundStage
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
 * [season] é o ANO CIVIL do encaixe (issue #131). Com o mundo do MVP várias
 * temporadas convivem no mesmo encaixe — a Europa de 2026/27 joga no mesmo
 * domingo que o Brasil de 2027 —, então a rodada não pertence a uma temporada:
 * a temporada de cada jogo é a da competição dele (ver `SeasonPlan`). O ano
 * civil é o rótulo honesto do relógio do jogo.
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
