package com.carlesso.goalfather.config

import com.carlesso.goalfather.application.port.out.PackLoadResult
import com.carlesso.goalfather.application.port.out.WorldDataProvider
import com.carlesso.goalfather.application.seed.InitialWorld
import com.carlesso.goalfather.application.seed.toInitialWorld
import com.carlesso.goalfather.domain.rules.WorldStart
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.time.measureTimedValue

/** Um jogo novo pronto para gravar: o mundo e a abertura da temporada. */
data class NewWorld(val world: InitialWorld, val start: WorldStart)

/**
 * Monta o mundo de um jogo novo a partir do pack (issue #131): carrega pelo
 * [WorldDataProvider], passa pelo seed puro (`toInitialWorld`) e abre a
 * temporada (`startWorld`). Não grava nada — isso é do [SeedWriter].
 *
 * O resultado fica em memória (`by lazy`): é imutável e determinístico para o
 * mesmo pack, então o reset do E2E, que recria o mundo a cada teste, não relê
 * o disco nem recalcula o seed. O `lazy` padrão é sincronizado (só uma thread
 * calcula) e, se o cálculo lançar, NÃO guarda a falha: a próxima chamada tenta
 * de novo.
 */
@Component
class NewWorldFactory(
    private val provider: WorldDataProvider,
    private val properties: WorldDataProperties,
) {
    private val cached: NewWorld by lazy { build() }

    fun create(): NewWorld = cached

    private fun build(): NewWorld {
        // `runBlocking`: ponte para o port `suspend` no boot, onde não há
        // thread de request a bloquear (mesma escolha do resto do seed).
        val (loaded, loadTime) = measureTimedValue { runBlocking { provider.load() } }
        // `when` exaustivo sobre o resultado sealed: pack inválido é erro de
        // CONFIGURAÇÃO do servidor — aqui sim uma exception, que derruba o boot
        // listando todos os problemas.
        val pack = when (loaded) {
            is PackLoadResult.Loaded -> loaded.pack
            is PackLoadResult.Invalid -> error(
                "Pack do mundo inválido em '${properties.path}' (${loaded.errors.size} erro(s)):\n" +
                    loaded.errors.joinToString("\n") { "  - ${it.message}" },
            )
        }
        val (world, seedTime) = measureTimedValue { pack.toInitialWorld() }
        val (start, startTime) = measureTimedValue { world.start() }
        log.info(
            "Mundo montado do pack '{}': {} competições, {} clubes, {} jogadores — " +
                "carga do pack {} ms, seed {} ms, abertura da temporada {} ms",
            properties.path,
            world.competitions.size,
            world.clubs.size,
            world.clubs.sumOf { it.squad.size } + world.market.size,
            loadTime.inWholeMilliseconds,
            seedTime.inWholeMilliseconds,
            startTime.inWholeMilliseconds,
        )
        return NewWorld(world, start)
    }

    private companion object {
        val log = LoggerFactory.getLogger(NewWorldFactory::class.java)
    }
}
