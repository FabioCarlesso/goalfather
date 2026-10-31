package com.carlesso.goalfather.domain.event

import com.carlesso.goalfather.domain.model.BoardReview
import com.carlesso.goalfather.domain.model.CupClub
import com.carlesso.goalfather.domain.model.Retirement
import com.carlesso.goalfather.domain.model.RoundFinance
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings

/**
 * Mensagem do stream de rodada: multiplexa eventos de várias partidas + sinal
 * de fim de rodada/temporada.
 *
 * Tipo de DOMÍNIO, não formato de fio (issue #96): quem vai para o WebSocket
 * `/ws/round/{n}` é o `RoundEventDto` (`adapter/in/web/dto/RoundEventDtos.kt`),
 * que espelha o `oneOf` + `discriminator` do `contract/openapi.yaml`. Por isso
 * este sealed NÃO é `@Serializable` — tentar serializá-lo direto é erro de
 * compilação, e não um vazamento silencioso como o do `Player.star` (issue #72).
 */
sealed interface RoundEvent {

    data class MatchUpdate(
        val matchId: Long,
        val event: MatchEvent,
    ) : RoundEvent

    /**
     * `standings` traz UMA tabela por divisão (issue #47), ordenadas da
     * elite para baixo — todas jogam na mesma rodada global.
     *
     * `training` (issue #58) conta a semana de treino de cada clube: o foco
     * aplicado e quem evoluiu ou se machucou nele. Vem de TODOS os clubes,
     * como `finances` — cabe ao cliente filtrar o do técnico. Default vazio
     * porque o replay de uma rodada já encerrada não reaplica os efeitos:
     * só há tabela para contar.
     */
    data class RoundFinished(
        val standings: List<Standings>,
        val finances: List<RoundFinance> = emptyList(),
        val training: List<TrainingReport> = emptyList(),
    ) : RoundEvent

    /**
     * Emitido quando a última rodada do turno é encerrada: a temporada
     * acabou. Carrega o `champion` (líder da elite — divisão 1) e as
     * tabelas finais de todas as divisões para que o cliente celebre o
     * título. O backend já abriu a próxima temporada (promoção/rebaixamento
     * aplicados, rodada 1 + tabelas zeradas) antes de emitir.
     *
     * `retirements` conta a outra história da virada (issue #55): quem
     * pendurou as chuteiras e qual garoto da base assumiu a vaga. Vem de TODOS
     * os clubes — cabe ao cliente filtrar o que interessa ao técnico.
     *
     * `cupChampion` (issue #61) é o vencedor da copa mata-mata da temporada
     * que acabou — o mesmo que vai para o histórico. Nulo quando não houve
     * copa ou ela não chegou à final.
     *
     * `boardReviews` (issue #62) é o balanço da diretoria de cada clube: meta,
     * posição final e o veredito (bônus, permanência ou demissão). Vem de TODOS
     * os clubes com meta na temporada — cabe ao cliente filtrar o do técnico.
     */
    data class SeasonFinished(
        val season: Int,
        val champion: StandingRow,
        val standings: List<Standings>,
        val retirements: List<Retirement> = emptyList(),
        val cupChampion: CupClub? = null,
        val boardReviews: List<BoardReview> = emptyList(),
    ) : RoundEvent
}
