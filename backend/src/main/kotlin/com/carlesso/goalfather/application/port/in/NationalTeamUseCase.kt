package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.model.CalledUpPlayer
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Country
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.FifaWindow
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.NationalMatch
import com.carlesso.goalfather.domain.rules.NationalTableRow

/**
 * Leituras das seleções (issue #134): quem do clube vai (ou foi) convocado, os
 * jogos das datas FIFA, as Eliminatórias e o ranking. Só leitura — quem
 * convoca e joga é o [NationalTeamClock], chamado pelo relógio do jogo.
 */
interface NationalTeamUseCase {
    /** Convocados do clube na janela corrente ou na próxima. `null` = clube inexistente. */
    suspend fun clubCallUps(clubId: ClubId): ClubCallUps?

    /** Janelas do ano, últimos resultados, Eliminatórias e ranking. */
    suspend fun overview(): NationalTeamsOverview
}

/**
 * O relógio das datas FIFA (issue #134). O `PlayRoundService` o chama UMA vez
 * por encaixe encerrado (depois do claim, issue #46), com o encaixe que acabou
 * e o próximo: as janelas em que o relógio entra convocam, as que ele deixa
 * para trás são jogadas e devolvem os convocados.
 */
fun interface NationalTeamClock {
    suspend fun advance(from: Matchday, to: Matchday)

    companion object {
        /**
         * Sem seleções: o default do `PlayRoundService` montado à mão nos
         * testes que não tratam de datas FIFA. `fun interface` deixa a lambda
         * virar a implementação, sem `object : NationalTeamClock { … }`.
         */
        val NONE: NationalTeamClock = NationalTeamClock { _, _ -> }
    }
}

/** Situação de uma data FIFA no save. */
enum class WindowStatus {
    /** Ainda não chegou: só existe a prévia da lista. */
    UPCOMING,

    /** Convocados com as seleções; jogos ainda por disputar. */
    CALLED,

    /** Jogada: placares conhecidos, convocados de volta. */
    PLAYED,
}

/** Uma data FIFA com a situação dela e quantos jogos as seleções fazem. */
data class WindowSummary(
    val window: FifaWindow,
    val status: WindowStatus,
    val matchSlots: Int,
)

/** Um convocado do clube e a seleção que o chamou. */
data class ClubCallUp(val player: CalledUpPlayer, val country: Country)

/**
 * Os convocados do clube para a [window] (a corrente, ou a próxima). Com
 * [WindowStatus.UPCOMING] a lista é a PRÉVIA — o que a regra convocaria com os
 * elencos de agora —, que o técnico vê antes da janela. `window == null` só
 * num save sem encaixe.
 */
data class ClubCallUps(
    val window: WindowSummary?,
    val players: List<ClubCallUp>,
)

/** Classificação das Eliminatórias do ciclo corrente. */
data class QualifiersView(
    val cycle: Int,
    val roundsPlayed: Int,
    val totalRounds: Int,
    val table: List<NationalTableRow>,
)

/**
 * Painel das seleções. [countries] resolve os códigos de [lastResults],
 * [qualifiers] e [ranking] para nome e bandeira no adapter web.
 */
data class NationalTeamsOverview(
    val windows: List<WindowSummary>,
    val nextWindow: WindowSummary?,
    val lastWindow: WindowSummary?,
    val lastResults: List<NationalMatch>,
    val qualifiers: QualifiersView,
    val ranking: List<NationalTableRow>,
    val countries: Map<CountryCode, Country>,
)
