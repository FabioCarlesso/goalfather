package com.carlesso.goalfather.application.port.out

import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.world.WorldClubId
import com.carlesso.goalfather.domain.model.world.WorldPack
import com.carlesso.goalfather.domain.model.world.WorldPlayerId

/**
 * Port de saída do mundo do jogo (issue #126): de onde vêm países,
 * competições, clubes, elencos e calendário.
 *
 * Quem implementa hoje é o `FileWorldDataProvider`, que lê o pack versionado
 * em `data/world/` como se fosse uma API externa. Trocar por um pack fora do
 * git ou por HTTP é escrever OUTRO adapter deste port — o domínio e os casos
 * de uso não mudam (é a anticorruption layer da arquitetura hexagonal; ver
 * `docs/WORLD_DATA.md`).
 *
 * `suspend` como os outros ports: a leitura é I/O e o adapter a tira da
 * thread do chamador (`withContext(Dispatchers.IO)`).
 */
interface WorldDataProvider {
    /**
     * Carrega e valida o pack inteiro. Nunca lança por dado ruim: pack
     * inválido volta como [PackLoadResult.Invalid] com os erros específicos.
     */
    suspend fun load(): PackLoadResult
}

/**
 * Resultado da carga do pack — erro como VALOR (CLAUDE.md), não exception.
 *
 * `sealed` com dois casos: quem chama faz `when (result)` exaustivo e o
 * compilador não deixa esquecer o caminho de erro. O `Invalid` traz a LISTA de
 * problemas: validar um pack de centenas de arquivos e mostrar um erro por vez
 * transformaria cada correção numa nova rodada de "roda, quebra, conserta".
 */
sealed interface PackLoadResult {
    data class Loaded(val pack: WorldPack) : PackLoadResult

    data class Invalid(val errors: List<PackLoadError>) : PackLoadResult {
        init {
            require(errors.isNotEmpty()) { "Invalid sem erros" }
        }
    }
}

/**
 * O que pode estar errado num pack. [resource] é o caminho lógico do recurso
 * (`competitions/bra-serie-a/clubs.json`), o mesmo que seria a URL relativa
 * numa API — não um caminho de disco, para valer igual em qualquer adapter.
 */
sealed interface PackLoadError {
    /** Um texto curto para log/diagnóstico. Cada variante sabe se descrever. */
    val message: String

    /** Envelope com `schemaVersion` que este código não sabe ler. */
    data class UnsupportedSchemaVersion(val resource: String, val found: Int, val supported: Set<Int>) : PackLoadError {
        override val message: String get() = "$resource: schemaVersion $found não suportada (suportadas: $supported)"
    }

    /** Recurso obrigatório ausente (arquivo não existe / 404). */
    data class MissingResource(val resource: String) : PackLoadError {
        override val message: String get() = "$resource: recurso ausente"
    }

    /** JSON inválido ou fora do formato do schema. */
    data class MalformedResource(val resource: String, val reason: String) : PackLoadError {
        override val message: String get() = "$resource: formato inválido — $reason"
    }

    /** JSON bem formado, mas com um valor que o domínio recusa (capacidade negativa, data invertida...). */
    data class InvalidValue(val resource: String, val reason: String) : PackLoadError {
        override val message: String get() = "$resource: valor inválido — $reason"
    }

    /** Código de país usado sem estar em `countries.json`. */
    data class UnknownCountry(val resource: String, val code: CountryCode) : PackLoadError {
        override val message: String get() = "$resource: país '${code.value}' não está em countries.json"
    }

    /** A competição cita um clube que nenhum `clubs.json` define por completo. */
    data class UnknownClubReference(val competition: CompetitionId, val club: WorldClubId) : PackLoadError {
        override val message: String get() = "competição '${competition.value}' referencia clube inexistente '${club.value}'"
    }

    /** O mesmo clube definido de dois jeitos diferentes em competições diferentes. */
    data class ConflictingClubDefinition(val club: WorldClubId) : PackLoadError {
        override val message: String get() = "clube '${club.value}' definido com dados diferentes em mais de uma competição"
    }

    /** Elenco sem goleiro: o clube não conseguiria escalar um time. */
    data class SquadWithoutGoalkeeper(val club: WorldClubId) : PackLoadError {
        override val message: String get() = "elenco de '${club.value}' não tem goleiro"
    }

    /** O mesmo jogador em mais de um elenco (ou repetido no mesmo). */
    data class DuplicatePlayer(val player: WorldPlayerId, val clubs: List<WorldClubId>) : PackLoadError {
        override val message: String get() = "jogador '${player.value}' aparece em mais de um elenco: $clubs"
    }
}
