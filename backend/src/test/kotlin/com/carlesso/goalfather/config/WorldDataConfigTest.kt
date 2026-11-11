package com.carlesso.goalfather.config

import com.carlesso.goalfather.adapter.out.worlddata.FileWorldDataProviderTest
import com.carlesso.goalfather.application.port.out.PackLoadResult
import com.carlesso.goalfather.application.port.out.WorldDataProvider
import kotlinx.coroutines.runBlocking
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * Fiação do provedor do mundo (issue #126) sem subir a aplicação inteira:
 * `ApplicationContextRunner` monta um contexto só com a config testada —
 * milissegundos, contra os segundos de um `@SpringBootTest`.
 */
class WorldDataConfigTest {

    private val runner = ApplicationContextRunner().withUserConfiguration(WorldDataConfig::class.java)

    @Test
    fun `registra o adapter de arquivo no caminho da property`() {
        runner
            .withPropertyValues("app.world-data.path=${FileWorldDataProviderTest.worldSamplePath()}")
            .run { context ->
                val provider = context.getBean(WorldDataProvider::class.java)
                // `runBlocking` faz a ponte do callback síncrono do runner para o `suspend`.
                assertIs<PackLoadResult.Loaded>(runBlocking { provider.load() })
            }
    }

    @Test
    fun `sobe mesmo sem o pack no disco - a carga e preguicosa`() {
        runner
            .withPropertyValues("app.world-data.path=/caminho/que/nao/existe")
            .run { context ->
                val provider = context.getBean(WorldDataProvider::class.java)
                assertIs<PackLoadResult.Invalid>(runBlocking { provider.load() })
            }
    }
}
