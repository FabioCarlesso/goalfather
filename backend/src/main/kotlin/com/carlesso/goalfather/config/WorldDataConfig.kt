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
 * Quem consome é o seed (issue #131, [NewWorldFactory]): com o banco vazio, o
 * mundo do jogo nasce deste pack. O profile `e2e` aponta para o pack
 * reduzido `data/world/e2e/v1` (ver `application.yml`).
 */
@Configuration
@EnableConfigurationProperties(WorldDataProperties::class)
class WorldDataConfig {

    @Bean
    fun worldDataProvider(properties: WorldDataProperties): WorldDataProvider =
        FileWorldDataProvider(Path.of(properties.path))
}
