package com.carlesso.goalfather.domain.model

/**
 * Os termos pessoais oferecidos a um jogador (issue #138): salário mensal,
 * duração e, opcionalmente, cláusula de rescisão. Vale para as três formas de
 * assinar — contratar um livre do mercado, fechar uma transferência entre
 * técnicos e renovar com o próprio clube. Quem diz "sim" ou "não" é o jogador,
 * pela regra pura `playerResponse` (`ContractNegotiationRules`).
 *
 * [years] são TEMPORADAS, na mesma conta do `signContract` (#136): a temporada
 * corrente conta como a primeira. Uma duração fora do que o jogador topa não é
 * erro de validação — é a resposta dele (`TOO_SHORT`/`TOO_LONG`) —, por isso o
 * `init` não a restringe. Já salário negativo e cláusula não positiva são
 * pedidos sem sentido, e o adapter web os barra antes (400) de chegarem aqui.
 */
data class ContractOffer(
    val salary: Money,
    val years: Int,
    val releaseClause: Money? = null,
) {
    init {
        require(!salary.isNegative) { "salário negativo: $salary" }
        require(salary <= MAX_SALARY) { "salário acima do teto: $salary" }
        require(releaseClause == null || releaseClause.isPositive) { "cláusula de rescisão deve ser positiva: $releaseClause" }
    }
}

/**
 * Teto do salário mensal oferecível: US$ 100 milhões — muito acima de qualquer
 * exigência do jogo. Existe para a folha (soma dos salários de um elenco, em
 * `Long` de centavos) nunca estourar na virada do encaixe por um número
 * absurdo vindo do cliente. Como `companion` seria verboso aqui, é uma
 * constante de topo no mesmo arquivo — o idioma Kotlin para "estático".
 */
val MAX_SALARY: Money = Money.dollars(100_000_000)
