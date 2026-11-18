package com.carlesso.goalfather.config

import com.carlesso.goalfather.adapter.out.persistence.SavedCountryCatalog
import com.carlesso.goalfather.adapter.out.persistence.TransferWindowJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.toEntity
import com.carlesso.goalfather.adapter.out.persistence.repository.ClubJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.CompetitionCatalogJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Configuration
import kotlin.time.measureTime

/**
 * Seed do jogo (issue #131): com o banco vazio, monta o mundo do MVP a partir
 * do pack (`data/world/`, via [NewWorldFactory]) e o grava ([SeedWriter]).
 * Este runner só decide QUANDO gravar. O que o mundo tem — competições,
 * clubes, elencos, economia inicial — é o seed puro de
 * `application/seed/WorldSeed.kt`, testado sem Spring.
 *
 * Três estados possíveis do banco no boot:
 * - **vazio:** grava o mundo novo;
 * - **com mundo** (clubes + catálogo de competições): segue o jogo;
 * - **save antigo** (clubes SEM catálogo — o mundo de 12 clubes de antes da
 *   #131): incompatível com o mundo novo (ids, ligas e calendário mudaram), e
 *   não há migração possível que faça sentido. O boot FALHA dizendo como
 *   zerar o banco. Falhar alto é melhor que subir com um mundo pela metade.
 *
 * Por isso morreram os remendos de save antigo (`restartSeasonWithoutMatchday`,
 * da #125, e `backfillBoards`, da #62): um save anterior à #131 nem passa
 * daqui, e o seed atual já grava encaixe e metas.
 */
@Configuration
class DataInitializer(
    private val clubRepo: ClubJpaRepository,
    private val catalogRepo: CompetitionCatalogJpaRepository,
    private val newWorld: NewWorldFactory,
    private val seed: SeedWriter,
    private val countries: SavedCountryCatalog,
    // Janelas de transferência (issue #137, V24).
    private val transferWindowRepo: TransferWindowJpaRepository,
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        if (clubRepo.count() > 0L) {
            check(catalogRepo.count() > 0L) { LEGACY_SAVE_MESSAGE }
            backfillTransferWindows()
            countries.reload()
            return
        }
        val total = measureTime {
            val (world, start) = newWorld.create()
            val written = measureTime { seed.write(world, start) }
            log.info("Mundo novo gravado em {} ms ({} clubes)", written.inWholeMilliseconds, world.clubs.size)
        }
        countries.reload()
        log.info("Seed do mundo concluído em {} ms (carga do pack + seed + gravação)", total.inWholeMilliseconds)
    }

    /**
     * Save de antes da #137 (V24 criou a tabela vazia): sem janelas, o mercado
     * ficaria fechado para sempre. Ganha as janelas do pack, uma vez. Ao
     * contrário do save anterior à #131, aqui a migração faz sentido: as
     * janelas são dado da federação, não dependem de nada do save.
     */
    private fun backfillTransferWindows() {
        if (transferWindowRepo.count() > 0L) return
        val windows = newWorld.create().world.transferWindows
        transferWindowRepo.saveAll(windows.map { it.toEntity() })
        log.info("Save anterior à #137: {} janelas de transferência copiadas do pack", windows.size)
    }

    companion object {
        private val log = LoggerFactory.getLogger(DataInitializer::class.java)

        const val LEGACY_SAVE_MESSAGE: String =
            "O banco tem um save anterior ao mundo do MVP (issue #131): há clubes, mas nenhum catálogo de " +
                "competições. Esse save não é compatível com o mundo montado a partir do pack. Para começar um " +
                "jogo novo, zere o banco e suba de novo: no H2 de desenvolvimento, `rm -rf backend/data/`; no " +
                "Postgres, recrie o schema (ex.: `docker compose down -v`). Ver docs/WORLD_DATA.md, seção \"Seed\"."
    }
}
