package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.ClubCallUp
import com.carlesso.goalfather.application.port.`in`.ClubCallUps
import com.carlesso.goalfather.application.port.`in`.NationalTeamClock
import com.carlesso.goalfather.application.port.`in`.NationalTeamUseCase
import com.carlesso.goalfather.application.port.`in`.NationalTeamsOverview
import com.carlesso.goalfather.application.port.`in`.QualifiersView
import com.carlesso.goalfather.application.port.`in`.WindowStatus
import com.carlesso.goalfather.application.port.`in`.WindowSummary
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.NationalWindowRepository
import com.carlesso.goalfather.domain.engine.QuickMatchSimulator
import com.carlesso.goalfather.domain.model.Availability
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.Country
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.FifaWindow
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.NationalMatchKind
import com.carlesso.goalfather.domain.model.NationalSquad
import com.carlesso.goalfather.domain.model.NationalWindow
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.rules.CONMEBOL_QUALIFIERS
import com.carlesso.goalfather.domain.rules.QUALIFIER_ROUNDS
import com.carlesso.goalfather.domain.rules.calledUpFor
import com.carlesso.goalfather.domain.rules.dutyReturnSeed
import com.carlesso.goalfather.domain.rules.firstWeek
import com.carlesso.goalfather.domain.rules.interimFifaWindows
import com.carlesso.goalfather.domain.rules.lastWeek
import com.carlesso.goalfather.domain.rules.matchSlots
import com.carlesso.goalfather.domain.rules.nationalRanking
import com.carlesso.goalfather.domain.rules.nationalTable
import com.carlesso.goalfather.domain.rules.playWindow
import com.carlesso.goalfather.domain.rules.qualifierCycleOf
import com.carlesso.goalfather.domain.rules.returnFromDuty
import com.carlesso.goalfather.domain.rules.windowFixtures
import com.carlesso.goalfather.domain.rules.worldCallUps
import java.time.LocalDate
import kotlin.random.Random

/**
 * Seleções nas datas FIFA (issue #134): o relógio das janelas
 * ([NationalTeamClock]) e as leituras da UI ([NationalTeamUseCase]).
 *
 * Toda regra é de `domain/rules/NationalTeamRules.kt`; aqui só se lê, se
 * chama a regra e se grava. O `PlayRoundService` chama [advance] num ponto só,
 * depois de agendar o próximo encaixe — e só quem venceu o claim do encaixe
 * chega lá, então cada janela é convocada e jogada exatamente uma vez. O
 * estado gravado da janela (`Called`/`Played`) é a segunda trava: uma janela
 * já convocada não convoca de novo.
 *
 * [windowsOf] dá as datas FIFA de um ano: as interinas até o pack trazer o
 * calendário completo (ver `FifaCalendar.kt`).
 */
class NationalTeamService(
    private val clubRepo: ClubRepository,
    private val windowRepo: NationalWindowRepository,
    private val leagueRepo: LeagueRepository,
    private val windowsOf: (Int) -> List<FifaWindow> = ::interimFifaWindows,
    private val simulator: QuickMatchSimulator = QuickMatchSimulator(),
) : NationalTeamClock, NationalTeamUseCase {

    /**
     * O relógio foi do encaixe [from] para [to]. Em ordem cronológica:
     *
     * - janela que o relógio TOCA agora (começa até a semana de [to] e não
     *   acabou antes de [from]) e ainda não foi convocada → convoca;
     * - janela convocada cuja última semana ficou para trás de [to] → joga e
     *   devolve os convocados.
     *
     * Um salto que atravesse uma janela inteira (nenhum encaixe dentro dela)
     * faz as duas coisas na mesma chamada: os jogos de seleção acontecem mesmo
     * que nenhum clube do mundo jogue naquelas semanas.
     */
    override suspend fun advance(from: Matchday, to: Matchday) {
        val windows = ((from.date.year - 1)..to.date.year).flatMap(windowsOf).sortedBy { it.start }
        for (window in windows) {
            var stored = windowRepo.find(window.start)
            if (stored == null && window.firstWeek <= to.week && window.lastWeek >= from.week) {
                stored = callUp(window)
            }
            if (stored is NationalWindow.Called && window.lastWeek < to.week) {
                play(stored)
            }
        }
    }

    /** Convoca o mundo para a [window] e tira os convocados dos clubes. */
    private suspend fun callUp(window: FifaWindow): NationalWindow.Called {
        val clubs = clubRepo.findAll()
        val called = NationalWindow.called(window, worldCallUps(window, clubs))
        // A janela antes dos clubes: se a gravação dos clubes falhar no meio, a
        // janela gravada ainda devolve na saída quem chegou a ser convocado.
        windowRepo.save(called)
        val countryOf = called.squads.flatMap { squad -> squad.players.map { it.playerId to squad.country } }.toMap()
        for (club in clubs) {
            if (club.squad.none { it.id in countryOf }) continue
            saveReapplying(club) { current ->
                current.copy(
                    squad = current.squad.map { player ->
                        val country = countryOf[player.id]
                        // Só quem continua apto vai: um jogador que se lesionou
                        // entre a leitura e a gravação fica no clube.
                        if (country != null && player.availability == Availability.Available) {
                            player.calledUpFor(country, window)
                        } else {
                            player
                        }
                    },
                )
            }
        }
        return called
    }

    /** Joga a janela convocada e devolve cada convocado ao clube. */
    private suspend fun play(called: NationalWindow.Called) {
        val window = called.window
        val clubs = clubRepo.findAll()
        val byId = clubs.flatMap { it.squad }.associateBy { it.id }
        // O estado ATUAL de cada convocado (stamina de hoje); quem saiu do mundo
        // (aposentado) some da lista e vira reforço estimado.
        val squads = called.squads.associate { squad -> squad.country to squad.players.mapNotNull { byId[it.playerId] } }
        val fixtures = windowFixtures(window, called.squads.map { it.country }, windowsOf)
        val outcome = playWindow(window, squads, fixtures, simulator)
        windowRepo.save(called.played(outcome.matches))

        for (club in clubs) {
            if (club.squad.none { it.isOnDutyUntil(window) }) continue
            saveReapplying(club) { current ->
                current.copy(
                    squad = current.squad.map { player ->
                        if (player.isOnDutyUntil(window)) {
                            player.returnFromDuty(outcome.duty[player.id], Random(dutyReturnSeed(window, player.id)))
                        } else {
                            player
                        }
                    },
                )
            }
        }
    }

    /** Convocado para esta janela ou uma anterior (que, por algum motivo, não o devolveu). */
    private fun Player.isOnDutyUntil(window: FifaWindow): Boolean {
        val duty = availability as? Availability.OnInternationalDuty ?: return false
        return !duty.until.isAfter(window.end)
    }

    override suspend fun clubCallUps(clubId: ClubId): ClubCallUps? {
        val club = clubRepo.findById(clubId) ?: return null
        val now = leagueRepo.findLatest()?.matchday ?: return ClubCallUps(window = null, players = emptyList())
        val stored = windowRepo.findAll().associateBy { it.start }
        val window = upcoming(now, stored) ?: return ClubCallUps(window = null, players = emptyList())

        // A prévia precisa do mundo inteiro (a lista de uma seleção depende de
        // quem mais tem a nacionalidade), e os nomes dos países também saem dele.
        val clubs = clubRepo.findAll()
        // Já convocada: a lista gravada. Ainda não: a PRÉVIA, com os elencos de
        // agora — o que o técnico vê antes da janela.
        val squads: List<NationalSquad> = when (val saved = stored[window.window.start]) {
            is NationalWindow.Called -> saved.squads
            is NationalWindow.Played -> saved.squads
            null -> worldCallUps(window.window, clubs)
        }
        val countries = countriesOf(clubs)
        val players = squads.flatMap { squad ->
            squad.players.filter { it.clubId == club.id }.map { ClubCallUp(it, countryOf(squad.country, countries)) }
        }
        return ClubCallUps(window = window, players = players)
    }

    override suspend fun overview(): NationalTeamsOverview {
        val now = leagueRepo.findLatest()?.matchday
        val stored = windowRepo.findAll()
        val byStart = stored.associateBy { it.start }
        val year = now?.date?.year ?: FIRST_YEAR_WITHOUT_CLOCK
        val windows = (windowsOf(year) + windowsOf(year + 1)).sortedBy { it.start }.map { summaryOf(it, byStart) }

        val played = stored.filterIsInstance<NationalWindow.Played>()
        val lastPlayed = played.maxByOrNull { it.start }
        val cycle = qualifierCycleOf(year)
        val qualifierMatches = played
            .filter { qualifierCycleOf(it.start.year) == cycle }
            .flatMap { it.matches }
            .filter { it.kind == NationalMatchKind.QUALIFIER }

        val clubs = clubRepo.findAll()
        return NationalTeamsOverview(
            windows = windows,
            nextWindow = now?.let { upcoming(it, byStart) },
            lastWindow = lastPlayed?.let { summaryOf(it.window, byStart) },
            lastResults = lastPlayed?.matches.orEmpty(),
            qualifiers = QualifiersView(
                cycle = cycle,
                roundsPlayed = qualifierMatches.maxOfOrNull { it.qualifierRound ?: 0 } ?: 0,
                totalRounds = QUALIFIER_ROUNDS,
                table = nationalTable(CONMEBOL_QUALIFIERS, qualifierMatches),
            ),
            ranking = nationalRanking(played.flatMap { it.matches }),
            countries = countriesOf(clubs),
        )
    }

    /** A janela em que o relógio está ou a próxima — a primeira ainda não jogada que não ficou para trás. */
    private fun upcoming(now: Matchday, stored: Map<LocalDate, NationalWindow>): WindowSummary? =
        (now.date.year..now.date.year + 1).flatMap(windowsOf).sortedBy { it.start }
            .firstOrNull { it.lastWeek >= now.week && stored[it.start] !is NationalWindow.Played }
            ?.let { summaryOf(it, stored) }

    private fun summaryOf(window: FifaWindow, stored: Map<LocalDate, NationalWindow>): WindowSummary {
        val status = when (stored[window.start]) {
            is NationalWindow.Called -> WindowStatus.CALLED
            is NationalWindow.Played -> WindowStatus.PLAYED
            null -> WindowStatus.UPCOMING
        }
        return WindowSummary(window, status, window.matchSlots)
    }

    /**
     * Países por código, tirados das nacionalidades dos jogadores (o mapper já
     * as resolveu nos países do save). As seleções das Eliminatórias sem
     * jogador no mundo (a Bolívia do pack) caem no catálogo fixo `Countries`.
     */
    private fun countriesOf(clubs: List<Club>): Map<CountryCode, Country> {
        val fromPlayers = clubs.asSequence().flatMap { it.squad }.map { it.nationality }.associateBy { it.code }
        val fallback = CONMEBOL_QUALIFIERS.mapNotNull { code -> Countries.find(code) }.associateBy { it.code }
        return fallback + fromPlayers
    }

    private fun countryOf(code: CountryCode, countries: Map<CountryCode, Country>): Country =
        countries[code] ?: Countries.find(code) ?: Country(code, code.value, Countries.DEFAULT.confederation, "")

    /**
     * Grava uma mudança de elenco sem perder escrita concorrente (issue #89):
     * no conflito, relê o clube e reaplica [transform] — mesma postura do
     * `saveReapplying` do `PlayRoundService` (o relógio não devolve 409).
     */
    private suspend inline fun saveReapplying(snapshot: Club, transform: (Club) -> Club) {
        var current = snapshot
        val outcome = retryOnConflict(
            maxAttempts = CLOCK_CLUB_WRITE_ATTEMPTS,
            isConflict = { it is ClubSaveResult.Conflict },
        ) { attempt ->
            if (attempt > 0) {
                current = checkNotNull(clubRepo.findById(snapshot.id)) {
                    "Clube ${snapshot.id.value} sumiu durante a data FIFA"
                }
            }
            val updated = transform(current)
            if (updated == current) ClubSaveResult.Saved(current) else clubRepo.save(updated)
        }
        check(outcome is ClubSaveResult.Saved) {
            "Clube ${snapshot.id.value}: $CLOCK_CLUB_WRITE_ATTEMPTS conflitos seguidos ao gravar a data FIFA"
        }
    }

    private companion object {
        const val CLOCK_CLUB_WRITE_ATTEMPTS = 5

        /** Ano mostrado num save sem encaixe (não acontece fora de teste). */
        const val FIRST_YEAR_WITHOUT_CLOCK = 2026
    }
}
