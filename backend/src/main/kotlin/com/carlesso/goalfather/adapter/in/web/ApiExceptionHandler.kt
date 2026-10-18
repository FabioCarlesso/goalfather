package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.ErrorResponse
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.beans.TypeMismatchException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MissingRequestValueException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * Tradução global de exceptions em `ErrorResponse` (issue #81). Sem isto, o
 * que escapava dos controllers caía no `BasicErrorController` e saía com o
 * corpo padrão do Spring (`timestamp,status,error,path`), fora do contrato.
 *
 * Herdar de [ResponseEntityExceptionHandler] dá de graça o mapeamento de
 * TODAS as exceptions de infraestrutura do Spring MVC para o status certo
 * (rota inexistente → 404, método errado → 405, media type → 415,
 * `ResponseStatusException` → o status dela...). Só sobrescrevemos o ponto
 * único por onde todas passam ([handleExceptionInternal]) para trocar o
 * `ProblemDetail` pelo nosso `ErrorResponse` — então nenhum status que o Spring
 * já resolvia muda, só o formato do corpo.
 *
 * Controllers `suspend` (despacho ASYNC, ver issue #42): a exception da
 * corrotina vira o "resultado" do async e é reprocessada no dispatch de volta
 * pelos mesmos `HandlerExceptionResolver`s, então este advice cobre os dois
 * fluxos — os testes em `ApiErrorHandlingMvcTest` exercitam ambos.
 *
 * 401/403 do Spring Security não passam por aqui: são decididos nos filtros
 * (`RestAuthenticationEntryPoint`), antes do `DispatcherServlet`.
 */
@RestControllerAdvice
class ApiExceptionHandler(private val json: Json) : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Corpo que não desserializa. O conversor kotlinx do Spring já embrulha a
     * `SerializationException` em `HttpMessageNotReadableException` (tratada
     * pela classe base e roteada para [handleExceptionInternal]); este handler
     * cobre a `SerializationException` crua, caso alguém desserialize na mão.
     */
    @ExceptionHandler(SerializationException::class)
    fun handleSerialization(ex: SerializationException): ResponseEntity<Any> =
        errorJson(HttpStatus.BAD_REQUEST, malformed(ex))

    /**
     * Exceptions do Spring Security lançadas DENTRO de um controller devolvem
     * a decisão ao `ExceptionTranslationFilter` (401/403) em vez de caírem no
     * fallback 500. `Nothing` é o tipo de "nunca retorna": deixa claro, no tipo,
     * que este handler só relança.
     */
    @ExceptionHandler(AccessDeniedException::class, AuthenticationException::class)
    fun rethrowSecurity(ex: RuntimeException): Nothing = throw ex

    /**
     * Fallback: qualquer coisa não prevista (inclusive `require(...)` de domínio)
     * vira 500 `INTERNAL_ERROR` com mensagem genérica. O stacktrace vai para o
     * log, NUNCA para o corpo — detalhes internos não vazam para o cliente.
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<Any> {
        log.error("Erro não tratado na API", ex)
        return errorJson(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_BODY)
    }

    /**
     * Ponto único das exceptions de infraestrutura do Spring MVC. O `statusCode`
     * já vem resolvido pela classe base; aqui só escolhemos `code`/`message`.
     * `when` com `is` faz *smart cast*: dentro de cada ramo `ex` já é do tipo
     * testado, sem cast explícito.
     */
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val error = when (ex) {
            is HttpMessageNotReadableException -> malformed(ex)
            is MethodArgumentTypeMismatchException -> invalidParameter(ex.name, ex.value)
            is TypeMismatchException -> invalidParameter(ex.propertyName ?: "?", ex.value)
            is MissingRequestValueException -> ErrorResponse(
                code = "INVALID_PARAMETER",
                message = "Parâmetro obrigatório ausente",
                details = mapOf("reason" to ex.firstLine()),
            )
            else -> ErrorResponse(
                code = codeFor(statusCode),
                // Só a `reason` de um `ResponseStatusException` é escrita por nós;
                // o `detail` padrão do Spring vem em inglês e ecoa o path.
                message = (ex as? ResponseStatusException)?.reason ?: defaultMessage(statusCode),
            )
        }
        // `super` preserva o comportamento da base: não escreve se a resposta já
        // foi commitada e marca o request quando é 500 (para métricas/log).
        // Cópia: `headers` pode ser o objeto da própria exception (ex.: `Allow`
        // do 405) — não mutamos estado alheio. `apply` devolve o receptor já configurado.
        val jsonHeaders = HttpHeaders().apply {
            putAll(headers)
            contentType = JSON_UTF8
        }
        val encoded = json.encodeToString(ErrorResponse.serializer(), error)
        return super.handleExceptionInternal(ex, encoded, jsonHeaders, statusCode, request)
    }

    private fun malformed(ex: Exception) = ErrorResponse(
        code = "MALFORMED_REQUEST",
        message = "Corpo da requisição inválido",
        // A causa mais específica (ex.: "Formação desconhecida: 2-2-6") descreve
        // a ENTRADA do cliente, não o servidor — ajuda a depurar sem vazar nada.
        details = mapOf("reason" to ex.rootCause().firstLine().withoutPackages()),
    )

    private fun invalidParameter(name: String, value: Any?) = ErrorResponse(
        code = "INVALID_PARAMETER",
        message = "Parâmetro '$name' inválido",
        details = mapOf("parameter" to name, "value" to value.toString()),
    )

    /** Corpo já serializado pelo kotlinx (mesma ideia do [okJson]): não depende do Jackson. */
    private fun errorJson(status: HttpStatus, body: ErrorResponse): ResponseEntity<Any> =
        ResponseEntity.status(status)
            .contentType(JSON_UTF8)
            .body(json.encodeToString(ErrorResponse.serializer(), body))

    private fun codeFor(status: HttpStatusCode): String =
        if (status.value() == 500) "INTERNAL_ERROR"
        else HttpStatus.resolve(status.value())?.name ?: "HTTP_${status.value()}"

    private fun defaultMessage(status: HttpStatusCode): String = when (status.value()) {
        404 -> "Recurso não encontrado"
        405 -> "Método HTTP não suportado nesta rota"
        406 -> "Formato de resposta não suportado"
        415 -> "Tipo de conteúdo não suportado"
        else -> if (status.is5xxServerError) INTERNAL_ERROR_BODY.message else "Requisição inválida"
    }

    private companion object {
        val JSON_UTF8 = MediaType("application", "json", Charsets.UTF_8)
        val INTERNAL_ERROR_BODY = ErrorResponse(
            code = "INTERNAL_ERROR",
            message = "Erro interno no servidor. Tente novamente em instantes.",
        )

        // Extension functions privadas: "métodos" utilitários pendurados em
        // `Throwable` sem criar uma classe `ThrowableUtils` estática à la Java.

        /** `generateSequence` percorre a cadeia de `cause` até a última. */
        fun Throwable.rootCause(): Throwable = generateSequence(this) { it.cause }.last()

        /** Mensagens do kotlinx trazem dicas multi-linha; só a 1ª linha interessa. */
        fun Throwable.firstLine(): String = message?.lineSequence()?.firstOrNull().orEmpty()

        /**
         * O kotlinx cita tipos pelo nome qualificado ("com.carlesso.goalfather
         * .domain.model.TrainingFocus does not contain…"). O nome simples basta
         * para o cliente entender o erro; o pacote só expõe a estrutura interna.
         */
        fun String.withoutPackages(): String = replace(QUALIFIED_NAME) { it.groupValues[1] }

        /** Segmentos minúsculos seguidos de ponto antes de um tipo em PascalCase. */
        val QUALIFIED_NAME = Regex("""\b(?:[a-z_][a-z0-9_]*\.)+([A-Z]\w*)""")
    }
}
