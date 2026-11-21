package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.application.port.out.ContractTalksRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.PlayerId
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.io.Serializable
import java.time.LocalDate

/** Chave composta jogador × clube da `contract_talks` (V26). */
@Embeddable
data class ContractTalksId(
    @Column(name = "player_id") var playerId: Long = 0,
    @Column(name = "club_id") var clubId: Long = 0,
) : Serializable

/** Esfriamento da renovação (issue #138). Separada do domínio pela regra de ouro. */
@Entity
@Table(name = "contract_talks")
class ContractTalksEntity(
    @EmbeddedId
    var id: ContractTalksId = ContractTalksId(),

    @Column(name = "cooldown_until", nullable = false)
    var cooldownUntil: LocalDate = LocalDate.EPOCH,
)

interface ContractTalksJpaRepository : JpaRepository<ContractTalksEntity, ContractTalksId>

@Repository
class ContractTalksPersistenceAdapter(
    private val repo: ContractTalksJpaRepository,
) : ContractTalksRepository {

    override suspend fun cooldownUntil(playerId: PlayerId, clubId: ClubId): LocalDate? = withContext(Dispatchers.IO) {
        repo.findById(ContractTalksId(playerId.value, clubId.value)).orElse(null)?.cooldownUntil
    }

    /** `save` com a mesma chave faz UPDATE: a conversa nova substitui a antiga. */
    override suspend fun startCooldown(playerId: PlayerId, clubId: ClubId, until: LocalDate) {
        withContext(Dispatchers.IO) {
            repo.save(ContractTalksEntity(ContractTalksId(playerId.value, clubId.value), until))
        }
    }
}
