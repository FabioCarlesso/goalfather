package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.Position
import kotlin.random.Random

/**
 * O pool de jogadores livres do mundo (issue #139): quem fica nele, quem se
 * aposenta e como o técnico o pesquisa. Funções PURAS.
 */

// ─── Envelhecimento do pool ────────────────────────────────────────────────

/**
 * Quantas viradas do mundo sem clube aposentam um livre. Com 1, quem a virada
 * encontra no pool desde a anterior sai de cena: o brasileiro dispensado em
 * dezembro teve um ano inteiro (as duas janelas dele e as da Europa); o
 * europeu dispensado em maio, a janela de verão e a de janeiro. Quem "se
 * aposenta" aqui é, no mundo real, quem foi jogar numa divisão que o jogo não
 * simula. No teste de 10 temporadas (`WorldLongRunTest`) o pool se estabiliza
 * em ~500 livres; com 2 viradas, passava de 950.
 */
const val FREE_AGENT_IDLE_SEASONS_TO_RETIRE: Int = 1

/**
 * O que a virada do mundo fez com um livre. `sealed`: o chamador decide pelo
 * `when` exaustivo entre gravar a entrada nova e apagar o jogador.
 */
sealed interface PoolAging {
    /** Continua no pool, um ano mais velho e com mais uma temporada sem clube. */
    data class StillListed(val entry: MarketEntry) : PoolAging

    /** Pendurou as chuteiras: pela idade e nível, ou por tempo demais sem clube. */
    data class Retired(val player: Player) : PoolAging
}

/**
 * Uma virada para quem está no pool: o envelhecimento de todo jogador
 * ([ageOneSeason]) mais o contador de temporadas sem clube, que aposenta ao
 * chegar a [retireAfter]. O limite é parâmetro (com o default do jogo) para a
 * regra do contador ser testável com qualquer valor.
 */
fun MarketEntry.agedInPool(rng: Random, retireAfter: Int = FREE_AGENT_IDLE_SEASONS_TO_RETIRE): PoolAging {
    val outcome = player.ageOneSeason(rng)
    val idle = seasonsWithoutClub + 1
    return when {
        outcome is AgingOutcome.Retired || idle >= retireAfter -> PoolAging.Retired(outcome.player)
        else -> PoolAging.StillListed(copy(player = outcome.player, seasonsWithoutClub = idle))
    }
}

// ─── Busca ─────────────────────────────────────────────────────────────────

/** Critério de ordenação da busca do mercado. */
enum class MarketSort { PRICE, VALUE, OVERALL, AGE }

/**
 * Filtros da busca do mercado. Todo critério é opcional (`null` = não filtra);
 * a faixa de valor usa o valor de REFERÊNCIA do mercado ([marketReferenceValue],
 * o mesmo que a tela mostra), não o preço — o livre de verdade sai de graça.
 */
data class MarketFilter(
    val position: Position? = null,
    val nationality: CountryCode? = null,
    val minAge: Int? = null,
    val maxAge: Int? = null,
    val minValue: Money? = null,
    val maxValue: Money? = null,
    val maxPrice: Money? = null,
    /** Trecho do nome, sem diferenciar maiúsculas nem acentos. */
    val query: String? = null,
    val sort: MarketSort = MarketSort.PRICE,
    val descending: Boolean = false,
)

/** Uma página de resultados. [page] começa em 0. */
data class MarketPage(
    val items: List<MarketEntry>,
    val page: Int,
    val size: Int,
    val totalItems: Int,
) {
    /** Ao menos 1: a página 0 de uma busca vazia existe, só não tem itens. */
    val totalPages: Int get() = if (totalItems == 0) 1 else (totalItems + size - 1) / size
}

/**
 * Filtra, ordena e pagina o pool. Empate na ordenação cai no nome e depois no
 * id, sempre crescentes: a mesma busca devolve a mesma ordem, e a página 2 não
 * repete jogador da página 1.
 */
fun List<MarketEntry>.search(filter: MarketFilter, page: Int, size: Int): MarketPage {
    require(page >= 0) { "page negativa: $page" }
    require(size > 0) { "size deve ser positivo: $size" }
    val needle = filter.query?.trim()?.normalizedForSearch().orEmpty()
    // Valor calculado uma vez por entrada: é o filtro e um dos critérios.
    val valued = map { it to marketReferenceValue(it.player) }
    val matching = valued.filter { (entry, value) -> filter.accepts(entry, value, needle) }

    val key: (Pair<MarketEntry, Money>) -> Long = when (filter.sort) {
        MarketSort.PRICE -> { (entry, _) -> entry.price.cents }
        MarketSort.VALUE -> { (_, value) -> value.cents }
        MarketSort.OVERALL -> { (entry, _) -> entry.player.overall.toLong() }
        MarketSort.AGE -> { (entry, _) -> entry.player.age.toLong() }
    }
    val primary = if (filter.descending) compareByDescending(key) else compareBy(key)
    val sorted = matching.sortedWith(primary.thenBy { it.first.player.name }.thenBy { it.first.player.id.value })

    return MarketPage(
        // `drop` + `take`: a fatia da página, sem índices manuais (e sem estourar
        // quando a página pedida passa do fim — vem vazia).
        items = sorted.asSequence().drop(page * size).take(size).map { it.first }.toList(),
        page = page,
        size = size,
        totalItems = sorted.size,
    )
}

private fun MarketFilter.accepts(entry: MarketEntry, value: Money, needle: String): Boolean {
    val player = entry.player
    return (position == null || player.position == position) &&
        (nationality == null || player.nationality.code == nationality) &&
        (minAge == null || player.age >= minAge) &&
        (maxAge == null || player.age <= maxAge) &&
        (minValue == null || value >= minValue) &&
        (maxValue == null || value <= maxValue) &&
        (maxPrice == null || entry.price <= maxPrice) &&
        (needle.isEmpty() || player.name.normalizedForSearch().contains(needle))
}

/**
 * Sem acento e em minúsculas: "muñoz" acha "Muñoz" e "munoz" também. O `\p{M}`
 * remove as marcas que a decomposição NFD separa da letra.
 */
private fun String.normalizedForSearch(): String =
    java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase()

private val DIACRITICS = Regex("\\p{M}+")
