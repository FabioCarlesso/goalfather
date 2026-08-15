package com.carlesso.goalfather.domain.model

import kotlinx.serialization.Serializable

/** Uma temporada da carreira: em que ano foi e como o clube terminou. */
@Serializable
data class CareerCampaign(
    val season: Int,
    val standing: SeasonStanding,
)

/**
 * Perfil do técnico visto pelo clube que ele comanda (issue #60): quantas
 * temporadas disputou, em quais foi campeão e qual foi a melhor campanha.
 *
 * É uma PROJEÇÃO de `List<SeasonRecord>` (ver `careerOf` em
 * `domain/rules/SeasonHistoryRules.kt`), não um agregado persistido: derivar
 * na leitura mantém a história com uma fonte de verdade só e faz a carreira
 * acompanhar de graça qualquer temporada nova.
 *
 * `titles` guarda as temporadas do título em vez de um contador — o número sai
 * do tamanho da lista, e "campeão em 2027 e 2031" é o que o técnico quer ler.
 */
@Serializable
data class ClubCareer(
    val clubId: ClubId,
    val clubName: String,
    val seasonsPlayed: Int,
    val titles: List<Int>,
    val bestCampaign: CareerCampaign? = null,
)
