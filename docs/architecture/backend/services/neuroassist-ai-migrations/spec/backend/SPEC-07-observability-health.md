# SPEC-07 — Observability & Health

> Contracts: api-contract §1/§3. Framework rule: D-423 (pipeline activity
> trail), best-effort.

## Objective

Make every migration run and provisioning call traceable via correlation ID
and the pipeline activity trail, and expose an operator-readable status view
(Flyway `info` equivalent) without depending on an event bus.

## Scope

- **In:** correlation ID propagation, activity-trail recording (D-423,
  best-effort), health/readiness, `GET /internal/migrations/status`.
- **Out:** metrics dashboards / alerting wiring (infra concern), distributed
  tracing spans beyond correlation ID propagation.

## Technical decisions

- **Correlation ID:** every run/provisioning call generates or propagates
  `X-Correlation-Id`; it is echoed on the response and included in the
  activity-trail record.
- **Activity trail (D-423):** MIG records `PHASE-START`/`PHASE-END`-style
  facts (schema migrado, empresa provisionada, migração falhou) to the
  pipeline activity trail; an unreachable Elasticsearch never blocks or
  fails a run (doc 06, Dependências Críticas).
- **Health:** `/actuator/health/readiness` reflects the internal HTTP face's
  readiness; the job path's own success/failure is the run's exit code, not
  a health check.

## Implementation

- `infrastructure`: `CorrelationIdFilter` (HTTP face) / CLI flag
  (`--correlation-id`, generated if absent for the job path).
- `ActivityTrailReporter` — best-effort HTTP/ES call, swallows failures with
  a WARN log; never throws into the migration flow.

## Edge cases

- Activity trail sink unreachable → run proceeds and completes normally;
  the failure to record is logged, not surfaced as a run failure.
- `GET /internal/migrations/status` called mid-run → reflects the
  last-committed state per schema (not a live lock view).

## Acceptance criteria (EARS)

`THE SYSTEM SHALL propagate or generate a correlation ID for every run and provisioning call and SHALL echo it on every response.`
`THE SYSTEM SHALL record schema-lifecycle facts to the pipeline activity trail on a best-effort basis.`
`IF the activity-trail sink is unreachable, THEN THE SYSTEM SHALL log the failure and SHALL NOT fail the migration run because of it.`
`WHEN GET /internal/migrations/status is called, THE SYSTEM SHALL return the last-committed applied/pending version per schema.`
