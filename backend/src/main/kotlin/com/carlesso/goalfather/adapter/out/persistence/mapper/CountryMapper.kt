package com.carlesso.goalfather.adapter.out.persistence.mapper

import com.carlesso.goalfather.adapter.out.persistence.entity.CountryEntity
import com.carlesso.goalfather.domain.model.Confederation
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.Country
import com.carlesso.goalfather.domain.model.CountryCode

/**
 * Coluna de país (trigrama FIFA, issue #123) → [Country].
 *
 * Onde procurar (issue #131): primeiro nos países DO SAVE ([SavedCountries],
 * tabela `countries`, gravada pelo seed a partir do pack); depois no catálogo
 * fixo do domínio ([Countries]), que cobre os testes e o save que ainda não
 * carregou os países.
 *
 * Mesma postura do `trainingFocus` no `ClubMapper`: a coluna é dado externo,
 * não invariante nosso. Um código fora dos dois catálogos (linha antiga, país
 * removido do pack) cai no país default em vez de derrubar a leitura do clube.
 * Compara pelo texto cru em vez de montar um `CountryCode` primeiro, porque o
 * `init` do value class rejeitaria um valor malformado com exception.
 */
internal fun String.toCountry(): Country = findInCatalog() ?: Countries.DEFAULT

/**
 * Versão para a coluna opcional (segunda nacionalidade): `null` continua `null`
 * e um código fora do catálogo também vira `null`, NUNCA o default. Cair no
 * Brasil aqui daria a um estrangeiro uma segunda nacionalidade brasileira
 * inventada, e `isForeignIn` passaria a tratá-lo como nacional (revisão da PR #149).
 */
internal fun String?.toCountryOrNull(): Country? = this?.findInCatalog()

private fun String.findInCatalog(): Country? =
    SavedCountries.find(this) ?: Countries.all.firstOrNull { it.code.value == this }

/**
 * Os países do save em memória (issue #131), para os mappers — funções de
 * extensão chamadas em todo `toDomain` de clube e jogador — resolverem o
 * trigrama sem consulta ao banco. Quem enche é o `SavedCountryCatalog`, no
 * boot e depois de cada seed.
 *
 * `object` com estado é um singleton global, o que normalmente se evita; aqui
 * é o preço de manter os mappers como funções puras de extensão (trocar todas
 * as assinaturas para receber um catálogo espalharia a mudança por todos os
 * adapters). O estado é um `Map` IMUTÁVEL trocado inteiro: `@Volatile` garante
 * que toda thread enxergue o mapa novo assim que ele é publicado, sem lock.
 */
internal object SavedCountries {
    @Volatile
    private var byCode: Map<String, Country> = emptyMap()

    fun replace(countries: Collection<Country>) {
        byCode = countries.associateBy { it.code.value }
    }

    fun find(code: String): Country? = byCode[code]
}

fun Country.toEntity(): CountryEntity = CountryEntity(
    code = code.value,
    name = name,
    confederation = confederation.name,
    isoAlpha2 = isoAlpha2,
)

/** Linha → domínio. A tabela só é escrita pelo seed (dado já validado pelo pack). */
fun CountryEntity.toDomain(): Country = Country(
    code = CountryCode(code),
    name = name,
    confederation = Confederation.valueOf(confederation),
    isoAlpha2 = isoAlpha2,
)
