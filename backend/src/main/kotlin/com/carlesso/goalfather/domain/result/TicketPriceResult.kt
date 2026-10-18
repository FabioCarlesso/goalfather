package com.carlesso.goalfather.domain.result

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Resultado de definir o preço do ingresso (issue #59).
 *
 * `PriceOutOfRange` é o ponto da issue: preço fora da faixa é um DESFECHO do
 * comando, não um acidente — vem do técnico digitando um número, que é entrada
 * normal de usuário. Modelado como valor (e não `IllegalArgumentException`),
 * ele carrega a faixa junto, o `when` do controller continua exaustivo, e o
 * compilador cobra tratamento de todo desfecho novo — regra do CLAUDE.md.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface TicketPriceResult {

    @Serializable
    @SerialName("Success")
    data class Success(val club: Club) : TicketPriceResult

    @Serializable
    @SerialName("ClubNotFound")
    data class ClubNotFound(val clubId: ClubId) : TicketPriceResult

    @Serializable
    @SerialName("NotOwner")
    data class NotOwner(val clubId: ClubId) : TicketPriceResult

    @Serializable
    @SerialName("PriceOutOfRange")
    data class PriceOutOfRange(val price: Long, val min: Long, val max: Long) : TicketPriceResult
}
