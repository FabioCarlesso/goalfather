package com.carlesso.goalfather.adapter.`in`.web.dto

import com.carlesso.goalfather.domain.model.CareerCampaign
import com.carlesso.goalfather.domain.model.ClubCareer
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CupClub
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.KnockoutPhase
import com.carlesso.goalfather.domain.model.PenaltyScore
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.ReadinessStatus
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.SeasonRecord
import com.carlesso.goalfather.domain.model.SeasonStanding
import com.carlesso.goalfather.domain.model.SeasonTopScorer
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * O JSON dos endpoints de `LeagueController` tem de ser o que
 * `contract/openapi.yaml` descreve (issue #97). Mesmo espírito de
 * `ResponsesTest`/`RoundEventDtosTest`: o E2E roda contra o MSW, que implementa
 * o contrato — só a comparação com o JSON literal pega o backend emitindo o
 * domínio direto.
 *
 * Os conjuntos de chaves abaixo são os `properties` de cada schema do contrato.
 *
 * Usa a MESMA config do bean `json` de `SerializationConfig`.
 */
class LeagueDtosTest {

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }

    private fun <T> encode(serializer: KSerializer<T>, value: T): JsonObject =
        json.parseToJsonElement(json.encodeToString(serializer, value)).jsonObject

    private fun JsonObject.obj(field: String): JsonObject = getValue(field).jsonObject
    private fun JsonObject.string(field: String): String = getValue(field).jsonPrimitive.content

    private val standingRowKeys = setOf(
        "position", "clubId", "clubName", "played", "wins", "draws", "losses",
        "goalsFor", "goalsAgainst", "goalDifference", "points",
    )
    private val seasonStandingKeys = setOf("division", "row", "pointsPercentage")

    private val row = StandingRow(
        position = 1, clubId = ClubId(1), clubName = "Alfa", played = 10, wins = 8,
        draws = 0, losses = 2, goalsFor = 20, goalsAgainst = 8, goalDifference = 12, points = 24,
    )
    private val seasonStanding = SeasonStanding(division = Division(2), row = row, pointsPercentage = 80)

    // ─── Standings ────────────────────────────────────────────────────────

    @Test
    fun `standings achata divisao e clubId em numero e expoe so os campos do contrato`() {
        val standings = Standings(
            season = 2026, round = 3, rows = listOf(row),
            division = Division(2), promotionSpots = 2, relegationSpots = 0,
        )
        val encoded = encode(StandingsDto.serializer(), standings.toDto())

        assertEquals(
            setOf("season", "division", "round", "promotionSpots", "relegationSpots", "rows"),
            encoded.keys,
        )
        assertEquals("2", encoded.string("division"))
        val rowJson = encoded.getValue("rows").jsonArray.single().jsonObject
        assertEquals(standingRowKeys, rowJson.keys)
        assertEquals("1", rowJson.string("clubId"))
    }

    // ─── SeasonRecord ─────────────────────────────────────────────────────

    private val record = SeasonRecord(
        season = 2026,
        champion = seasonStanding,
        finalStandings = listOf(seasonStanding),
        topScorer = SeasonTopScorer(PlayerId(42), "Artilheiro", ClubId(1), "Alfa", goals = 23),
        cupChampion = CupClub(ClubId(3), "Gama"),
    )

    @Test
    fun `season record completo tem exatamente as chaves do contrato`() {
        val encoded = encode(SeasonRecordDto.serializer(), record.toDto())

        assertEquals(setOf("season", "champion", "finalStandings", "topScorer", "cupChampion"), encoded.keys)

        val champion = encoded.obj("champion")
        assertEquals(seasonStandingKeys, champion.keys)
        assertEquals("2", champion.string("division"))
        assertEquals("80", champion.string("pointsPercentage"))
        assertEquals(standingRowKeys, champion.obj("row").keys)
        assertEquals(seasonStandingKeys, encoded.getValue("finalStandings").jsonArray.single().jsonObject.keys)

        val scorer = encoded.obj("topScorer")
        assertEquals(setOf("playerId", "playerName", "clubId", "clubName", "goals"), scorer.keys)
        assertEquals("42", scorer.string("playerId"))

        assertEquals(setOf("id", "name"), encoded.obj("cupChampion").keys)
    }

    @Test
    fun `season record sem artilheiro nem copa omite os opcionais em vez de mandar null`() {
        val encoded = encode(SeasonRecordDto.serializer(), record.copy(topScorer = null, cupChampion = null).toDto())

        assertEquals(setOf("season", "champion", "finalStandings"), encoded.keys)
    }

    // ─── ClubCareer ───────────────────────────────────────────────────────

    @Test
    fun `carreira tem as chaves do contrato e a melhor campanha aninhada`() {
        val career = ClubCareer(
            clubId = ClubId(1), clubName = "Alfa", seasonsPlayed = 2,
            titles = listOf(2026), bestCampaign = CareerCampaign(2026, seasonStanding), cupTitles = listOf(2027),
        )
        val encoded = encode(ClubCareerDto.serializer(), career.toDto())

        assertEquals(
            setOf("clubId", "clubName", "seasonsPlayed", "titles", "cupTitles", "bestCampaign"),
            encoded.keys,
        )
        assertEquals("1", encoded.string("clubId"))
        val campaign = encoded.obj("bestCampaign")
        assertEquals(setOf("season", "standing"), campaign.keys)
        assertEquals(seasonStandingKeys, campaign.obj("standing").keys)
    }

    @Test
    fun `carreira sem campanha omite bestCampaign mas mantem cupTitles requerido`() {
        val career = ClubCareer(clubId = ClubId(1), clubName = "Alfa", seasonsPlayed = 0, titles = emptyList())
        val encoded = encode(ClubCareerDto.serializer(), career.toDto())

        assertNull(encoded["bestCampaign"])
        assertEquals(0, encoded.getValue("cupTitles").jsonArray.size)
    }

    // ─── Round ────────────────────────────────────────────────────────────

    private val matchKeys = setOf(
        "matchId", "homeClubId", "awayClubId", "homeClubName", "awayClubName",
        "status", "homeGoals", "awayGoals", "minute", "division",
    )

    private val leagueRound = Round(
        number = 5,
        season = 2026,
        matches = listOf(RoundMatch(501, ClubId(1), ClubId(2), "Alfa", "Beta", division = Division(2))),
        stage = RoundStage.League(matchday = 3),
    )

    @Test
    fun `rodada de liga tem as chaves do contrato e o stage League discriminado`() {
        val encoded = encode(RoundDto.serializer(), leagueRound.toDto())

        assertEquals(setOf("number", "season", "status", "stage", "matches"), encoded.keys)
        assertEquals("Scheduled", encoded.string("status"))

        val stage = encoded.obj("stage")
        assertEquals(setOf("type", "matchday"), stage.keys)
        assertEquals("League", stage.string("type"))
        assertEquals("3", stage.string("matchday"))

        // Sem pênaltis a chave some — `penalties` não é requerido no contrato.
        val match = encoded.getValue("matches").jsonArray.single().jsonObject
        assertEquals(matchKeys, match.keys)
        assertEquals("2", match.string("division"))
        assertEquals("2", match.string("awayClubId"))
    }

    @Test
    fun `fase de copa tem o stage Cup discriminado e os penaltis da partida`() {
        val cupRound = leagueRound.copy(
            status = RoundStatus.Finished,
            stage = RoundStage.Cup(KnockoutPhase.SEMI_FINALS),
            matches = listOf(
                leagueRound.matches.single().copy(
                    status = RoundStatus.Finished, homeGoals = 1, awayGoals = 1, minute = 90,
                    penalties = PenaltyScore(home = 4, away = 3),
                ),
            ),
        )
        val encoded = encode(RoundDto.serializer(), cupRound.toDto())

        val stage = encoded.obj("stage")
        assertEquals(setOf("type", "phase"), stage.keys)
        assertEquals("Cup", stage.string("type"))
        assertEquals("SEMI_FINALS", stage.string("phase"))

        val match = encoded.getValue("matches").jsonArray.single().jsonObject
        assertEquals(matchKeys + "penalties", match.keys)
        assertEquals(setOf("home", "away"), match.obj("penalties").keys)
        assertEquals("4", match.obj("penalties").string("home"))
    }

    // ─── ReadinessStatus ──────────────────────────────────────────────────

    @Test
    fun `prontidao nao vaza as computed properties allReady e canStart`() {
        val status = ReadinessStatus(
            roundNumber = 5, readyCount = 1, totalCount = 2,
            pendingUsernames = listOf("beltrano"), secondsRemaining = 30,
        )
        val encoded = encode(ReadinessStatusDto.serializer(), status.toDto())

        assertEquals(
            setOf("roundNumber", "readyCount", "totalCount", "pendingUsernames", "secondsRemaining", "timedOut"),
            encoded.keys,
        )
        assertEquals("30", encoded.string("secondsRemaining"))
        assertEquals("false", encoded.string("timedOut"))
    }

    @Test
    fun `prontidao sem cronometro armado omite secondsRemaining`() {
        val status = ReadinessStatus(roundNumber = 5, readyCount = 0, totalCount = 2, pendingUsernames = listOf("a", "b"))

        assertNull(encode(ReadinessStatusDto.serializer(), status.toDto())["secondsRemaining"])
    }
}
