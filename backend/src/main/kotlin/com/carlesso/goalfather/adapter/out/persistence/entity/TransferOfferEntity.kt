package com.carlesso.goalfather.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Entidade JPA da proposta de transferência (issue #63). Separada do
 * `TransferOffer` de domínio pela regra de ouro do CLAUDE.md.
 *
 * `status` + `expiryReason` são o `sealed OfferStatus` achatado em duas
 * colunas de texto (o banco não tem soma de tipos); o mapper reconstrói o
 * `sealed`, como o `injuredForRounds` do jogador vira `Availability`.
 *
 * Sem `@Version`: toda transição é um UPDATE condicional ao status `PENDING`
 * (ver `TransferOfferJpaRepository.saveIfPending`), e como os demais estados
 * são terminais, o status JÁ é a versão que importa.
 */
@Entity
@Table(name = "transfer_offers")
class TransferOfferEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "buyer_club_id", nullable = false)
    var buyerClubId: Long = 0,

    @Column(name = "buyer_club_name", nullable = false)
    var buyerClubName: String = "",

    @Column(name = "seller_club_id", nullable = false)
    var sellerClubId: Long = 0,

    @Column(name = "seller_club_name", nullable = false)
    var sellerClubName: String = "",

    @Column(name = "player_id", nullable = false)
    var playerId: Long = 0,

    @Column(name = "player_name", nullable = false)
    var playerName: String = "",

    @Column(name = "amount", nullable = false)
    var amount: Long = 0,

    @Column(name = "rounds_left", nullable = false)
    var roundsLeft: Int = 0,

    @Column(name = "status", nullable = false, length = 16)
    var status: String = PENDING,

    @Column(name = "expiry_reason", length = 24)
    var expiryReason: String? = null,
) {
    companion object {
        /** Único status com saída — a condição de todo UPDATE de transição. */
        const val PENDING: String = "PENDING"
    }
}
