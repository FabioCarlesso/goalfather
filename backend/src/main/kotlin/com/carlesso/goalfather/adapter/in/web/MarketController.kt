package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.BuyPlayerRequest
import com.carlesso.goalfather.adapter.`in`.web.dto.ErrorResponse
import com.carlesso.goalfather.adapter.`in`.web.dto.INVALID_CONTRACT_OFFER
import com.carlesso.goalfather.adapter.`in`.web.dto.SellPlayerRequest
import com.carlesso.goalfather.adapter.`in`.web.dto.toDto
import com.carlesso.goalfather.application.metrics.GoalfatherMetrics
import com.carlesso.goalfather.application.port.`in`.BuyPlayerUseCase
import com.carlesso.goalfather.application.port.`in`.SellPlayerUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.CompetitionCatalogRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.application.service.today
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.result.TransferResult
import com.carlesso.goalfather.domain.rules.MarketFilter
import com.carlesso.goalfather.domain.rules.MarketSort
import com.carlesso.goalfather.domain.rules.search
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.serialization.json.Json
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/market")
class MarketController(
    private val marketRepo: MarketRepository,
    private val clubRepo: ClubRepository,
    private val buyUseCase: BuyPlayerUseCase,
    private val sellUseCase: SellPlayerUseCase,
    private val meterRegistry: MeterRegistry,
    // Catálogo do save (issue #131): o clube da compra sai com a liga dele.
    private val catalog: CompetitionCatalogRepository,
    // Encaixe corrente (issues #135/#136): o "hoje" do valor de mercado e do
    // "último ano de contrato" no clube devolvido.
    private val leagueRepo: LeagueRepository,
    // `TransferResultDto` é sealed: sai pelo kotlinx (`okJson`) para levar o
    // `type` do contrato — com `ResponseEntity<Any>` o Spring usaria o Jackson.
    private val json: Json,
) {

    /**
     * Busca no pool de livres (issue #139): filtros, ordenação e paginação no
     * servidor — o pool tem centenas de jogadores, e filtrar só a página
     * carregada mentiria para o técnico. A regra (o que casa, em que ordem)
     * é a função pura `search` do domínio; aqui só se traduz a query string.
     */
    @GetMapping
    suspend fun listMarket(
        @RequestParam(required = false) position: Position?,
        @RequestParam(required = false) nationality: String?,
        @RequestParam(required = false) minAge: Int?,
        @RequestParam(required = false) maxAge: Int?,
        @RequestParam(required = false) minValue: Long?,
        @RequestParam(required = false) maxValue: Long?,
        @RequestParam(required = false) maxPrice: Long?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false, defaultValue = "price") sort: String,
        @RequestParam(required = false, defaultValue = "asc") dir: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
    ): ResponseEntity<Any> {
        val marketSort = MARKET_SORTS[sort] ?: return invalidQuery("sort deve ser um de ${MARKET_SORTS.keys}: '$sort'")
        if (dir != "asc" && dir != "desc") return invalidQuery("dir deve ser asc ou desc: '$dir'")
        if (page < 0) return invalidQuery("page não pode ser negativa: $page")
        if (size !in 1..MAX_PAGE_SIZE) return invalidQuery("size deve estar em 1..$MAX_PAGE_SIZE: $size")
        if (listOfNotNull(minValue, maxValue, maxPrice).any { it < 0 }) return invalidQuery("valores e preço não podem ser negativos")
        // `runCatching` transforma o `require` do value class num resultado:
        // código malformado vira 400, não 500.
        val country = nationality?.let { code ->
            runCatching { CountryCode(code) }.getOrNull() ?: return invalidQuery("nationality deve ser um código FIFA de 3 letras: '$code'")
        }
        val filter = MarketFilter(
            position = position,
            nationality = country,
            minAge = minAge,
            maxAge = maxAge,
            minValue = minValue?.let(::Money),
            maxValue = maxValue?.let(::Money),
            maxPrice = maxPrice?.let(::Money),
            query = q,
            sort = marketSort,
            descending = dir == "desc",
        )
        return json.okJson(marketRepo.findAll().search(filter, page, size).toDto())
    }

    private fun invalidQuery(message: String): ResponseEntity<Any> =
        ResponseEntity.badRequest().body(ErrorResponse(code = "INVALID_MARKET_QUERY", message = message))

    @PostMapping("/buy")
    suspend fun buy(
        @RequestBody req: BuyPlayerRequest,
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<Any> {
        ownershipError(req.clubId, userId)?.let { return it }
        val offer = req.contract.toDomain() ?: return ResponseEntity.badRequest().body(INVALID_CONTRACT_OFFER)
        val result = buyUseCase.execute(ClubId(req.clubId), PlayerId(req.playerId), offer)
        // Conta a compra dimensionada pelo desfecho (issue #44). `conflict` cobre
        // o `PlayerNotAvailable` — na prática, a perda do lock otimista do mercado
        // (#21) quando dois donos disputam o mesmo jogador.
        countTransfer(result)
        return json.okJson(result.toDto(catalog.findAll(), leagueRepo.today()))
    }

    private fun countTransfer(result: TransferResult) {
        val outcome = when (result) {
            is TransferResult.Success -> "success"
            is TransferResult.InsufficientFunds -> "insufficient_funds"
            is TransferResult.SquadFull -> "squad_full"
            is TransferResult.PlayerNotAvailable -> "conflict"
            // Perda do lock otimista do CLUBE (issue #89) — outra corrida que a
            // do jogador, então tag própria para não misturar as duas no painel.
            is TransferResult.Conflict -> "club_conflict"
            // Janela e limite de registros (issue #137).
            is TransferResult.TransferWindowClosed -> "window_closed"
            is TransferResult.TransferLimitReached -> "limit_reached"
            // Termos pessoais recusados pelo livre (issue #138).
            is TransferResult.PlayerRefused -> "player_refused"
        }
        meterRegistry.counter(GoalfatherMetrics.MARKET_TRANSFERS, GoalfatherMetrics.TAG_RESULT, outcome)
            .increment()
    }

    @PostMapping("/sell")
    suspend fun sell(
        @RequestBody req: SellPlayerRequest,
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<Any> {
        ownershipError(req.clubId, userId)?.let { return it }
        return json.okJson(sellUseCase.execute(ClubId(req.clubId), PlayerId(req.playerId)).toDto(catalog.findAll(), leagueRepo.today()))
    }

    /** 403 se o usuário não é dono do clube alvo da operação (issue #18). */
    private suspend fun ownershipError(clubId: Long, userId: Long): ResponseEntity<Any>? {
        val club = clubRepo.findById(ClubId(clubId)) ?: return ResponseEntity.status(404).body(
            ErrorResponse(code = "CLUB_NOT_FOUND", message = "Clube $clubId não encontrado"),
        )
        return if (club.ownerId != userId) {
            ResponseEntity.status(403).body(
                ErrorResponse(code = "FORBIDDEN", message = "Você não é dono deste clube"),
            )
        } else {
            null
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100

        /** O valor do contrato (`MarketSort` no YAML) → o critério do domínio. */
        val MARKET_SORTS = mapOf(
            "price" to MarketSort.PRICE,
            "value" to MarketSort.VALUE,
            "overall" to MarketSort.OVERALL,
            "age" to MarketSort.AGE,
        )
    }
}
