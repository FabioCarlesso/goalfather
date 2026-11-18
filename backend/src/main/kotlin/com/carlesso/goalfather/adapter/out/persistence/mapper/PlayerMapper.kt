package com.carlesso.goalfather.adapter.out.persistence.mapper

import com.carlesso.goalfather.adapter.out.persistence.entity.PlayerEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.PositionEnum
import com.carlesso.goalfather.domain.model.Availability
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.Employment
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.Suspension
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

fun PlayerEntity.toDomain(): Player {
    // Decodifica o `discipline_json` UMA vez por jogador: a leitura de elenco
    // passa por aqui para cada jogador de cada clube.
    val discipline = discipline()
    return Player(
        id = PlayerId(id),
        name = name,
        position = position.toDomain(),
        overall = overall,
        pace = pace,
        shooting = shooting,
        passing = passing,
        defending = defending,
        stamina = stamina,
        employment = employment(),
        age = age,
        goals = goals,
        yellowCards = yellowCards,
        redCards = redCards,
        availability = availability(),
        nationality = nationality.toCountry(),
        // `takeIf` descarta a segunda nacionalidade se ela coincidir com a primeira
        // depois de resolvidas (ex.: dois códigos desconhecidos caindo no default),
        // em vez de deixar o `require` do `Player` derrubar a leitura.
        secondNationality = secondNationality.toCountryOrNull()?.takeIf { it != nationality.toCountry() },
        pendingYellows = discipline.pendingYellows
            .filterValues { it >= 1 }
            .mapKeys { (id, _) -> CompetitionId(id) },
        // `distinctBy` acomoda uma linha corrompida com dois ganchos na mesma
        // competição em vez de deixar o `require` do `Player` derrubar a leitura.
        suspensions = discipline.suspensions
            .filter { it.matchesLeft >= 1 }
            .distinctBy { it.competitionId }
            .map { Suspension(CompetitionId(it.competitionId), it.matchesLeft) },
    )
}

fun Player.toEntity(clubId: Long? = null): PlayerEntity = PlayerEntity(
    id = id.value,
    clubId = clubId,
    name = name,
    position = position.toEntity(),
    overall = overall,
    pace = pace,
    shooting = shooting,
    passing = passing,
    defending = defending,
    stamina = stamina,
    age = age,
    goals = goals,
    yellowCards = yellowCards,
    redCards = redCards,
    // `when` exaustivo sobre o sealed: a soma de tipos vira o Int da coluna.
    injuredForRounds = when (val a = availability) {
        is Availability.Available, is Availability.OnInternationalDuty -> 0
        is Availability.Injured -> a.roundsOut
    },
    // Convocação (issue #134): `as?` devolve `null` para as outras variantes.
    dutyCountry = (availability as? Availability.OnInternationalDuty)?.country?.value,
    dutyUntil = (availability as? Availability.OnInternationalDuty)?.until,
    nationality = nationality.code.value,
    secondNationality = secondNationality?.code?.value,
    // Contrato (issue #136) só para quem vai gravado COM clube: sem `clubId` a
    // linha é de livre, e um contrato sem clube não volta do banco. O `clubId`
    // da linha é o do vínculo — é ele que o mapper de leitura devolve ao
    // `UnderContract`.
    salary = (contract?.takeIf { clubId != null }?.salary ?: Money.ZERO).cents.let(Math::toIntExact),
    contractStart = contract?.takeIf { clubId != null }?.start,
    contractEnd = contract?.takeIf { clubId != null }?.end,
    releaseClause = contract?.takeIf { clubId != null }?.releaseClause?.cents,
    disciplineJson = PlayerDisciplineRecord(
        pendingYellows = pendingYellows.mapKeys { (id, _) -> id.value },
        suspensions = suspensions.map { SuspensionRecord(it.competitionId.value, it.matchesLeft) },
    ).takeUnless { it.isClean }?.let { disciplineFormat.encodeToString(PlayerDisciplineRecord.serializer(), it) },
)

/**
 * Formato da coluna `discipline_json` (issue #130). Tipo PRÓPRIO da
 * persistência, e não o `Player` serializado: renomear um campo do domínio não
 * pode quebrar a leitura das linhas já gravadas.
 */
@Serializable
private data class PlayerDisciplineRecord(
    val pendingYellows: Map<String, Int> = emptyMap(),
    val suspensions: List<SuspensionRecord> = emptyList(),
) {
    val isClean: Boolean get() = pendingYellows.isEmpty() && suspensions.isEmpty()
}

@Serializable
private data class SuspensionRecord(val competitionId: String, val matchesLeft: Int)

private val disciplineFormat = Json { ignoreUnknownKeys = true }

/**
 * Colunas → `sealed Availability`. A convocação (issue #134) vem primeiro: as
 * duas colunas dela preenchidas valem mais que uma lesão (que o domínio nunca
 * grava junto). Código de país inválido numa linha corrompida cai em "apto",
 * em vez de derrubar a leitura do elenco.
 */
private fun PlayerEntity.availability(): Availability {
    val country = dutyCountry?.takeIf { it.length == 3 && it.all { c -> c in 'A'..'Z' } }
    val until = dutyUntil
    return when {
        country != null && until != null -> Availability.OnInternationalDuty(CountryCode(country), until)
        injuredForRounds >= 1 -> Availability.Injured(injuredForRounds)
        else -> Availability.Available
    }
}

/**
 * Colunas → `sealed Employment` (issue #136). Sem clube = livre, sejam quais
 * forem as outras colunas. Com clube, o contrato da linha; uma linha com clube
 * e sem datas (só escrita fora do mapper — a V23 preencheu as antigas) ganha
 * [LEGACY_CONTRACT_END] em vez de derrubar a leitura do elenco, mesma postura
 * da disciplina e da convocação acima. Cláusula não positiva é descartada
 * pelo mesmo motivo (o `init` do contrato a recusaria).
 */
private fun PlayerEntity.employment(): Employment {
    val club = clubId ?: return Employment.FreeAgent
    val start = contractStart ?: LEGACY_CONTRACT_START
    val end = contractEnd?.takeIf { it.isAfter(start) } ?: maxOf(LEGACY_CONTRACT_END, start.plusYears(1))
    return Employment.UnderContract(
        clubId = ClubId(club),
        salary = Money(salary.toLong().coerceAtLeast(0)),
        start = start,
        end = end,
        releaseClause = releaseClause?.takeIf { it > 0 }?.let(::Money),
    )
}

private val LEGACY_CONTRACT_START: LocalDate = LocalDate.of(2026, 1, 1)
private val LEGACY_CONTRACT_END: LocalDate = LocalDate.of(2028, 12, 31)

/** Coluna nula (ficha limpa ou linha anterior à issue) → registro vazio. */
private fun PlayerEntity.discipline(): PlayerDisciplineRecord =
    disciplineJson?.let { disciplineFormat.decodeFromString(PlayerDisciplineRecord.serializer(), it) }
        ?: PlayerDisciplineRecord()

private fun PositionEnum.toDomain(): Position = when (this) {
    PositionEnum.GK -> Position.GK
    PositionEnum.CB -> Position.CB
    PositionEnum.MF -> Position.MF
    PositionEnum.FW -> Position.FW
}

private fun Position.toEntity(): PositionEnum = when (this) {
    Position.GK -> PositionEnum.GK
    Position.CB -> PositionEnum.CB
    Position.MF -> PositionEnum.MF
    Position.FW -> PositionEnum.FW
}
