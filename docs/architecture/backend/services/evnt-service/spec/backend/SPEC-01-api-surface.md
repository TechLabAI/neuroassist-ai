# SPEC-01 — API Surface

> Service: `evnt-service` · EARS acceptance criteria (guide: `standards/ears-notation.md`).
> Canonical contract: `contracts/api-contract.md`.

## Objetivo
Freeze the HTTP surface of evnt-service: ingress, live delivery (SSE + poll), confirmation, query, health —
implementable without re-reading the vision.

## Escopo
- **In:** all endpoints in api-contract §2 (`/reports`, `/events/**`, `/events/stream`, `/health`), their
  status codes, RFC 7807 errors, mandatory headers, versioning.
- **Out:** internal orchestration logic (SPEC-02), live-channel mechanics (SPEC-03), data model (SPEC-04),
  security detail (SPEC-06).

## Decisões técnicas
- Path-versioned `/api/v1`; JSON except `text/event-stream` on `/events/stream` (ADR-005 / FE ADR-005).
- Errors are RFC 7807 `application/problem+json` with `correlationId`.
- Tenant is always resolved from the JWT `tenant` claim, never a path/body param (ADR-003 shared).

## Implementação
- `api` package: `ReportIngressController`, `EventStreamController`, `ConfirmationController`,
  `EventQueryController`, `HealthController`. Bean-validation on all request DTOs; a `@ControllerAdvice`
  maps domain/validation failures to `ProblemDetail`.

## Casos de borda
- Duplicate `reportId` → `200` (not `201`), original body, no side effect.
- Biometric payload present → `400 forbidden-biometric-payload`.
- Unknown `eventoId` in tenant → `404`, without leaking existence across tenants.

## Critérios de aceite (EARS)
- THE SYSTEM SHALL expose exactly the endpoints in api-contract §2 under base path `/api/v1`.
- WHEN a request lacks `X-Correlation-Id`, THE SYSTEM SHALL accept the value injected by GWY and echo it.
- THE SYSTEM SHALL return every error as an RFC 7807 `ProblemDetail` carrying `type`, `status`, `detail`,
  `instance`, and `correlationId`.
- IF a request body contains image/frame/landmark data, THEN THE SYSTEM SHALL reject it with `400`.
- WHEN a breaking contract change is required, THE SYSTEM SHALL introduce `/api/v2` and keep `/api/v1`.
