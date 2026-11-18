package com.carlesso.goalfather.domain.model

import com.carlesso.goalfather.domain.serialization.LocalDateSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import java.time.LocalDate

/**
 * Seleções nas datas FIFA (issue #134). As regras (convocação, jogos,
 * Eliminatórias, ranking) moram em `domain/rules/NationalTeamRules.kt`; aqui
 * ficam só os dados que viajam entre elas e a persistência.
 */

/**
 * Um convocado como a seleção o registra NA HORA da convocação: nome, posição,
 * overall e clube viram foto, para a lista continuar legível mesmo que o
 * jogador mude de clube ou se aposente depois.
 */
@Serializable
data class CalledUpPlayer(
    val playerId: PlayerId,
    val name: String,
    val position: Position,
    val overall: Int,
    val clubId: ClubId,
    val clubName: String,
)

/** A lista de uma seleção numa janela. Vazia para quem joga só com reforços estimados. */
@Serializable
data class NationalSquad(
    val country: CountryCode,
    val players: List<CalledUpPlayer>,
)

/** Jogo de seleção: Eliminatórias (CONMEBOL) ou amistoso. */
@Serializable
enum class NationalMatchKind { QUALIFIER, FRIENDLY }

/**
 * Um jogo de seleção já disputado. [matchNumber] é a ordem dentro da janela
 * (1..4); [qualifierRound] só existe nas Eliminatórias (1..18).
 */
@Serializable
data class NationalMatch(
    val home: CountryCode,
    val away: CountryCode,
    val kind: NationalMatchKind,
    val matchNumber: Int,
    val homeGoals: Int,
    val awayGoals: Int,
    val qualifierRound: Int? = null,
) {
    init {
        require(home != away) { "Seleção não joga contra si mesma: $home" }
        require(homeGoals >= 0 && awayGoals >= 0) { "Placar negativo: $homeGoals x $awayGoals" }
        require((kind == NationalMatchKind.QUALIFIER) == (qualifierRound != null)) {
            "Só jogo de Eliminatórias tem rodada: $kind, $qualifierRound"
        }
    }
}

/**
 * Uma data FIFA do ponto de vista das seleções — os estados possíveis dela no
 * save. `sealed` porque o resultado só existe depois de jogada: "convocada com
 * resultados" ou "jogada sem resultados" não são representáveis.
 *
 * Janela futura (ainda sem convocação) não é gravada: ela é só uma data do
 * calendário, e a prévia da lista sai da regra pura, sem estado.
 *
 * As datas ficam em [start]/[end] (e não um `FifaWindow`) porque o tipo
 * compartilhado do calendário não é `@Serializable`; [window] o remonta.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface NationalWindow {
    val start: LocalDate
    val end: LocalDate
    val squads: List<NationalSquad>

    val window: FifaWindow get() = FifaWindow(start, end)

    /** Convocações feitas: os jogadores estão com a seleção, os jogos ainda não aconteceram. */
    @Serializable
    @SerialName("Called")
    data class Called(
        @Serializable(with = LocalDateSerializer::class) override val start: LocalDate,
        @Serializable(with = LocalDateSerializer::class) override val end: LocalDate,
        override val squads: List<NationalSquad>,
    ) : NationalWindow {
        /** Fecha a janela com os [matches] disputados. */
        fun played(matches: List<NationalMatch>): Played = Played(start, end, squads, matches)
    }

    /** Janela encerrada: jogos disputados e convocados de volta aos clubes. */
    @Serializable
    @SerialName("Played")
    data class Played(
        @Serializable(with = LocalDateSerializer::class) override val start: LocalDate,
        @Serializable(with = LocalDateSerializer::class) override val end: LocalDate,
        override val squads: List<NationalSquad>,
        val matches: List<NationalMatch>,
    ) : NationalWindow

    companion object {
        fun called(window: FifaWindow, squads: List<NationalSquad>): Called = Called(window.start, window.end, squads)
    }
}
