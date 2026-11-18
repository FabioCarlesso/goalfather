package com.carlesso.goalfather.domain.model

import com.carlesso.goalfather.domain.serialization.CompetitionIdSerializer
import kotlinx.serialization.Serializable

/**
 * Campanha de UM clube numa temporada encerrada (issue #60): onde terminou,
 * em qual divisão e com que aproveitamento.
 *
 * Composição em vez de repetição: a linha da tabela viaja inteira em [row] —
 * copiar os dez campos de `StandingRow` aqui criaria dois lugares para
 * consertar quando o critério de pontuação mudar. O que a história ACRESCENTA
 * é a divisão (a linha sozinha não diz em que tier aquele 1º lugar valeu) e o
 * aproveitamento já calculado.
 *
 * `pointsPercentage` é gravado, não derivado na leitura, justamente porque
 * isto é história: se a liga passar a dar 2 pontos por vitória, as temporadas
 * antigas continuam contando a verdade da época delas.
 */
@Serializable
data class SeasonStanding(
    val division: Division,
    val row: StandingRow,
    val pointsPercentage: Int,
)

/**
 * Artilheiro da liga na temporada — o jogador com mais gols entre TODOS os
 * clubes, humanos ou da IA (`PlayRoundService.persistRoundEffects` acumula
 * `goals` para todo elenco, não só para o do técnico).
 *
 * Nome e clube são copiados, não referenciados por id: o artilheiro pode se
 * aposentar na própria virada que grava este record (issue #55) e sumir da
 * tabela `players`. História que depende de FK vira história apagada.
 */
@Serializable
data class SeasonTopScorer(
    val playerId: PlayerId,
    val playerName: String,
    val clubId: ClubId,
    val clubName: String,
    val goals: Int,
)

/**
 * Snapshot IMUTÁVEL da temporada encerrada de UM país (issues #60 e #131). Diferente do
 * resto do domínio, este agregado nunca sofre `copy()` depois de criado — é
 * append-only tanto no tipo quanto na tabela (PK por `season`).
 *
 * Sem ele a virada de temporada apagava a memória do jogo: tabela zerada,
 * `goals` de cada jogador resetado, e nenhum vestígio de quem foi campeão.
 *
 * `finalStandings` é a classificação final de TODAS as divisões, ordenada da
 * elite para baixo e, dentro de cada uma, por posição — a lista já sai na
 * ordem em que a UI a mostra. `champion` é a primeira linha da elite, repetida
 * aqui para a listagem de temporadas não precisar reabrir a classificação.
 *
 * `topScorer` é nulo quando ninguém marcou na temporada inteira (liga de
 * teste, temporada abortada) — ausência real, modelada no tipo.
 *
 * `cupChampion` (issue #61) é o vencedor da copa mata-mata da temporada. Nulo
 * quando não houve copa (liga pequena demais, ou temporada anterior à copa) ou
 * quando ela não chegou à final — de novo ausência real, não um "sem campeão"
 * inventado. Default nulo: records gravados antes da copa desserializam sem
 * migração do `record_json`.
 */
@Serializable
data class SeasonRecord(
    val season: Int,
    val champion: SeasonStanding,
    val finalStandings: List<SeasonStanding>,
    val topScorer: SeasonTopScorer? = null,
    val cupChampion: CupClub? = null,
    /**
     * País da temporada (issue #131): cada país vira no seu tempo, então a
     * história é por (temporada, país) — o Brasil de 2026 e a Europa de
     * 2026/27 são dois records de `season = 2026`. Nulo só nos records
     * gravados antes do mundo do MVP (o mundo antigo era só o Brasil).
     */
    val country: Country? = null,
    /** `2026` ou `2026/27` (issue #131); nulo nos records antigos. */
    val seasonLabel: String? = null,
    /**
     * Campeões das continentais que o calendário deste país hospeda (issue
     * #132): a Libertadores fecha junto com a temporada do Brasil, então o
     * título dela entra no record do Brasil. Lista vazia (e não nula) quando
     * não houve: o default deixa os records antigos desserializarem sem migração.
     */
    val continentalChampions: List<ContinentalTitle> = emptyList(),
)

/** Um título continental na história (issue #132). Nomes copiados, como o resto do record. */
@Serializable
data class ContinentalTitle(
    @Serializable(with = CompetitionIdSerializer::class)
    val competitionId: CompetitionId,
    val competitionName: String,
    val champion: CupClub,
)
