# ADR-005 — Connectivity is derived from heartbeat freshness, never trusted from a persisted flag

- **Status:** proposed (2026-07-29)
- **Scope:** local (module EDGE)

## Context

EDGE-FE's central job is to tell the truth about whether the local edge agent is
**actually watching the patient right now**. The `DispositivoEdge` record carries a
persisted enum `status: online | offline`, plus `ultimaSincronizacao` (last sync
timestamp) and `versao`. The agent reports to `edge-service` over **synchronous
REST with an outbox/retry** — there is **no broker, no websocket, no server push**
in the MVP (project-context `stack.messaging` is disabled). So the browser learns
about connectivity only by **asking**.

The danger is specific and safety-critical. If EDGE-FE renders the persisted
`status = online` flag as current truth, a device that **silently stopped
reporting** hours ago still shows a green "Dispositivo online" chip — exactly the
always-visible chip the prototype topbar (`topbar.component.ts`, "connectivity is
never hidden") and onboarding step 3 (`onboarding.component.ts`) put in front of the
caregiver. A caregiver would believe Márcio is being watched while the watcher is
dead. For a residual-movement channel that is the person's only way to call for
help, a false-green is a **safety defect**, not a cosmetic bug.

This mirrors the honest-state posture already adopted elsewhere in the project:
PAC's ADR-005 (an edit is not "live" until the device acknowledges it) and GEST's
honest-degraded invariant (`degraded:true` never emits a fake recognition). EDGE
needs the same discipline for connectivity.

We must decide how the connectivity state shown to users is computed.

Options:

1. **Trust the persisted flag** — render `status` directly. Simplest; one field, no
   timing logic. **Rejected:** a stale flag is indistinguishable from live truth; it
   produces false-green during silent outages — the exact failure we cannot allow.
2. **Freshness-derived state with an explicit staleness threshold** — poll the device
   status on a cadence and compute `online | offline | stale/unknown` from the age of
   `ultimaSincronizacao` (and poll success), not from the raw flag. **Chosen.**
3. **Require server push (websocket/SSE) before showing any state** — most accurate,
   but the MVP has no broker/push path and messaging is disabled. **Rejected for the
   MVP;** revisit if a broker is introduced when the fleet grows.

## Decision

EDGE-FE presents a **derived connectivity state**, computed client-side, and treats
the persisted `status` flag as a hint — never as current truth.

- The FE **polls** the device status endpoint on a cadence (`edge.pollingEnabled`,
  interval flag), pausing while the tab is hidden and resuming on focus.
- Derived state is a `computed()` over the latest polled device and a clock signal:
  - `age = now - ultimaSincronizacao` (prefer a server-relative age if the BE
    provides one, to avoid client clock skew).
  - `age < edge.stalenessThresholdMs` **and** last poll succeeded → **`online`**.
  - `edge.stalenessThresholdMs ≤ age < edge.offlineWindowMs` → **`stale`** (shown
    distinctly, not green).
  - `age ≥ edge.offlineWindowMs` → **`offline`** (loud).
  - last poll failed → **`unknown`** (degraded), never optimistically `online`.
- The **pairing flow** reaches `paired` only after a **real first heartbeat**, not
  merely after the pairing code is claimed.
- EDGE exposes this derived state as the **single connectivity read-model** the
  Shell topbar, Home and other consumers read; no consumer re-derives "online" from
  a raw flag.

The thresholds are **feature flags**, tunable per pilot without redeploy.

## Alternatives considered

- **Trust persisted flag (option 1)** — rejected: false-green risk.
- **Push-only (option 3)** — rejected for MVP: no broker/push; messaging disabled.
- **Binary online/offline without a `stale` state** — rejected: collapsing "recently
  quiet" into either extreme either hides early outage signals or flaps noisily; a
  distinct `stale/unknown` band is the honest middle and gives caregivers an early,
  non-alarming warning.

## Consequences

- **Positive:** the connectivity chip is honest by construction; silent outages
  surface within one staleness window; `unknown` under poll failure prevents
  optimistic lies; one read-model means every surface agrees; thresholds are
  tunable safety parameters, not code constants. Consistent with PAC/GEST honesty.
- **Negative / costs:** polling adds gateway traffic (mitigated by visibility-paused
  polling; a broker is the growth-path answer); the FE invents `stale/unknown` that
  does not exist on the record, so if the BE later exposes a dedicated
  `lastHeartbeatAt` the derivation must move to it; freshness math is clock-sensitive
  (prefer server-relative age). Threshold tuning is a live pilot responsibility.

## References

- `docs/architecture/frontend/modules/edge/_brief.md` (§4, §8)
- `docs/architecture/frontend/modules/edge/frontend/03-domain-framing-frontend.md` (§5 invariants)
- `docs/architecture/frontend/modules/edge/frontend/05-state-and-data-flow.md` (§3–§5)
- `docs/architecture/frontend/modules/pac/frontend/adr/ADR-005-command-edit-not-live-until-synced.md` (honest-state precedent)
- Prototype: `frontend/design-reference/angular-prototype/.../shell/topbar.component.ts`, `.../screens/onboarding/onboarding.component.ts`
- project-context `stack.messaging` (broker disabled, REST/outbox sync)
