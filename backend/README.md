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
