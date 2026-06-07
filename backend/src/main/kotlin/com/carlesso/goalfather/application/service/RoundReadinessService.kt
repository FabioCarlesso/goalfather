package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.RoundReadinessUseCase
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.RoundReadinessRepository
import com.carlesso.goalfather.application.port.out.UserRepository
import com.carlesso.goalfather.domain.model.ReadinessStatus
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.UserId
import com.carlesso.goalfather.domain.result.StartRoundResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Implementação da sincronização de rodadas da liga compartilhada (issue #20).
 *
 * Técnico humano = usuário com `clubId != null`. A rodada destrava quando o
 * número de prontos iguala o número de técnicos.
 *
 * A concorrência é o ponto delicado: dois usuários podem clicar "jogar" no
 * mesmo instante após ambos prontos. O [Mutex] em [start] serializa a
 * transição `Scheduled → InProgress`, então apenas uma corrotina dispara a
 * simulação — idioma Kotlin (coroutine-aware) no lugar de `synchronized`, que
 * bloquearia a thread.
 */
class RoundReadinessService(
    private val leagueRepo: LeagueRepository,
    private val userRepo: UserRepository,
    private val readinessRepo: RoundReadinessRepository,
) : RoundReadinessUseCase {

    private val startMutex = Mutex()

    override suspend fun status(): ReadinessStatus =
        buildStatus(leagueRepo.findLatest()?.number ?: 0)

    override suspend fun markReady(userId: UserId): ReadinessStatus {
        val round = leagueRepo.findLatest() ?: return buildStatus(0)
        readinessRepo.markReady(round.number, userId)
        return buildStatus(round.number)
    }

    override suspend fun start(): StartRoundResult = startMutex.withLock {
        val round = leagueRepo.findLatest() ?: return StartRoundResult.NoRound
        if (round.status == RoundStatus.Finished) {
            return StartRoundResult.AlreadyFinished(round.number)
        }

        val status = buildStatus(round.number)
        if (!status.allReady) return StartRoundResult.NotReady(status)

        // Idempotente: se outro técnico já destravou, a rodada está InProgress —
        // não regravamos, só confirmamos para o cliente conectar no WS.
        if (round.status == RoundStatus.Scheduled) {
            leagueRepo.saveRound(round.copy(status = RoundStatus.InProgress))
        }
        StartRoundResult.Started(round.number)
    }

    /**
     * Monta o status comparando técnicos (donos de clube) com quem já marcou
     * pronto. `pending` preserva a ordem de [UserRepository.findManagers] para
     * uma listagem estável no 409/UI.
     */
    private suspend fun buildStatus(roundNumber: Int): ReadinessStatus {
        val managers = userRepo.findManagers()
        val ready = readinessRepo.readyUserIds(roundNumber)
        val pending = managers.filter { it.id !in ready }
        return ReadinessStatus(
            roundNumber = roundNumber,
            readyCount = managers.size - pending.size,
            totalCount = managers.size,
            pendingUsernames = pending.map { it.username },
        )
    }
}
