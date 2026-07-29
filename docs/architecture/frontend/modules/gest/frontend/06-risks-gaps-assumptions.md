# 06 — Risks, Gaps, Assumptions — GEST (Frontend)

## 1. Risks

| # | Risk | Impact | Mitigation |
|---|---|---|---|
| R1 | **Engine drift** — a browser-side ML preview would decide differently from the production edge agent, so a command "passes" the test but misses in production. | Safety-critical (missed help request). | ADR-005: the live test bridges to the *same* edge engine; no separate browser classifier. |
| R2 | **Edge bridge availability** — localhost WS/SSE to the agent may be blocked (mixed-content, CORS, agent down, panel on a different device than the agent). | Live test can't get a real verdict. | Explicit degraded mode (mock + banner); never report a fake "recognized"; feature flag `edgeLiveBridge`. |
| R3 | **Camera permission UX** on a non-technical persona — a denied/again-denied prompt can dead-end the caregiver. | Onboarding/calibration blocked. | First-class `camera-error` state + plain-language recovery + retry; never auto-request on load. |
| R4 | **Composite-gesture false negatives** (2 primitives in sequence) — inherently higher miss rate (LBC §12 Amendment 1). | Emergency command silently fails. | Explicit two-step test with per-step feedback; encourage test-before-save; surfaced as ADR-005 consequence. |
| R5 | **Privacy perception** — live camera near a vulnerable patient. | Trust / LGPD. | Camera only during explicit test; no recording/upload/persistence; centralized stream teardown asserted in e2e. |
| R6 | **Boundary temptation** — implementers importing `scope:gest` directly from PAC/onboarding. | Hard nx-boundaries violation / drift. | Page-level composition at `scope:web` documented (doc 02 §4, doc 04 §5); ESLint enforces. |
| R7 | **Bundle weight** if a browser ML lib were added. | Perf regression on a background-open panel. | No browser ML in MVP (ADR-005); edge does the compute. |

## 2. Known gaps

- Exact **edge bridge protocol** (WebSocket vs SSE, message schema, auth/handshake
  with the local agent) is not yet contract-frozen — to be settled in specs with
  the BE/edge team.
- **Landmark visualization** (drawing detected points over the preview) is not in
  MVP scope; only a binary/step verdict is shown. Revisit if caregivers need
  richer feedback.
- Whether the panel and the edge agent are ever on **different devices** (and thus
  can't use localhost) is unconfirmed — see Assumption A2.

## 3. Assumptions to validate

- **A1** — The caregiver's panel usually runs on the **same device** as the edge
  agent (patient bedside notebook/tablet), so a localhost bridge is viable in the
  common case. (Validate with the pilot deployment topology.)
- **A2** — When they are on different devices, degraded/mock mode is acceptable
  for MVP (the test still confirms camera + gesture *for the operator*, and real
  validation happens against the paired device's own detection).
- **A3** — Recognition-signal latency from the edge agent stays ≤ ~300 ms p95, low
  enough for a "live" feel.
- **A4** — Target browsers support `getUserMedia` over the panel's origin
  (secure context / HTTPS or localhost).

## 4. Critical dependencies

- **Edge Agent (GEST runtime, BE side)** — must expose a local recognition
  stream and be paired/online (EDGE module).
- **PAC** — provides the `Comando` draft (primitivos, sensibilidade, tempo
  windows) that defines what the test expects.
- **EDGE** — device pairing + online status feeding the gest guard.
- **Shared libs** — `access-http-base`, `empresa-context`, `access-decision`,
  `contracts`, `error-model`, `ui-*`.
- **Design tokens** — `@neuroassist/shared/ui-tokens` (`--color-alarm`, `brand`,
  `success`, `danger`).
