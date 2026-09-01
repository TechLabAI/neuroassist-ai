# SPEC-07 — Observability and Health

## Objetivo
Definir health/readiness, tracing por correlação e sinais operacionais — em especial tornar a
**degradação observável** (por que a busca/voz mudou), atendendo ao risco R4.

## Escopo
**In:** `GET /health` (projeção de domínio), Actuator, `X-Correlation-Id`, sinal `ConvServedDegraded`,
métricas mínimas.
**Out:** dashboards (DASH), pipeline de auditoria persistente (HIST).

## Decisões técnicas
- Actuator `health` é a fonte canônica; `GET /api/v1/conv/health` é a projeção de domínio que reporta a
  alcançabilidade das dependências soft e o estado `degradedCapabilities`.
- Toda seleção é auditável no HIST como vinda da **sessão do paciente** (`ADR-003`).
- `X-Correlation-Id` propagado a todas as chamadas downstream e ecoado nas respostas.

## Implementação
- Health projeta: `elasticsearch` (REACHABLE/UNREACHABLE), `audioStore`, `acs`, `degradedCapabilities[]`.
  `status` = UP enquanto navegação funciona; DEGRADED se ACS indisponível (hard dep).
- Emissão de `ConvServedDegraded` quando servir em modo degradado (ES down ou áudio ausente).
- Métricas: contadores de `retrieval=semantic|static`, `playback=audio-cacheado|web-speech`, latência de
  `/search` e de checagem de blacklist.
- Convenções de trace/pipeline-events padrão do monorepo.

## Casos de borda
- ES indisponível → `elasticsearch: UNREACHABLE`, `semantic-search` em `degradedCapabilities`, `status: UP`.
- ACS indisponível → `acs: UNREACHABLE`, `status: DEGRADED`.
- Áudio ausente → `cached-audio` em `degradedCapabilities` na janela do request.

## Critérios de aceite (EARS)
- WHILE Elasticsearch is unreachable, THE SYSTEM SHALL report `elasticsearch: UNREACHABLE` and include `semantic-search` in `degradedCapabilities` while keeping `status: UP`.
- IF ACS is unreachable, THEN THE SYSTEM SHALL report `acs: UNREACHABLE` and `status: DEGRADED`.
- WHEN a request is served in degraded mode, THE SYSTEM SHALL emit a `ConvServedDegraded` fact naming the `capability` and `reason`.
- THE SYSTEM SHALL propagate `X-Correlation-Id` to every downstream call and echo it on the response.
