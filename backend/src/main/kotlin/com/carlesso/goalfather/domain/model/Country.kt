package com.carlesso.goalfather.domain.model

import kotlinx.serialization.Serializable

/**
 * Código de país (issue #123): o **trigrama FIFA** (`BRA`, `ARG`, `ENG`, `GER`).
 *
 * A issue sugeria ISO 3166 alfa-3, mas no futebol o país é a FEDERAÇÃO:
 * Inglaterra, Escócia, País de Gales e Irlanda do Norte são seleções distintas
 * e não têm código ISO próprio (as quatro seriam `GBR`). O trigrama FIFA cobre
 * esse caso e coincide com o ISO na maioria dos países — onde diverge
 * (`GER`×`DEU`, `NED`×`NLD`, `URU`×`URY`), vale o do futebol.
 *
 * `value class` pelo mesmo motivo de [PlayerId]: o compilador impede passar um
 * `String` qualquer (nome do clube, posição) onde se espera um país. Sem
 * serializer custom: o kotlinx serializa value class como o valor de dentro,
 * então no JSON ele sai como `"BRA"` cru.
 */
@JvmInline
@Serializable
value class CountryCode(val value: String) {
    init {
        require(value.length == 3 && value.all { it in 'A'..'Z' }) {
            "CountryCode deve ter 3 letras maiúsculas: '$value'"
        }
    }

    override fun toString(): String = value
}

/** As seis confederações da FIFA — conjunto fechado, por isso `enum`. */
@Serializable
enum class Confederation { CONMEBOL, UEFA, CONCACAF, CAF, AFC, OFC }

/**
 * País como o futebol o enxerga (issue #123): uma federação filiada a uma
 * [Confederation]. É ela que decide em que continental o clube joga e qual
 * seleção o jogador pode defender.
 *
 * [isoAlpha2] existe só para a apresentação montar a bandeira (os emojis de
 * bandeira são indexados pelo código ISO de 2 letras). Para as nações do Reino
 * Unido ele é a subdivisão ISO 3166-2 (`GB-ENG`), que é como o Unicode indexa
 * as bandeiras delas.
 */
@Serializable
data class Country(
    val code: CountryCode,
    val name: String,
    val confederation: Confederation,
    val isoAlpha2: String,
)

/**
 * Catálogo inicial de países (issue #123).
 *
 * **Interino:** a lista de países é DADO e vai morar no pack do mundo
 * (issue #126). Até lá ela fica aqui, com o mínimo que o seed precisa: o Brasil
 * e os países de onde vêm os estrangeiros do seed, mais as federações do MVP da
 * Fase 8 (CONMEBOL e as cinco grandes ligas da UEFA), para o catálogo não ter
 * que mudar a cada clube novo.
 *
 * `object` = singleton sem cerimônia (o `static final` + construtor privado do
 * Java), e [byCode] é calculado uma vez, na primeira leitura do objeto.
 */
object Countries {
    val BRAZIL = Country(CountryCode("BRA"), "Brasil", Confederation.CONMEBOL, "BR")
    val ARGENTINA = Country(CountryCode("ARG"), "Argentina", Confederation.CONMEBOL, "AR")
    val URUGUAY = Country(CountryCode("URU"), "Uruguai", Confederation.CONMEBOL, "UY")
    val PARAGUAY = Country(CountryCode("PAR"), "Paraguai", Confederation.CONMEBOL, "PY")
    val CHILE = Country(CountryCode("CHI"), "Chile", Confederation.CONMEBOL, "CL")
    val COLOMBIA = Country(CountryCode("COL"), "Colômbia", Confederation.CONMEBOL, "CO")
    val ECUADOR = Country(CountryCode("ECU"), "Equador", Confederation.CONMEBOL, "EC")
    val PERU = Country(CountryCode("PER"), "Peru", Confederation.CONMEBOL, "PE")
    val BOLIVIA = Country(CountryCode("BOL"), "Bolívia", Confederation.CONMEBOL, "BO")
    val VENEZUELA = Country(CountryCode("VEN"), "Venezuela", Confederation.CONMEBOL, "VE")
    val ENGLAND = Country(CountryCode("ENG"), "Inglaterra", Confederation.UEFA, "GB-ENG")
    val SPAIN = Country(CountryCode("ESP"), "Espanha", Confederation.UEFA, "ES")
    val ITALY = Country(CountryCode("ITA"), "Itália", Confederation.UEFA, "IT")
    val GERMANY = Country(CountryCode("GER"), "Alemanha", Confederation.UEFA, "DE")
    val FRANCE = Country(CountryCode("FRA"), "França", Confederation.UEFA, "FR")
    val PORTUGAL = Country(CountryCode("POR"), "Portugal", Confederation.UEFA, "PT")

    /** Todos os países conhecidos, na ordem acima. */
    val all: List<Country> = listOf(
        BRAZIL, ARGENTINA, URUGUAY, PARAGUAY, CHILE, COLOMBIA, ECUADOR, PERU, BOLIVIA, VENEZUELA,
        ENGLAND, SPAIN, ITALY, GERMANY, FRANCE, PORTUGAL,
    )

    private val byCode: Map<CountryCode, Country> = all.associateBy { it.code }

    /** País pelo código, ou `null` se não está no catálogo — ausência no tipo, sem exception. */
    fun find(code: CountryCode): Country? = byCode[code]

    /**
     * País de quem não informou um (default de [Club.country] e de
     * [Player.nationality]): o país-sede do seed atual. O pack do mundo
     * (issue #126) sempre informa o país; o default só poupa o seed e os
     * testes anteriores à issue de repetir "Brasil" em cada construtor.
     */
    val DEFAULT: Country = BRAZIL
}
