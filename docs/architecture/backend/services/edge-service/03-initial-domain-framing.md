<!-- OUTPUT-SCHEMA
- Ubiquitous Language
- Aggregates and Entities
- Domain Events
- State Transitions
- Invariants
-->

# 03 — Initial Domain Framing

## Ubiquitous Language
- **Dispositivo Edge (Edge Device)** — the registered backend identity of an edge agent running on a
  patient's device. The `DispositivoEdge` aggregate root.
- **Edge Agent** — the physical/software process on the patient's device that runs detection; outside
  the service boundary. The device *is* the agent's backend identity.
- **Registration** — the act by which an edge agent obtains a `DispositivoEdge` identity and credentials.
- **Pairing** — the exclusive binding of one registered device to one `Paciente`. **Unpairing** dissolves it.
- **Heartbeat** — a periodic authenticated signal from a paired device reporting it is alive and which
  detection-profile version it is running.
- **Liveness** — the derived state `online`/`offline`, computed from heartbeat freshness against the
  **staleness threshold** (the max age of `ultimaSincronizacao` before a device counts as offline).
- **Última Sincronização (`ultimaSincronizacao`)** — timestamp of the last accepted heartbeat.
- **Versão (`versao`)** — the edge-agent version string reported on heartbeat.
- **Detection-profile version pointer** — the identifier of the profile version GEST currently
  considers active for the patient; EDGE delivers it, GEST authors it.
- **Profile drift** — a mismatch between the version the device reports running and the current pointer.

## Aggregates and Entities
- **`DispositivoEdge` (aggregate root)** — `id`, `pacienteId` (cross-service ref → PAC `Paciente`,
  null until paired), `status` (`online`/`offline`, derived), `ultimaSincronizacao` (datetime),
  `versao` (string). MVP extensions to be confirmed in Specs: a `pairingState`
  (`registered`/`paired`/`unpaired`/`retired`), device-credential reference, `runningProfileVersion`,
  and `tenant_id` (defense-in-depth). Source: BA data-model `DispositivoEdge` (5 base fields).
- **Cross-boundary references (not owned here):** `Paciente` (PAC), the detection-profile version
  (GEST). EDGE stores only ids/version pointers, never the referenced rows.

## Domain Events
> No event bus in the MVP (`stack.messaging.enabled = false`). These are **domain** events (in-service
> facts / state transitions), surfaced via query endpoints, **not** published to a broker.
- **DispositivoRegistrado** — a new edge device obtained its backend identity.
- **DispositivoPareado** — a device was bound to a patient (enables PAC's `dispositivo_edge_id`).
- **DispositivoDespareado** — a device was unbound (replacement/retirement).
- **HeartbeatRecebido** — *anchor event*; refreshes liveness and running profile version.
- **DispositivoFicouOffline** — liveness crossed the staleness threshold (safety-critical, caregiver-facing).
- **DispositivoVoltouOnline** — a stale device resumed heartbeating.
- **DeriveDeProfileDetectada (ProfileDriftDetected)** — reported running version ≠ current pointer.

## State Transitions
**Pairing lifecycle:** `registered → paired → unpaired` and `registered|unpaired → retired`
(terminal). Re-pairing a device moves `unpaired → paired`. Retirement is explicit and audited.

**Liveness (derived, orthogonal to pairing):** a *paired* device is `offline` until its first
heartbeat, then `online`; `online → offline` when `now − ultimaSincronizacao` exceeds the staleness
threshold; `offline → online` on the next accepted heartbeat. An `unpaired`/`retired` device has no
meaningful liveness (reported as `offline`/`n/a`).

## Invariants
1. **Exclusive pairing** — at any time a patient has at most one device in `paired` state, and a device
   is paired to at most one patient (MVP: one active device per patient).
2. **Authenticated identity** — a heartbeat/pull refreshes only the device that authenticated the call;
   the reporting id is never taken from the request body (ADR-005).
3. **Deterministic liveness** — `status` is a pure function of heartbeat freshness and the staleness
   threshold; the same inputs always yield the same state for every reader (ADR-006).
4. **Pair before heartbeat matters** — heartbeats from an unpaired device are accepted for liveness of
   the device but do not imply monitoring of any patient until pairing exists.
5. **Referential honesty for PAC** — a `dispositivo_edge_id` PAC resolves must map to a device that is
   registered and (for an active pairing) currently paired to that same patient.
6. **No media, ever** — no video/image/landmark is accepted or stored (privacy invariant inherited from
   GEST doc 02); a heartbeat carries only liveness + version metadata.
