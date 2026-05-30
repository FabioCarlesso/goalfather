package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.PlayRoundUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.engine.MatchSetup
import com.carlesso.goalfather.domain.engine.MatchSimulator
import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.event.RoundEvent
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundFinance
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.teamStrength
import com.carlesso.goalfather.domain.rules.applyRoundToStandings
import com.carlesso.goalfather.domain.rules.generateRound
import com.carlesso.goalfather.domain.rules.isSalaryRound
import com.carlesso.goalfather.domain.rules.ticketRevenue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlin.random.Random

/**
 * Implementação de `PlayRoundUseCase`. Orquestra a engine + repos para
 * simular TODAS as partidas de uma rodada e emitir um stream
 * multiplexado de `RoundEvent`.
 *
 * Determinismo: cada partida usa `matchId` como seed do `Random`.
 * Mesmo `matchId` → mesma sequência. Útil para testes E2E e debug.
 *
 * Espelha exatamente o handler MSW `/ws/round/:n` em
 * `frontend/src/mocks/handlers.ts`. Quando este service rodar dentro
 * do Spring (Fase 3), o handler MSW pode ser desligado e a UI
 * continua igual.
 */
class PlayRoundService(
    private val clubRepo: ClubRepository,
    private val leagueRepo: LeagueRepository,
    private val simulator: MatchSimulator = MatchSimulator(),
) : PlayRoundUseCase {

    override fun stream(roundNumber: Int): Flow<RoundEvent> = flow {
        val round = leagueRepo.findRound(roundNumber)
            ?: throw IllegalArgumentException("Rodada $roundNumber não encontrada")

        // Pré-computa todos os eventos de todas as partidas.
        // (Em uma versão futura podemos intercalar geração + emissão para
        // não materializar tudo na memória, mas com ~3 partidas × ~30
        // eventos cabe folgado.)
        val tagged = mutableListOf<Pair<Long, MatchEvent>>()
        val finalScores = mutableMapOf<Long, Pair<Int, Int>>()
        // Clubes que entraram em campo nesta rodada — guardados para
        // persistir as estatísticas dos jogadores ao final (issue #2).
        val involvedClubs = mutableMapOf<Long, Club>()

        for (match in round.matches) {
            val home = clubRepo.findById(match.homeClubId)
            val away = clubRepo.findById(match.awayClubId)
            home?.let { involvedClubs[it.id.value] = it }
            away?.let { involvedClubs[it.id.value] = it }

            val setup = MatchSetup(
                home = home?.startingLineup() ?: emptyLineup(),
                away = away?.startingLineup() ?: emptyLineup(),
                homeName = match.homeClubName,
                awayName = match.awayClubName,
            )

            for (event in simulator.simulate(setup, Random(match.matchId)).toList()) {
                tagged.add(match.matchId to event)
                if (event is MatchEvent.FullTime) {
                    finalScores[match.matchId] = event.homeGoals to event.awayGoals
                }
            }
        }

        // Estável por minuto: eventos de minuto igual saem juntos
        // (sensação de "vários estádios ao mesmo tempo").
        tagged.sortBy { it.second.minute }
        for ((matchId, event) in tagged) {
            emit(RoundEvent.MatchUpdate(matchId, event))
        }

        // Idempotência: se a rodada JÁ está finalizada, este stream é um replay
        // (reconexão ao WS). Re-emitimos os eventos para visualização, mas NÃO
        // re-aplicamos estatísticas/caixa nem geramos a próxima rodada — caso
        // contrário gols, cartões e bilheteria seriam contados em dobro.
        if (round.status == RoundStatus.Finished) {
            emit(RoundEvent.RoundFinished(leagueRepo.currentStandings()))
            return@flow
        }

        // Calcula o balanço financeiro da rodada — bilheteria (mandante) e
        // folha salarial em rodadas de "mês" (issue #4).
        val finances = computeFinances(round, involvedClubs.values)

        // Acumula estatísticas dos jogadores (issue #2) e aplica o caixa
        // (issue #4) em uma única gravação por clube.
        persistRoundEffects(tagged.map { it.second }, involvedClubs.values, finances)

        // Atualiza estado pós-rodada e dispara RoundFinished.
        val finishedMatches = round.matches.map { m -> m.finish(finalScores[m.matchId]) }
        val finishedRound = round.copy(matches = finishedMatches, status = RoundStatus.Finished)
        val newStandings = applyRoundToStandings(finishedRound, leagueRepo.currentStandings())

        leagueRepo.saveRound(finishedRound)
        leagueRepo.saveStandings(newStandings)

        // Gera a PRÓXIMA rodada (Berger) antes de sinalizar RoundFinished, para
        // que `getCurrentRound` já encontre uma rodada Scheduled e o usuário
        // possa jogar N rodadas em sequência sem intervenção.
        val clubs = clubRepo.findAll()
        if (clubs.size >= 2 && clubs.size % 2 == 0) {
            val nextRound = generateRound(round.number + 1, round.season, clubs)
            leagueRepo.saveRound(nextRound)
        }

        emit(RoundEvent.RoundFinished(newStandings, finances))
    }

    /**
     * Calcula o balanço financeiro de cada clube na rodada: bilheteria (só
     * o mandante, em função da capacidade e da força do time) e folha
     * salarial (a cada N rodadas). Função sem efeitos colaterais — a
     * gravação fica em [persistRoundEffects].
     */
    private fun computeFinances(round: Round, clubs: Collection<Club>): List<RoundFinance> {
        val homeClubIds = round.matches.map { it.homeClubId.value }.toSet()
        val salaryRound = isSalaryRound(round.number)
        return clubs.map { club ->
            val revenue =
                if (club.id.value in homeClubIds)
                    ticketRevenue(club.stadiumCapacity, club.startingLineup().teamStrength())
                else 0L
            val salaries = if (salaryRound) club.squad.sumOf { it.salary.toLong() } else 0L
            RoundFinance(club.id, ticketRevenue = revenue, salariesPaid = salaries)
        }
    }

    /**
     * Aplica, numa ÚNICA gravação por clube, o incremento de estatísticas
     * dos jogadores (gols/cartões/lesão — issue #2) e a variação de caixa
     * da rodada (bilheteria − salários — issue #4).
     *
     * A atribuição de estatísticas é por `playerId`: cada clube só atualiza
     * jogadores do próprio elenco (ids únicos), sem ambiguidade entre
     * mandante e visitante. O caixa nunca fica negativo (`coerceAtLeast(0)`).
     * Clubes sem nenhuma mudança não são salvos.
     */
    private suspend fun persistRoundEffects(
        events: List<MatchEvent>,
        clubs: Collection<Club>,
        finances: List<RoundFinance>,
    ) {
        val goals = mutableMapOf<Long, Int>()
        val yellow = mutableMapOf<Long, Int>()
        val red = mutableMapOf<Long, Int>()
        val injured = mutableSetOf<Long>()

        for (event in events) {
            when (event) {
                is MatchEvent.Goal -> goals.merge(event.scorerId.value, 1, Int::plus)
                is MatchEvent.Card ->
                    if (event.red) red.merge(event.playerId.value, 1, Int::plus)
                    else yellow.merge(event.playerId.value, 1, Int::plus)
                is MatchEvent.Injury -> injured.add(event.playerId.value)
                is MatchEvent.KickOff, is MatchEvent.Save, is MatchEvent.FullTime -> Unit
            }
        }

        val financeByClub = finances.associateBy { it.clubId.value }

        for (club in clubs) {
            val updatedSquad = club.squad.map { p ->
                val id = p.id.value
                val g = goals[id] ?: 0
                val y = yellow[id] ?: 0
                val r = red[id] ?: 0
                val hurt = id in injured
                if (g == 0 && y == 0 && r == 0 && !hurt) {
                    p
                } else {
                    p.copy(
                        goals = p.goals + g,
                        yellowCards = p.yellowCards + y,
                        redCards = p.redCards + r,
                        injured = p.injured || hurt,
                    )
                }
            }

            val finance = financeByClub[club.id.value]
            val newCash = if (finance == null) club.cash
                else (club.cash + finance.ticketRevenue - finance.salariesPaid).coerceAtLeast(0)

            if (updatedSquad != club.squad || newCash != club.cash) {
                clubRepo.save(club.copy(squad = updatedSquad, cash = newCash))
            }
        }
    }

    private fun RoundMatch.finish(score: Pair<Int, Int>?): RoundMatch =
        if (score == null) this
        else copy(
            status = RoundStatus.Finished,
            homeGoals = score.first,
            awayGoals = score.second,
            minute = 90,
        )

    private fun emptyLineup() =
        Lineup(players = emptyList(), formation = Formation.F_4_4_2)
}
