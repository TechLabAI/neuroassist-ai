# 06 — Risks, Gaps, Assumptions — PAC (Frontend)

## 1. Risks

| # | Risk | Impact | Mitigation |
|---|---|---|---|
| R1 | **Silent stale calibration** — a caregiver edits/saves a command and assumes it is armed, but the edge device has not pulled the change yet, so the *old* (or no) behavior is live. | Safety-critical (a "fixed" emergency command silently not active). | ADR-005 local: never show `ativo no dispositivo` before an EDGE device-ack; explicit `pendente de sincronização` per command. |
| R2 | **TipoGesto naming ≠ detectability** — the inline "add new type" lets the family name a gesture the engine cannot actually recognize yet, breeding false confidence. | Command created for an undetectable gesture. | Delegate the request to GEST (owner of the detectability lifecycle); surface GEST's status; encourage test-before-save (GEST live-test). |
| R3 | **Composite-command false negatives** (2 primitives) — inherently higher miss rate (LBC §12 Amendment 1). | Emergency command silently fails. | Author-side: mark composite commands, encourage the two-step live test before save; document as ADR-005 consequence / GEST ADR-005. |
| R4 | **Boundary temptation** — implementers importing `scope:gest` directly from PAC's editor to embed the live test. | Hard nx-boundaries violation / drift. | Page-level composition at `scope:web` documented (doc 02 §4, doc 04 §5); ESLint enforces. |
| R5 | **Calibration cross-talk** — a refactor accidentally shares editor state so editing one command mutates another. | Wrong sensitivity/urgency on an untouched command. | Per-command draft isolation invariant (doc 03 §5); e2e asserts editing one leaves others unchanged. |
| R6 | **Ownership ambiguity in onboarding** — the wizard mixes ACS (tenant, caregiver), PAC (patient, first command) and EDGE (device); unclear ownership leaks patient logic into ACS. | Module boundary erosion. | PAC owns only the patient step + command editor feature; the onboarding shell (ACS) orchestrates; features composed at `scope:web`. |
| R7 | **LGPD over-collection** — free-text `rotulo` could invite health details. | Privacy / compliance. | Collect the minimum; label copy frames rótulo as a *word the family uses*, not a clinical note; no health free-text fields. |

## 2. Known gaps

- The exact **device-ack signal** that flips a command `pending → synced` (EDGE
  heartbeat payload? explicit catalog-version ack?) is not contract-frozen — settle
  in specs with EDGE (see Assumption A1).
- Whether `TipoGesto` creation is **synchronous** (GEST returns an id immediately)
  or **request-and-pending** (a new type starts `pending-training`) is not yet fixed
  on the PAC side — depends on GEST's detectability lifecycle contract.
- **Concurrent editing** (two caregivers editing the same catalog) is not modeled in
  MVP; last-write-wins is assumed until a conflict signal exists.
- The `tempoMinimoGestoMs` / `tempoMaximoGestoMs` editing UI is implied by the model
  but not surfaced in the prototype editor — confirm whether it is caregiver-facing
  or conductor-only.

## 3. Assumptions to validate

- **A1** — EDGE exposes a per-patient/per-device signal indicating which command
  catalog version is active on the device, enabling an honest `synced` flag.
  (Validate with EDGE specs.)
- **A2** — The command catalog stays **small** (single patient, a handful of
  commands), so fetch-on-navigation without paging/caching is adequate.
- **A3** — A new `TipoGesto` requested inline is acceptable to the caregiver even if
  it starts non-detectable, provided the surface says so (naming ≠ detectability).
- **A4** — Onboarding is conducted once by the technical conductor; the patient step
  does not need the full daily-caregiver affordances (simpler form is acceptable).

## 4. Critical dependencies

- **pac-service (BE)** — command + patient CRUD endpoints, tenant-scoped; system of
  record for the catalog.
- **GEST** — `TipoGesto` catalog + new-type request + the `GestureLiveTestComponent`
  composed into the editor; detectability lifecycle.
- **EDGE** — device-ack / catalog-version status that powers the sync-honesty flag.
- **ACS** — active tenant + onboarding wizard orchestration (patient↔caregiver).
- **EVNT** — downstream reader of `Comando` (urgency); no direct FE coupling, but
  the urgency semantics authored here must match EVNT's policy.
- **Shared libs** — `access-http-base`, `empresa-context`, `access-decision`,
  `contracts`, `error-model`, `ui-*`.
- **Design tokens** — `@neuroassist/shared/ui-tokens` (`--color-alarm`, `brand`,
  `success`, `danger`).
