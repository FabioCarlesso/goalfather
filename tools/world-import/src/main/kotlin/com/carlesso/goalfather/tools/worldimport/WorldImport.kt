package com.carlesso.goalfather.tools.worldimport

import com.carlesso.goalfather.adapter.out.worlddata.FileWorldDataProvider
import com.carlesso.goalfather.application.port.out.PackLoadResult
import com.carlesso.goalfather.tools.worldimport.assemble.AssemblyResult
import com.carlesso.goalfather.tools.worldimport.assemble.PackAssembler
import com.carlesso.goalfather.tools.worldimport.config.CuratedInputs
import com.carlesso.goalfather.tools.worldimport.output.PackWriter
import com.carlesso.goalfather.tools.worldimport.output.Provenance
import com.carlesso.goalfather.tools.worldimport.wikidata.CacheMode
import com.carlesso.goalfather.tools.worldimport.wikidata.SparqlClient
import com.carlesso.goalfather.tools.worldimport.wikidata.WikidataFetcher
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.system.exitProcess

/** Versão do importador, gravada no envelope. Suba ao mudar regra que altera a saída. */
const val IMPORTER_VERSION = "1.0.0"

/**
 * Ponto de entrada de `./gradlew worldImport` (issue #127). Roda da raiz do
 * repositório:
 *
 * 1. lê a curadoria (os YAML de `data/world/`);
 * 2. consulta o Wikidata (ou o cache em `tools/world-import/cache/`);
 * 3. monta o pack ([PackAssembler], puro) e o escreve em `data/world/v1/`;
 * 4. carrega o resultado com o MESMO adapter do backend e falha se ele
 *    não for um pack válido.
 *
 * Argumentos: `--offline` (só cache: reprodutível, sem rede) e `--refresh`
 * (ignora o cache e consulta tudo de novo).
 */
fun main(args: Array<String>) {
    val mode = when {
        "--offline" in args -> CacheMode.OFFLINE
        "--refresh" in args -> CacheMode.REFRESH
        else -> CacheMode.PREFER_CACHE
    }
    val worldDir = Path.of("data/world")
    val result = WorldImport(
        worldDir = worldDir,
        packDir = worldDir.resolve("v1"),
        client = SparqlClient(Path.of("tools/world-import/cache"), mode),
    ).run()
    exitProcess(if (result) 0 else 1)
}

/** O fluxo do importador, separado do `main` para o teste rodar com cache e diretórios temporários. */
class WorldImport(
    private val worldDir: Path,
    private val packDir: Path,
    private val client: SparqlClient,
    private val log: (String) -> Unit = ::println,
) {
    fun run(): Boolean {
        val inputs = CuratedInputs.read(worldDir)
        val settings = inputs.competitions.import
        val snapshot = WikidataFetcher(client).fetch(
            clubsByCompetition = inputs.competitions.competitions.associate { source ->
                source.competition.id to source.clubs.mapNotNull { it.wikidata }
            },
            extraPlayers = inputs.overrides.players.filterValues { it.club != null }.keys,
            minMembershipStart = LocalDate.parse(settings.minMembershipStart),
            minBirthDate = LocalDate.parse(settings.minBirthDate),
        )

        // `when` exaustivo sobre o resultado sealed: os dois caminhos tratados.
        val assembled = when (val result = PackAssembler(inputs, snapshot).assemble()) {
            is AssemblyResult.Failed -> {
                log("Importação falhou — corrija a curadoria (data/world/*.yaml):")
                result.errors.forEach { log("  - $it") }
                return false
            }
            is AssemblyResult.Assembled -> result
        }

        // Segundos bastam para proveniência; o cache antigo guardava nanossegundos.
        val retrievedAt = snapshot.retrievedAt.truncatedTo(ChronoUnit.SECONDS)
        val date = retrievedAt.toString().take(10)
        PackWriter(
            packDir,
            Provenance(
                generatedAt = retrievedAt.toString(),
                source = "Wikidata (CC0), consulta de $date; curadoria em data/world/*.yaml",
                generator = "world-import $IMPORTER_VERSION",
            ),
        ).write(assembled.pack)

        with(assembled.report) {
            log("Pack gerado em $packDir: $clubs clubes, $realPlayers jogadores reais, $generatedPlayers gerados")
            dropped.forEach { (reason, count) -> log("  descartados ($reason): $count") }
            warnings.forEach { log("  aviso: $it") }
        }
        return validate()
    }

    /** O pack gerado tem que carregar sem erro no adapter do backend (#126). */
    private fun validate(): Boolean = when (val loaded = runBlocking { FileWorldDataProvider(packDir).load() }) {
        is PackLoadResult.Loaded -> true.also { log("Validação: pack carregado pelo FileWorldDataProvider sem erros") }
        is PackLoadResult.Invalid -> false.also {
            log("Validação FALHOU:")
            loaded.errors.forEach { log("  - ${it.message}") }
        }
    }
}
