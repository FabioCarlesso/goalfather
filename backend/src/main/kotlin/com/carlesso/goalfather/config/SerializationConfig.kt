package com.carlesso.goalfather.config

import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.KotlinSerializationJsonHttpMessageConverter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Registra `kotlinx.serialization` como conversor HTTP. Sem isso, Spring
 * usa Jackson (que NÃO entende `@JsonClassDiscriminator` do kotlinx).
 *
 * Esta config faz o Spring serializar/deserializar:
 * - `MatchEvent` como `{"type":"Goal",...}` (discriminator)
 * - `RoundEvent` como `{"type":"MatchUpdate",...}` ou `{"type":"RoundFinished",...}`
 * - `TransferResult` com seu discriminator
 *
 * `ignoreUnknownKeys = true` deixa o backend tolerante a campos extras
 * vindo do frontend (forward-compatibility).
 */
@Configuration
class SerializationConfig : WebMvcConfigurer {

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Bean
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }

    @Bean
    fun kotlinSerializationJsonHttpMessageConverter(json: Json): HttpMessageConverter<*> =
        KotlinSerializationJsonHttpMessageConverter(json)
}
