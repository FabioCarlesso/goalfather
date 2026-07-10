package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.RoundReadinessUseCase
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.RoundReadinessRepository
import com.carlesso.goalfather.application.port.out.UserRepository
import com.carlesso.goalfather.domain.model.ReadinessStatus
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.UserId
import com.carlesso.goalfather.domain.result.StartRoundResult
import java.time.Clock
import java.time.Duration

/**
 * Implementação da sincronização de rodadas da liga compartilhada (issue #20)
 * + escape hatch para técnico ausente (issue #45).
 *
 * Técnico humano = usuário com `clubId != null`. A rodada destrava quando o
 * número de prontos iguala o número de técnicos — OU quando o timeout expira.
 *
 * **Escape hatch (issue #45):** um único dono offline travava a liga para
 * sempre. Agora, assim que o PRIMEIRO técnico sinaliza pronto, começa a correr
 * [timeout]; ao expirar, o gate passa a aceitar o start mesmo com pendentes.
 * Os ausentes entram com a última escalação salva no clube — a engine já usa
 * `club.startingLineup()` em [PlayRoundService], então não é preciso "escalar
 * por eles" aqui; nada a fazer no caminho de simulação.
 *
 * O tempo entra por [Clock] injetável (mesmo espírito do `Random` da engine):
 * em teste, um `Clock` fixo/adiantável verifica o timeout sem `Thread.sleep`.
 *
 * A concorrência é o ponto delicado: dois usuários podem clicar "jogar" no
 * mesmo instante após ambos prontos. A transição `Scheduled → InProgress` é
 * serializada no banco por [LeagueRepository.startRound] (lock otimista via
 * `@Version` na rodada, issue #46) — antes era um `Mutex` in-JVM, que só valia
 * com instância única. O perdedor da corrida recebe `false` e o trata como
 * sucesso idempotente: a rodada está liberada, é só conectar no WS.
 */
class RoundReadinessService(
    private val leagueRepo: LeagueRepository,
    private val userRepo: UserRepository,
    private val readinessRepo: RoundReadinessRepository,
    private val timeout: Duration = Duration.ofMinutes(2),
    private val clock: Clock = Clock.systemUTC(),
) : RoundReadinessUseCase {

    override suspend fun status(): ReadinessStatus =
        buildStatus(leagueRepo.findLatest()?.number ?: 0)

    override suspend fun markReady(userId: UserId): ReadinessStatus {
        val round = leagueRepo.findLatest() ?: return buildStatus(0)
        // Só técnicos (donos de clube) sinalizam prontidão. Sem o guard, um
        // usuário autenticado sem clube criaria linha órfã em `round_readiness`
        // (ignorada na contagem, mas lixo persistido) — issue #20, ponto #6.
        if (userRepo.findManagers().any { it.id == userId }) {
            readinessRepo.markReady(round.number, userId)
        }
        return buildStatus(round.number)
    }

    override suspend fun start(): StartRoundResult {
        val round = leagueRepo.findLatest() ?: return StartRoundResult.NoRound
        if (round.status == RoundStatus.Finished) {
            return StartRoundResult.AlreadyFinished(round.number)
        }

        val status = buildStatus(round.number)
        // Gate agora é "todos prontos OU timeout estourado" (issue #45): um
        // ausente não segura mais a liga indefinidamente.
        if (!status.canStart) return StartRoundResult.NotReady(status)

        // Idempotente: se outro técnico (nesta ou noutra instância) já destravou,
        // `startRound` devolve false. Isso engloba dois casos — já `InProgress`
        // (sucesso: o cliente conecta no WS) ou já `Finished` (a rodada foi
        // simulada entre o `findLatest` e aqui). Só o segundo muda a resposta,
        // então relemos ESTA rodada por número para não anunciar `Started` de
        // uma rodada encerrada. Reler por `findLatest()` não serviria: quando o
        // vencedor conclui a finalização ele já gerou a rodada `N+1` (Scheduled),
        // que passaria a ser a "última" e mascararia o encerramento da nossa.
        if (round.status == RoundStatus.Scheduled && !leagueRepo.startRound(round.number)) {
            val fresh = leagueRepo.findRound(round.number)
            if (fresh != null && fresh.status == RoundStatus.Finished) {
                return StartRoundResult.AlreadyFinished(fresh.number)
            }
        }
        return StartRoundResult.Started(round.number)
    }

    /**
     * Monta o status comparando técnicos (donos de clube) com quem já marcou
     * pronto. `pending` preserva a ordem de [UserRepository.findManagers] para
     * uma listagem estável no 409/UI.
     *
     * Também resolve o timeout do escape hatch (issue #45): o cronômetro só
     * corre quando há pendentes E ao menos um técnico já sinalizou — ou seja,
     * nunca destrava uma liga sem nenhum pronto (o primeiro "pronto" arma o
     * relógio). `secondsRemaining` fica `null` fora dessa janela.
     */
    private suspend fun buildStatus(roundNumber: Int): ReadinessStatus {
        val managers = userRepo.findManagers()
        val ready = readinessRepo.readyUserIds(roundNumber)
        val pending = managers.filter { it.id !in ready }
        val hasPending = managers.isNotEmpty() && pending.isNotEmpty()

        // Só faz sentido cronometrar quando ainda há pendentes e alguém já se
        // marcou pronto (o primeiro "pronto" ancora o timeout).
        val firstReady = if (hasPending && ready.isNotEmpty()) readinessRepo.firstReadyAt(roundNumber) else null
        val secondsRemaining = firstReady?.let {
            val remaining = Duration.between(clock.instant(), it.plus(timeout))
            remaining.seconds.coerceAtLeast(0).toInt()
        }

        return ReadinessStatus(
            roundNumber = roundNumber,
            readyCount = managers.size - pending.size,
            totalCount = managers.size,
            pendingUsernames = pending.map { it.username },
            secondsRemaining = secondsRemaining,
            // Expirou quando o cronômetro estava armado e chegou a zero.
            timedOut = secondsRemaining == 0,
        )
    }
}
