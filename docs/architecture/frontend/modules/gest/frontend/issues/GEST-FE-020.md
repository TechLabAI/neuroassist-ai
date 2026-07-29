# GEST-FE-020 -- Error UI — camera-error first-class panel, toast/banner/modal routing per SPEC-07 §4

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens
**GitHub:** [#TBD]

## Outcome
All GEST error surfaces are routed correctly: `camera-error` → first-class error panel (GEST-FE-019); backend REST errors → `AppError` → toast/banner/modal per SPEC-07 §4 criteria table; degraded → persistent banner (GEST-FE-015). No raw biometric data in any error message or log.

## Context / Problem statement
- SPEC-07 §4 defines 4 surface types (toast, banner, modal, first-class panel) with deterministic routing based on error kind.
- This issue finalizes the integration: ensuring `GestureTestFacade` and `GestEligibilityService` route errors through `shared/error-model` and trigger the correct surface.
- Source: SPEC-07 §1, §3, §4.

## Approach (execution path)
1. Verify `GestureTestFacade` and `GestEligibilityService` pipe REST errors through `gest-error-map.ts` (GEST-FE-010).
2. In `GestureLiveTestComponent`, subscribe to `AppError` stream from `shared/error-model`'s `errorBus`.
3. Route `kind: 'forbidden'` + `surface: 'modal'` → open modal (tenant-mismatch).
4. Route `kind: 'forbidden'` + `surface: 'toast'` → show toast (insufficient-scope).
5. Route banner kinds → show inline banner within the component.
6. Confirm no PII/biometric in any error path.

## Non-goals
- First-class camera-error panel is in GEST-FE-019.
- Degraded banner is in GEST-FE-015.

## Layer(s)
frontend / feature lib

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-07-error-handling.md` (§1, §3, §4)

## Architecture alignment
- `standalone: true`, `changeDetection: OnPush`
- Tag Nx: `scope:gest`, `type:feature`

## Implementation checklist (by layer/file)

**Feature lib + data-access**
- [ ] `libs/gest/feature-live-test/src/lib/gesture-live-test/gesture-live-test.component.html`
- [ ] `libs/gest/data-access/src/lib/gest-error-map.ts`

**Tests**
- [ ] `npx nx test gest-feature-live-test` PASS (each error surface routed correctly)
- [ ] `npx nx lint gest-feature-live-test` PASS
- [ ] `npx nx build gest-feature-live-test` PASS

## Acceptance criteria
- `tenant-mismatch` → modal rendered
- `insufficient-scope` → toast rendered
- `unknown-primitive` → inline banner
- `transient` → toast with retry after 2 backoff attempts
- No video/image/landmark/confidence in any error message
- `npx nx build gest-feature-live-test` PASS

## Dependencies
- GEST-FE-010 (error map)
- GEST-FE-019 (camera-error panel)

## Empresa-context impact
Nenhum.

## Milestone
Wave 7 / UX polish

## Labels
type:feature
layer:frontend
scope:gest
priority:P7

## Risks / Notes
- Retry-with-backoff for transient errors must not re-prompt camera or re-open bridge — only retries the REST call

## Prioridade
P7
