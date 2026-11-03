package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.config.E2eStateReset
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Reset de estado para o `npm run e2e:real` (issue #101). Só existe no profile
 * `e2e`: fora dele o bean não é registrado e a rota responde 404.
 *
 * Fora de `/api` de propósito: não é API do jogo, não entra no
 * `contract/openapi.yaml` e, por isso, não pode estar sob o prefixo que o
 * `ContractDriftTest` exige documentado. Sem autenticação — cai no
 * `anyRequest permitAll` da `SecurityConfig` — porque o helper do Playwright
 * reseta ANTES de existir usuário.
 */
@RestController
@Profile("e2e")
class E2eResetController(private val state: E2eStateReset) {

    @PostMapping("/test-support/reset")
    fun reset(): ResponseEntity<Unit> {
        state.reset()
        return ResponseEntity.noContent().build()
    }
}
