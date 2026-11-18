package com.carlesso.goalfather.application.seed

import com.carlesso.goalfather.application.seed.dsl.playerNames
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.CompetitionKind
import com.carlesso.goalfather.domain.model.ContinentalCup
import com.carlesso.goalfather.domain.model.Country
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.SeasonSpan
import com.carlesso.goalfather.domain.model.Venue
import com.carlesso.goalfather.domain.model.world.WorldClubId
import com.carlesso.goalfather.domain.model.world.ClubProfile
import com.carlesso.goalfather.domain.model.world.CompetitionOwner
import com.carlesso.goalfather.domain.model.world.LeagueStrength
import com.carlesso.goalfather.domain.model.world.PlayerProfile
import com.carlesso.goalfather.domain.model.world.Reputation
import com.carlesso.goalfather.domain.model.world.WorldCompetition
import com.carlesso.goalfather.domain.model.world.WorldPack
import com.carlesso.goalfather.domain.rules.inStartingOrder
import com.carlesso.goalfather.domain.rules.AttributeGenerator
import com.carlesso.goalfather.domain.rules.ContinentalDraw
import com.carlesso.goalfather.domain.rules.continentalDrawSeed
import com.carlesso.goalfather.domain.rules.drawContinental
import com.carlesso.goalfather.domain.rules.PlayerAttributes
import com.carlesso.goalfather.domain.rules.WorldStart
import com.carlesso.goalfather.domain.rules.initialCash
import com.carlesso.goalfather.domain.rules.initialSalary
import com.carlesso.goalfather.domain.rules.initialTicketPrice
import com.carlesso.goalfather.domain.rules.marketListingPrice
import com.carlesso.goalfather.domain.rules.marketReferenceValue
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
    /** As continentais já sorteadas da temporada [year] (a Libertadores, issue #132). */
    val continentals: List<ContinentalCup> = emptyList(),
) {
    /**
     * Primeiro encaixe, tabelas e copas da temporada [year]. Delega a
     * [startWorld] (o calendário), que é dono dessa decisão: o seed só monta
     * clubes, catálogo e o sorteio da 1ª continental (que depende do pack).
     */
    fun start(): WorldStart = startWorld(year, clubs, competitions, continentals)
}

/**
 * Função pura `pack → mundo inicial` (issue #131). Sem Spring, sem I/O: o
 * `DataInitializer` carrega o pack pelo `WorldDataProvider` e só grava o que
 * sai daqui — por isso dá para testar o mundo do MVP inteiro sem subir
 * contexto (`WorldSeedTest`).
 *
 * Regras (detalhes em `docs/WORLD_DATA.md`, seção "Seed"):
 * - **Competições:** as do pack, na ordem dele. Liga (pontos corridos com
 *   `tier`) vira [CompetitionKind.League]; mata-mata nacional vira
 *   [CompetitionKind.NationalCup]; continental com grupos (Libertadores, issue
 *   #132) vira [CompetitionKind.Continental], hospedada pelo calendário do
 *   primeiro país da confederação com liga no catálogo (o Brasil).
 * - **Clubes só da continental** (Boca, Peñarol… — países sem liga simulada)
 *   entram DEPOIS dos clubes de liga, numerados em seguida. Divisão 1 (não
 *   disputam liga nenhuma: `leagueOf` devolve `null`) e a força da liga mais
 *   forte da confederação (`continentalOnlyStrength`).
 * - **Ids:** clubes numerados `1..N` na ordem do pack (liga a liga); jogador =
 *   `clube × 1000 + posição no elenco` (a convenção do seed antigo, que o
 *   `youthPlayerId` evita); jogador livre do mercado = `1..999` (o "clube 0").
 *   Estáveis entre boots para o MESMO pack.
 * - **Atributos:** [AttributeGenerator] com um `Random` POR JOGADOR, de seed
 *   derivada do id do pack — o jogador não muda se outro entrar no elenco.
 */
fun WorldPack.toInitialWorld(year: Int = startYear()): InitialWorld {
    val referenceDate = LocalDate.of(year, Month.JULY, 1)
    val national = competitions.mapNotNull { it.toGameCompetition() }
    // Continentais depois: precisam saber que país da confederação tem liga.
    // `associateWith` monta o mapa id → competição do save sem `for`.
    val continental = competitions.associateWith { it.toContinental(national) }.filterValues { it != null }
    // Mesma ordem do pack, com as continentais no lugar delas.
    val catalog = competitions.mapNotNull { world -> national.firstOrNull { it.id == world.id } ?: continental[world] }
    // As ligas do catálogo, ainda como `WorldCompetition`: são elas que sabem
    // os clubes (`clubIds`), a força e o tier.
    val leagues = competitions.filter { world -> catalog.any { it.id == world.id && it.kind is CompetitionKind.League } }

    // Um clube entra uma vez só, na primeira liga em que aparece. `distinctBy`
    // preserva a ordem de encontro — é ela que numera os clubes.
    val profiles: List<Pair<ClubProfile, WorldCompetition>> = leagues
        .flatMap { league -> league.clubIds.mapNotNull { id -> club(id)?.let { it to league } } }
        .distinctBy { (profile, _) -> profile.id }
    // Clubes que só jogam a continental (issue #132): depois dos de liga, na ordem do pack.
    val leagueClubIds = profiles.mapTo(HashSet()) { (profile, _) -> profile.id }
    val continentalOnly: List<Pair<ClubProfile, WorldCompetition>> = continental.keys
        .flatMap { cup -> cup.clubIds.filterNot { it in leagueClubIds }.mapNotNull { id -> club(id)?.let { it to cup } } }
        .distinctBy { (profile, _) -> profile.id }

    val clubs = (profiles + continentalOnly).mapIndexed { index, (profile, league) ->
        val clubId = ClubId(index + 1L)
        val strength = leagueStrengthOf(profile.id)
            ?: continentalOnlyStrength(profile)
            ?: DEFAULT_LEAGUE_STRENGTH
        // Os 11 primeiros = o melhor 4-4-2: é o time da escalação automática.
        // Os ids saem DEPOIS da ordem, porque o elenco é relido do banco pelo
        // id do jogador — numerar pela ordem do pack desfaria a ordenação.
        val squad = profile.squad
            // Id provisório pela ordem do pack: desempata a ordenação.
            .mapIndexed { slot, player ->
                player.toPlayer(
                    id = PlayerId(clubId.value * PLAYER_ID_BLOCK + slot + 1),
                    referenceDate = referenceDate,
                    strength = strength,
                    reputation = profile.reputation,
                )
            }
            .inStartingOrder()
            .mapIndexed { slot, player -> player.copy(id = PlayerId(clubId.value * PLAYER_ID_BLOCK + slot + 1)) }
        Club(
            id = clubId,
            name = profile.name,
            cash = initialCash(profile.reputation, squad),
            stadiumCapacity = profile.stadium.capacity,
            squad = squad,
            // Liga do catálogo sempre tem tier (ver toGameCompetition); o clube
            // só da continental não tem liga e fica na divisão 1.
            division = league.tier?.let(::Division) ?: Division.FIRST,
            ticketPriceCents = initialTicketPrice(strength, profile.reputation),
            country = profile.country,
        )
    }

    // Do id do pack ao clube do save, para sortear a 1ª edição da continental.
    val clubByPackId = (profiles + continentalOnly).map { it.first.id }.zip(clubs).toMap()
    return InitialWorld(
        year = year,
        countries = countries,
        competitions = catalog,
        clubs = clubs,
        market = freeAgents(catalog, leagues, takenNames = clubs.flatMapTo(HashSet()) { c -> c.squad.map { it.name } }),
        continentals = continental.mapNotNull { (world, game) ->
            game?.let { firstContinentalEdition(year, world, it, clubByPackId) }
        },
    )
}

/**
 * Força dos clubes que só jogam a continental (issue #132): o pack não tem a
 * liga deles, então vale a da liga mais forte da MESMA confederação no pack (a
 * Série A para a CONMEBOL). A reputação do clube já espalha uns acima e outros
 * abaixo dessa régua no [AttributeGenerator].
 */
private fun WorldPack.continentalOnlyStrength(profile: ClubProfile): LeagueStrength? =
    competitions
        .filter { (it.owner as? CompetitionOwner.National)?.country?.confederation == profile.country.confederation }
        .mapNotNull { it.strength }
        .maxByOrNull { it.value }

/**
 * A 1ª edição da continental, sorteada entre os clubes do pack (a fase de
 * grupos real de 2026): cabeças de chave pela reputação do pack — o dado que o
 * seed ainda tem; nas edições seguintes, sem o pack, vale a força do elenco. A
 * sede da final é o maior estádio entre os participantes. Pack sem os clubes
 * suficientes (o recorte do E2E) ou com sorteio impossível: sem continental.
 */
private fun WorldPack.firstContinentalEdition(
    year: Int,
    world: WorldCompetition,
    game: GameCompetition,
    clubByPackId: Map<WorldClubId, Club>,
): ContinentalCup? {
    val entrants = world.clubIds.mapNotNull { id -> club(id)?.let { profile -> clubByPackId[id]?.let { profile to it } } }
    val seeded = entrants
        .sortedWith(compareByDescending<Pair<ClubProfile, Club>> { it.first.reputation.value }.thenBy { it.second.id.value })
        .map { it.second }
    val venue = entrants.map { it.first.stadium }.maxByOrNull { it.capacity }?.let { Venue(it.name, it.capacity) }
    return when (val draw = drawContinental(year, game, seeded, venue, Random(continentalDrawSeed(year, game.id)))) {
        is ContinentalDraw.Drawn -> draw.edition
        is ContinentalDraw.WrongSize, ContinentalDraw.Impossible -> null
    }
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
    // Continental entra por [toContinental], que precisa do catálogo nacional.
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
                // Força da liga (issue #135): o pack só a informa em liga — copa vem `null`.
                strength = strength,
            )
        }
    }
}

/**
 * A continental do pack como competição do save (issue #132), ou `null` se ela
 * não é de grupos + mata-mata (a suíça é da #145) ou se nenhum país da
 * confederação tem liga no catálogo (sem calendário que a hospede).
 */
private fun WorldCompetition.toContinental(national: List<GameCompetition>): GameCompetition? {
    val owner = owner as? CompetitionOwner.Continental ?: return null
    if (competition.format !is CompetitionFormat.GroupsThenKnockout) return null
    val host = national.firstOrNull { it.kind is CompetitionKind.League && it.country.confederation == owner.confederation }
        ?: return null
    return GameCompetition(
        competition = competition,
        country = host.country,
        kind = CompetitionKind.Continental,
        span = SeasonSpan.of(owner.confederation),
        pausesOnFifaWindows = pausesOnFifaWindows,
    )
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
        // Valor de referência do mercado (issue #135): contrato pleno, liga de
        // referência — a mesma régua do anúncio de um jogador vendido.
        MarketEntry(player = player, price = marketListingPrice(marketReferenceValue(player)))
    }
}

/** 16 livres: 2 goleiros, 5 zagueiros, 5 meias e 4 atacantes — dá para remendar qualquer setor. */
private val FREE_AGENT_POSITIONS: List<Position> =
    List(2) { Position.GK } + List(5) { Position.CB } + List(5) { Position.MF } + List(4) { Position.FW }

private val FREE_AGENT_AGES = 20..32

private const val MARKET_SEED: Long = 131
