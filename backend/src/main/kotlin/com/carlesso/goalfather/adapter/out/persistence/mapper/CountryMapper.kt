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
internal fun String.toCountry(): Country = findInCatalog() ?: Countries.DEFAULT

/**
 * Versão para a coluna opcional (segunda nacionalidade): `null` continua `null`
 * e um código fora do catálogo também vira `null`, NUNCA o default. Cair no
 * Brasil aqui daria a um estrangeiro uma segunda nacionalidade brasileira
 * inventada, e `isForeignIn` passaria a tratá-lo como nacional (revisão da PR #149).
 */
internal fun String?.toCountryOrNull(): Country? = this?.findInCatalog()

private fun String.findInCatalog(): Country? = Countries.all.firstOrNull { it.code.value == this }
