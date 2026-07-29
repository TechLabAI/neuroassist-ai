# SPEC-02 — Notification Orchestration (core)

> Service: `evnt-service` · Traces ADR-004 (system of record), ADR-005 (urgency-tiered notification),
> ADR-006 (feedback). This is the module's core orchestration: ingest → generate → notify → confirm.

## Objetivo
Specify the end-to-end orchestration that turns a validated report into a persisted Evento, dispatches the
urgency-matched notification, and closes the loop with the caregiver's terminal verdict.

## Escopo
- **In:** the four use cases `IngestRecognizedReport`, `GenerateEvento`, `OrchestrateNotification`,
  `ConfirmEvento`; tier derivation; durable-first ordering; fail-open dispatch + retry; false-positive rate.
- **Out:** HTTP shape (SPEC-01), SSE mechanics (SPEC-03), idempotency/concurrency internals (SPEC-05),
  persistence DDL (SPEC-04).

## Decisões técnicas
- **Durable-first:** validate (GEST) → persist Evento + idempotency ledger in one transaction → *then*
  attempt dispatch. A dispatch failure never rolls back the Evento (ADR-004).
- **Tier derivation is pure:** `NotificationTier.from(urgencia)` — `emergencia → full_screen_alarm`,
  `rotina → discreet`. No branch may downgrade an emergency (ADR-005).
- **Fail-open notify:** dispatch failure records `notification_dispatch.status = failed` and is retried by
  an in-process, bounded retry + a boot-time sweep of `queued`/`failed` (doc 06 risk #4).
- **Fail-closed lookups on emergency:** GEST/PAC/ACS timeout on an `emergencia` surfaces `502/504`, never a
  silent drop.
- **Feedback terminal + attributed:** `ConfirmEvento` sets `feedback` once, requires a resolvable
  `confirmadoPor`, appends to HIST (ADR-006).

## Implementação (fluxo)
1. `IngestRecognizedReport`: idempotency check → GEST validate → `GenerateEvento`.
2. `GenerateEvento`: resolve `Comando` (PAC), snapshot label/urgencia/timing, persist `evento` +
   `report_idempotency` (same tx), publish `EventoCriado`.
3. `OrchestrateNotification` (listener on `EventoCriado`): resolve caregiver (ACS), derive tier, create
   `notification_dispatch (queued)`, call DASH; on success → `delivered` + `NotificacaoDespachada`; on
   failure → `failed` + `NotificacaoFalhou` + re-queue.
4. `ConfirmEvento`: validate not-terminal, resolve `confirmadoPor` (ACS), set verdict, append HIST, publish
   `EventoConfirmado`/`FalsoPositivoMarcado`.

## Casos de borda
- `Comando` deleted/deactivated in PAC → `422 command-unresolvable`, no Evento (doc 06 assumption #1).
- Retry budget exhausted → dispatch stays `failed`, surfaced (never silently abandoned for an emergency).
- Verdict correction → new audited action, never in-place overwrite.

## Critérios de aceite (EARS)
- WHEN a validated report with a new `reportId` is ingested, THE SYSTEM SHALL persist exactly one Evento
  before attempting any notification.
- THE SYSTEM SHALL derive the notification tier solely from the snapshotted `urgencia`
  (`emergencia → full_screen_alarm`, `rotina → discreet`).
- WHEN an `emergencia` Evento is persisted, THE SYSTEM SHALL create at least one `full_screen_alarm`
  dispatch and, on delivery failure, re-queue it until success or budget exhaustion.
- IF GEST or PAC is unreachable within timeout on an `emergencia`, THEN THE SYSTEM SHALL fail closed
  (`502/504`) and record the failure rather than create/notify silently.
- WHEN a caregiver sets a verdict on a `nao_avaliado` Evento, THE SYSTEM SHALL set `feedback` once, record
  `confirmadoPor`+`confirmadoAt`, and append the fact to HIST.
- IF a verdict is submitted for an Evento with terminal feedback, THEN THE SYSTEM SHALL reject it with `409`.
- THE SYSTEM SHALL expose the per-command false-positive rate as a queryable aggregate.
