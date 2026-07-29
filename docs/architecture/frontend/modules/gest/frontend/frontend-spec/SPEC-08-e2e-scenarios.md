# SPEC-08 — E2E Scenarios — GEST (Frontend)

> Each scenario below becomes a `.feature` file under `../e2e/`. Playwright specs
> live in `apps/gest-e2e`. Scenarios are written to cover every `THE SYSTEM SHALL`
> in SPEC-01..07 (EARS ↔ Gherkin coverage, gate `fe-architect-specs-validate`).
> Domain nouns are shared between each `THE SYSTEM SHALL` and its `Cenário`.

## Scenario inventory

| # | Feature file | Covers (SPEC) | Intent |
|---|---|---|---|
| 1 | `live-test-simple-recognized.feature` | 03, 04, 05 | Golden path: simple command recognized via real edge stream |
| 2 | `live-test-composite.feature` | 03, 04 | Two-step composite gesture recognized in sequence |
| 3 | `live-test-degraded-offline.feature` | 01, 04, 05, 07 | Device offline → honest degraded mode, never fake "recognized" |
| 4 | `camera-permission-error.feature` | 03, 07 | Camera denied → `camera-error` state + recovery/retry |
| 5 | `camera-lifecycle-teardown.feature` | 02, 03, 06 | Zero camera leaks on stop / navigate-away / destroy |
| 6 | `live-test-timeout-not-recognized.feature` | 03, 04, 07 | No signal within window → `not-recognized` |
| 7 | `composition-boundary.feature` | 01, 02, 06 | Feature composed at scope:web host page; no direct scope:gest import; GEST contributes no breadcrumb; no nav item; login redirect; bootstrap init |
| 8 | `backend-error-handling.feature` | 07 | `ProblemDetail` → user-facing surface (validation/tenant-mismatch/insufficient-scope/unknown-primitive); no biometric data in errors |

## Notes

- Scenarios 1, 2, 6 stub the edge bridge with a fake `EdgeRecognitionSignal`
  emitter (frozen contract v1.0.0 shape) so the verdict path is deterministic.
- Scenario 3 forces the bridge unreachable (`edgeLiveBridge` off or device
  offline) and asserts `degraded: true` and the absence of a real `recognized`.
- Scenario 5 asserts, after each exit path, that
  `navigator.mediaDevices` tracks are all `readyState === 'ended'` (zero-leak
  success signal, doc 01 §7).
- Scenario 7 is a structural/composition assertion (host renders the slot; the
  ESLint nx-boundary is separately enforced at lint, referenced here for traceability).
