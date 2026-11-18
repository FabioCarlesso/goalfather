package com.carlesso.goalfather.test

import java.time.LocalDate
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.Employment
import com.carlesso.goalfather.application.port.out.CupRepository
import com.carlesso.goalfather.domain.model.Availability
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Cup
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.GameWeek
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.Slot
import com.carlesso.goalfather.domain.model.cents
import com.carlesso.goalfather.domain.rules.cupMatches

/**
 * Construtores compactos de domínio para testes — evita boilerplate
 * repetido em cada classe de teste sem inflar o domínio com helpers
 * que não são usados em produção.
 */
internal fun makePlayer(
    id: Long,
    position: Position = Position.MF,
    overall: Int = 75,
    stamina: Int = 100,
    availability: Availability = Availability.Available,
    age: Int = 25,
    salary: Int = 10_000,
    clubId: Long = 1,
): Player = Player(
    id = PlayerId(id),
    name = "Player$id",
    position = position,
    overall = overall,
    pace = overall,
    shooting = overall,
    passing = overall,
    defending = overall,
    stamina = stamina,
    employment = testContract(salary.cents, clubId),
    age = age,
    availability = availability,
)

/**
 * Contrato de teste (issue #136): longo de propósito (até 2099), para nenhum
 * teste anterior aos contratos ver jogador sair livre numa virada. Quem testa
 * vencimento monta o próprio contrato com término real.
 */
internal fun testContract(salary: Money = 10_000.cents, clubId: Long = 1): Employment.UnderContract =
    Employment.UnderContract(
        clubId = ClubId(clubId),
        salary = salary,
        start = LocalDate.of(2026, 1, 1),
        end = LocalDate.of(2099, 12, 31),
    )

internal fun makeClub(
    id: Long = 1,
    name: String = "Test FC",
    cash: Long = 1_000_000_00,
    squadSize: Int = 11,
    overall: Int = 75,
    lineup: Lineup? = null,
    ownerId: Long? = null,
    division: Division = Division.FIRST,
    stamina: Int = 100,
): Club = Club(
    id = ClubId(id),
    name = name,
    cash = cash.cents,
    stadiumCapacity = 15_000,
    squad = (1L..squadSize.toLong()).map { makePlayer(it, overall = overall, stamina = stamina, clubId = id) },
    lineup = lineup,
    ownerId = ownerId,
    division = division,
)

/**
 * Copa em memória para os testes de serviço (issue #61). Fake em vez de
 * `mockk(relaxed = true)`: um mock relaxado devolveria uma `Cup` falsa em
 * `findBySeason` (relaxed não devolve `null` para tipo de referência), e o
 * calendário passaria a reservar datas de copa que o teste não pediu.
 */
internal class InMemoryCupRepository(vararg initial: Cup) : CupRepository {
    val cups: MutableMap<Int, Cup> = initial.associateBy { it.season }.toMutableMap()

    override suspend fun findBySeason(season: Int): Cup? = cups[season]

    override suspend fun findLatest(): Cup? = cups.maxByOrNull { it.key }?.value

    override suspend fun save(cup: Cup) {
        cups[cup.season] = cup
    }
}

/**
 * Encaixe de teste (issue #125): o fim de semana da semana [week] de 2026.
 * Os testes antigos falavam em "rodada 1, 2, 3"; `md(1)`, `md(2)`… mantêm a
 * mesma leitura com a chave nova (semana + encaixe).
 */
internal fun md(week: Int, slot: Slot = Slot.WEEKEND): Matchday = Matchday(GameWeek(2026, week), slot)

/**
 * Encaixe "lá na frente" para testes de integração que dividem o mesmo H2 e
 * precisam que a SUA rodada seja a corrente (`findLatest` = o encaixe mais
 * adiante no tempo). Números distintos dão encaixes distintos, e número maior
 * é encaixe posterior — a mesma propriedade que a antiga rodada `9101`, `9401`… tinha.
 */
internal fun farMatchday(n: Int): Matchday = Matchday(GameWeek(2900 + n / 50, n % 50 + 1), Slot.WEEKEND)

/**
 * Encaixe só de copa na semana [week] de 2026 (issue #125): a perna em aberto
 * de cada confronto da fase corrente de [cup].
 */
internal fun cupRound(week: Int, season: Int, cup: Cup, clubs: Collection<Club>): Round =
    Round(matchday = md(week), season = season, matches = cupMatches(md(week), cup, clubs))
