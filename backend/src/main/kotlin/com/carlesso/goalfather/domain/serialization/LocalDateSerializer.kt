package com.carlesso.goalfather.domain.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate

/**
 * `LocalDate` como texto ISO-8601 (`2026-03-31`) — o `format: date` do OpenAPI
 * (issue #134). O kotlinx não traz serializer para `java.time`, e o módulo
 * `kotlinx-datetime` seria dependência nova só para isto; um `KSerializer`
 * pequeno, no mesmo padrão de `MoneySerializer`, resolve.
 */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate =
        LocalDate.parse(decoder.decodeString())
}
