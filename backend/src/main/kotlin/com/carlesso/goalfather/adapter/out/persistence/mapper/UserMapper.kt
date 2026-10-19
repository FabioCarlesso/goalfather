package com.carlesso.goalfather.adapter.out.persistence.mapper

import com.carlesso.goalfather.adapter.out.persistence.entity.UserEntity
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Dismissal
import com.carlesso.goalfather.domain.model.User
import com.carlesso.goalfather.domain.model.UserId
import kotlinx.serialization.json.Json

/**
 * `Json` local para a demissão (issue #62). `Dismissal` é um valor achatado,
 * sem `sealed` nem default que importe, então não precisa do bean configurado
 * em `SerializationConfig` — e assim os mappers de usuário seguem sem
 * parâmetro, como sempre foram. `ignoreUnknownKeys` deixa um campo removido no
 * futuro sem derrubar a leitura das linhas antigas.
 */
private val dismissalCodec = Json { ignoreUnknownKeys = true }

fun UserEntity.toDomain(): User = User(
    id = UserId(id),
    username = username,
    passwordHash = passwordHash,
    clubId = clubId?.let { ClubId(it) },
    dismissal = dismissalJson?.let(::decodeDismissal),
)

fun User.toEntity(): UserEntity = UserEntity(
    // id = 0 sinaliza insert (IDENTITY gera o real); !=0 atualiza a linha.
    id = id.value,
    username = username,
    passwordHash = passwordHash,
    clubId = clubId?.value,
    dismissalJson = dismissal?.let(::encodeDismissal),
)

fun encodeDismissal(dismissal: Dismissal): String =
    dismissalCodec.encodeToString(Dismissal.serializer(), dismissal)

/**
 * Coluna ilegível vira "sem demissão" em vez de derrubar o login: a demissão é
 * só a tela de game over, e perder a notícia é melhor que perder o acesso.
 */
private fun decodeDismissal(raw: String): Dismissal? =
    // `SerializationException` é subclasse de `IllegalArgumentException` — o
    // mesmo `catch` cobre JSON quebrado e um `require` do domínio recusando.
    try {
        dismissalCodec.decodeFromString(Dismissal.serializer(), raw)
    } catch (_: IllegalArgumentException) {
        null
    }
