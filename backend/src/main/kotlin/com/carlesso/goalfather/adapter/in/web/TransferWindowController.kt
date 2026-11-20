package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.ErrorResponse
import com.carlesso.goalfather.application.port.`in`.TransferWindowUseCase
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.world.TransferWindowScope
import com.carlesso.goalfather.domain.rules.WindowStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/**
 * Janela de transferência do clube (issue #137): o selo "Janela aberta até …"
 * / "Abre em …" do mercado e do cabeçalho. Leitura de qualquer usuário autenticado.
 */
@RestController
class TransferWindowController(
    private val windows: TransferWindowUseCase,
    private val json: Json,
) {

    @GetMapping("/api/clubs/{id}/transfer-window")
    suspend fun transferWindow(@PathVariable id: Long): ResponseEntity<Any> {
        val status = windows.statusOf(ClubId(id))
            ?: return ResponseEntity.status(404).body(
                ErrorResponse(code = "CLUB_NOT_FOUND", message = "Clube $id não encontrado"),
            )
        return json.okJson(status.toDto())
    }
}

/**
 * Espelha `TransferWindowStatus` do contrato (`oneOf` + `discriminator`).
 * Datas em ISO-8601, como o resto do contrato.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface TransferWindowStatusDto {
    @Serializable
    @SerialName("Open")
    data class Open(val closes: String, val scope: TransferWindowScope) : TransferWindowStatusDto

    @Serializable
    @SerialName("Closed")
    data class Closed(val nextOpens: String? = null) : TransferWindowStatusDto
}

/** `when` exaustivo sobre o `sealed WindowStatus` do domínio. */
fun WindowStatus.toDto(): TransferWindowStatusDto = when (this) {
    is WindowStatus.Open -> TransferWindowStatusDto.Open(closes = closes.toString(), scope = scope)
    is WindowStatus.Closed -> TransferWindowStatusDto.Closed(nextOpens = nextOpens?.toString())
}
