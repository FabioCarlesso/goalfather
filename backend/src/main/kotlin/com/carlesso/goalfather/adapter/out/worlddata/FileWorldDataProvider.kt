package com.carlesso.goalfather.adapter.out.worlddata

import com.carlesso.goalfather.application.port.out.PackLoadResult
import com.carlesso.goalfather.application.port.out.WorldDataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

/**
 * Adapter de arquivo do [WorldDataProvider] (issue #126): lê o pack de um
 * diretório (`data/world/v1` no repositório) tratando cada arquivo como a
 * resposta de um endpoint REST.
 *
 * Toda a tradução e validação ficam no [WorldPackLoader]/[PackMapper]; este
 * adapter só sabe "ler um recurso do disco". Por isso trocar por HTTP é
 * escrever um `HttpWorldDataProvider` que passa outra função `read` ao mesmo
 * loader (ver `docs/WORLD_DATA.md`).
 *
 * Preguiçoso de propósito: nada é lido no construtor, então o bean sobe mesmo
 * sem o pack no disco (ex.: imagem Docker, que não copia `data/`) — a ausência
 * aparece como [com.carlesso.goalfather.application.port.out.PackLoadError.MissingResource]
 * quando alguém chamar [load].
 */
class FileWorldDataProvider(
    private val root: Path,
    private val loader: WorldPackLoader = WorldPackLoader(),
) : WorldDataProvider {

    /** I/O bloqueante do `java.nio` vai para o pool de I/O, como nos adapters de persistência. */
    override suspend fun load(): PackLoadResult = withContext(Dispatchers.IO) {
        // `::readResource` é uma referência de função: passa o método como o
        // valor `suspend (String) -> String?` que o loader espera (uma função
        // comum é aceita onde se pede uma `suspend`).
        loader.load(read = ::readResource, calendarYears = calendarYears())
    }

    /**
     * Texto do recurso, ou `null` se não existe. O `normalize` + `startsWith`
     * é defesa em profundidade contra caminho fora do pack (o loader já
     * recusa ids com `/` ou `..`).
     */
    private fun readResource(resource: String): String? {
        val base = root.toAbsolutePath().normalize()
        val file = base.resolve(resource).normalize()
        return if (file.startsWith(base) && file.isRegularFile()) file.readText() else null
    }

    /**
     * Anos com calendário: os `calendar/{ano}.json` presentes. Num adapter
     * HTTP isso viria de um índice do servidor; no disco, listar a pasta é o
     * equivalente. `toIntOrNull` ignora arquivo cujo nome não é um ano.
     */
    private fun calendarYears(): List<Int> {
        val dir = root.resolve("calendar")
        if (!dir.isDirectory()) return emptyList()
        return dir.listDirectoryEntries("*.json")
            .filter { it.isRegularFile() }
            .mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            .sorted()
    }
}
