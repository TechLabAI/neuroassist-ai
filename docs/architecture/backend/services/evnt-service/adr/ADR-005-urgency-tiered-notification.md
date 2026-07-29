<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-005 — Urgency-Tiered Notification Orchestration (Synchronous, Broker-Deferred)

## Status
aceita

## Contexto
The module's reason to exist (registry description) is to orchestrate notification **by urgency level** —
a **full-screen alarm for `emergencia`**, a **discreet notice for `rotina`** — and route it to the
responsible caregiver. Two forces shape how: (1) urgency must be **deterministic** — a life-safety alarm
can never be downgraded by a heuristic or an AI inference (`ai.hasAi = false`); and (2) there is **no
message broker** in the MVP (`stack.messaging.enabled = false`), so there is no async topic to publish an
alarm onto. The naive alternatives — inferring urgency, or coupling the alarm to a fire-and-forget call
with no delivery tracking — both risk a silently missed emergency.

## Decisão
Notification tier is a **pure deterministic function of the command's `urgencia`** snapshotted on the
Evento: `emergencia → full_screen_alarm`, `rotina → discreet`. Orchestration is **synchronous and
in-process** in the MVP: after the Evento is committed (ADR-004), EVNT resolves the responsible caregiver
(ACS) and dispatches to DASH, recording a `NotificationDispatch` with a tracked `status`
(`queued → delivered → acknowledged`, or `failed → retry`). Dispatch **fails open** into a persisted retry
queue so a transient DASH/caregiver outage never loses an emergency. The domain events
(`EventoCriado`, `NotificacaoDespachada`, …) are **named to move onto async topics with no rename** WHERE a
broker is later introduced (Framing §11.2 growth trigger) — the broker is deferred, not designed away.

## Consequências
**Positivas:** an emergency can never be silently downgraded (tier is pure policy); delivery is tracked and
retryable, not fire-and-forget; the design is broker-ready without a rename; zero AI/Hub dependency on the
alarm path keeps it simple and auditable.
**Negativas / trade-offs:** synchronous dispatch couples EVNT to DASH/ACS availability on the notify path
(mitigated by fail-open retry + persisted dispatch status); in-process retry can be interrupted by a
restart (mitigated by a startup sweep of `queued`/`failed` dispatches); DASH-only delivery in the MVP means
an inattentive caregiver is a real risk (out-of-band channels deferred — doc 06). Escalation of an
unacknowledged emergency depends on ACS routing and is left open for Specs/Backlog.
