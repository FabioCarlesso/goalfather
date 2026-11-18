package com.carlesso.goalfather.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "players")
class PlayerEntity(
    @Id
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "club_id")
    var clubId: Long? = null,        // null = livre / no mercado

    @Column(name = "name", nullable = false)
    var name: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "position", nullable = false, length = 2)
    var position: PositionEnum = PositionEnum.MF,

    @Column(name = "overall", nullable = false)
    var overall: Int = 0,

    @Column(name = "pace", nullable = false)
    var pace: Int = 0,

    @Column(name = "shooting", nullable = false)
    var shooting: Int = 0,

    @Column(name = "passing", nullable = false)
    var passing: Int = 0,

    @Column(name = "defending", nullable = false)
    var defending: Int = 0,

    @Column(name = "stamina", nullable = false)
    var stamina: Int = 100,

    /** Salário do CONTRATO (issue #136), em centavos; 0 para o livre. */
    @Column(name = "salary", nullable = false)
    var salary: Int = 0,

    /**
     * Contrato (issue #136): as três colunas descrevem o `UnderContract` do
     * domínio, cujo `clubId` é o próprio [clubId]. Nulas = livre — o banco não
     * tem soma de tipos, e o mapper reconstrói o `sealed Employment`.
     */
    @Column(name = "contract_start")
    var contractStart: LocalDate? = null,

    @Column(name = "contract_end")
    var contractEnd: LocalDate? = null,

    @Column(name = "release_clause")
    var releaseClause: Long? = null,

    @Column(name = "age", nullable = false)
    var age: Int = 25,

    @Column(name = "goals", nullable = false)
    var goals: Int = 0,

    @Column(name = "yellow_cards", nullable = false)
    var yellowCards: Int = 0,

    @Column(name = "red_cards", nullable = false)
    var redCards: Int = 0,

    // Rodadas de afastamento restantes; 0 = apto (issue #54). A coluna é um
    // Int cru porque o banco não tem soma de tipos — o `sealed Availability`
    // do domínio é reconstruído no PlayerMapper.
    @Column(name = "injured_for_rounds", nullable = false)
    var injuredForRounds: Int = 0,

    /** Trigrama FIFA da nacionalidade (issue #123); o mapper resolve o `Country`. */
    @Column(name = "nationality", nullable = false, length = 3)
    var nationality: String = "BRA",

    @Column(name = "second_nationality", length = 3)
    var secondNationality: String? = null,

    /**
     * Ficha disciplinar por competição (issue #130) em JSON; `null` = limpa.
     * O formato é o `PlayerDisciplineRecord` do mapper, não o domínio.
     */
    @Column(name = "discipline_json", columnDefinition = "TEXT")
    var disciplineJson: String? = null,

    /**
     * Convocação para a seleção (issue #134): país e último dia da data FIFA.
     * As duas nulas = não convocado; o mapper remonta o `OnInternationalDuty`.
     */
    @Column(name = "duty_country", length = 3)
    var dutyCountry: String? = null,

    @Column(name = "duty_until")
    var dutyUntil: LocalDate? = null,
) {
    /**
     * Tira o jogador do clube (venda, contrato vencido): sem clube, sem
     * contrato. Uma linha com `club_id` nulo e contrato preenchido seria um
     * livre com salário — justamente o estado que o domínio não representa.
     */
    fun releaseFromClub() {
        clubId = null
        salary = 0
        contractStart = null
        contractEnd = null
        releaseClause = null
    }
}

/**
 * Enum interno da persistência. Mantemos um por camada para evitar que
 * mudanças no domínio (ex.: adicionar uma posição) automaticamente
 * impactem o DB. Mapeamento explícito feito em PlayerMapper.
 */
enum class PositionEnum { GK, CB, MF, FW }
