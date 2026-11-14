package com.carlesso.goalfather.domain.model

/**
 * Resto do catálogo interino do mundo antigo (issue #125). Desde a issue #131
 * nome, disciplina e limite de estrangeiros saem do `Competition` do catálogo
 * do save (`GameCompetition`); as ligas por divisão (`liga-d1`…) deixaram de
 * existir.
 *
 * Só sobrou o id da copa porque o motor ATUAL da copa (`CupRules.cupMatches`)
 * ainda o grava nas partidas — e o calendário o troca pelo id da copa do
 * catálogo logo na saída (`nationalCupMatches`).
 *
 * #131 integrador: apagar este arquivo quando o motor da Copa do Brasil da
 * frente C entrar (ele já usa o `competitionId` da própria copa).
 */
object SeasonCompetitions {
    val CUP_ID: CompetitionId = CompetitionId("copa")
    const val CUP_NAME: String = "Copa"
}
