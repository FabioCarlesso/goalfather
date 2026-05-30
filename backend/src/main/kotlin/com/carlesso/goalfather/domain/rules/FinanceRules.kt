package com.carlesso.goalfather.domain.rules

/**
 * Regras financeiras de rodada (issue #4). Funções PURAS — sem estado,
 * determinísticas. A receita de bilheteria depende só da capacidade do
 * estádio e da força do mandante; a folha salarial é a soma dos salários
 * do elenco, cobrada a cada `SALARY_EVERY_N_ROUNDS` rodadas.
 */

/** Preço do ingresso, em centavos (R$ 50). Constante de domínio. */
const val TICKET_PRICE_CENTS: Long = 50_00

/** Cadência da folha salarial: a cada 2 rodadas (~"um mês" de temporada curta). */
const val SALARY_EVERY_N_ROUNDS: Int = 2

/**
 * Taxa de ocupação do estádio: entre 50% (time fraco) e 100% (time forte),
 * interpolada linearmente pela força do mandante na faixa ~60..100.
 */
fun attendanceRate(homeStrength: Double): Double =
    (0.5 + 0.5 * ((homeStrength - 60.0) / 40.0)).coerceIn(0.5, 1.0)

/** Público pagante = capacidade × taxa de ocupação. */
fun attendance(stadiumCapacity: Int, homeStrength: Double): Int =
    (stadiumCapacity * attendanceRate(homeStrength)).toInt()

/** Receita de bilheteria do mandante, em centavos. */
fun ticketRevenue(stadiumCapacity: Int, homeStrength: Double): Long =
    attendance(stadiumCapacity, homeStrength) * TICKET_PRICE_CENTS

/** Há cobrança de folha salarial nesta rodada? */
fun isSalaryRound(roundNumber: Int): Boolean =
    roundNumber % SALARY_EVERY_N_ROUNDS == 0
