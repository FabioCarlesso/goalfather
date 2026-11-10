package com.carlesso.goalfather.domain.serialization

import com.carlesso.goalfather.domain.model.Money
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * `Money` (issue #122) vira inteiro cru de centavos no JSON — a forma que o
 * contrato sempre declarou para dinheiro. Mesmo padrão de `PlayerIdSerializer`.
 */
object MoneySerializer : KSerializer<Money> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Money", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Money) =
        encoder.encodeLong(value.cents)

    override fun deserialize(decoder: Decoder): Money =
        Money(decoder.decodeLong())
}
