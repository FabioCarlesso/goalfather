package com.carlesso.goalfather.tools.worldimport.assemble

import java.text.Normalizer

/**
 * Nome → id do pack (`Agustín Rossi` → `agustin-rossi`): só minúsculas,
 * dígitos e hífen, o padrão que o loader exige (`WorldPackLoader.SAFE_ID`).
 *
 * Extension function sobre `String`: lê como método da própria string
 * (`name.slug()`) sem classe utilitária estática.
 */
fun String.slug(): String {
    // Letras que a decomposição Unicode (NFD) não separa em base + acento.
    val transliterated = buildString {
        for (char in this@slug.lowercase()) {
            append(SPECIAL_LETTERS[char] ?: char.toString())
        }
    }
    // NFD separa "í" em "i" + acento combinante; o regex remove os acentos.
    return Normalizer.normalize(transliterated, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .replace(NON_ALPHANUMERIC, "-")
        .trim('-')
}

private val SPECIAL_LETTERS: Map<Char, String> = mapOf(
    'ß' to "ss", 'ø' to "o", 'æ' to "ae", 'œ' to "oe", 'đ' to "d", 'ð' to "d",
    'ł' to "l", 'ı' to "i", 'þ' to "th", 'ħ' to "h",
)
private val COMBINING_MARKS = Regex("\\p{M}+")
private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
