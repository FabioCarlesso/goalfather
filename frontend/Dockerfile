# syntax=docker/dockerfile:1

# ─── Build stage ──────────────────────────────────────────────────────────
FROM node:22-alpine AS build
WORKDIR /app

# Copia lockfile + .npmrc e usa `npm ci`: instalação limpa e reproduzível.
# O .npmrc carrega `legacy-peer-deps=true` (openapi-typescript@7 ainda pede
# peer typescript@^5, mas o projeto usa TS 6); sem ele o npm aborta com ERESOLVE.
COPY package.json package-lock.json .npmrc ./
RUN npm ci

COPY . .

# Build de produção apontando para a API real (sem MSW). O `generated.d.ts` já
# é versionado; o `gen:api` precisaria de `../contract` (fora do contexto de
# build), então aqui rodamos só typecheck + bundle.
ENV VITE_USE_MOCKS=false
RUN npx tsc -b && npx vite build

# ─── Runtime stage ────────────────────────────────────────────────────────
FROM nginx:1.27-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
