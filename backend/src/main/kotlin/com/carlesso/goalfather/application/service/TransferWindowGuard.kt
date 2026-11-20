package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.TransferWindowUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.PlayerRegistrationRepository
import com.carlesso.goalfather.application.port.out.TransferWindowRepository
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.ExpiryReason
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.TransferOffer
import com.carlesso.goalfather.domain.result.OfferResult
import com.carlesso.goalfather.domain.result.TransferResult
import com.carlesso.goalfather.domain.rules.TransferBlock
import com.carlesso.goalfather.domain.rules.WindowStatus
import com.carlesso.goalfather.domain.rules.registrationBlocker
import com.carlesso.goalfather.domain.rules.windowBlocker
import com.carlesso.goalfather.domain.rules.windowStatus
import java.time.LocalDate

/**
 * A guarda de janela de transferência (issue #137) que compra, venda rápida,
 * criação e aceite de proposta consultam ANTES de mexer em qualquer coisa, e
 * que a virada do encaixe usa para expirar propostas pendentes.
 *
 * As regras são do domínio (`TransferWindowRules.kt`); aqui só se junta o
 * que elas pedem: as janelas do save, os registros do jogador e a data do
 * relógio.
 *
 * **[today] é uma função `suspend`, não um repositório.** O relógio do jogo é
 * a data do encaixe corrente (`leagueRepo.findLatest()`), mas a guarda só
 * precisa de "que dia é hoje?". Um tipo de função (`suspend () -> LocalDate?`)
 * é a interface de um método só sem declarar interface — o `BeanConfig`
 * passa uma lambda, o teste passa `{ LocalDate.of(2026, 4, 1) }`. Sem relógio
 * (`null`: mundo sem encaixe nenhum), nada é bloqueado.
 */
class TransferWindowGuard(
    private val windows: TransferWindowRepository,
    private val registrations: PlayerRegistrationRepository,
    private val today: suspend () -> LocalDate?,
) {

    /**
     * O que barra registrar [playerId] no [buyer] vindo de [origin] (o país do
     * clube vendedor; `null` = do mercado): janela e limite de registros.
     */
    suspend fun registrationBlocker(buyer: Club, origin: CountryCode?, playerId: PlayerId): TransferBlock? {
        val date = today() ?: return null
        return registrationBlocker(buyer.country, origin, date, windows.findAll(), registrations.datesOf(playerId))
    }

    /** Venda rápida ao mercado: não há comprador, vale a janela do [seller]. */
    suspend fun saleBlocker(seller: Club): TransferBlock? {
        val date = today() ?: return null
        return windowBlocker(seller.country.code, origin = null, date = date, windows = windows.findAll())
    }

    /** Anota o registro de [playerId] em [clubId] na data do relógio — conta para o limite. */
    suspend fun recordRegistration(playerId: PlayerId, clubId: ClubId) {
        today()?.let { registrations.record(playerId, clubId, it) }
    }

    /** A janela da federação de [club] hoje; sem relógio, fechada e sem data. */
    suspend fun status(club: Club): WindowStatus {
        val date = today() ?: return WindowStatus.Closed(nextOpens = null)
        return windowStatus(club.country.code, date, windows.findAll())
    }

    /**
     * As propostas de [pending] que a janela matou na data do relógio — já
     * expiradas por [ExpiryReason.WINDOW_CLOSED], prontas para gravar. Mesma
     * regra do aceite ([windowBlocker] com o país do vendedor): fechou, ou
     * virou só doméstica com vendedor de fora. Clube que sumiu do mapa fica
     * de fora (o aceite cuida dele).
     */
    suspend fun closedByWindow(pending: List<TransferOffer>, clubs: Map<ClubId, Club>): List<TransferOffer> {
        if (pending.isEmpty()) return emptyList()
        val date = today() ?: return emptyList()
        val all = windows.findAll()
        return pending.mapNotNull { offer ->
            val buyer = clubs[offer.buyerClubId] ?: return@mapNotNull null
            val seller = clubs[offer.sellerClubId] ?: return@mapNotNull null
            windowBlocker(buyer.country.code, seller.country.code, date, all)
                ?.let { offer.expire(ExpiryReason.WINDOW_CLOSED) }
        }
    }

    companion object {
        /**
         * Sem relógio, nada bloqueia: o default dos serviços montados à mão
         * nos testes anteriores à #137 (mesmo padrão de `ContinentalRepository.NONE`).
         */
        val UNRESTRICTED: TransferWindowGuard =
            TransferWindowGuard(TransferWindowRepository.NONE, PlayerRegistrationRepository.NONE) { null }
    }
}

/**
 * O bloqueio do domínio como resultado do mercado. `when` exaustivo sobre o
 * `sealed TransferBlock`: um bloqueio novo quebra a compilação aqui.
 */
fun TransferBlock.toTransferResult(playerId: PlayerId): TransferResult = when (this) {
    is TransferBlock.WindowClosed -> TransferResult.TransferWindowClosed(nextOpens)
    is TransferBlock.LimitReached -> TransferResult.TransferLimitReached(playerId, limit)
}

/** O bloqueio do domínio como erro de negócio das propostas. */
fun TransferBlock.toOfferRejection(playerId: PlayerId): OfferResult.Rejection = when (this) {
    is TransferBlock.WindowClosed -> OfferResult.TransferWindowClosed(nextOpens)
    is TransferBlock.LimitReached -> OfferResult.TransferLimitReached(playerId, limit)
}

/** Implementação de [TransferWindowUseCase]: o selo de janela do clube. */
class TransferWindowService(
    private val clubRepo: ClubRepository,
    private val guard: TransferWindowGuard,
) : TransferWindowUseCase {
    override suspend fun statusOf(clubId: ClubId): WindowStatus? = clubRepo.findById(clubId)?.let { guard.status(it) }
}
