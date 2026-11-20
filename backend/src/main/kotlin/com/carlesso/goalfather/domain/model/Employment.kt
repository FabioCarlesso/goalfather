package com.carlesso.goalfather.domain.model

import com.carlesso.goalfather.domain.serialization.LocalDateSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import java.time.LocalDate

/**
 * Vínculo de um jogador (onda 6: #135, #136, #137).
 *
 * Tipo compartilhado da onda: a #136 o pendura no [Player] no lugar do
 * `salary` solto e traz as regras de duração e vencimento; a #135 o lê para o
 * deságio por contrato restante. Fica aqui antes, num commit base, para as
 * duas frentes andarem em paralelo sem inventar dois tipos.
 *
 * `sealed` pelo mesmo motivo de [Availability]: um livre com salário ou com
 * cláusula de rescisão simplesmente não é representável. O contrato é do
 * VÍNCULO, não do jogador — o [clubId] dentro de [UnderContract] deixa claro
 * que trocar de clube troca o contrato.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface Employment {

    @Serializable
    @SerialName("UnderContract")
    data class UnderContract(
        val clubId: ClubId,
        val salary: Money,
        @Serializable(with = LocalDateSerializer::class) val start: LocalDate,
        @Serializable(with = LocalDateSerializer::class) val end: LocalDate,
        val releaseClause: Money? = null,
    ) : Employment {
        init {
            require(end.isAfter(start)) { "contrato termina ($end) antes de começar ($start)" }
            require(!salary.isNegative) { "salário negativo: $salary" }
            require(releaseClause == null || releaseClause.isPositive) { "cláusula de rescisão deve ser positiva: $releaseClause" }
        }
    }

    /** Sem clube: sai sem taxa de transferência. */
    @Serializable
    @SerialName("FreeAgent")
    data object FreeAgent : Employment
}
