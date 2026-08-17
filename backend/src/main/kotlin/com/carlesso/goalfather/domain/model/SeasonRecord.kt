package com.carlesso.goalfather.domain.model

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
 * Snapshot IMUTÁVEL de uma temporada encerrada (issue #60). Diferente do
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
 */
@Serializable
data class SeasonRecord(
    val season: Int,
    val champion: SeasonStanding,
    val finalStandings: List<SeasonStanding>,
    val topScorer: SeasonTopScorer? = null,
)
