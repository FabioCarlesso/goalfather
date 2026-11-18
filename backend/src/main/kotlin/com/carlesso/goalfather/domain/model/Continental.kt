package com.carlesso.goalfather.domain.model

import com.carlesso.goalfather.domain.serialization.CompetitionIdSerializer
import kotlinx.serialization.Serializable

/**
 * Clube como a continental o enxerga (issue #132): id, nome e país. O país
 * entra porque é regra do sorteio (dois clubes do mesmo país não caem no mesmo
 * grupo) e da cota da edição seguinte (quantas vagas cada país tem). O nome é
 * COPIADO, como no `CupClub`: a edição vira história.
 */
@Serializable
data class ContinentalClub(val id: ClubId, val name: String, val country: CountryCode) {
    fun toCupClub(): CupClub = CupClub(id, name)
}

/** Um grupo sorteado: o nome (`"Grupo A"`) e os clubes na ordem dos potes. */
@Serializable
data class ContinentalGroup(val name: String, val clubs: List<ContinentalClub>) {
    init {
        require(name.isNotBlank()) { "Grupo sem nome" }
        require(clubs.size >= 2) { "Grupo $name com menos de 2 clubes" }
        require(clubs.distinctBy { it.id }.size == clubs.size) { "Clube repetido no $name" }
    }
}

/** Um jogo de grupo já disputado: a rodada do grupo e o placar, na orientação mandante × visitante. */
@Serializable
data class GroupResult(
    val group: String,
    val round: Int,
    val home: ClubId,
    val away: ClubId,
    val homeGoals: Int,
    val awayGoals: Int,
) {
    init {
        require(round >= 1) { "Rodada de grupo < 1: $round" }
        require(homeGoals >= 0 && awayGoals >= 0) { "Placar negativo: $homeGoals × $awayGoals" }
    }
}

/** Sede neutra da final única (issue #132): um estádio real do pack. */
@Serializable
data class Venue(val name: String, val capacity: Int) {
    init {
        require(name.isNotBlank()) { "Sede sem nome" }
        require(capacity > 0) { "Sede sem capacidade: $capacity" }
    }
}

/**
 * Uma edição de competição continental (issue #132): a fase de grupos e, depois
 * dela, o mata-mata — o formato `GroupsThenKnockout` da Libertadores.
 *
 * - [groups]: o sorteio, fixado quando a edição nasce; os jogos de cada grupo
 *   são o turno e returno dele (`roundRobinSchedule`), então não precisam ser
 *   guardados — só os [results].
 * - [knockoutPlan]: as fases do mata-mata do formato, ainda SEM entrantes. Os
 *   classificados só se conhecem quando os grupos acabam.
 * - [knockout]: o mata-mata, reaproveitando a `Cup` da Copa do Brasil (ida e
 *   volta, pênaltis, sorteio fase a fase). `null` enquanto os grupos não
 *   terminam — ausência real, modelada no tipo.
 * - [finalVenue]: a sede da final única. `null` = a final é no estádio do
 *   mandante sorteado (pack sem estádio).
 * - [drawSeed]: semente dos sorteios futuros (oitavas e fases seguintes), como
 *   em `Cup.drawSeed`: a edição inteira é função da seed da temporada.
 *
 * Imutável como o resto do domínio: cada encaixe produz uma cópia (`copy()`).
 */
@Serializable
data class ContinentalCup(
    val season: Int,
    @Serializable(with = CompetitionIdSerializer::class)
    val competitionId: CompetitionId,
    val competitionName: String,
    val groups: List<ContinentalGroup>,
    val groupLegs: Int,
    val qualifiersPerGroup: Int,
    val knockoutPlan: List<CupPhase>,
    val drawSeed: Long,
    val results: List<GroupResult> = emptyList(),
    val knockout: Cup? = null,
    val finalVenue: Venue? = null,
) {
    init {
        require(competitionName.isNotBlank()) { "Continental sem nome: ${competitionId.value}" }
        require(groups.isNotEmpty()) { "Continental sem grupos" }
        require(groupLegs >= 1) { "Grupo precisa de ao menos 1 turno: $groupLegs" }
        require(knockoutPlan.isNotEmpty()) { "Continental sem mata-mata" }
        require(knockoutPlan.all { it.entrants.isEmpty() }) { "O plano do mata-mata não tem entrantes antes dos grupos" }
        require(knockoutPlan.first().teams == groups.size * qualifiersPerGroup) {
            "1ª fase do mata-mata (${knockoutPlan.first().teams}) ≠ ${groups.size} grupos × $qualifiersPerGroup"
        }
        val everyone = groups.flatMap { it.clubs }
        require(everyone.distinctBy { it.id }.size == everyone.size) { "Clube em dois grupos" }
        require(knockout == null || knockout.competitionId == competitionId) { "Mata-mata de outra competição" }
    }

    /** Todos os participantes, grupo a grupo. */
    val clubs: List<ContinentalClub> get() = groups.flatMap { it.clubs }

    /** Vagas de cada país nesta edição — a cota que a edição seguinte repete. */
    val quotas: Map<CountryCode, Int> get() = clubs.groupingBy { it.country }.eachCount()

    /** Campeão: o vencedor da final; `null` enquanto ela não foi disputada. */
    val champion: CupClub? get() = knockout?.champion
}
