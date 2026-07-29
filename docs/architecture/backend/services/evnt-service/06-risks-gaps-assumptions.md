<!-- OUTPUT-SCHEMA
- Riscos
- Gaps Conhecidos
- Suposicoes a Validar
- Dependencias Criticas
-->

# 06 — Risks, Gaps & Assumptions

## Riscos
1. **Missed emergency (safety-critical).** A recognized emergency that fails to alert the caregiver is the
   highest-severity failure mode. Mitigation: durable Evento before notification, idempotent ingress,
   retryable dispatch with recorded status, and fail-open notification into a retry queue.
2. **Notification fatigue from false positives.** Too many `falso_positivo` alarms erode trust and cause
   real alarms to be ignored. Mitigation: first-class false-positive feedback feeding a per-command
   false-positive rate for sensitivity tuning (with PAC/GEST).
3. **Synchronous coupling to GEST/PAC/ACS.** With no broker, EVNT depends on live REST calls; a slow/down
   dependency can delay an emergency Evento. Mitigation: timeouts + fail-closed-and-surface for emergencies
   (escalate/log rather than drop), circuit-breaking, and snapshotting so the Evento survives once created.
4. **In-process retry only (MVP).** Notification retries live in-process; an evnt-service restart could
   interrupt a pending retry. Mitigation: dispatch status is persisted (`queued`/`failed`) so a startup
   sweep re-queues outstanding dispatches. A broker/outbox is the deferred hardening.
5. **Delivery channel is DASH-only in MVP.** If the caregiver is not looking at the panel, an emergency may
   go unseen. Out-of-band channels (push/SMS) are out of MVP scope — a real risk to name explicitly for the
   pilot's operating assumptions.

## Gaps Conhecidos
1. **Device-token auth shape for ingress is not frozen** — jointly owned with EDGE (GEST event-contracts
   §3.3). Assumed JWT-shaped device token (`deviceId` + `tenant`) until EDGE decides mTLS vs. OAuth2
   `client_credentials`. Must be closed in Specs/Backlog with EDGE.
2. **Acknowledgement semantics.** Whether "delivered" vs. "acknowledged" requires an explicit caregiver
   action (and any emergency escalation timeout if unacknowledged) is not yet specified.
3. **Escalation policy.** Whether an unacknowledged emergency escalates to a secondary caregiver (and the
   routing rules for that) depends on ACS routing capabilities — not yet designed.
4. **Idempotency ledger vs. unique index** — whether a separate `report_idempotency` table is needed or the
   `evento.report_id` unique constraint suffices is deferred to Specs.
5. **Feedback → tuning loop ownership.** EVNT records false positives; which service consumes the rate to
   adjust `sensibilidade` (PAC) or primitive thresholds (GEST) is not yet contracted.

## Suposicoes a Validar
1. Every recognized-command report carries a resolvable `commandRef` whose `Comando` (PAC) yields a valid
   `urgencia`. (What EVNT does with a report whose command was deleted/deactivated must be specified.)
2. The MVP runs at pilot scale (one patient, one edge, one/few caregivers), so synchronous REST + in-process
   retry is sufficient; the broker is deferred until scale grows (Framing §11.2 trigger).
3. DASH can render both a full-screen alarm and a discreet notice and will call back the confirmation
   endpoint with the acting caregiver's identity (resolvable via ACS).
4. HIST accepts append-only audit facts synchronously and is not on the critical path for raising an alarm
   (audit append can be retried without blocking the emergency dispatch).
5. Urgency is fully determined by the `Comando` at creation time; no runtime re-classification of urgency.

## Dependencias Criticas
- **GEST** — validation of the recognized-command report (no Evento without it).
- **PAC** — `Comando` (urgency/sensitivity/label) + `Paciente` resolution.
- **ACS** — responsible-caregiver resolution and confirmation-actor identity.
- **DASH** — notification rendering and the confirmation callback.
- **HIST** — audit-trail persistence of EVNT transitions.
- **GWY / REG** — routing and discovery for all of the above (independent processes).
- **MIG** — centralized Flyway that materializes EVNT tables per tenant schema.
