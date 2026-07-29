# SPEC-05 — Idempotency + Concurrency

> Service: `evnt-service` · Traces ADR-004. Guarantees *one report ⇒ one Evento* under retries and races.

## Objetivo
Specify how EVNT guarantees exactly-once Evento creation and safe concurrent transitions with no broker.

## Escopo
- **In:** ingress idempotency (`reportId`), race handling on concurrent duplicate posts, verdict-transition
  concurrency, dispatch retry idempotency, idempotency window/retention.
- **Out:** table DDL (SPEC-04), orchestration flow (SPEC-02).

## Decisões técnicas
- **Idempotency key:** client-generated `reportId` (UUID), unique per tenant schema.
- **Insert-guarded, not check-then-act:** ingress inserts `evento` + `report_idempotency` in one
  transaction; a concurrent duplicate loses the unique-constraint race, catches the violation, and returns
  the original Evento (`200`). No distributed lock needed.
- **Verdict concurrency:** `ConfirmEvento` uses a conditional update
  (`UPDATE … SET feedback=? WHERE id=? AND feedback='nao_avaliado'`); zero rows affected ⇒ already terminal
  ⇒ `409` (or `200` if the same verdict). Optimistic — no row lock held across the ACS call.
- **Dispatch retries are idempotent:** a retry re-uses the same `notification_dispatch` row (increments
  `attempts`); DASH delivery carries `dispatchId` so a duplicate delivery is detectable downstream.
- **Window:** idempotency ledger retained ≥ 24h (aligned with GEST idempotency window); pilot indefinite.

## Casos de borda
- Two identical `POST /reports` in parallel → exactly one Evento; the loser returns the winner's verdict.
- Two confirmations racing → the first wins terminal; the second gets `409`.
- Restart mid-dispatch → boot sweep re-queues `queued`/`failed` without creating a second dispatch.

## Critérios de aceite (EARS)
- WHEN two reports with the same `reportId` are processed concurrently, THE SYSTEM SHALL create exactly one
  Evento and return the original verdict for the duplicate.
- IF a verdict transition targets an Evento whose `feedback` is no longer `nao_avaliado`, THEN THE SYSTEM
  SHALL reject a differing verdict with `409` and accept an identical one with `200`.
- THE SYSTEM SHALL retain the idempotency ledger for at least the edge retry window (≥ 24h).
- WHEN a dispatch is retried, THE SYSTEM SHALL reuse the existing dispatch record rather than create a new
  Evento or a new dispatch identity.
