<!-- OUTPUT-SCHEMA
- Ubiquitous Language
- Aggregates and Entities
- Domain Events
- State Transitions
- Invariants
-->

# 03 — Initial Domain Framing

## Ubiquitous Language
- **Painel / Panel:** the caregiver-facing Angular application DASH serves as its backend-for-frontend. Its
  screens (dashboard, histórico, calibração, status do dispositivo, alarme) are the unit of composition.
- **View Model (Modelo de Visão):** a panel-shaped, caregiver-scoped read projection DASH composes on demand
  from upstream services — never a persisted entity. It is discarded after the response.
- **Caregiver Scope (Escopo do Cuidador):** the set of patients an authenticated caregiver may see, resolved
  from ACS (tenant + caregiver↔patient linkage); the boundary of every read and write.
- **Live Session (Sessão ao Vivo):** an open server-push (SSE) connection between a caregiver's panel and
  DASH, over which notifications are delivered. Ephemeral, in-memory, keyed by (tenant, caregiver, connection).
- **Notification (Notificação):** a `full_screen_alarm` or `discreet` notice **originated and owned by EVNT**
  (`NotificationDispatch`); DASH is its *delivery* to a human, not its source.
- **dispatchId:** EVNT-minted identifier of a `NotificationDispatch`; DASH echoes it on the
  `delivered`/`acknowledged` callbacks so EVNT's idempotent lifecycle stays exact.
- **Delivery Signal (Sinal de Entrega):** `delivered` (rendered on an open panel) / `acknowledged` (caregiver
  confirmed seeing it), reported back to EVNT — DASH produces the signal, EVNT records the state.
- **Write-through (Escrita-Passante):** a caregiver action DASH forwards to the owner service (calibration→PAC,
  confirmation/ack→EVNT) without storing the value itself.
- **Liveness Indicator (Indicador de Liveness):** the device online/offline/stale state DASH derives from
  polling EDGE (ETag/304, `DASH poll 15s`); a derived read, not owned state.
- **UI Preference (opcional):** a non-sensitive, per-caregiver presentation setting (e.g. default screen) —
  the only durable state DASH might own, and it is deferred.

## Aggregates and Entities
> Draft domain shape; the authoritative model is frozen in Specs (`contracts/data-model.md`).

- **`PanelSession`** — the closest thing to an aggregate, and it is **in-memory and ephemeral**: attributes
  `sessionId`, `tenantId`, `caregiverId`, `connectionRef`, `connectedAt`, `lastHeartbeatAt`. It is the live
  SSE connection; it is never persisted and is dropped on disconnect. It is *not* a business record.
- **`PanelViewModel` (transient)** — a family of composed read projections (`PatientOverview`,
  `HistoryPage`, `CalibrationView`, `DeviceStatus`), each assembled per request from upstream data and
  returned, never stored.
- **`NotificationEnvelope` (transient)** — the in-flight representation of an EVNT `NotificationDispatch`
  while DASH pushes it and awaits acknowledgement; carries `dispatchId`, `tier`, `targetCaregiverId`,
  `payload snapshot`. Not persisted; its authoritative state lives in EVNT.
- **`CaregiverUiPreference` (optional, deferred)** — the single candidate *persisted* entity: `caregiverId`
  (PK, tenant-scoped) + non-sensitive presentation settings. Out of MVP unless a concrete need appears.
- **Not entities here:** `Evento` / `NotificationDispatch` (EVNT), `Comando` / calibration (PAC), `Cuidador`
  / `Tenant` (ACS), `AuditRecord` (HIST), `DispositivoEdge` (EDGE) — all referenced/read, never owned.

## Domain Events
Messaging is disabled (MVP), so these are **internal semantics / caregiver-facing signals**, not broker
messages. DASH's "events" are mostly *delivery* facts it reports to EVNT:
- **`NotificacaoEntregue` (NotificationDelivered)** — the anchor: an EVNT dispatch was rendered on an open
  panel; DASH reports `delivered(dispatchId)` to EVNT.
- **`NotificacaoConfirmada` (NotificationAcknowledged)** — the caregiver acknowledged; DASH reports
  `acknowledged(dispatchId)` to EVNT.
- **`CuidadorConectado` / `CuidadorDesconectado` (PanelSessionOpened/Closed)** — a live SSE session opened or
  dropped; drives on-connect catch-up and undelivered handling. In-memory only.
- **`AcaoEncaminhada` (ActionProxied)** — a caregiver write (calibration/confirmation) was forwarded to its
  owner; DASH records only the trace, not the value.

DASH publishes no domain event to a broker (none exists); if a broker is later introduced, delivery/ack
signals to EVNT could move onto topics without changing their meaning.

## State Transitions
DASH persists almost no lifecycle; its meaningful state machines are the **live session** and the **in-flight
notification delivery** — both ephemeral.

```
PanelSession:   (none) --connect--> connected --heartbeat--> connected --disconnect/timeout--> (gone)
Notification:   dispatched(EVNT) --push--> delivered --caregiver ack--> acknowledged   [state owned by EVNT]
                dispatched(EVNT) --no open session--> undelivered(reported to EVNT for retry)
```

- DASH does not own the `queued → delivered → acknowledged → failed` record — **EVNT does** (EVNT ADR-005).
  DASH only *causes* the `delivered`/`acknowledged` transitions by reporting them.
- A `PanelSession` has no durable persistence; a restart of DASH drops all sessions and panels reconnect
  (triggering catch-up).

## Invariants
1. **No owned business truth:** DASH persists no operational aggregate; every business fact is read from, or
   written through to, its owner service (ADR-004 local). A DASH restart loses no domain data.
2. **Delivery is reported, never invented:** a `delivered`/`acknowledged` signal is emitted only when the
   notification actually reached an open panel / was actually acknowledged; DASH never fabricates delivery to
   silence EVNT's retry (safety-critical honesty).
3. **Undelivered is surfaced, not dropped:** if the target caregiver has no open session, DASH tells EVNT so
   its fail-open retry persists the emergency — DASH never absorbs and loses a dispatch (ADR-005 local).
4. **Scope-bound composition:** every view model and every proxied write is bounded by the caregiver's tenant
   + patient linkage (ACS); a cross-tenant or unauthorized-patient projection is not representable (ADR-003
   shared).
5. **Write-through only:** a caregiver action changes state exclusively in the owner service (PAC/EVNT); DASH
   holds no divergent copy of a calibration value or a confirmation verdict (ADR-006 local).
6. **No biometric data:** DASH handles only structured derived state and references — never video, image or
   facial-landmark data (project-wide LGPD hard rule).
7. **Idempotent delivery reporting:** `delivered`/`acknowledged` callbacks are keyed by `dispatchId`, so a
   retried callback never double-counts against EVNT's lifecycle.
