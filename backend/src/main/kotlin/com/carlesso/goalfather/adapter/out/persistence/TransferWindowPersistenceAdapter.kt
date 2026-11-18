package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.mapper.toCountry
import com.carlesso.goalfather.application.port.out.PlayerRegistrationRepository
import com.carlesso.goalfather.application.port.out.TransferWindowRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.world.TransferWindow
import com.carlesso.goalfather.domain.model.world.TransferWindowScope
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * Janela de transferência do save (issue #137, V24). Separada do
 * [TransferWindow] de domínio pela regra de ouro do CLAUDE.md; o país vai
 * como trigrama e volta pelo catálogo de países do save.
 */
@Entity
@Table(name = "transfer_windows")
class TransferWindowEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "country", nullable = false, length = 3)
    var country: String = "",

    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate = LocalDate.EPOCH,

    @Column(name = "end_date", nullable = false)
    var endDate: LocalDate = LocalDate.EPOCH,

    @Column(name = "scope", nullable = false, length = 16)
    var scope: String = TransferWindowScope.INTERNATIONAL.name,
)

interface TransferWindowJpaRepository : JpaRepository<TransferWindowEntity, Long>

fun TransferWindow.toEntity(): TransferWindowEntity =
    TransferWindowEntity(country = country.code.value, startDate = start, endDate = end, scope = scope.name)

/**
 * Coluna → domínio. Escopo desconhecido (texto que esta versão não sabe ler)
 * vira o comum, como o `ExpiryReason` desconhecido vira `DEADLINE` no mapper
 * das propostas: um dado esquisito não derruba a leitura do mercado.
 */
fun TransferWindowEntity.toDomain(): TransferWindow = TransferWindow(
    country = country.toCountry(),
    start = startDate,
    end = endDate,
    scope = TransferWindowScope.entries.firstOrNull { it.name == scope } ?: TransferWindowScope.INTERNATIONAL,
)

@Repository
class TransferWindowPersistenceAdapter(
    private val repo: TransferWindowJpaRepository,
) : TransferWindowRepository {
    override suspend fun findAll(): List<TransferWindow> = withContext(Dispatchers.IO) {
        repo.findAll().map { it.toDomain() }
    }
}

/** Um registro de jogador num clube (issue #137, V24). */
@Entity
@Table(name = "player_registrations")
class PlayerRegistrationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "player_id", nullable = false)
    var playerId: Long = 0,

    @Column(name = "club_id", nullable = false)
    var clubId: Long = 0,

    @Column(name = "registered_on", nullable = false)
    var registeredOn: LocalDate = LocalDate.EPOCH,
)

interface PlayerRegistrationJpaRepository : JpaRepository<PlayerRegistrationEntity, Long> {
    /** Query derivada do nome do método — o Spring Data monta o `WHERE player_id = ?`. */
    fun findByPlayerId(playerId: Long): List<PlayerRegistrationEntity>
}

@Repository
class PlayerRegistrationPersistenceAdapter(
    private val repo: PlayerRegistrationJpaRepository,
) : PlayerRegistrationRepository {
    override suspend fun datesOf(playerId: PlayerId): List<LocalDate> = withContext(Dispatchers.IO) {
        repo.findByPlayerId(playerId.value).map { it.registeredOn }
    }

    override suspend fun record(playerId: PlayerId, clubId: ClubId, date: LocalDate) {
        withContext(Dispatchers.IO) {
            repo.save(PlayerRegistrationEntity(playerId = playerId.value, clubId = clubId.value, registeredOn = date))
        }
    }
}
