# 06 — Risks, Gaps, Assumptions — EDGE (Frontend)

## 1. Risks

- **UX / safety — false-green connectivity.** The single worst outcome is a device
  shown `online` while actually silent. Mitigated by ADR-005 (freshness-derived
  state + `stale/unknown`), but the **threshold values** are a live safety
  parameter: too long hides real outages, too short cries wolf. Must be tuned with
  the pilot and exposed as flags (`edge.stalenessThresholdMs`, `edge.offlineWindowMs`).
- **Performance — polling cost.** A short poll interval across many patients loads
  the gateway. Mitigated by visibility-paused polling; revisit if the fleet grows
  (project-context flags a possible broker then).
- **Contract instability — `edge-service` not yet generated.** No BE EDGE vision or
  API contract exists yet (only PAC/GEST/EVNT do). Pairing-code TTL, the claim/
  first-heartbeat semantics, and the status/heartbeat endpoint shape are **assumed**
  here and must be reconciled in Specs against the BE.
- **Semantic gap — `status` vs derived state.** The persisted enum is only
  `online|offline`; there is **no `stale/unknown` on the record**. The FE invents
  `stale/unknown` from freshness. If the BE later adds a real `lastHeartbeatAt`
  distinct from `ultimaSincronizacao`, the derivation must switch to it.
- **Browser compat / clock skew.** Freshness math relies on client clock vs server
  timestamps; skew could mis-classify. Prefer server-relative age if the BE returns
  it, rather than `now(browser) - ultimaSincronizacao`.

## 2. Known gaps

- **No EDGE backend artifacts yet** — `edge-service` has no vision/specs/contract in
  `docs/architecture/backend/services/`. FE Specs must not assume endpoints beyond
  what BE confirms.
- **Pairing mechanics undefined at protocol level** — code format, TTL, claim
  channel (agent→edge-service) and first-heartbeat signal are conceptual here.
- **Multi-device / replacement policy** — MVP assumes one active device per patient;
  fleet/replacement history is out of scope but likely near-future.
- **Where "offline ⇒ alarm undeliverable" is handled** — EDGE surfaces offline;
  whether EVNT escalates a prolonged offline as its own alert is an EVNT decision,
  not settled here.

## 3. Assumptions to validate

- The edge agent posts heartbeats at a **known, roughly fixed cadence** the FE can
  base its staleness threshold on (e.g. every 15–30 s). **Unconfirmed.**
- `edge-service` exposes a pollable device-status endpoint returning
  `status`, `ultimaSincronizacao`, `versao` for the active patient. **Assumed.**
- Pairing is code-based (agent claims a code) rather than QR/manual serial.
  **Assumed from the prototype's "Parear dispositivo" affordance.**
- Target devices are desktop/laptop browsers in a room (not mobile-first); the chip
  must remain legible at a glance. **Assumed.**
- One conductor pairs; caregivers only monitor. Entitlement split confirmed via ACS.

## 4. Critical dependencies

- **`edge-service` (BE)** — pairing-code issuance, device status/heartbeat endpoint,
  re-pair/unpair. **Blocking for Specs** — must be generated/aligned.
- **ACS** — active `Tenant`/`Paciente` context and the conductor-vs-caregiver
  entitlement used by `deviceGuard`.
- **Shell** — must consume EDGE's connectivity read-model in the topbar/Home
  (composition point already present in the prototype's `topbar.component.ts` and
  `onboarding.component.ts` step 3).
- **Shared standards / ADR-006** — module-pattern, nx-boundaries, design tokens,
  entry-as-library — EDGE must not diverge.
- **`@hub/*` (or project equivalents)** — http-base, error-model, ui-feedback,
  design tokens.
