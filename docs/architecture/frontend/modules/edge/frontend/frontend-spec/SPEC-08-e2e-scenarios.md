# SPEC-08 — E2E Scenarios — EDGE (Frontend)

> Each scenario below becomes a `.feature` file under `../e2e/`. Playwright specs live in `apps/edge-e2e`.
> Scenarios are written to cover every `THE SYSTEM SHALL` in SPEC-01..07 (EARS ↔ Gherkin coverage, gate
> `fe-architect-specs-validate`). Domain nouns are shared between each `THE SYSTEM SHALL` and its `Cenário`
> so the coverage matcher casa.

## Scenario inventory

| # | Feature file | Covers (SPEC) | Intent |
|---|---|---|---|
| 1 | `pairing-happy-path.feature` | 01, 03, 04, 06 | Register → show one-time code → agent claims → first heartbeat → "pareado · online · versão X" |
| 2 | `pairing-code-expired.feature` | 03, 04, 07 | Enrollment token TTL elapses before claim → `expired` → re-register (no silent hang) |
| 3 | `pairing-claim-no-heartbeat.feature` | 03, 04 | Agent claims but never heartbeats → stays `awaiting-first-heartbeat`, never advances to `paired·online` |
| 4 | `connectivity-derived-freshness.feature` | 01, 03, 04 | Chip `online` while fresh; degrades `online → stale → offline` as server-relative age crosses thresholds; never green past window |
| 5 | `connectivity-poll-failure-unknown.feature` | 04, 07 | Liveness poll fails → `unknown` (degraded), never optimistically `online`; silent retry |
| 6 | `device-repair-unpair.feature` | 03, 04, 05, 07 | Conductor re-pair/unpair/retire; `pairing-conflict`/`optimistic-lock` handled by refresh; caregiver actions hidden |
| 7 | `shell-chip-composition.feature` | 01, 02, 05, 06 | Topbar chip reads read-model on every authenticated route; chip click → `/edge/device`; no direct scope:edge import; login redirect; bootstrap init/destroy |
| 8 | `backend-error-handling.feature` | 07 | `ProblemDetail` → correct surface (validation/insufficient-scope/pairing-conflict/device-retired); no credential/biometric in errors |

## Notes

- Scenarios 1–3 stub `edge-service` with deterministic register/liveness responses (frozen `RegisterDeviceResult`
  / `LivenessView` shapes) so the pairing path is deterministic; the "agent claim + first heartbeat" is
  simulated by flipping the stubbed `ultimaSincronizacao` to a fresh server-relative value.
- Scenario 4 drives a **virtual clock**: it advances `serverTime − ultimaSincronizacao` past
  `stalenessThresholdMs` then `offlineWindowMs` and asserts the chip transitions `online → stale → offline`
  and **never** reads `online` once the window elapsed (the ADR-005 core success signal, doc 01 §7).
- Scenario 5 forces the liveness poll to reject and asserts `derivedState = 'unknown'` with the absence of a
  green/`online` chip, plus a successful poll on a later tick restoring the honest state.
- Scenario 6 asserts a caregiver session sees **no** management affordance (hidden, not disabled) and that a
  `409` on unpair triggers a read-model refresh rather than a stale write.
- Scenario 7 is a structural/composition assertion (topbar renders the chip from the read-model; the
  ESLint nx-boundary that forbids a `scope:*`→`scope:edge` import is separately enforced at lint, referenced
  here for traceability).
- No scenario asserts any biometric/credential value — those never reach the browser (privacy invariant).
