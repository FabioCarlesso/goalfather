package com.carlesso.goalfather.domain.model

/**
 * Identificador tipado de competição (issue #124). `String` e não `Long`
 * porque as competições vêm do pack de dados do mundo (issue #126) com ids
 * legíveis e estáveis entre versões do pack — `"bra-serie-a"`, `"conmebol-libertadores"` —,
 * e não de uma sequence do banco.
 */
@JvmInline
value class CompetitionId(val value: String) {
    init {
        require(value.isNotBlank()) { "CompetitionId não pode ser vazio" }
    }
}

/**
 * Uma competição descrita por DADOS (issue #124): formato + critérios de
 * desempate. Geração de jogos, classificação e avanço de fase saem de funções
 * puras sobre esses dados (`domain/rules/CompetitionScheduling.kt`,
 * `domain/rules/TiebreakRules.kt`) — trocar o Brasileirão pela Premier League
 * é trocar o `Competition`, não escrever código novo.
 *
 * O dono da competição (país ou confederação) entra quando o modelo de país
 * da issue #123 e o calendário da #125 estiverem no lugar; aqui só o que o
 * formato precisa.
 */
data class Competition(
    val id: CompetitionId,
    val name: String,
    val format: CompetitionFormat,
    val tiebreakers: List<Tiebreaker> = DEFAULT_TIEBREAKERS,
) {
    init {
        require(name.isNotBlank()) { "Competição sem nome: ${id.value}" }
        require(tiebreakers.firstOrNull() == Tiebreaker.POINTS) {
            "O primeiro critério de uma tabela é sempre POINTS: $tiebreakers"
        }
        require(tiebreakers.distinct().size == tiebreakers.size) { "Critério de desempate repetido: $tiebreakers" }
    }

    companion object {
        /** Pontos → saldo → gols pró: o critério que a liga do jogo sempre usou. */
        val DEFAULT_TIEBREAKERS: List<Tiebreaker> =
            listOf(Tiebreaker.POINTS, Tiebreaker.GOAL_DIFFERENCE, Tiebreaker.GOALS_FOR)
    }
}

/**
 * Critério de classificação de uma tabela, na ordem em que a competição os
 * aplica. Brasileirão: POINTS, WINS, GOAL_DIFFERENCE, GOALS_FOR, HEAD_TO_HEAD,
 * FEWER_RED_CARDS, FEWER_YELLOW_CARDS, DRAWING_OF_LOTS. Premier League: saldo
 * logo depois dos pontos. La Liga e Serie A: confronto direto primeiro.
 *
 * `enum` e não `sealed`: nenhum critério carrega dado próprio — o que varia é
 * só a ORDEM, que mora na lista de [Competition.tiebreakers].
 */
enum class Tiebreaker {
    POINTS,
    WINS,
    GOAL_DIFFERENCE,
    GOALS_FOR,

    /** Pontos no mini-campeonato só entre os clubes empatados. */
    HEAD_TO_HEAD,

    /** Saldo de gols no mini-campeonato entre os empatados (La Liga). */
    HEAD_TO_HEAD_GOAL_DIFFERENCE,
    FEWER_RED_CARDS,
    FEWER_YELLOW_CARDS,

    /**
     * Sorteio. Nome longo de propósito: `DRAW` em futebol é "empate", e um
     * critério de desempate chamado "empate" confundiria qualquer leitor.
     */
    DRAWING_OF_LOTS,
}

/** Uma partida ainda não jogada: quem recebe e quem visita. */
data class Pairing(val home: ClubId, val away: ClubId) {
    init {
        require(home != away) { "Clube não enfrenta a si mesmo: ${home.value}" }
    }

    /** O jogo de volta: os mesmos clubes com o mando invertido. */
    fun reversed(): Pairing = Pairing(home = away, away = home)
}

/** Uma partida jogada, como as regras de tabela a enxergam (placar no tempo normal). */
data class PlayedMatch(
    val home: ClubId,
    val away: ClubId,
    val homeGoals: Int,
    val awayGoals: Int,
) {
    init {
        require(homeGoals >= 0 && awayGoals >= 0) { "Placar negativo: $homeGoals × $awayGoals" }
    }
}

/** Cartões de um clube na competição — insumo dos critérios FEWER_*_CARDS. */
data class Discipline(val yellowCards: Int = 0, val redCards: Int = 0)

/** Jogo único ou ida e volta. */
enum class Legs(val matches: Int) {
    SINGLE(1),
    TWO_LEGGED(2),
}

/**
 * Uma fase de mata-mata no formato genérico. Diferente de [KnockoutPhase] (as
 * fases fixas 32-avos → final da copa do jogo, com prêmio), aqui a fase é DADO:
 * a Copa do Brasil 2026 tem 126 clubes e fases que nenhum enum de 5 valores
 * descreveria.
 *
 * [newEntrants] é quantos clubes ENTRAM nesta fase. Na primeira, todos; nas
 * seguintes, os cabeças de chave que pulam fases (a Série A só entra na 5ª fase
 * da Copa do Brasil) — e é também como se descrevem as folgas da copa atual.
 */
data class KnockoutStage(
    val name: String,
    val teams: Int,
    val legs: Legs = Legs.SINGLE,
    val newEntrants: Int = 0,
) {
    init {
        require(teams >= 2 && teams % 2 == 0) { "Fase $name precisa de nº par de clubes >= 2: $teams" }
        require(newEntrants in 0..teams) { "Fase $name: entrantes fora de 0..$teams: $newEntrants" }
    }

    val ties: Int get() = teams / 2
}

/**
 * Formato de uma competição (issue #124). `sealed` porque cada formato carrega
 * dados diferentes e TODO consumidor (gerador de jogos, tabela, avanço de fase,
 * calendário) precisa decidir o que fazer com cada um: um `when` exaustivo sem
 * `else` faz o compilador apontar cada lugar quando um quinto formato aparecer.
 */
sealed interface CompetitionFormat {

    /**
     * Pontos corridos. `legs = 1` é turno único (a liga atual do jogo);
     * `legs = 2` é turno e returno (Brasileirão, ligas europeias).
     */
    data class RoundRobin(val legs: Int = 2) : CompetitionFormat {
        init {
            require(legs >= 1) { "Pontos corridos precisa de ao menos 1 turno: $legs" }
        }
    }

    /**
     * Mata-mata em fases. Cada fase recebe os vencedores da anterior mais os
     * [KnockoutStage.newEntrants] dela — a conta tem que fechar fase a fase até
     * uma final de 2 clubes, senão a chave seria impossível de jogar.
     */
    data class Knockout(val stages: List<KnockoutStage>) : CompetitionFormat {
        init {
            require(stages.isNotEmpty()) { "Mata-mata sem fases" }
            require(stages.first().newEntrants == stages.first().teams) {
                "Na 1ª fase todos são entrantes: ${stages.first()}"
            }
            // `zipWithNext` (stdlib) percorre pares consecutivos sem índice manual.
            stages.zipWithNext().forEach { (previous, stage) ->
                require(stage.teams == previous.ties + stage.newEntrants) {
                    "Fase ${stage.name}: ${previous.ties} vencedores + ${stage.newEntrants} entrantes ≠ ${stage.teams}"
                }
            }
            require(stages.last().teams == 2) { "A última fase é a final (2 clubes): ${stages.last()}" }
        }

        /** Quantos clubes disputam o mata-mata inteiro. */
        val entrants: Int get() = stages.sumOf { it.newEntrants }
    }

    /**
     * Fase de grupos + mata-mata (Libertadores: 8 grupos de 4, ida e volta,
     * 2 classificados por grupo). Os classificados formam a 1ª fase do
     * [knockout], então ela precisa ter exatamente `groups × qualifiersPerGroup`
     * clubes.
     */
    data class GroupsThenKnockout(
        val groups: Int,
        val perGroup: Int,
        val qualifiersPerGroup: Int,
        val knockout: Knockout,
        val groupLegs: Int = 2,
    ) : CompetitionFormat {
        init {
            require(groups >= 1) { "Ao menos 1 grupo: $groups" }
            require(perGroup >= 2) { "Grupo precisa de ao menos 2 clubes: $perGroup" }
            require(qualifiersPerGroup in 1..perGroup) { "Classificados fora de 1..$perGroup: $qualifiersPerGroup" }
            require(groupLegs >= 1) { "Grupo precisa de ao menos 1 turno: $groupLegs" }
            require(knockout.stages.first().teams == groups * qualifiersPerGroup) {
                "1ª fase do mata-mata (${knockout.stages.first().teams}) ≠ $groups grupos × $qualifiersPerGroup"
            }
        }

        val teams: Int get() = groups * perGroup
    }

    /**
     * Fase de liga no modelo suíço (Champions desde 2024/25): todos numa tabela
     * só, cada clube enfrenta [matchesPerTeam] adversários DIFERENTES, metade em
     * casa. Do 1º ao [directQualifiers] vão direto ao mata-mata; quem cai em
     * [playoffRange] joga o playoff; o resto está fora.
     */
    data class SwissLeague(
        val teams: Int,
        val matchesPerTeam: Int,
        val directQualifiers: Int,
        val playoffRange: IntRange,
    ) : CompetitionFormat {
        init {
            require(teams >= 4 && teams % 2 == 0) { "Fase de liga precisa de nº par de clubes >= 4: $teams" }
            require(matchesPerTeam >= 2 && matchesPerTeam % 2 == 0) {
                "Jogos por clube deve ser par (metade em casa): $matchesPerTeam"
            }
            require(matchesPerTeam < teams) { "Mais jogos ($matchesPerTeam) que adversários (${teams - 1})" }
            require(directQualifiers in 0..teams) { "Classificados diretos fora de 0..$teams: $directQualifiers" }
            require(playoffRange.isEmpty() || (playoffRange.first > directQualifiers && playoffRange.last <= teams)) {
                "Playoff $playoffRange precisa vir depois dos $directQualifiers diretos e caber em $teams"
            }
        }
    }
}

/**
 * Onde uma posição da fase de liga suíça leva o clube. Três destinos fechados —
 * `enum` basta, nenhum carrega dado.
 */
enum class SwissPlacement { DIRECT_TO_KNOCKOUT, PLAYOFF, ELIMINATED }

/** Placar de um jogo, na orientação mandante × visitante daquele jogo. */
data class LegScore(val homeGoals: Int, val awayGoals: Int) {
    init {
        require(homeGoals >= 0 && awayGoals >= 0) { "Placar negativo: $homeGoals × $awayGoals" }
    }
}

/**
 * Confronto de ida e volta decidido (issue #124). [first] recebe a ida e
 * [second] recebe a volta; os placares estão na orientação de CADA jogo, como
 * aparecem na súmula.
 *
 * Mesmo raciocínio do [TieResult] de jogo único: o `init` torna irrepresentável
 * um confronto sem classificado. Agregado empatado exige [penalties] (cobrados
 * ao fim da volta, então na orientação da volta: mandante = [second]); agregado
 * decidido não admite pênaltis. Não existe gol fora de casa — a regra foi
 * abolida pela UEFA em 2021 e pela CONMEBOL em 2022.
 */
data class TwoLeggedTieResult(
    val first: ClubId,
    val second: ClubId,
    val firstLeg: LegScore,
    val secondLeg: LegScore,
    val penalties: PenaltyScore? = null,
) {
    init {
        require(first != second) { "Clube não enfrenta a si mesmo: ${first.value}" }
        if (aggregateFirst == aggregateSecond) {
            requireNotNull(penalties) { "Agregado empatado ($aggregateFirst × $aggregateSecond) exige pênaltis" }
            require(penalties.home != penalties.away) { "Pênaltis não podem terminar empatados" }
        } else {
            require(penalties == null) { "Pênaltis só com agregado empatado: $aggregateFirst × $aggregateSecond" }
        }
    }

    /** Gols de [first] somando os dois jogos (mandante na ida, visitante na volta). */
    val aggregateFirst: Int get() = firstLeg.homeGoals + secondLeg.awayGoals

    /** Gols de [second] somando os dois jogos. */
    val aggregateSecond: Int get() = firstLeg.awayGoals + secondLeg.homeGoals

    val winner: ClubId
        get() = when {
            aggregateFirst > aggregateSecond -> first
            aggregateSecond > aggregateFirst -> second
            // `!!` evitado: o `init` já garantiu pênaltis aqui; `requireNotNull` documenta.
            else -> if (requireNotNull(penalties).home > penalties.away) second else first
        }
}
