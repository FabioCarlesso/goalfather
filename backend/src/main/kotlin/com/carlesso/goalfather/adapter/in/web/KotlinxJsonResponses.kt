package com.carlesso.goalfather.adapter.`in`.web

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

/**
 * `200 OK` com o corpo serializado pelo kotlinx.serialization — mesmo num
 * handler que declara `ResponseEntity<Any>` (issue #61). Usado em todo corpo
 * de sucesso que carrega um `sealed`: `Round` (`stage`), `CupDto` (confrontos),
 * `ClubDto` (`availability` de cada jogador) e `TransferResultDto`.
 *
 * Por que isto existe: o conversor `KotlinSerializationJsonHttpMessageConverter`
 * procura o serializer pelo tipo DECLARADO do corpo. Com `ResponseEntity<Any>`
 * esse tipo é `Object`, não há serializer, e o Spring cai no Jackson — que não
 * conhece `@JsonClassDiscriminator`. Para tipo plano isso passa despercebido,
 * mas um `sealed` sai SEM o `"type"` que o `oneOf` do contrato exige: o
 * `RoundStage` da rodada e os confrontos `Bye`/`Fixture` da copa chegariam ao
 * cliente impossíveis de desambiguar. (Os métodos de escrita do conversor do
 * Spring são `final`, então resolver pelo tipo de runtime lá dentro não é uma
 * opção limpa.)
 *
 * `reified` deixa o compilador escolher o serializer do tipo DECLARADO no ponto
 * de chamada — o que o Spring não consegue fazer depois do type erasure. Para
 * um `sealed` no topo (`TransferResultDto`) isso importa: o tipo inferido é a
 * interface, então sai o serializer polimórfico, que escreve o `type`; o da
 * subclasse concreta (`Success`) não escreveria.
 */
inline fun <reified T> Json.okJson(body: T): ResponseEntity<Any> =
    ResponseEntity.ok()
        .contentType(MediaType("application", "json", Charsets.UTF_8))
        .body(encodeToString(body))
