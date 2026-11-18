package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.application.port.out.NationalWindowRepository
import com.carlesso.goalfather.domain.model.NationalWindow
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * Uma data FIFA já convocada (issue #134, V22). Separada do `NationalWindow` de
 * domínio — regra de ouro do CLAUDE.md. A janela inteira (listas e placares)
 * viaja em `window_json`, como a chave da copa em `cups.cup_json`: é lida e
 * gravada sempre inteira, nunca consultada por SQL.
 */
@Entity
@Table(name = "national_windows")
class NationalWindowEntity(
    @Id
    @Column(name = "start_date")
    var startDate: LocalDate = LocalDate.EPOCH,

    @Column(name = "end_date", nullable = false)
    var endDate: LocalDate = LocalDate.EPOCH,

    @Column(name = "status", nullable = false, length = 16)
    var status: String = "",

    @Column(name = "window_json", columnDefinition = "TEXT", nullable = false)
    var windowJson: String = "{}",
)

interface NationalWindowJpaRepository : JpaRepository<NationalWindowEntity, LocalDate>

@Repository
class NationalWindowPersistenceAdapter(
    private val repo: NationalWindowJpaRepository,
    private val json: Json,
) : NationalWindowRepository {

    override suspend fun findAll(): List<NationalWindow> = withContext(Dispatchers.IO) {
        repo.findAll().map { it.toDomain() }.sortedBy { it.start }
    }

    override suspend fun find(start: LocalDate): NationalWindow? = withContext(Dispatchers.IO) {
        repo.findById(start).orElse(null)?.toDomain()
    }

    override suspend fun save(window: NationalWindow) {
        withContext(Dispatchers.IO) {
            repo.save(
                NationalWindowEntity(
                    startDate = window.start,
                    endDate = window.end,
                    // `when` exaustivo: uma variante nova obriga a escolher o rótulo.
                    status = when (window) {
                        is NationalWindow.Called -> "CALLED"
                        is NationalWindow.Played -> "PLAYED"
                    },
                    windowJson = json.encodeToString(NationalWindow.serializer(), window),
                ),
            )
        }
    }

    private fun NationalWindowEntity.toDomain(): NationalWindow =
        json.decodeFromString(NationalWindow.serializer(), windowJson)
}
