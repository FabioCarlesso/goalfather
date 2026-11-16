package com.carlesso.goalfather.domain.model

import com.carlesso.goalfather.domain.rules.DEFAULT_TICKET_PRICE_CENTS
import com.carlesso.goalfather.domain.rules.inReplacementOrder
import com.carlesso.goalfather.domain.rules.inStartingOrder
import com.carlesso.goalfather.domain.rules.isForeignAt
import com.carlesso.goalfather.domain.serialization.ClubIdSerializer
import kotlinx.serialization.Serializable

/**
 * Identificador tipado de clube. `@JvmInline value class` — zero
 * overhead em runtime, type-safety em tempo de compilação.
 */
@JvmInline
@Serializable(with = ClubIdSerializer::class)
value class ClubId(val value: Long)

/**
 * Clube — agregado de domínio que reúne caixa, estádio, elenco e
 * escalação. `lineup` é opcional: clubes podem existir sem ter
 * escalação salva (fallback aos 11 primeiros do elenco em 4-4-2).
 *
 * Valores monetários em centavos (Long) — mesma convenção do contrato.
 * `ownerId == null` significa controlado pela IA (preparado para
 * multiplayer da Fase 5). `division` é o tier em que o clube disputa a
 * temporada corrente (issue #47) — muda na virada via promoção/rebaixamento.
 *
 * `trainingFocus` (issue #58) é a decisão da SEMANA, e por isso mora no clube
 * e não na `Lineup`: ela vale entre rodadas, para o elenco inteiro, enquanto a
 * escalação é a decisão da PARTIDA. Fica valendo até o técnico trocar — quem
 * nunca escolheu (e todo clube da IA) treina no default, `DESCANSO`.
 *
 * `ticketPriceCents` (issue #59) é a decisão de BILHETERIA, do mesmo tipo:
 * vale até o técnico mexer e alimenta a curva de demanda em `FinanceRules`.
 * Quem nunca mexeu — e todo clube da IA — cobra `DEFAULT_TICKET_PRICE_CENTS`.
 *
 * `board` (issue #62) é a meta da diretoria para a temporada e a confiança
 * corrente no técnico. Nula só em save anterior à issue, até a primeira rodada
 * fixar a meta — ausência real, modelada no tipo.
 *
 * `version` (issue #89) é a versão do agregado NO MOMENTO DA LEITURA — dado
 * puro, um `Long` sem anotação JPA (regra de ouro do CLAUDE.md). Ela viaja
 * dentro do `copy()` de cada comando até o `ClubRepository.save`, que só grava
 * se o banco ainda estiver nessa versão: é o que faz o lock otimista comparar
 * contra o que o use case LEU, e não contra o que o adapter acabou de reler.
 * Clube ainda não persistido nasce com `0`.
 */
@Serializable
data class Club(
    val id: ClubId,
    val name: String,
    val cash: Money,
    val stadiumCapacity: Int,
    val squad: List<Player>,
    val lineup: Lineup? = null,
    val ownerId: Long? = null,
    val division: Division = Division.FIRST,
    val trainingFocus: TrainingFocus = TrainingFocus.DEFAULT,
    val ticketPriceCents: Money = DEFAULT_TICKET_PRICE_CENTS,
    val board: BoardExpectation? = null,
    val version: Long = 0,
    /**
     * País da federação do clube (issue #123): decide a liga nacional, a
     * continental e quem é estrangeiro no elenco. Nunca nulo — todo clube é de
     * algum país. O default é o mesmo de `Player.nationality`.
     */
    val country: Country = Countries.DEFAULT,
) {
    init {
        require(!cash.isNegative) { "cash não pode ser negativo: $cash" }
        require(stadiumCapacity >= 0) { "stadiumCapacity não pode ser negativo: $stadiumCapacity" }
        // A FAIXA de preço (`TICKET_PRICE_RANGE`) NÃO é checada aqui de
        // propósito: ela é regra de balanceamento, não invariante estrutural do
        // agregado. Vive no comando que aceita a decisão do técnico
        // (`SetTicketPriceService`, que a devolve como valor — issue #59), e o
        // mapper de leitura acomoda uma linha antiga fora da faixa em vez de
        // derrubar o carregamento do clube. Um `require` aqui transformaria a
        // regra de negócio em exception, contra o CLAUDE.md.
    }

    /**
     * Escalação que o time DE FATO entra em campo — `lineup` salvo, ou
     * fallback dos 11 primeiros do elenco em 4-4-2.
     *
     * Duas correções sobre o `lineup` salvo, que é só uma FOTO do momento em
     * que o técnico escalou e envelhece a cada rodada:
     *
     * 1. **Reidratação pelo id a partir do `squad`**, única fonte de verdade
     *    do estado do jogador — sem isso a fadiga da issue #54 nunca chegaria
     *    à força do time. `mapNotNull` descarta quem saiu do elenco desde a
     *    escalação (jogador vendido).
     * 2. **Lesionado não entra em campo.** `SaveLineupService` recusa escalar
     *    lesionado, mas isso vale para o momento de SALVAR: quem se machuca
     *    depois continuaria titular na rodada seguinte se a escalação não
     *    fosse revalidada aqui, no apito inicial.
     * 3. **Suspenso não entra em campo** (issue #130) — mas só nos jogos da
     *    [competition] em que cumpre gancho: suspenso na liga joga a copa.
     * 4. **Limite de estrangeiros** (issue #130): com [foreignPlayerLimit], os
     *    estrangeiros além do limite ficam fora (os últimos da escalação), e a
     *    vaga vai para um reserva que não estoure o limite. É o que faz a
     *    escalação AUTOMÁTICA (clube da IA, sem `lineup`) respeitar a regra.
     *
     * Sem escalação salva (clube da IA, técnico que nunca salvou), o time é o
     * melhor 4-4-2 dos aptos ([inStartingOrder]), e não os 11 primeiros do
     * elenco: a ordem do elenco é a da persistência, e um jogador comprado
     * (id baixo do mercado) ou relido do banco em outra ordem punha dois
     * goleiros em campo (revisão da PR #154).
     *
     * Com escalação salva, cada vaga aberta vai para o melhor reserva apto da
     * MESMA posição de quem saiu ([inReplacementOrder]); sem um, para o melhor
     * reserva que sobrar. Preencher "na ordem do elenco" punha o goleiro reserva
     * no lugar do atacante lesionado, porque o seed lista os reservas por
     * posição, goleiros primeiro (revisão da PR #154). Sem reservas aptos
     * suficientes o time entra desfalcado, com menos de 11 — que é o resultado
     * correto para um elenco dizimado.
     *
     * Os parâmetros têm default `null` (sem competição, sem limite): partida
     * avulsa e telas que não sabem o jogo seguinte continuam chamando
     * `startingLineup()` como antes.
     */
    fun startingLineup(competition: CompetitionId? = null, foreignPlayerLimit: Int? = null): Lineup {
        val byId = squad.associateBy { it.id }
        val eligible = squad.filter { it.canPlayIn(competition) }
        val saved = lineup

        val starters = saved?.players.orEmpty().mapNotNull { byId[it.id] }.filter { it.canPlayIn(competition) }
        val onField = starters.map { it.id }.toSet()
        // Titulares primeiro, depois reservas: o filtro de estrangeiros corta
        // sempre do fim, então quem o técnico escalou tem prioridade.
        val reserves = eligible.filterNot { it.id in onField }
        // Posição de cada titular salvo que ficou fora (lesionado, suspenso ou
        // vendido). A do `squad` vale mais que a da foto salva; o vendido só
        // existe na foto. `?:` escolhe sem `if`.
        val vacancies = saved?.players.orEmpty()
            .filterNot { it.id in onField }
            .map { byId[it.id]?.position ?: it.position }
        val bench = if (saved == null) reserves.inStartingOrder() else reserves.inReplacementOrder(vacancies)
        val candidates = starters + bench
        val players = candidates.withinForeignLimit(foreignPlayerLimit).take(11)

        return saved?.copy(players = players) ?: Lineup(formation = Formation.F_4_4_2, players = players)
    }

    /**
     * Mantém a ordem e pula o estrangeiro que estouraria [limit]. `fold` com um
     * `Pair` acumula a lista e a contagem sem `var` — o "for com contador" do
     * Java vira uma redução imutável.
     */
    private fun List<Player>.withinForeignLimit(limit: Int?): List<Player> {
        if (limit == null) return this
        return fold(emptyList<Player>() to 0) { (kept, foreigners), player ->
            when {
                !player.isForeignAt(this@Club) -> (kept + player) to foreigners
                foreigners < limit -> (kept + player) to foreigners + 1
                else -> kept to foreigners
            }
        }.first
    }
}
