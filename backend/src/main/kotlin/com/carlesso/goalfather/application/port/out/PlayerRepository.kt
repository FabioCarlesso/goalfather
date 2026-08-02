package com.carlesso.goalfather.application.port.out

import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId

/**
 * Port de saída para os jogadores que NÃO estão num elenco — os que o
 * `ClubRepository` não alcança, já que ele só sincroniza o `squad` de um clube.
 *
 * Existe por causa da virada de temporada (issue #55): quem está anunciado no
 * mercado também envelhece, e quem se aposenta precisa sair do banco em vez de
 * virar linha órfã com `club_id = null` — indistinguível de um agente livre.
 */
interface PlayerRepository {

    /**
     * Grava jogadores sem clube (mercado/agentes livres). Não mexe no vínculo
     * com clube: quem está num elenco continua sendo gravado via
     * `ClubRepository.save`.
     */
    suspend fun saveFreeAgents(players: List<Player>)

    /** Apaga jogadores de vez — aposentadoria. */
    suspend fun deleteAll(ids: List<PlayerId>)
}
