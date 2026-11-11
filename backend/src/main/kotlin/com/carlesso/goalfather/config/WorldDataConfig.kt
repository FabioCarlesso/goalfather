package com.carlesso.goalfather.config

import com.carlesso.goalfather.adapter.out.worlddata.FileWorldDataProvider
import com.carlesso.goalfather.application.port.out.WorldDataProvider
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path

/**
 * Onde está o pack do mundo (issue #126). O default `../data/world/v1` é
 * relativo ao diretório de trabalho do `./gradlew bootRun` (`backend/`) e
 * aponta para o pack versionado na raiz do repositório. Sobrescreva com
 * `WORLD_DATA_PATH` (ver `application.yml`) para usar um pack fora do git.
 */
@ConfigurationProperties("app.world-data")
data class WorldDataProperties(
    val path: String = "../data/world/v1",
)

/**
 * Registra o adapter de arquivo como implementação do port. Fica numa config
 * própria (e não no `BeanConfig`) porque é o ponto de troca: um adapter HTTP
 * futuro seria outro `@Bean` aqui, escolhido por property/profile.
 *
 * **Ainda não é usado pelo seed** — trocar o `InitialLeague` pelo pack é a
 * issue #131. O bean existe para a fiação estar pronta e testada.
 */
@Configuration
@EnableConfigurationProperties(WorldDataProperties::class)
class WorldDataConfig {

    @Bean
    fun worldDataProvider(properties: WorldDataProperties): WorldDataProvider =
        FileWorldDataProvider(Path.of(properties.path))
}
