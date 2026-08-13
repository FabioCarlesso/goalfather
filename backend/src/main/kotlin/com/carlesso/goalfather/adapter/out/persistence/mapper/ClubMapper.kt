package com.carlesso.goalfather.adapter.out.persistence.mapper

import com.carlesso.goalfather.adapter.out.persistence.entity.ClubEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.PlayerEntity
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.TrainingFocus
import kotlinx.serialization.json.Json

fun ClubEntity.toDomain(squadEntities: List<PlayerEntity>, json: Json): Club = Club(
    id = ClubId(id),
    name = name,
    cash = cash,
    stadiumCapacity = stadiumCapacity,
    squad = squadEntities.map { it.toDomain() },
    lineup = lineupJson?.let { json.decodeFromString(Lineup.serializer(), it) },
    ownerId = ownerId,
    division = Division(division),
    // `entries.find` em vez de `valueOf`: texto fora do enum (linha antiga,
    // foco renomeado) cai no default em vez de estourar exception na leitura
    // do clube — a coluna é dado externo, não um invariante nosso.
    trainingFocus = TrainingFocus.entries.find { it.name == trainingFocus } ?: TrainingFocus.DEFAULT,
)

// NOTA: NÃO existe `Club.toEntity` de propósito. Persistir um clube deve
// CARREGAR a entidade gerenciada e copiar os campos (ver ClubPersistenceAdapter),
// preservando o @Version do lock otimista. Mapear para uma entidade nova
// (version=0) causa StaleObjectStateException após o claim (issue #19).
