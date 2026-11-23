package com.carlesso.goalfather.application.seed.dsl

import com.carlesso.goalfather.domain.rules.NamePool
import kotlin.random.Random

/**
 * Nomes plausíveis por país para jogadores GERADOS: os que completam elencos
 * curtos no importador (issue #127) e os jovens da base de cada virada (issue
 * #139). Mora no backend desde a #139 — o importador compila junto do `main`
 * e continua usando daqui.
 *
 * O Brasil reusa o gerador de nomes da #88 (`Random.playerNames`, em
 * `SyntheticSquad.kt`). Os outros países usam listas próprias com o MESMO
 * esquema (prenome + sobrenome sorteados, distintos dos já usados no clube).
 * São nomes comuns, não de jogadores reais: a combinação é aleatória.
 *
 * O contrato [NamePool] é do domínio (a regra da base pede nomes sem saber de
 * onde vêm); as listas são dado, e ficam aqui.
 */

/** Pool de listas: o mesmo `generateSequence` + `distinct` do gerador da #88. */
private class ListPool(private val first: List<String>, private val last: List<String>) : NamePool {
    override fun names(random: Random, taken: Set<String>): Sequence<String> =
        generateSequence { "${first.random(random)} ${last.random(random)}" }
            .distinct()
            .filterNot { it in taken }
}

private val ENGLISH = ListPool(
    first = listOf(
        "Adam", "Alfie", "Ben", "Callum", "Charlie", "Connor", "Daniel", "Dominic", "Ethan", "Finley",
        "George", "Harry", "Harvey", "Jack", "Jacob", "Jake", "James", "Joe", "Jordan", "Josh",
        "Kieran", "Lewis", "Liam", "Luke", "Mason", "Matthew", "Nathan", "Oliver", "Reece", "Ryan",
        "Sam", "Scott", "Tom", "Tyler", "William",
    ),
    last = listOf(
        "Bailey", "Barnes", "Bennett", "Brooks", "Carter", "Clarke", "Cole", "Cooper", "Davies", "Edwards",
        "Evans", "Fletcher", "Green", "Hall", "Harris", "Hughes", "Jackson", "Johnson", "King", "Lewis",
        "Marshall", "Mills", "Morgan", "Morris", "Parker", "Phillips", "Robinson", "Shaw", "Taylor", "Turner",
        "Walker", "Ward", "Watson", "Wilson", "Wright",
    ),
)

private val SPANISH = ListPool(
    first = listOf(
        "Adrián", "Alberto", "Alejandro", "Álvaro", "Andrés", "Antonio", "Borja", "Carlos", "Daniel", "David",
        "Diego", "Eduardo", "Enrique", "Fernando", "Francisco", "Gonzalo", "Hugo", "Iker", "Iván", "Javier",
        "Jorge", "José", "Juan", "Luis", "Manuel", "Marcos", "Mario", "Miguel", "Pablo", "Pedro",
        "Raúl", "Rubén", "Sergio", "Víctor", "Xabier",
    ),
    last = listOf(
        "Alonso", "Álvarez", "Blanco", "Castro", "Cruz", "Delgado", "Díaz", "Domínguez", "Fernández", "García",
        "Gil", "Gómez", "González", "Gutiérrez", "Hernández", "Iglesias", "Jiménez", "López", "Martín", "Martínez",
        "Molina", "Moreno", "Muñoz", "Navarro", "Ortega", "Pérez", "Ramírez", "Ramos", "Rodríguez", "Romero",
        "Rubio", "Ruiz", "Sánchez", "Serrano", "Torres",
    ),
)

/** Espanhol da América do Sul: prenomes e sobrenomes mais comuns por lá. */
private val SOUTH_AMERICAN_SPANISH = ListPool(
    first = listOf(
        "Agustín", "Alexis", "Brian", "Bruno", "Cristian", "Damián", "Emiliano", "Ezequiel", "Facundo", "Federico",
        "Franco", "Gastón", "Gonzalo", "Ignacio", "Jhon", "Joaquín", "Jonathan", "Juan Pablo", "Julián", "Kevin",
        "Leandro", "Lautaro", "Lucas", "Luciano", "Matías", "Maximiliano", "Nahuel", "Nicolás", "Rodrigo", "Santiago",
        "Sebastián", "Tomás", "Valentín", "Walter", "Yeferson",
    ),
    last = listOf(
        "Acosta", "Aguirre", "Benítez", "Cabrera", "Cáceres", "Castillo", "Duarte", "Espinoza", "Fernández", "Flores",
        "Giménez", "Gómez", "González", "Herrera", "Ibarra", "Medina", "Méndez", "Morales", "Núñez", "Ortiz",
        "Pereyra", "Quintero", "Ramírez", "Ríos", "Rojas", "Romero", "Ruiz", "Silva", "Sosa", "Suárez",
        "Torres", "Valdez", "Vargas", "Vega", "Villalba",
    ),
)

private val ITALIAN = ListPool(
    first = listOf(
        "Alessandro", "Andrea", "Antonio", "Christian", "Daniele", "Davide", "Emanuele", "Fabio", "Federico", "Filippo",
        "Francesco", "Gabriele", "Giacomo", "Gianluca", "Giorgio", "Giovanni", "Giuseppe", "Leonardo", "Lorenzo", "Luca",
        "Manuel", "Marco", "Matteo", "Mattia", "Michele", "Nicola", "Nicolò", "Paolo", "Pietro", "Riccardo",
        "Roberto", "Simone", "Stefano", "Tommaso", "Valerio",
    ),
    last = listOf(
        "Barbieri", "Bianchi", "Bruno", "Caruso", "Colombo", "Conti", "Costa", "D'Amico", "De Luca", "Esposito",
        "Fabbri", "Ferrara", "Ferrari", "Fontana", "Gallo", "Giordano", "Greco", "Lombardi", "Mancini", "Marchetti",
        "Mariani", "Marino", "Martini", "Moretti", "Pellegrini", "Ricci", "Rinaldi", "Rizzo", "Romano", "Rossi",
        "Russo", "Santoro", "Serra", "Testa", "Villa",
    ),
)

private val GERMAN = ListPool(
    first = listOf(
        "Alexander", "Andreas", "Benedikt", "Christian", "Daniel", "Dennis", "Dominik", "Fabian", "Felix", "Florian",
        "Jan", "Jannik", "Jonas", "Julian", "Kai", "Kevin", "Lars", "Leon", "Luca", "Lukas",
        "Marcel", "Marco", "Max", "Maximilian", "Moritz", "Niklas", "Nico", "Pascal", "Patrick", "Philipp",
        "Robin", "Sebastian", "Simon", "Tim", "Tobias",
    ),
    last = listOf(
        "Bauer", "Becker", "Braun", "Fischer", "Frank", "Friedrich", "Fuchs", "Hartmann", "Hoffmann", "Hofmann",
        "Keller", "Klein", "Koch", "Krüger", "Lang", "Lehmann", "Meyer", "Müller", "Neumann", "Richter",
        "Schäfer", "Schmid", "Schmidt", "Schneider", "Scholz", "Schröder", "Schulz", "Schwarz", "Wagner", "Walter",
        "Weber", "Werner", "Wolf", "Zimmermann", "Ziegler",
    ),
)

private val FRENCH = ListPool(
    first = listOf(
        "Adrien", "Alexandre", "Antoine", "Arthur", "Baptiste", "Benjamin", "Clément", "Corentin", "Damien", "Enzo",
        "Florian", "Hugo", "Jérémy", "Jordan", "Julien", "Kévin", "Killian", "Louis", "Lucas", "Mathieu",
        "Mathis", "Maxime", "Nicolas", "Noah", "Olivier", "Pierre", "Quentin", "Rayan", "Romain", "Samuel",
        "Théo", "Thomas", "Valentin", "Yanis", "Yoann",
    ),
    last = listOf(
        "André", "Bernard", "Bertrand", "Blanc", "Bonnet", "Boyer", "Chevalier", "David", "Dubois", "Dupont",
        "Durand", "Fontaine", "Fournier", "Garnier", "Girard", "Gauthier", "Lambert", "Laurent", "Lefebvre", "Leroy",
        "Martin", "Mercier", "Michel", "Moreau", "Morel", "Petit", "Richard", "Robert", "Roux", "Simon",
        "Thomas", "Vincent", "Dumont", "Faure", "Rousseau",
    ),
)

/** O gerador da #88, que já é de nomes brasileiros, embrulhado no contrato [NamePool]. */
private val BRAZILIAN = NamePool { random, taken -> random.playerNames(taken) }

/**
 * Pool pelo trigrama FIFA do país do clube. País sem lista própria usa a de
 * língua mais próxima; o fallback final é o brasileiro (o país-sede do jogo).
 */
fun namePoolFor(countryCode: String): NamePool = when (countryCode) {
    "BRA" -> BRAZILIAN
    "ENG" -> ENGLISH
    "ESP" -> SPANISH
    "ITA" -> ITALIAN
    "GER" -> GERMAN
    "FRA" -> FRENCH
    "ARG", "URU", "PAR", "CHI", "COL", "ECU", "PER", "BOL", "VEN" -> SOUTH_AMERICAN_SPANISH
    else -> BRAZILIAN
}
