package com.carlesso.goalfather.adapter.`in`.web.dto

import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.event.RoundEvent
import com.carlesso.goalfather.domain.event.TrainingEvent
import com.carlesso.goalfather.domain.event.TrainingReport
import com.carlesso.goalfather.domain.model.BoardObjective
import com.carlesso.goalfather.domain.model.BoardReview
import com.carlesso.goalfather.domain.model.BoardVerdict
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CupClub
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.Retirement
import com.carlesso.goalfather.domain.model.RoundFinance
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.domain.model.TrainedAttribute
import com.carlesso.goalfather.domain.model.TrainingFocus
import com.carlesso.goalfather.test.makePlayer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O JSON do WS `/ws/round/{n}` tem de ser o que `contract/openapi.yaml`
 * descreve (issue #72). Mesmo espírito de `ResponsesTest`: o E2E roda contra o
 * MSW, que implementa o contrato — então só a comparação com o JSON literal
 * pega o backend emitindo o domínio direto.
 *
 * Usa a MESMA config do bean `json` de `SerializationConfig`.
 */
class RoundEventDtosTest {

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }

    private fun encode(event: RoundEvent): JsonObject =
        json.parseToJsonElement(json.encodeToString(RoundEventDto.serializer(), event.toDto())).jsonObject

    private fun JsonObject.string(field: String): String = getValue(field).jsonPrimitive.content

    private val row = StandingRow(
        position = 1, clubId = ClubId(1), clubName = "Alfa", played = 2, wins = 2,
        goalsFor = 5, goalsAgainst = 1, goalDifference = 4, points = 6,
    )

    private val standings = Standings(
        season = 2026, round = 2, rows = listOf(row),
        division = Division(2), promotionSpots = 2, relegationSpots = 0,
    )

    // Veterano craque (star = true) e garoto da base (star = false): o par
    // prova que `star` é materializado, não um default constante.
    private val retirement = Retirement(
        clubId = ClubId(1),
        retired = makePlayer(10, overall = 88, age = 37),
        promoted = makePlayer(11, overall = 55, age = 18),
    )

    private val seasonFinished = RoundEvent.SeasonFinished(
        season = 2026,
        champion = row,
        standings = listOf(standings),
        retirements = listOf(retirement),
        cupChampion = CupClub(ClubId(3), "Gama"),
        boardReviews = listOf(
            BoardReview(
                clubId = ClubId(1), clubName = "Alfa", season = 2026, objective = BoardObjective.TITLE,
                targetPosition = 1, finalPosition = 1, confidence = 80,
                verdict = BoardVerdict.TargetMet(bonusCents = 300_000_00),
            ),
        ),
    )

    // ─── SeasonFinished ───────────────────────────────────────────────────

    @Test
    fun `SeasonFinished emite star nos jogadores das aposentadorias`() {
        val retirementJson = encode(seasonFinished).getValue("retirements").jsonArray.single().jsonObject

        assertEquals(setOf("clubId", "retired", "promoted"), retirementJson.keys)
        assertEquals("true", retirementJson.getValue("retired").jsonObject.string("star"))
        assertEquals("false", retirementJson.getValue("promoted").jsonObject.string("star"))
    }

    @Test
    fun `SeasonFinished carrega o discriminador e so os campos do contrato`() {
        val encoded = encode(seasonFinished)

        assertEquals("SeasonFinished", encoded.string("type"))
        assertEquals(
            setOf("type", "season", "champion", "standings", "retirements", "cupChampion", "boardReviews"),
            encoded.keys,
        )
        assertEquals(setOf("id", "name"), encoded.getValue("cupChampion").jsonObject.keys)
        assertEquals("3", encoded.getValue("cupChampion").jsonObject.string("id"))

        val review = encoded.getValue("boardReviews").jsonArray.single().jsonObject
        assertEquals(
            setOf(
                "clubId", "clubName", "season", "objective", "targetPosition",
                "finalPosition", "confidence", "verdict",
            ),
            review.keys,
        )
        val verdict = review.getValue("verdict").jsonObject
        assertEquals("TargetMet", verdict.string("type"))
        assertEquals("30000000", verdict.string("bonusCents"))
    }

    @Test
    fun `SeasonFinished sem copa omite cupChampion em vez de mandar null`() {
        assertNull(encode(seasonFinished.copy(cupChampion = null))["cupChampion"])
    }

    // ─── Standings / StandingRow (viajam nas duas variantes de fim) ────────

    @Test
    fun `standings achata a divisao em inteiro e expoe os campos do contrato`() {
        val table = encode(seasonFinished).getValue("standings").jsonArray.single().jsonObject

        assertEquals(
            setOf("season", "division", "round", "promotionSpots", "relegationSpots", "rows"),
            table.keys,
        )
        assertEquals("2", table.string("division"))
        assertEquals("2", table.string("promotionSpots"))

        val rowJson = table.getValue("rows").jsonArray.single().jsonObject
        assertEquals(
            setOf(
                "position", "clubId", "clubName", "played", "wins", "draws", "losses",
                "goalsFor", "goalsAgainst", "goalDifference", "points",
            ),
            rowJson.keys,
        )
        assertEquals("1", rowJson.string("clubId"))
        assertEquals("6", rowJson.string("points"))
    }

    // ─── RoundFinished ────────────────────────────────────────────────────

    private val roundFinished = RoundEvent.RoundFinished(
        standings = listOf(standings),
        finances = listOf(
            RoundFinance(
                clubId = ClubId(1), ticketRevenue = 50_000_00, salariesPaid = 20_000_00,
                deficit = 0, ticketPrice = 30_00, attendance = 12_000, cupPrize = 0,
            ),
        ),
        training = listOf(
            TrainingReport(
                clubId = ClubId(1),
                focus = TrainingFocus.ATAQUE,
                events = listOf(
                    TrainingEvent.Improved(makePlayer(20, overall = 82), TrainedAttribute.SHOOTING),
                    TrainingEvent.Injured(makePlayer(21, overall = 70), roundsOut = 2),
                ),
            ),
        ),
    )

    @Test
    fun `RoundFinished carrega o discriminador e as financas do contrato`() {
        val encoded = encode(roundFinished)

        assertEquals("RoundFinished", encoded.string("type"))
        assertEquals(setOf("type", "standings", "finances", "training"), encoded.keys)

        val finance = encoded.getValue("finances").jsonArray.single().jsonObject
        assertEquals(
            setOf("clubId", "ticketRevenue", "salariesPaid", "deficit", "ticketPrice", "attendance", "cupPrize"),
            finance.keys,
        )
        // Long adjacentes e trocáveis: valores distintos pegam um swap no mapeamento.
        assertEquals("5000000", finance.string("ticketRevenue"))
        assertEquals("2000000", finance.string("salariesPaid"))
    }

    @Test
    fun `eventos de treino preservam o discriminador e emitem star no jogador`() {
        // `TrainingEvent` também embute o `Player` de domínio — mesmo defeito
        // das aposentadorias, na outra variante de fim de rodada.
        val report = encode(roundFinished).getValue("training").jsonArray.single().jsonObject
        assertEquals(setOf("clubId", "focus", "events"), report.keys)
        assertEquals("ATAQUE", report.string("focus"))

        val (improved, injured) = report.getValue("events").jsonArray.map { it.jsonObject }
        assertEquals("Improved", improved.string("type"))
        assertEquals("SHOOTING", improved.string("attribute"))
        assertEquals("true", improved.getValue("player").jsonObject.string("star"))

        assertEquals("Injured", injured.string("type"))
        assertEquals("2", injured.string("roundsOut"))
        assertEquals("false", injured.getValue("player").jsonObject.string("star"))
    }

    // ─── MatchUpdate ──────────────────────────────────────────────────────

    @Test
    fun `MatchUpdate mantem o envelope e o MatchEvent discriminado`() {
        val encoded = encode(RoundEvent.MatchUpdate(7, MatchEvent.KickOff(0, "Casa", "Fora", 70.0, 65.0)))

        assertEquals("MatchUpdate", encoded.string("type"))
        assertEquals(setOf("type", "matchId", "event"), encoded.keys)
        assertEquals("7", encoded.string("matchId"))
        assertEquals("KickOff", encoded.getValue("event").jsonObject.string("type"))
    }

    @Test
    fun `todo jogador embutido carrega os campos obrigatorios do schema Player`() {
        val required = setOf(
            "id", "name", "position", "overall", "pace", "shooting", "passing", "defending",
            "stamina", "salary", "age", "goals", "yellowCards", "redCards", "availability", "star",
        )
        val retirementJson = encode(seasonFinished).getValue("retirements").jsonArray.single().jsonObject
        val trainingEvents = encode(roundFinished).getValue("training").jsonArray.single().jsonObject
            .getValue("events").jsonArray.map { it.jsonObject }

        val players = listOf(retirementJson.getValue("retired"), retirementJson.getValue("promoted"))
            .map { it.jsonObject } + trainingEvents.map { it.getValue("player").jsonObject }

        for (player in players) {
            assertTrue(required.all { it in player }, "faltam campos do contrato em $player")
        }
    }
}
