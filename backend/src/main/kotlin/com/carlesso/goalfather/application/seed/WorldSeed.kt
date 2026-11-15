package com.carlesso.goalfather.application.seed

import com.carlesso.goalfather.application.seed.dsl.playerNames
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.CompetitionKind
import com.carlesso.goalfather.domain.model.Country
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.SeasonSpan
import com.carlesso.goalfather.domain.model.world.ClubProfile
import com.carlesso.goalfather.domain.model.world.CompetitionOwner
import com.carlesso.goalfather.domain.model.world.LeagueStrength
import com.carlesso.goalfather.domain.model.world.PlayerProfile
import com.carlesso.goalfather.domain.model.world.Reputation
import com.carlesso.goalfather.domain.model.world.WorldCompetition
import com.carlesso.goalfather.domain.model.world.WorldPack
import com.carlesso.goalfather.domain.rules.AttributeGenerator
import com.carlesso.goalfather.domain.rules.PlayerAttributes
import com.carlesso.goalfather.domain.rules.WorldStart
import com.carlesso.goalfather.domain.rules.initialCash
import com.carlesso.goalfather.domain.rules.initialSalary
import com.carlesso.goalfather.domain.rules.initialTicketPrice
import com.carlesso.goalfather.domain.rules.marketListingPrice
import com.carlesso.goalfather.domain.rules.startWorld
import java.time.LocalDate
import java.time.Month
import kotlin.random.Random
import kotlin.random.nextInt

/**
 * O mundo de um jogo NOVO (issue #131), montado a partir do pack: o que o
 * `SeedWriter` grava no banco vazio. Só dados de domínio — nada de JPA.
 *
 * [competitions] é o catálogo do save (as competições do MVP, na ordem do
 * pack); [countries] são todos os países do pack, porque nacionalidades de
 * jogadores citam países sem liga (Nigéria, Japão...).
 */
data class InitialWorld(
    val year: Int,
    val countries: List<Country>,
    val competitions: List<GameCompetition>,
    val clubs: List<Club>,
    val market: List<MarketEntry>,
) {
    /**
     * Primeiro encaixe, tabelas e copas da temporada [year]. Delega a
     * [startWorld] (o calendário), que é dono dessa decisão: o seed só monta
     * clubes e catálogo.
     */
    fun start(): WorldStart = startWorld(year, clubs, competitions)
}

/**
 * Função pura `pack → mundo inicial` (issue #131). Sem Spring, sem I/O: o
 * `DataInitializer` carrega o pack pelo `WorldDataProvider` e só grava o que
 * sai daqui — por isso dá para testar o mundo do MVP inteiro sem subir
 * contexto (`WorldSeedTest`).
 *
 * Regras (detalhes em `docs/WORLD_DATA.md`, seção "Seed"):
 * - **Competições:** as NACIONAIS do pack. Liga (pontos corridos com `tier`)
 *   vira [CompetitionKind.League]; mata-mata nacional vira
 *   [CompetitionKind.NationalCup]. Continentais (Libertadores) ficam fora do
 *   MVP (#132), e com elas os clubes que só aparecem nelas.
 * - **Ids:** clubes numerados `1..N` na ordem do pack (liga a liga); jogador =
 *   `clube × 1000 + posição no elenco` (a convenção do seed antigo, que o
 *   `youthPlayerId` evita); jogador livre do mercado = `1..999` (o "clube 0").
 *   Estáveis entre boots para o MESMO pack.
 * - **Atributos:** [AttributeGenerator] com um `Random` POR JOGADOR, de seed
 *   derivada do id do pack — o jogador não muda se outro entrar no elenco.
 */
fun WorldPack.toInitialWorld(year: Int = startYear()): InitialWorld {
    val referenceDate = LocalDate.of(year, Month.JULY, 1)
    val catalog = competitions.mapNotNull { it.toGameCompetition() }
    // As ligas do catálogo, ainda como `WorldCompetition`: são elas que sabem
    // os clubes (`clubIds`), a força e o tier.
    val leagues = competitions.filter { world -> catalog.any { it.id == world.id && it.kind is CompetitionKind.League } }

    // Um clube entra uma vez só, na primeira liga em que aparece. `distinctBy`
    // preserva a ordem de encontro — é ela que numera os clubes.
    val profiles: List<Pair<ClubProfile, WorldCompetition>> = leagues
        .flatMap { league -> league.clubIds.mapNotNull { id -> club(id)?.let { it to league } } }
        .distinctBy { (profile, _) -> profile.id }

    val clubs = profiles.mapIndexed { index, (profile, league) ->
        val clubId = ClubId(index + 1L)
        val strength = leagueStrengthOf(profile.id) ?: DEFAULT_LEAGUE_STRENGTH
        val squad = profile.squad.mapIndexed { slot, player ->
            player.toPlayer(
                id = PlayerId(clubId.value * PLAYER_ID_BLOCK + slot + 1),
                referenceDate = referenceDate,
                strength = strength,
                reputation = profile.reputation,
            )
        }
        Club(
            id = clubId,
            name = profile.name,
            cash = initialCash(profile.reputation, squad),
            stadiumCapacity = profile.stadium.capacity,
            squad = squad,
            // `requireNotNull`: liga do catálogo sempre tem tier (ver toGameCompetition).
            division = Division(requireNotNull(league.tier)),
            ticketPriceCents = initialTicketPrice(strength, profile.reputation),
            country = profile.country,
        )
    }

    return InitialWorld(
        year = year,
        countries = countries,
        competitions = catalog,
        clubs = clubs,
        market = freeAgents(catalog, leagues, takenNames = clubs.flatMapTo(HashSet()) { c -> c.squad.map { it.name } }),
    )
}

/**
 * O ano em que o mundo começa: o primeiro calendário do pack (o pack de 2026
 * traz `calendar/2026.json`). Sem calendário, o ano do MVP.
 */
fun WorldPack.startYear(): Int = calendars.minOfOrNull { it.year } ?: MVP_START_YEAR

/** Temporada em que o mundo do MVP nasce (Brasil 2026, Europa 2026/27). */
const val MVP_START_YEAR: Int = 2026

/** Bloco de ids de jogador por clube (`clube × 1000 + slot`). */
const val PLAYER_ID_BLOCK: Long = 1_000

/** Força de reserva para liga sem `strength` no pack (não acontece no pack do MVP). */
private val DEFAULT_LEAGUE_STRENGTH = LeagueStrength(60)

/**
 * A competição do pack como competição do save, ou `null` se ela fica fora
 * do MVP. `when` sobre o `sealed` [CompetitionOwner]: continental cai fora
 * explicitamente, e um terceiro tipo de dono obrigaria a decidir aqui.
 */
private fun WorldCompetition.toGameCompetition(): GameCompetition? = when (val owner = owner) {
    is CompetitionOwner.Continental -> null
    is CompetitionOwner.National -> {
        val kind = when {
            competition.format is CompetitionFormat.RoundRobin && tier != null -> CompetitionKind.League(Division(tier))
            competition.format is CompetitionFormat.Knockout -> CompetitionKind.NationalCup
            else -> null
        }
        kind?.let {
            GameCompetition(
                competition = competition,
                country = owner.country,
                kind = it,
                span = SeasonSpan.of(owner.country.confederation),
                pausesOnFifaWindows = pausesOnFifaWindows,
            )
        }
    }
}

/**
 * Fato real → jogador do jogo. O `Random` nasce do id do pack: `String.hashCode`
 * é definido pela especificação do Java (não muda entre JVMs nem boots), então
 * o mesmo jogador tem sempre os mesmos atributos, seja qual for a ordem do
 * elenco. O `xor` com um sal separa estas seeds das de outros sorteios.
 */
private fun PlayerProfile.toPlayer(
    id: PlayerId,
    referenceDate: LocalDate,
    strength: LeagueStrength,
    reputation: Reputation,
): Player {
    // Idade fora da faixa do jogo (um garoto de 14 anos no pack) entra pela borda.
    val age = ageOn(referenceDate).coerceIn(Player.AGE_RANGE)
    val attributes = AttributeGenerator(Random(this.id.value.hashCode().toLong() xor ATTRIBUTE_SEED_SALT))
        .generate(position, age, strength, reputation)
    return attributes.toPlayer(id, shownName, position, age, nationality, secondNationality)
}

private fun PlayerAttributes.toPlayer(
    id: PlayerId,
    name: String,
    position: Position,
    age: Int,
    nationality: Country,
    secondNationality: Country?,
): Player = Player(
    id = id,
    name = name,
    position = position,
    overall = overall,
    pace = pace,
    shooting = shooting,
    passing = passing,
    defending = defending,
    salary = initialSalary(overall),
    age = age,
    nationality = nationality,
    secondNationality = secondNationality,
)

private const val ATTRIBUTE_SEED_SALT: Long = 0x5EED_0131L

/**
 * Mercado inicial (issue #131): [FREE_AGENT_POSITIONS] jogadores livres
 * GERADOS — o pack só tem jogadores com clube, e a tela de mercado não pode
 * nascer vazia. Nomes do gerador da #88 (brasileiros), nacionalidade do país
 * da primeira liga do catálogo (o Brasil no MVP), atributos do
 * [AttributeGenerator] na força dessa liga com reputação mediana, idade de 20
 * a 32 e preço de tabela ([marketListingPrice]). Seed fixa por vaga.
 */
private fun freeAgents(
    catalog: List<GameCompetition>,
    leagues: List<WorldCompetition>,
    takenNames: Set<String>,
): List<MarketEntry> {
    val home = leagues.firstOrNull() ?: return emptyList()
    val country = catalog.first { it.id == home.id }.country
    val strength = home.strength ?: DEFAULT_LEAGUE_STRENGTH
    val names = Random(MARKET_SEED).playerNames(takenNames).take(FREE_AGENT_POSITIONS.size).toList()
    return FREE_AGENT_POSITIONS.mapIndexed { index, position ->
        val slot = index + 1
        val random = Random(MARKET_SEED + slot)
        val age = random.nextInt(FREE_AGENT_AGES)
        val player = AttributeGenerator(random)
            .generate(position, age, strength, Reputation.AVERAGE)
            .toPlayer(PlayerId(slot.toLong()), names[index], position, age, country, secondNationality = null)
        MarketEntry(player = player, price = marketListingPrice(player))
    }
}

/** 16 livres: 2 goleiros, 5 zagueiros, 5 meias e 4 atacantes — dá para remendar qualquer setor. */
private val FREE_AGENT_POSITIONS: List<Position> =
    List(2) { Position.GK } + List(5) { Position.CB } + List(5) { Position.MF } + List(4) { Position.FW }

private val FREE_AGENT_AGES = 20..32

private const val MARKET_SEED: Long = 131
