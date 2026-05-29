# CLAUDE.md — Instruções para o Claude Code

Este arquivo orienta o Claude Code ao trabalhar no **GoalFather (GF)**.

## Contexto do projeto

GoalFather é um manager de futebol estilo Elifoot. Backend em **Kotlin + Spring Boot**, frontend React. É um **projeto de estudo de Kotlin** — o dono tem 15+ anos de Java/Spring Boot e quer aprender Kotlin idiomático. Portanto:

- **Prefira sempre a solução Kotlin idiomática à tradução direta de Java.** Ex.: `sealed interface` + `when` em vez de hierarquias com `instanceof`; `data class` + `copy()` em vez de setters; coroutines/`Flow` em vez de `CompletableFuture`/`@Async`; extension functions em vez de classes utilitárias estáticas.
- **Ao introduzir um recurso Kotlin não óbvio, explique brevemente o porquê** em comentário ou na resposta. O objetivo é aprendizado, não só código funcionando.

## A regra de ouro (arquitetura)

**O módulo `domain` é puro: ZERO dependências de Spring, JPA ou qualquer framework.**

- A regra de dependência aponta sempre para dentro: `adapter` → `application` → `domain`. Nunca o contrário.
- Inversão via *ports*: interfaces declaradas em `application/port`, implementadas em `adapter`.
- **NÃO** anote as `data class` de domínio com `@Entity`. Crie entidades JPA separadas em `adapter/out/persistence` e traduza com mappers.
- A engine de simulação deve ser **testável sem subir contexto Spring** (`@SpringBootTest` é proibido para testar domínio).

## Convenções de código

- **Imutabilidade por padrão:** `val` sobre `var`; mutações produzem novas instâncias via `copy()`.
- **Null-safety:** evite `!!`. Use `?.`, `?:`, `requireNotNull`, ou modele a ausência no tipo.
- **Erros de negócio como valores:** use `sealed`/`Result`, não exceptions para fluxo de controle. Exceptions só para casos verdadeiramente excepcionais.
- **Aleatoriedade injetável:** a engine recebe `Random` por construtor, para testes determinísticos com seed fixa.
- **Pacote base:** `com.carlesso.goalfather`
- **Testes:** JUnit 5 + `kotlin.test` + MockK (não Mockito) + `kotlinx-coroutines-test` (`runTest`).

## Estrutura de pacotes

```
com.carlesso.goalfather
├── domain        // model, event (sealed), engine, rules — PURO
├── application   // port.in, port.out, service (use cases)
├── adapter       // in.web (controllers/DTO), out.persistence (JPA)
└── config        // Spring beans, security, OpenAPI
```

## Ordem de desenvolvimento

Siga o roadmap em `docs/ARQUITETURA.md`. **Fase 1 antes de tudo:** domínio + engine + testes, sem Spring. Só depois suba a camada de aplicação e os adapters ao redor do domínio já testado.

## Fonte de verdade das regras

O protótipo em `prototype/goalfather-web.jsx` define as regras de jogo atuais (formações, atributos, cálculo de força, eventos de partida, mercado, tabela). Ao implementar o backend, **trate o protótipo como especificação executável** — replique e refine essas regras no domínio Kotlin.

## Comandos úteis (após scaffold)

```bash
cd backend
./gradlew test            # testes (domínio deve passar sem Spring)
./gradlew bootRun         # sobe a aplicação
./gradlew ktlintCheck     # lint (se configurado)
```

## O que NÃO fazer

- Não vazar o modelo de domínio na API (use DTOs).
- Não colocar lógica de negócio em controllers ou em entidades JPA.
- Não usar `@SpringBootTest` para testar a engine ou regras de domínio.
- Não recorrer a padrões Java verbosos quando há um idioma Kotlin equivalente — este é um projeto de aprendizado de Kotlin.
