package com.carlesso.goalfather.adapter.out.persistence.mapper

import com.carlesso.goalfather.adapter.out.persistence.entity.ClubEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.PlayerEntity
import com.carlesso.goalfather.domain.model.BoardExpectation
import com.carlesso.goalfather.domain.model.BoardObjective
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.TrainingFocus
import com.carlesso.goalfather.domain.rules.TICKET_PRICE_RANGE
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
    // Mesma postura do foco acima: a coluna é dado externo, não invariante
    // nosso. Um preço fora da faixa (linha antiga, faixa reapertada depois)
    // entra pela borda mais próxima em vez de derrubar a leitura do clube —
    // e a próxima gravação já normaliza a linha.
    ticketPriceCents = ticketPriceCents.coerceIn(TICKET_PRICE_RANGE),
    board = boardExpectation(),
    // A versão LIDA sobe para o domínio (issue #89) e volta no `save`, que a
    // compara com a do banco — sem isso o lock otimista não protege nada.
    version = version,
)

/**
 * Meta da diretoria (issue #62) a partir das quatro colunas. Qualquer uma nula
 * — save anterior à issue — dá `null`: meta pela metade não existe. Mesma
 * postura defensiva do foco e do preço: objetivo desconhecido ou confiança fora
 * da escala (dado externo) não derrubam a leitura do clube; o objetivo inválido
 * descarta a meta, que a próxima rodada fixa de novo, e a confiança é trazida
 * para a escala.
 */
private fun ClubEntity.boardExpectation(): BoardExpectation? {
    val season = boardSeason ?: return null
    val objective = BoardObjective.entries.find { it.name == boardObjective } ?: return null
    val target = boardTargetPosition?.takeIf { it >= 1 } ?: return null
    val confidence = boardConfidence ?: return null
    return BoardExpectation(
        season = season,
        objective = objective,
        targetPosition = target,
        confidence = confidence.coerceIn(BoardExpectation.CONFIDENCE_RANGE),
    )
}

/** Copia a meta da diretoria para as colunas — `null` limpa as quatro. */
fun ClubEntity.applyBoard(board: BoardExpectation?) {
    boardSeason = board?.season
    boardObjective = board?.objective?.name
    boardTargetPosition = board?.targetPosition
    boardConfidence = board?.confidence
}

// NOTA: NÃO existe `Club.toEntity` de propósito. Persistir um clube deve
// CARREGAR a entidade gerenciada e copiar os campos (ver ClubPersistenceAdapter):
// mapear para uma entidade nova (version=0) causava StaleObjectStateException
// após o claim (issue #19). A checagem de versão é feita explicitamente lá,
// contra `Club.version` (issue #89).
