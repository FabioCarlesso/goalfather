package com.carlesso.goalfather.adapter.`in`.web.dto

import com.carlesso.goalfather.domain.model.Confederation
import com.carlesso.goalfather.domain.model.Country
import kotlinx.serialization.Serializable

/**
 * País como a API o descreve (issue #123) — espelha `Country` no contrato.
 *
 * [flag] é apresentação pura (emoji montado a partir do código ISO), por isso
 * nasce aqui no adapter e não no domínio: assim o frontend só exibe, sem
 * manter uma tabela própria de códigos → bandeiras.
 */
@Serializable
data class CountryDto(
    val code: String,
    val name: String,
    val confederation: Confederation,
    val flag: String,
)

fun Country.toDto(): CountryDto = CountryDto(
    code = code.value,
    name = name,
    confederation = confederation,
    flag = flagEmoji(isoAlpha2),
)

/**
 * Emoji de bandeira a partir do código ISO.
 *
 * - País (`BR`): dois "regional indicator symbols", um por letra — o Unicode
 *   desenha o par como a bandeira.
 * - Subdivisão (`GB-ENG`): a bandeira preta (U+1F3F4) seguida das letras em
 *   "tag characters" (U+E0000 + ASCII minúsculo) e do terminador U+E007F. É
 *   assim que Inglaterra, Escócia e País de Gales têm bandeira própria.
 *
 * `buildString` + `appendCodePoint`: os dois casos usam code points fora do
 * plano básico (acima de U+FFFF), que um `Char` sozinho não representa.
 */
internal fun flagEmoji(isoAlpha2: String): String = buildString {
    if ('-' in isoAlpha2) {
        appendCodePoint(0x1F3F4)
        isoAlpha2.lowercase().filter { it.isLetter() }.forEach { appendCodePoint(0xE0000 + it.code) }
        appendCodePoint(0xE007F)
    } else {
        isoAlpha2.uppercase().forEach { appendCodePoint(0x1F1E6 + (it - 'A')) }
    }
}
