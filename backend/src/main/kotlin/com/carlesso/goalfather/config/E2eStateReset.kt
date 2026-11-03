package com.carlesso.goalfather.config

import com.carlesso.goalfather.application.seed.initialLeague
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Volta o backend ao estado de um boot limpo (issue #101): apaga toda tabela
 * da aplicação, regrava a seed e esvazia os caches. Existe para o
 * `npm run e2e:real`, cujos specs foram escritos para o MSW (estado isolado
 * por teste) e, contra o backend real, dividiam uma liga só: clubes
 * esgotavam, o calendário andava e o gate de prontidão travava.
 *
 * `@Profile("e2e")` no BEAN, não um `if` no método: fora do profile a classe
 * nem é registrada, e com ela some o controller que a injeta. Apagar o banco
 * não pode depender de alguém lembrar de checar uma flag.
 *
 * SQL específico do H2 (`SET REFERENTIAL_INTEGRITY`): o profile `e2e` fixa o
 * datasource em H2 em memória, ver `application.yml`.
 */
@Component
@Profile("e2e")
class E2eStateReset(
    private val jdbc: JdbcTemplate,
    private val seed: SeedWriter,
    private val caches: CacheManager,
) {

    /**
     * `@Synchronized` porque dois resets intercalados deixariam a seed gravada
     * duas vezes (violação de PK) ou pela metade. Com `workers: 1` no
     * Playwright não deveria acontecer, mas o custo de garantir é nulo.
     */
    @Synchronized
    fun reset() {
        // Tabelas lidas do catálogo, não listadas à mão: migration nova entra
        // no reset sem ninguém lembrar de editar esta classe. O histórico do
        // Flyway fica de fora — o schema não muda, só os dados.
        val tables = jdbc.queryForList(
            """
            SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_TYPE = 'BASE TABLE'
              AND LOWER(TABLE_NAME) <> 'flyway_schema_history'
            """.trimIndent(),
            String::class.java,
        )
        // Integridade referencial desligada só durante o TRUNCATE: a ordem
        // reversa das FKs seria mais uma lista a manter. `finally` religa
        // mesmo se um TRUNCATE falhar — senão o resto do run rodaria sem FKs.
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE")
        try {
            tables.forEach { jdbc.execute("""TRUNCATE TABLE "$it" RESTART IDENTITY""") }
        } finally {
            jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE")
        }

        seed.write(initialLeague())

        // O TRUNCATE passa por fora do JPA: nenhum `@CacheEvict` dispara. Hoje
        // os `save` da seed já despejam `market` e `standings`, mas um cache
        // sobre tabela que a seed não regrava seguiria servindo a liga
        // anterior. Referência de método (`caches::getCache`, `Cache::clear`)
        // no lugar de lambdas — o idioma Kotlin para "aplica esta função".
        caches.cacheNames.mapNotNull(caches::getCache).forEach(Cache::clear)
    }
}
