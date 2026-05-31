# GoalFather — Backend (Kotlin + Spring Boot)

API e engine de simulação do GoalFather. Arquitetura limpa/hexagonal: o
módulo `domain` é puro (sem Spring/JPA). Detalhes em
[`../docs/ARQUITETURA.md`](../docs/ARQUITETURA.md).

## Rodar localmente (sem Docker)

```bash
./gradlew test       # testes (domínio passa sem Spring)
./gradlew bootRun    # sobe em http://localhost:8080 (perfil default: H2 file-based)
```

Estado dev fica em `./data/` (H2 file-based). Para resetar: `rm -rf data/`.

## Docker

Imagem multi-stage (build com Gradle 8.11.1/JDK 21 → runtime JRE 21 Alpine).

```bash
# Build (contexto = este diretório)
docker build -t goalfather-backend .

# Run com H2 in-memory (perfil default)
docker run -p 8080:8080 goalfather-backend

# Run com PostgreSQL (perfil prod)
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/goalfather \
  -e DB_USER=goalfather \
  -e DB_PASSWORD=goalfather \
  goalfather-backend
```

Healthcheck: `GET /actuator/health` (usado pelo `HEALTHCHECK` da imagem e
pelo `depends_on` do docker-compose).

> Para subir backend + PostgreSQL + frontend de uma vez, use o
> [`docker-compose.yml`](../docker-compose.yml) na raiz do repositório.

### Variáveis de ambiente

| Variável | Default | Descrição |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | _(vazio)_ | `prod` ativa o PostgreSQL |
| `DB_URL` | `jdbc:postgresql://localhost:5432/goalfather` | JDBC do Postgres (perfil prod) |
| `DB_USER` | `goalfather` | usuário do banco |
| `DB_PASSWORD` | `goalfather` | senha do banco |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75` | flags da JVM |

## Validação do profile `prod` (PostgreSQL)

O profile `prod` aponta o JPA/Flyway para PostgreSQL. As migrations são
**portáveis** entre H2 (dev/test, com `MODE=PostgreSQL`) e Postgres por
construção:

- Tipos usados — `BIGINT`, `INTEGER`, `VARCHAR(n)`, `TEXT`, `BOOLEAN` — existem
  igualmente nos dois bancos.
- **IDs são fornecidos manualmente** (não há `AUTO_INCREMENT`/`IDENTITY`), então
  não existe a divergência clássica `BIGSERIAL` (Postgres) × `IDENTITY` (H2).
- As colunas JSON (`lineup_json`, `matches_json`, `rows_json`) são `TEXT`
  (serialização via kotlinx.serialization no mapper), não `jsonb` — portável.
- `ddl-auto: validate`: o Hibernate só valida; quem cria o schema é o Flyway.

### Runbook (ponta a ponta)

```bash
# 1. Subir só o Postgres (volume persistente)
docker compose up -d postgres

# 2. Rodar o backend contra ele
SPRING_PROFILES_ACTIVE=prod \
DB_URL=jdbc:postgresql://localhost:5432/goalfather \
DB_USER=goalfather DB_PASSWORD=goalfather \
./gradlew bootRun
# Esperado: Flyway aplica V1+V2 sem erro; /actuator/health = 200.

# 3. E2E contra o backend real (noutro terminal, em frontend/)
cd ../frontend && npm run e2e:real

# 4. Validar persistência: comprar/jogar rodada, reiniciar o backend, conferir
#    que o estado sobreviveu (Postgres + volume).
```

> A execução ao vivo exige Docker e não foi rodada no ambiente de
> desenvolvimento deste commit — o que foi garantido aqui é a **portabilidade
> das migrations** (revisão acima) e o runbook reproduzível.
