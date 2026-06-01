# syntax=docker/dockerfile:1

# ─── Build stage ──────────────────────────────────────────────────────────
# Gradle 8.11.1 + JDK 21 (mesma versão do wrapper). Copiamos os arquivos de
# build ANTES do código-fonte para que a camada de dependências fique em cache
# e só seja refeita quando build.gradle.kts/settings mudarem.
FROM gradle:8.11.1-jdk21 AS build
WORKDIR /workspace

COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
# Pré-resolve plugins (fase de configuração) + dependências para cachear este
# layer. SEM "|| true": se a rede falhar aqui, o build deve quebrar AGORA em vez
# de gravar um cache incompleto que faz o bootJar seguinte falhar sem explicação.
RUN gradle --no-daemon dependencies

COPY src ./src
RUN gradle --no-daemon clean bootJar

# ─── Runtime stage ────────────────────────────────────────────────────────
# JRE Alpine (~små) — imagem final enxuta, sem Gradle nem fontes.
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Usuário não-root.
RUN addgroup -S app && adduser -S app -G app

COPY --from=build /workspace/build/libs/goalfather.jar app.jar
USER app

EXPOSE 8080

# Configuração via ambiente (profile prod lê DB_URL/DB_USER/DB_PASSWORD):
#   docker run -e SPRING_PROFILES_ACTIVE=prod -e DB_URL=... goalfather-backend
ENV SPRING_PROFILES_ACTIVE=""
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
