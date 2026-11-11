package com.carlesso.goalfather.config

import com.carlesso.goalfather.adapter.out.persistence.repository.ClubJpaRepository
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.CupRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.seed.initialLeague
import com.carlesso.goalfather.domain.rules.boardExpectations
import com.carlesso.goalfather.domain.rules.canHoldCup
import com.carlesso.goalfather.domain.rules.canScheduleSeason
import com.carlesso.goalfather.domain.rules.cupDrawSeed
import com.carlesso.goalfather.domain.rules.drawCup
import com.carlesso.goalfather.domain.rules.seasonPlan
import com.carlesso.goalfather.domain.rules.seasonStandings
import kotlinx.coroutines.runBlocking
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Configuration
import kotlin.random.Random

/**
 * Seed inicial — espelha `frontend/src/mocks/seed.ts`.
 *
 * 12 clubes em 2 divisões (issue #47): divisão 1 com o clube do usuário +
 * 5 IA, divisão 2 com 6 clubes IA. 4 entradas iniciais no mercado, rodada 1
 * com 3 pareamentos POR divisão (Berger round-robin), standings vazias da
 * temporada 2026 (uma tabela por divisão) e a copa da temporada já sorteada
 * (issue #61) — 12 clubes numa chave de 16, com 4 folgas.
 *
 * A descrição do estado vive em [initialLeague] (`application/seed`, escrita
 * com a DSL de seed), que produz um `League` imutável — e por ser pura dá
 * para testá-la sem Spring (issue #88). A tradução `domínio → entidades JPA`
 * mora em [SeedWriter], que o reset do E2E (issue #101) também usa; este
 * runner só decide QUANDO gravar. Idempotente: só insere se o DB estiver vazio.
 *
 * Nota: os atributos secundários (pace/shooting/passing/defending) passam a
 * herdar o `overall` por padrão na DSL — antes havia uma variação cosmética
 * por jogador que nada lia (a engine usa só o `overall`). Simplificação
 * deliberada da refatoração (issue #10).
 */
@Configuration
class DataInitializer(
    private val clubRepo: ClubJpaRepository,
    private val seed: SeedWriter,
    private val clubs: ClubRepository,
    private val league: LeagueRepository,
    private val cups: CupRepository,
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        if (clubRepo.count() > 0L) {
            backfillBoards()
            restartSeasonWithoutMatchday()
            return
        }
        seed.write(initialLeague())
    }

    /**
     * Save anterior ao calendário semanal (issue #125): a migration V17 recria
     * `rounds` (um número de rodada não diz em que semana ela cai), então o
     * banco sobe com clubes e tabela, mas sem encaixe corrente — e a liga
     * ficaria parada para sempre.
     *
     * A saída pragmática é REINICIAR a temporada corrente no calendário novo:
     * tabelas zeradas, copa sorteada de novo (mesma seed por temporada) e o
     * primeiro encaixe do plano. Caixa, elencos e história ficam como estão.
     * Reaproveitar a tabela pela metade jogaria de novo rodadas já contadas.
     * Só roda uma vez: depois dela sempre há encaixe.
     */
    private fun restartSeasonWithoutMatchday() = runBlocking {
        if (league.findLatest() != null) return@runBlocking
        val season = league.currentStandings().firstOrNull()?.season ?: return@runBlocking
        val all = clubs.findAll()
        if (!canScheduleSeason(all)) return@runBlocking
        val cup = if (canHoldCup(all)) drawCup(season, all, Random(cupDrawSeed(season))).also { cups.save(it) } else null
        seasonStandings(season, all).forEach { league.saveStandings(it) }
        val plan = seasonPlan(season, all, cup)
        plan.first?.let { league.saveRound(plan.roundAt(it, all, cup)) }
    }

    /**
     * Save anterior à issue #62: fixa uma vez, no boot, a meta da temporada
     * corrente de quem ainda não tem. Sem isto o `GET /board` derivaria a meta
     * da liga inteira a cada chamada até a primeira rodada — e ela poderia
     * mudar entre chamadas se o mercado mexesse nos elencos.
     *
     * Passa pelos PORTS (não pelas entidades) porque a meta é regra de domínio
     * sobre o `Club` completo, com elenco. `runBlocking` faz a ponte para os
     * ports `suspend` — aceitável aqui: é o boot, não há thread de request a
     * bloquear. O `save` respeita o lock otimista; um `Conflict` (alguém gravou
     * o clube no meio) é ignorado, porque a rodada ainda fixa a meta que faltar.
     */
    private fun backfillBoards() = runBlocking {
        val season = league.currentStandings().firstOrNull()?.season ?: return@runBlocking
        val all = clubs.findAll()
        val missing = all.filter { it.board?.season != season }
        if (missing.isEmpty()) return@runBlocking
        val boards = boardExpectations(season, all)
        for (club in missing) clubs.save(club.copy(board = boards[club.id]))
    }
}
