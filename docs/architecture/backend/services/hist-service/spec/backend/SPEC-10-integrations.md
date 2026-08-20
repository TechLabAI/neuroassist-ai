# SPEC-10 — Integrations

> Service: `hist-service` · Module: HIST · Refs: ADR-005 · Contract: `contracts/integration-contracts.md`
> EARS notation (D-057).

## Objetivo

Especificar as integrações de HIST: produtores inbound (REST), consumidor DASH/FE (REST), e a única
dependência outbound (ACS `ActorDirectory`), incluindo timeouts, circuit breaker e fallback.

## Escopo

**In:** contratos de integração REST, `ActorDirectoryPort` (resolução de scope de cuidadora), MIG/REG/GWY.
**Out:** broker (SPEC-09); TechLab AI/hub (não há, `ai.hasAi=false`); qualquer canal biométrico/edge.

## Decisões técnicas

- **Inbound**: EVNT/PAC/ACS/EDGE → `POST /audit-facts` (`hist:write`); DASH + `@neuroassist/hist` →
  `GET /history*` (`hist:read`/`hist:read:all`). Todos via GWY, descobertos via REG.
- **Outbound ACS** (`ActorDirectoryPort`): `GET /api/v1/acs/directory/caregivers/{id}/patient-scope`.
  Timeout 2s; Resilience4j CB; **fail-closed** em `hist:read` (nunca ampliar scope não verificável). **Não**
  está no caminho de ingestão (ADR-005): `actorRef` é snapshot do produtor.
- **Infra**: PostgreSQL (`t_<empresa>`); MIG (migrações centralizadas, sem DDL no boot); REG (Eureka); GWY.

## Casos de borda

- ACS timeout/CB OPEN em leitura de cuidadora → `503` (`/errors/scope-unavailable`).
- Coordenador (`hist:read:all`) → HIST não chama ACS; serve tenant inteiro.
- Ingestão durante ACS indisponível → **não afetada** (append não depende de ACS).

## Critérios de aceite (EARS)

- WHEN a caregiver-scoped read needs a patient scope and ACS answers within timeout, THE SYSTEM SHALL
  filter results to the returned scope.
- IF ACS times out, errors, or its circuit is OPEN during a caregiver-scoped read, THEN THE SYSTEM SHALL
  respond `503` and SHALL NOT return unscoped results.
- WHERE the caller holds `hist:read:all`, THE SYSTEM SHALL NOT call ACS and SHALL serve the full tenant
  scope.
- THE SYSTEM SHALL never call ACS on the `POST /audit-facts` ingestion path.
- THE SYSTEM SHALL author its schema changes as MIG-applied migrations and SHALL register with REG for
  discovery.
- THE SYSTEM SHALL depend on no TechLab AI / hub bounded context and on no third-party SDK.
