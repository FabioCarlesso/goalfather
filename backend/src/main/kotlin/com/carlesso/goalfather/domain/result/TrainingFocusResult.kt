package com.carlesso.goalfather.domain.result

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Resultado de escolher o foco de treino da semana (issue #58).
 *
 * Não há variante de "custo": treinar é de graça, o preço é a forma física e
 * o risco de lesão. As falhas possíveis são as mesmas de qualquer comando
 * sobre o clube — não existir e não ser seu —, modeladas como VALOR e não
 * como exception (regra do CLAUDE.md), o que deixa o `when` do controller
 * exaustivo.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface TrainingFocusResult {

    @Serializable
    @SerialName("Success")
    data class Success(val club: Club) : TrainingFocusResult

    @Serializable
    @SerialName("ClubNotFound")
    data class ClubNotFound(val clubId: ClubId) : TrainingFocusResult

    @Serializable
    @SerialName("NotOwner")
    data class NotOwner(val clubId: ClubId) : TrainingFocusResult
}
