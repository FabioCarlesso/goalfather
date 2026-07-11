# syntax=docker/dockerfile:1
#
# ATENÇÃO: o contexto de build deste Dockerfile é a RAIZ do repositório, não
# `frontend/` (ver `docker-compose.yml`), porque o build precisa de
# `contract/openapi.yaml` para gerar os tipos TS (issue #65).
#
#   docker build -f frontend/Dockerfile .   # a partir da raiz
#   docker compose build frontend

# ─── Build stage ──────────────────────────────────────────────────────────
FROM node:22-alpine AS build
WORKDIR /app

# Copia lockfile + .npmrc e usa `npm ci`: instalação limpa e reproduzível.
# O .npmrc carrega `legacy-peer-deps=true` (openapi-typescript@7 ainda pede
# peer typescript@^5, mas o projeto usa TS 6); sem ele o npm aborta com ERESOLVE.
COPY frontend/package.json frontend/package-lock.json frontend/.npmrc ./
RUN npm ci

# O contrato vai para /contract porque o script `gen:api` o referencia como
# `../contract/openapi.yaml`: com o app em /app, o caminho relativo bate igual
# dentro e fora do container, e o package.json não precisa saber do Docker.
COPY contract/ /contract/
COPY frontend/ ./

# Build de produção apontando para a API real (sem MSW). `npm run build` já é
# `gen:api && tsc -b && vite build`, então os tipos são SEMPRE gerados a partir
# do contrato — nunca herdados de um `generated.d.ts` do disco de quem builda,
# que é gitignorado e pode estar velho ou ausente (issue #65).
ENV VITE_USE_MOCKS=false
RUN npm run build

# ─── Runtime stage ────────────────────────────────────────────────────────
FROM nginx:1.27-alpine AS runtime
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
