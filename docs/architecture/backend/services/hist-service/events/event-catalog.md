# HIST — Event Catalog (`hist-service`)

> Messaging **disabled** (`stack.messaging.enabled = false`). No broker events in the MVP — see
> `contracts/event-contracts.md`. This catalog records the REST-based ingestion "events" and the internal
> domain-event semantics that would become published events if a broker is introduced (ADR-005).

## Published (broker) events

| Name | Type | Payload | Known consumers | Status |
|---|---|---|---|---|
| — | — | — | — | **none (messaging disabled)** |

## Consumed (broker) events

| Name | Type | Payload | Producer | Status |
|---|---|---|---|---|
| — | — | — | — | **none (messaging disabled)** |

## REST-based ingestion "events" (the MVP transport)

| Name | Direction | Transport | Payload | Producers | Idempotency |
|---|---|---|---|---|---|
| `AuditFactSubmitted` | inbound (consumed) | `POST /api/v1/hist/audit-facts` | `AuditFactRequest` (`api-contract.md §2.1`) | EVNT, PAC, ACS, EDGE | `factId` |

## Internal domain-event semantics (not transported)

| Name | Meaning | Surfaced as | Future topic (deferred) |
|---|---|---|---|
| `FatoAuditado` (AuditFactRecorded) | new record appended + chained | log + `hist_audit_fact_recorded_total` | `hist.audit-fact.recorded` |
| `FatoDuplicadoIgnorado` (DuplicateFactIgnored) | duplicate `factId` returned original | log + `hist_duplicate_fact_ignored_total` | (log/metric only) |
| `CorrecaoRegistrada` (CorrectionRecorded) | corrective record appended | log + `hist_correction_recorded_total` | `hist.correction.recorded` |
| `CadeiaVerificada` (ChainVerified) | stream verified (intact / broken@N) | log + `hist_chain_verification_total{status}` | (metric only) |
