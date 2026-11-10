package com.carlesso.goalfather.adapter.out.persistence.mapper

import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.Country

/**
 * Coluna de país (trigrama FIFA, issue #123) → [Country] do catálogo.
 *
 * Mesma postura do `trainingFocus` no `ClubMapper`: a coluna é dado externo,
 * não invariante nosso. Um código fora do catálogo (linha antiga, país removido
 * do pack) cai no país default em vez de derrubar a leitura do clube. Compara
 * pelo texto cru em vez de montar um `CountryCode` primeiro, porque o `init` do
 * value class rejeitaria um valor malformado com exception.
 */
internal fun String.toCountry(): Country =
    Countries.all.firstOrNull { it.code.value == this } ?: Countries.DEFAULT

/** Versão para a coluna opcional (segunda nacionalidade): `null` continua `null`. */
internal fun String?.toCountryOrNull(): Country? = this?.toCountry()
