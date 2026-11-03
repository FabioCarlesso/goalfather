package com.carlesso.goalfather.config

import com.carlesso.goalfather.adapter.out.persistence.repository.ClubJpaRepository
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.seed.initialLeague
import com.carlesso.goalfather.domain.rules.boardExpectations
import kotlinx.coroutines.runBlocking
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Configuration

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
 * com a DSL de seed), que produz um [League] imutável — e por ser pura dá
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
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        if (clubRepo.count() > 0L) {
            backfillBoards()
            return
        }
        seed.write(initialLeague())
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
