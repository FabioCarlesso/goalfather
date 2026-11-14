package com.carlesso.goalfather.adapter.out.persistence.mapper

import com.carlesso.goalfather.adapter.out.persistence.entity.PlayerEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.PositionEnum
import com.carlesso.goalfather.domain.model.Availability
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.Suspension
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
        salary = Money(salary.toLong()),
        age = age,
        goals = goals,
        yellowCards = yellowCards,
        redCards = redCards,
        availability = if (injuredForRounds >= 1) Availability.Injured(injuredForRounds)
        else Availability.Available,
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
    salary = Math.toIntExact(salary.cents),
    age = age,
    goals = goals,
    yellowCards = yellowCards,
    redCards = redCards,
    // `when` exaustivo sobre o sealed: a soma de tipos vira o Int da coluna.
    injuredForRounds = when (val a = availability) {
        is Availability.Available -> 0
        is Availability.Injured -> a.roundsOut
    },
    nationality = nationality.code.value,
    secondNationality = secondNationality?.code?.value,
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
