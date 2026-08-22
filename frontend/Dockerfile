# Dockerfile — neuroassist-ai-web (Angular 21 + Nx) servido por nginx
#
# Build context = /frontend
#   docker compose -f docker/docker-compose.dev.yml build web
#
# Stage 1 compila o bundle de produção; stage 2 serve os estáticos e faz proxy
# de /api e /oauth2 para o gateway (ver nginx.conf) — o frontend usa base
# relativa (ACCESS_HTTP_BASE = '/api'), então não há CORS nem URL absoluta.

# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — build
# ─────────────────────────────────────────────────────────────────────────────
FROM node:22-alpine AS build

WORKDIR /workspace

# Camada de dependências: só invalida quando os lockfiles mudam
COPY package.json package-lock.json ./
# --legacy-peer-deps: o lockfile foi resolvido assim (jest 29 do @nx/jest vs.
# peerOptional jest 30 do @angular-devkit/build-angular 21.2.18)
RUN npm ci --no-audit --no-fund --legacy-peer-deps

# Código-fonte
COPY . .

ENV NX_DAEMON=false
RUN npx nx build neuroassist-ai-web --configuration=production

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — runtime
# ─────────────────────────────────────────────────────────────────────────────
FROM nginx:1.27-alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/apps/neuroassist-ai-web/browser /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=10s \
  CMD wget -qO- http://127.0.0.1/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
