package com.carlesso.goalfather.domain.serialization

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.PlayerId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializers manuais para `@JvmInline value class`. kotlinx.serialization
 * suporta value classes mas precisa de hint explícito; estes serializers
 * fazem o value class virar Long no JSON (transparente para o cliente,
 * que vê apenas `"id": 123`).
 *
 * Aplicados nas DTOs e em campos de domain quando necessário via
 * `@Serializable(with = PlayerIdSerializer::class)`. Quando o value class
 * for usado dentro de outro `@Serializable`, é mais simples usar
 * `@Serializable` no próprio value class — feito em Player.kt / Club.kt
 * via `@JvmInline` + `@Serializable` no value class.
 */
object PlayerIdSerializer : KSerializer<PlayerId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("PlayerId", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: PlayerId) =
        encoder.encodeLong(value.value)

    override fun deserialize(decoder: Decoder): PlayerId =
        PlayerId(decoder.decodeLong())
}

object ClubIdSerializer : KSerializer<ClubId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ClubId", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: ClubId) =
        encoder.encodeLong(value.value)

    override fun deserialize(decoder: Decoder): ClubId =
        ClubId(decoder.decodeLong())
}
