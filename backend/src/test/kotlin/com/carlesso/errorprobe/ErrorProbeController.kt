package com.carlesso.errorprobe

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller SÓ de teste que estoura exceptions inesperadas (issue #81).
 *
 * Mora fora de `com.carlesso.goalfather` de propósito: o component scan da
 * aplicação não o enxerga, então ele só existe no contexto do teste que o
 * traz via `@Import` — os outros `*MvcTest` não ganham uma rota extra.
 */
@RestController
@RequestMapping("/api/test-probe")
class ErrorProbeController {

    /** Igual a um `require(...)` de domínio estourando num handler comum. */
    @GetMapping("/require")
    fun failingRequire(): String {
        require(false) { "segredo interno: invariante violada" }
        return "inalcançável"
    }

    /** Mesmo erro num handler `suspend` — sai no dispatch ASYNC. */
    @GetMapping("/suspend")
    suspend fun failingSuspend(): String =
        throw IllegalStateException("segredo interno: falha na corrotina")
}
