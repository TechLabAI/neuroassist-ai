# SPEC-01 — API Surface

> Service: `hist-service` · Module: HIST · Refs: ADR-004, ADR-005, ADR-006 · Contract: `contracts/api-contract.md`
> EARS notation (D-057). Guide: `docs/architecture/backend/standards/ears-notation.md`.

## Objetivo

Definir a superfície REST completa de `hist-service`: os endpoints, seus contratos de status/erro, headers
obrigatórios e o mapeamento para os casos de uso da camada de aplicação. É a spec-âncora que as SPEC-02
(ingestão) e SPEC-03 (consulta) detalham.

## Escopo

**In:** dois surfaces REST — ingestão (`/audit-facts`) e histórico (`/history`) — mais o read-only de
vocabulário (`/fact-types`). **Out:** qualquer endpoint de UPDATE/DELETE (não existe, ADR-004);
mensageria (SPEC-09); UI.

## Superfície

| # | Método + Path | Caso de uso | Scope | SPEC |
|---|---|---|---|---|
| 1 | `POST /api/v1/hist/audit-facts` | `RecordAuditFactUseCase.append` | `hist:write` | SPEC-02 |
| 2 | `GET /api/v1/hist/audit-facts/{id}` | `QueryHistoryUseCase.findById` | `hist:read` | SPEC-03 |
| 3 | `GET /api/v1/hist/history` | `QueryHistoryUseCase.queryTimeline` | `hist:read`/`hist:read:all` | SPEC-03 |
| 4 | `GET /api/v1/hist/history/verify` | `VerifyChainUseCase.verifyStream` | `hist:read`/`hist:read:all` | SPEC-03 |
| 5 | `GET /api/v1/hist/fact-types` | `FactTypeCatalogService.listActive` | `hist:read` | SPEC-02 |

## Decisões técnicas

- URI-versioned (`/api/v1`); RFC 7807 para erros; JWT `tenant` claim como única autoridade de schema
  (SPEC-06). Sem endpoint de mutação (ADR-004).
- Tenant-context filter (inbound adapter) pina o schema `t_<empresa>` por unidade de trabalho antes de
  qualquer acesso a repositório.

## Critérios de aceite (EARS)

- THE SYSTEM SHALL expose exactly the five endpoints above and SHALL expose no endpoint that updates or
  deletes an `AuditRecord`.
- WHEN any request arrives without a valid bearer token, THE SYSTEM SHALL respond `401`.
- WHEN a request omits `X-Correlation-Id`, THE SYSTEM SHALL generate one and echo it in the response.
- THE SYSTEM SHALL derive the acting tenant only from the JWT `tenant` claim and SHALL reject a
  conflicting body/query/path tenant with `403` (`/errors/tenant-mismatch`).
- THE SYSTEM SHALL return every error as `application/problem+json` with a stable `type` and the request
  `correlationId`.

## Casos de borda

- Endpoint desconhecido sob `/api/v1/hist/**` → `404` ProblemDetail.
- Método não suportado em path válido (ex.: `DELETE /audit-facts/{id}`) → `405 Method Not Allowed`.
