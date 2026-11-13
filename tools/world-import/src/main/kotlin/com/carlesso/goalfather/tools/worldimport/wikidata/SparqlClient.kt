package com.carlesso.goalfather.tools.worldimport.wikidata

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Uma linha de resultado SPARQL já simplificada: variável → valor. URIs de
 * entidade viram o QID (`Q17479`), literais viram o texto cru. Variável não
 * ligada (OPTIONAL vazio) simplesmente não aparece no mapa.
 *
 * `typealias` dá nome ao tipo sem criar classe nova — para o compilador
 * continua sendo `Map<String, String>`.
 */
typealias Row = Map<String, String>

/** Como o cliente usa o cache local (`tools/world-import/cache/`). */
enum class CacheMode {
    /** Usa o cache se a consulta for a mesma; senão consulta o endpoint. */
    PREFER_CACHE,

    /** Só o cache: falha se faltar ou se a consulta mudou. É o modo reprodutível. */
    OFFLINE,

    /** Ignora o cache e consulta tudo de novo. */
    REFRESH,
}

/**
 * Resposta de uma consulta como fica guardada no cache: o texto da consulta
 * (para detectar que ela mudou), quando foi feita e as linhas já ORDENADAS —
 * a ordem do endpoint não é garantida, a do arquivo é.
 */
@Serializable
data class CachedResult(val query: String, val retrievedAt: String, val rows: List<Map<String, String>>)

/**
 * Cliente mínimo do SPARQL do Wikidata com cache em disco (issue #127).
 *
 * Política de uso do endpoint: User-Agent genérico do projeto, sem nome nem
 * e-mail de ninguém; poucas consultas grandes em lote (um `VALUES` com os
 * clubes de uma competição, não uma consulta por clube); pausa entre
 * consultas; e respeito ao `Retry-After` quando o servidor devolve 429.
 *
 * O [clock] é injetável pelo mesmo motivo do `Random` da engine: o teste
 * controla a data gravada no cache.
 */
class SparqlClient(
    private val cacheDir: Path,
    private val mode: CacheMode,
    private val endpoint: URI = URI.create("https://query.wikidata.org/sparql"),
    private val clock: Clock = Clock.systemUTC(),
    private val pauseBetweenQueries: Duration = Duration.ofSeconds(2),
    private val log: (String) -> Unit = ::println,
) {
    private val http: HttpClient by lazy {
        // `by lazy`: o HttpClient só nasce se alguma consulta for mesmo à rede
        // (no modo OFFLINE ele nunca é criado).
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()
    }

    private var queriesSent = 0

    /** Datas das respostas usadas nesta execução — a mais recente vai para o envelope. */
    val retrievals: MutableList<Instant> = mutableListOf()

    fun select(name: String, query: String): List<Row> {
        val file = cacheDir.resolve("$name.json")
        val cached = if (file.exists()) CACHE_JSON.decodeFromString(CachedResult.serializer(), file.readText()) else null
        val usable = cached?.takeIf { it.query == query }
        val result = when (mode) {
            CacheMode.OFFLINE -> usable ?: error(
                "cache '$name' ${if (cached == null) "ausente" else "é de outra consulta"}; rode sem --offline para consultar o Wikidata",
            )
            CacheMode.PREFER_CACHE -> usable ?: fetch(name, query).also { save(file, it) }
            CacheMode.REFRESH -> fetch(name, query).also { save(file, it) }
        }
        retrievals += Instant.parse(result.retrievedAt)
        return result.rows
    }

    private fun save(file: Path, result: CachedResult) {
        cacheDir.createDirectories()
        file.writeText(CACHE_JSON.encodeToString(CachedResult.serializer(), result) + "\n")
    }

    private fun fetch(name: String, query: String, attempt: Int = 1): CachedResult {
        if (queriesSent > 0) Thread.sleep(pauseBetweenQueries.toMillis())
        queriesSent++
        log("  consultando Wikidata: $name")
        // POST com o formulário: o `VALUES` com centenas de clubes estoura o
        // limite de tamanho de URL de um GET.
        val body = "query=" + URLEncoder.encode(query, Charsets.UTF_8)
        val request = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(120))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/sparql-results+json")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        return when {
            response.statusCode() == 200 ->
                CachedResult(query, Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString(), parse(response.body()))
            // 429/503: o servidor pediu calma. Espera o que ele mandar e tenta de novo, poucas vezes.
            response.statusCode() in setOf(429, 503) && attempt < MAX_ATTEMPTS -> {
                val wait = response.headers().firstValue("Retry-After").map { it.toLongOrNull() }.orElse(null) ?: 30L
                log("  $name: HTTP ${response.statusCode()}, aguardando ${wait}s")
                Thread.sleep(wait * 1000)
                fetch(name, query, attempt + 1)
            }
            else -> error("consulta '$name' falhou: HTTP ${response.statusCode()} — ${response.body().take(500)}")
        }
    }

    companion object {
        /** Genérico de propósito: identifica o projeto, não uma pessoa. */
        const val USER_AGENT = "GoalFather-world-import/1.0 (open-source study project)"
        private const val MAX_ATTEMPTS = 3
        private const val ENTITY_PREFIX = "http://www.wikidata.org/entity/"
        private const val ONTOLOGY_PREFIX = "http://wikiba.se/ontology#"

        /** Uma linha por objeto no arquivo: diff legível e tamanho contido. */
        private val CACHE_JSON = Json { prettyPrint = false }

        /**
         * JSON de resultados SPARQL → linhas simplificadas e ordenadas. A
         * ordenação usa a representação textual da linha inteira: estável e
         * independente da ordem em que o endpoint devolveu.
         */
        fun parse(body: String): List<Row> {
            val bindings = Json.parseToJsonElement(body).jsonObject.getValue("results").jsonObject
                .getValue("bindings").jsonArray
            return bindings.map { binding ->
                (binding as JsonObject).mapValues { (_, cell) ->
                    cell.jsonObject.getValue("value").jsonPrimitive.content
                        .removePrefix(ENTITY_PREFIX)
                        .removePrefix(ONTOLOGY_PREFIX)
                }.toSortedMap()
            }.distinct().sortedBy { it.toString() }
        }
    }
}
