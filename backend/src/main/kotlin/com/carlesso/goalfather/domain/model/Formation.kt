package com.carlesso.goalfather.domain.model

import com.carlesso.goalfather.domain.model.Position.CB
import com.carlesso.goalfather.domain.model.Position.FW
import com.carlesso.goalfather.domain.model.Position.GK
import com.carlesso.goalfather.domain.model.Position.MF
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Esquema tático. `label` é o nome convencional ("4-4-2") que casa com
 * o enum `Formation` no contrato. `slots` é a sequência de posições
 * esperada — propriedade comportamental anexada ao valor do enum.
 *
 * `attackMod`/`defenseMod` (issue #56) têm a MESMA semântica dos
 * modificadores de [Posture] — `attackMod` escala a chance de gol do
 * próprio time, `defenseMod` a do adversário —, mas com peso menor: a
 * formação inclina, a postura decide. 4-4-2 é o eixo neutro (1.0/1.0),
 * então nenhuma escalação existente muda de comportamento por acidente.
 *
 * Serializa como o label (ex.: "4-4-2") em vez do nome do enum (F_4_4_2)
 * para casar com o contrato OpenAPI.
 */
@Serializable(with = FormationSerializer::class)
enum class Formation(
    val label: String,
    val slots: List<Position>,
    val attackMod: Double = 1.0,
    val defenseMod: Double = 1.0,
) {
    F_4_4_2("4-4-2", listOf(GK, CB, CB, CB, CB, MF, MF, MF, MF, FW, FW)),
    F_4_3_3("4-3-3", listOf(GK, CB, CB, CB, CB, MF, MF, MF, FW, FW, FW), attackMod = 1.08, defenseMod = 1.04),
    F_3_5_2("3-5-2", listOf(GK, CB, CB, CB, MF, MF, MF, MF, MF, FW, FW), attackMod = 1.04, defenseMod = 1.02),
    F_5_3_2("5-3-2", listOf(GK, CB, CB, CB, CB, CB, MF, MF, MF, FW, FW), attackMod = 0.92, defenseMod = 0.94),
    ;

    companion object {
        fun fromLabel(label: String): Formation =
            entries.firstOrNull { it.label == label }
                ?: throw IllegalArgumentException("Formação desconhecida: $label")
    }
}

object FormationSerializer : KSerializer<Formation> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Formation", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Formation) = encoder.encodeString(value.label)
    override fun deserialize(decoder: Decoder): Formation = Formation.fromLabel(decoder.decodeString())
}
