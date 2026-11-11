package com.carlesso.goalfather

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regra de dependência checada por IMPORT (issue #126): o domínio não pode
 * enxergar adapters (em especial os DTOs do provedor de dados do mundo), nem
 * Spring/JPA; a camada `application` não pode enxergar adapters.
 *
 * Varre os fontes em vez de usar ArchUnit: não exige dependência nova e cobre
 * o que importa aqui. Limitação conhecida: um nome totalmente qualificado no
 * meio do código (sem `import`) escaparia — por isso o teste também procura o
 * pacote proibido em qualquer linha, não só nas de `import`.
 */
class ArchitectureBoundaryTest {

    private val sources: Path = Path.of("src/main/kotlin/com/carlesso/goalfather")

    private fun violations(layer: String, forbidden: List<String>): List<String> {
        val root = sources.resolve(layer)
        // `use` fecha o Stream do `Files.walk` (try-with-resources do Kotlin).
        return Files.walk(root).use { paths ->
            paths.toList()
                .filter { it.extension == "kt" }
                .flatMap { file ->
                    file.readLines().withIndex()
                        .filter { (_, line) -> forbidden.any { it in line } }
                        .map { (index, line) -> "${root.relativize(file)}:${index + 1}: ${line.trim()}" }
                }
        }
    }

    @Test
    fun `dominio nao referencia adapters, DTOs do provedor, application, Spring nem JPA`() {
        val found = violations(
            "domain",
            listOf(
                "com.carlesso.goalfather.adapter",
                "com.carlesso.goalfather.application",
                "com.carlesso.goalfather.config",
                "worlddata.dto",
                "org.springframework",
                "jakarta.persistence",
            ),
        )
        assertTrue(found.isEmpty(), "domínio importando o que não deve:\n${found.joinToString("\n")}")
    }

    @Test
    fun `application nao referencia adapters nem os DTOs do provedor`() {
        val found = violations("application", listOf("com.carlesso.goalfather.adapter", "worlddata.dto"))
        assertTrue(found.isEmpty(), "application importando adapter:\n${found.joinToString("\n")}")
    }

    @Test
    fun `o scanner enxerga os fontes (o teste nao passa por vazio)`() {
        // Se o diretório de trabalho mudar, `walk` acharia zero arquivos e os
        // testes acima passariam sem checar nada.
        val count = Files.walk(sources.resolve("domain")).use { paths -> paths.filter { it.extension == "kt" }.count() }
        assertTrue(count > 20, "esperava os fontes do domínio em $sources, achei $count")
    }
}
