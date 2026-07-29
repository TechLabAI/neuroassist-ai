# GEST-FE-008 -- GestureTestFacade — signals, VM state machine, EdgeRecognitionSignal → GestureTestVM mapping

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/7 itens
**GitHub:** [#TBD]

## Outcome
`libs/gest/data-access/src/lib/gesture-test.facade.ts` implements `GestureTestFacade` as the single owner of `MediaStream`, bridging `EdgeDetectionClient`'s RxJS observable into Angular signals. All components read `state()`, `step()`, `deviceOnline()`, `degraded()` as readonly signals.

## Context / Problem statement
- The facade is the single stream owner: it acquires `getUserMedia`, subscribes to the bridge observable, maps `EdgeRecognitionSignal` → `GestureTestVM` updates, and tears down both stream and bridge on `stopTest()`.
- Honest-degraded invariant: `degraded === true` signals may reach `state: 'recognized'` only when explicitly simulated and `GestureTestOutcome.degraded === true`.
- Source: SPEC-04 §3, SPEC-03 §4.
- Bloqueia: GEST-FE-011 (smart component reads facade signals)

## Approach (execution path)
1. Create `gesture-test.facade.ts` with `GestureTestFacade` injectable.
2. Declare `private readonly _state = signal<GestureTestVM>(initialVm())`.
3. Expose `state`, `step`, `deviceOnline`, `degraded` as `computed` readonly signals.
4. Implement `startTest(draft)`: acquire `getUserMedia`, choose `openStream` vs `mockStream` based on `edgeLiveBridge` flag + device online check.
5. Implement signal → VM mapping table per SPEC-04 §3 (7 rows).
6. Implement `stopTest()`: unsubscribe, stop all MediaStream tracks, call `client.close()`.
7. Implement `retry()`: `stopTest()` then `startTest(lastDraft)`.
8. Create `libs/gest/data-access/src/lib/gesture-test.vm.ts` with all VM types (`GestureTestVM`, `GestureTestState`, `GestureTestStep`, `DegradedReason`, `GestureTestOutcome`).

## Non-goals
- Not responsible for rendering (all components are presentational or read signals).
- Composite gating (`state: 'recognized'` requires both steps) is part of this facade's state machine.

## Layer(s)
frontend / data-access

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-04-data-access.md` (§3)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-03-view-models.md` (§4, §5)

## Architecture alignment
- `Injectable({ providedIn: 'root' })` or scoped — TBD in scaffold
- No `BehaviorSubject` in components; facade owns all mutable state
- Tag Nx: `scope:gest`, `type:data-access`

## Implementation checklist (by layer/file)

**Data-access**
- [ ] `libs/gest/data-access/src/lib/gesture-test.facade.ts`
- [ ] `libs/gest/data-access/src/lib/gesture-test.vm.ts`

**Tests**
- [ ] `npx nx test gest-data-access` PASS (state machine: all 7 signal→VM mappings; composite gating; teardown)
- [ ] `npx nx lint gest-data-access` PASS
- [ ] `npx nx build gest-data-access` PASS

## Acceptance criteria
- `stopTest()` stops ALL MediaStream tracks (no leak)
- `state: 'recognized'` not reachable when `degraded === true` without `GestureTestOutcome.degraded === true`
- Timeout (no signal within `tempoMaximoGestoMs`) → `state: 'not-recognized'`
- `composite === true` requires both primitives before `state: 'recognized'`
- `npx nx test gest-data-access` PASS

## Dependencies
- GEST-FE-007 (EdgeDetectionClient)
- GEST-INT-003 (EdgeRecognitionSignal type)

## Empresa-context impact
Nenhum — stream is per-device/per-session.

## Milestone
Wave 2 / Data access

## Labels
type:feature
layer:frontend
scope:gest
priority:P2

## Risks / Notes
- Camera `getUserMedia` failures must set `state: 'camera-error'`, not propagate exceptions
- Timeout implementation: `timer(tempoMaximoGestoMs).pipe(takeUntil(signal$))` — test with fake timers

## Prioridade
P2
