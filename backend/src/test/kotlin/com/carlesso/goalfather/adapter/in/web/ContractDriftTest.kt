package com.carlesso.goalfather.adapter.`in`.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import org.yaml.snakeyaml.Yaml
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Teste de drift contrato × backend (issue #82).
 *
 * `contract/openapi.yaml` é a fonte de verdade da API, mas nada no build gera
 * nem valida o backend a partir dele. Este teste fecha o laço nos dois sentidos:
 *
 * 1. toda operação HTTP `/api/...` do contrato tem um handler Spring com o
 *    mesmo método + padrão de path (senão o frontend evolui contra um mock MSW
 *    de um endpoint que não existe — foi o caso do `playMatch`);
 * 2. todo handler `/api/...` do Spring está documentado no contrato.
 *
 * Paths `/ws/...` ficam de fora: são WebSocket (registrados via
 * `WebSocketConfigurer`, não `@RequestMapping`) e só estão no YAML para gerar
 * os tipos das mensagens. Actuator, `/error` e afins ficam fora do sentido 2
 * porque não estão sob `/api`.
 *
 * Valida só a superfície (método + path); o formato dos payloads é coberto
 * pelos `*MvcTest` de cada endpoint.
 *
 * `@SpringBootTest` é aceitável aqui (teste de adapter, não de domínio) e
 * reaproveita o contexto em cache dos outros testes — na prática mais barato
 * que um `@WebMvcTest`, que exigiria mockar os ports de todos os controllers.
 */
@SpringBootTest
class ContractDriftTest {

    // Há mais de um RequestMappingHandlerMapping no contexto (o Actuator
    // registra o seu); o qualifier pega o do MVC da aplicação.
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private lateinit var handlerMapping: RequestMappingHandlerMapping

    /**
     * Par (método HTTP, path normalizado). `data class` dá equals/hashCode
     * estruturais de graça — é o que permite a diferença de conjuntos (`-`).
     * [ANY_METHOD] representa um mapping sem método declarado (atende todos).
     */
    private data class Endpoint(val method: String, val path: String) {
        override fun toString() = "$method $path"
    }

    @Test
    fun `toda operacao HTTP do contrato tem handler no backend`() {
        val backend = backendEndpoints()
        val missing = contractEndpoints().filterNot { it in backend || Endpoint(ANY_METHOD, it.path) in backend }
        if (missing.isNotEmpty()) {
            fail(
                "Operações do contrato sem handler Spring (implemente ou remova do openapi.yaml):\n" +
                    missing.report(),
            )
        }
    }

    @Test
    fun `todo handler da API esta documentado no contrato`() {
        val contract = contractEndpoints()
        val contractPaths = contract.map { it.path }.toSet()
        val undocumented = backendEndpoints().filterNot {
            if (it.method == ANY_METHOD) it.path in contractPaths else it in contract
        }
        if (undocumented.isNotEmpty()) {
            fail(
                "Handlers Spring /api/** ausentes do contrato (documente no openapi.yaml):\n" +
                    undocumented.report(),
            )
        }
    }

    @Test
    fun `contrato foi carregado e tem operacoes`() {
        // Guarda contra falso verde: se o parsing devolvesse vazio, o primeiro
        // teste passaria sem verificar nada.
        assertTrue(contractEndpoints().size > 10, "contrato sem operações /api — parsing quebrou?")
    }

    private fun contractEndpoints(): Set<Endpoint> {
        // O Gradle roda os testes com working dir = backend/.
        val file = File("../contract/openapi.yaml")
        check(file.isFile) { "Contrato não encontrado em ${file.absolutePath}" }

        // SnakeYAML vem transitivamente com o spring-boot-starter.
        val root: Map<String, Any?> = file.reader().use { Yaml().load(it) }
        val paths = root["paths"] as? Map<*, *> ?: error("openapi.yaml sem 'paths'")

        // `flatMap` encadeado: o equivalente idiomático de dois `for`
        // aninhados acumulando numa lista mutável.
        return paths.entries
            .filter { (path, _) -> path.toString().startsWith("/api/") }
            .flatMap { (path, item) ->
                (item as? Map<*, *>).orEmpty().keys
                    .map { it.toString().uppercase() }
                    .filter { it in HTTP_METHODS }
                    .map { Endpoint(it, normalize(path.toString())) }
            }
            .toSet()
    }

    private fun backendEndpoints(): Set<Endpoint> =
        handlerMapping.handlerMethods.keys
            .flatMap { it.endpoints() }
            .filter { it.path.startsWith("/api/") }
            .toSet()

    /** Expande um mapping em todos os (método, padrão) que ele atende. */
    private fun RequestMappingInfo.endpoints(): List<Endpoint> {
        // Boot 3 usa PathPatternParser; o fallback cobre AntPathMatcher.
        val patterns = pathPatternsCondition?.patternValues ?: patternsCondition?.patterns.orEmpty()
        val methods = methodsCondition.methods.map { it.name }.ifEmpty { listOf(ANY_METHOD) }
        return patterns.flatMap { pattern -> methods.map { Endpoint(it, normalize(pattern)) } }
    }

    private fun Collection<Endpoint>.report(): String =
        map { it.toString() }.sorted().joinToString("\n") { "  - $it" }

    private companion object {
        const val ANY_METHOD = "*"
        val HTTP_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

        /** `/api/clubs/{id}` e `/api/clubs/{clubId}` são o mesmo endpoint: o nome da variável não importa. */
        val PATH_VARIABLE = Regex("""\{[^}]+}""")

        fun normalize(path: String): String = path.replace(PATH_VARIABLE, "{}").trimEnd('/')
    }
}
