## [FE-EDGE-020] Telemetry events — pairing completed, device offline, poll failure (unknown), re-pair/unpair, token expired

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Key EDGE user interactions and system state transitions are instrumented via `@neuroassist/observability`:
pairing completed, device went offline, poll failure (unknown), re-pair/unpair/retire actions, and token
expired. Events include a `correlationId` (no PII, no biometric, no device credential).

**Context / Problem statement:**
- EDGE is a safety-critical module — understanding when devices drop offline or pairing fails in production
  is essential for the pilot feedback loop.
- Events must never include PII, biometric data, video, landmarks, or device credentials (api-contract §8,
  SPEC-07 §9).
- `correlationId` comes from `X-Correlation-Id` (shared interceptor) and links events to BE traces.
- This is an observability concern (cross-cutting) — it touches the facades and the smart components.

**Approach (execution path):**
1. Inject `ObservabilityService` (from `@neuroassist/observability`) into `PairingFacade` and `DeviceFacade`.
2. Instrument the following events:
   - `pairing.completed` — on `phase→paired` + `derivedState='online'` (PairingFacade)
   - `pairing.token_expired` — on `phase→expired` (PairingFacade)
   - `pairing.error` — on `register()` or `pairToPatient()` failure (PairingFacade)
   - `device.offline` — on `derivedState` transition to `offline` (DeviceFacade `computed` effect)
   - `device.unknown` — on `derivedState` transition to `unknown` (poll failure)
   - `device.unpaired` — on `unpair()` success (DeviceFacade)
   - `device.retired` — on `retire()` success (DeviceFacade)
3. Each event payload: `{ correlationId, moduleKey: 'edge', deviceId?, pacienteId? (opaque), derivedState? }`.
4. No `clientSecret`, `enrollmentToken`, video, landmark, or confidence score in any event payload.

**Non-goals:**
- Does not implement `ObservabilityService` (shared concern).
- Does not add UI analytics (click tracking, page views) — those are Growth layer (disabled for MVP).

**Layer(s):**
frontend / libs/edge/data-access (facades) + libs/edge/feature-pairing, libs/edge/feature-device-detail (smart components for action events)

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-04-data-access.md` (§3, §2 — lifecycle hooks)

**Architecture alignment:**
- Tag Nx: `scope:edge`, `type:data-access`
- Privacy invariant: no PII/biometric in telemetry

**Implementation checklist (by file):**

**Lib path**
- 🔲 `libs/edge/data-access/src/lib/pairing.facade.ts` (add ObservabilityService calls)
- 🔲 `libs/edge/data-access/src/lib/device.facade.ts` (add ObservabilityService calls)

**Tests**
- 🔲 Unit: `phase→paired` → `ObservabilityService.track('pairing.completed')` called
- 🔲 Unit: `phase→expired` → `ObservabilityService.track('pairing.token_expired')` called
- 🔲 Unit: `derivedState→offline` → `ObservabilityService.track('device.offline')` called
- 🔲 Unit: payload does NOT include `enrollmentToken`, `clientSecret`, or biometric field (negative assertion)
- 🔲 `nx test edge-data-access` PASS
- 🔲 `nx lint edge-data-access` PASS

**Milestone:**
Wave P8 / Observability

**Labels:**
type:feature
layer:frontend
scope:edge
priority:P8

**Acceptance criteria:**
- `npx nx test edge-data-access` PASS
- `npx nx lint edge-data-access` PASS
- `pairing.completed` event: includes `correlationId` and `derivedState='online'`; no `enrollmentToken`
- Negative assertion: no event payload contains `enrollmentToken` or `clientSecret`

**Dependencies:**
- FE-EDGE-007 (DeviceFacade — instrument offline/unknown transitions)
- FE-EDGE-008 (PairingFacade — instrument completed/expired/error)

**Data impact:**
- Nenhum

**API / Contract impact:**
- `POST /v1/telemetry/events` (or shared observability sink) — no BE EDGE contract change

**Testing / Evidence required:**
- `ObservabilityService.track` spy called with correct event name and safe payload

**Risks / Notes:**
- If `ObservabilityService` is not yet available, inject a `OBSERVABILITY_TOKEN` and stub in tests.
- `derivedState` transitions in a `computed()` do not fire Angular `effect()` unless explicitly subscribed.
  Use `toObservable(this.derivedState)` + `pairwise()` to detect transitions (online→offline, etc.).
