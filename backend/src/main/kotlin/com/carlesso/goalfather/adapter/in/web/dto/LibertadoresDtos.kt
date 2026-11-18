package com.carlesso.goalfather.adapter.`in`.web.dto

import com.carlesso.goalfather.application.port.`in`.ContinentalEditionView
import com.carlesso.goalfather.domain.rules.CONTINENTAL_PRIZE_FACTOR
import com.carlesso.goalfather.domain.rules.fixturesOf
import kotlinx.serialization.Serializable

/*
 * DTOs da Libertadores (issue #132) — `GET /api/libertadores`. A regra de
 * `Responses.kt` vale aqui: nada de domínio cruza o controller, e o DTO só
 * MATERIALIZA o que a UI precisaria recalcular (tabela ordenada, faixa de
 * classificação, jogos de cada grupo). O mata-mata reusa o `CupDto` da Copa do
 * Brasil: a chave é a mesma estrutura, e a UI da chave serve às duas.
 */

/** Um jogo de grupo: a rodada do grupo e, se já disputado, o placar. */
@Serializable
data class GroupFixtureDto(
    val round: Int,
    val homeClubId: Long,
    val homeClubName: String,
    val awayClubId: Long,
    val awayClubName: String,
    /** Ausente enquanto o jogo não foi disputado. */
    val homeGoals: Int? = null,
    val awayGoals: Int? = null,
)

@Serializable
data class ContinentalGroupDto(
    val name: String,
    /** Tabela do grupo, ordenada pelos desempates da competição; os classificados com `zone = QUALIFICATION`. */
    val rows: List<StandingRowDto>,
    /** Os jogos do grupo em ordem de rodada (turno e returno). */
    val fixtures: List<GroupFixtureDto>,
)

@Serializable
data class VenueDto(val name: String, val capacity: Int)

@Serializable
data class LibertadoresDto(
    val season: Int,
    val competitionId: String,
    val competitionName: String,
    /** Quantos de cada grupo vão ao mata-mata (2 na Libertadores). */
    val qualifiersPerGroup: Int,
    val groups: List<ContinentalGroupDto>,
    /** O mata-mata (mesma forma da Copa do Brasil); ausente enquanto os grupos não acabam. */
    val knockout: CupDto? = null,
    /** Sede neutra da final única; ausente se o pack não tiver estádio. */
    val finalVenue: VenueDto? = null,
    /** Vencedor da final; ausente enquanto ela não foi disputada. */
    val champion: CupClubDto? = null,
)

fun ContinentalEditionView.toDto(): LibertadoresDto {
    val names = edition.clubs.associate { it.id to it.name }
    val results = edition.results.associateBy { it.home to it.away }
    return LibertadoresDto(
        season = edition.season,
        competitionId = edition.competitionId.value,
        competitionName = edition.competitionName,
        qualifiersPerGroup = edition.qualifiersPerGroup,
        groups = edition.groups.map { group ->
            ContinentalGroupDto(
                name = group.name,
                rows = tables[group.name].orEmpty().map { it.toDto() },
                // `flatMapIndexed`: o índice da lista de rodadas vira o número da rodada.
                fixtures = edition.fixturesOf(group).flatMapIndexed { index, round ->
                    round.map { pairing ->
                        val result = results[pairing.home to pairing.away]
                        GroupFixtureDto(
                            round = index + 1,
                            homeClubId = pairing.home.value,
                            homeClubName = names.getValue(pairing.home),
                            awayClubId = pairing.away.value,
                            awayClubName = names.getValue(pairing.away),
                            homeGoals = result?.homeGoals,
                            awayGoals = result?.awayGoals,
                        )
                    }
                },
            )
        },
        // O prêmio da fase é o da copa × o fator da continental — o DTO da copa
        // calcula o da copa, e aqui só se corrige a escala.
        knockout = edition.knockout?.toDto()?.let { cup ->
            cup.copy(phases = cup.phases.map { it.copy(prizeCents = it.prizeCents * CONTINENTAL_PRIZE_FACTOR) })
        },
        finalVenue = edition.finalVenue?.let { VenueDto(it.name, it.capacity) },
        champion = edition.champion?.toDto(),
    )
}
