# GEST-FE-015 -- DegradedBannerComponent — honest degraded banner (4 reasons), always visible when degraded===true

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens
**GitHub:** [#TBD]

## Outcome
`DegradedBannerComponent` in `libs/gest/feature-live-test` renders an honest degraded banner that is always visible while `degraded === true`, with a reason-specific pt-BR message (device-offline, bridge-unreachable, version-mismatch, null).

## Context / Problem statement
- ADR-005 honest-degraded invariant: the caregiver must always know when the test is not real. The banner must never disappear silently while `degraded === true`.
- 4 reason values + `null` (generic fallback).
- Source: SPEC-02 §1, SPEC-07 §2.

## Approach (execution path)
1. Create `degraded-banner.component.ts` with `@Input() degraded: boolean` and `@Input() reason: DegradedReason`.
2. Template: `@if (degraded)` shows the banner; reason drives message text from SPEC-07 §2 copy table.
3. Banner uses warning color token; includes "Tentar reconectar" action for `bridge-unreachable`.
4. ARIA `role="alert"` for accessibility.

## Non-goals
- Not responsible for controlling `degraded` state (facade).

## Layer(s)
frontend / feature lib

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-07-error-handling.md` (§2)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§1)

## Architecture alignment
- `standalone: true`, `changeDetection: OnPush`
- ARIA `role="alert"`
- Tag Nx: `scope:gest`, `type:feature`

## Implementation checklist (by layer/file)

**Feature lib**
- [ ] `libs/gest/feature-live-test/src/lib/degraded-banner/degraded-banner.component.ts`
- [ ] `libs/gest/feature-live-test/src/lib/degraded-banner/degraded-banner.component.html`

**Tests**
- [ ] `npx nx test gest-feature-live-test` PASS (degraded false → hidden; all 4 reason messages; ARIA alert)
- [ ] `npx nx lint gest-feature-live-test` PASS
- [ ] `npx nx build gest-feature-live-test` PASS

## Acceptance criteria
- `degraded === false` → component renders nothing
- `degraded === true` → banner always visible regardless of `state`
- All 4 reason messages show correct pt-BR text (per SPEC-07 §2)
- ARIA `role="alert"` present
- `npx nx build gest-feature-live-test` PASS

## Dependencies
- GEST-FE-011 (consumed by GestureLiveTestComponent)

## Empresa-context impact
Nenhum.

## Milestone
Wave 3 / Feature libs

## Labels
type:feature
layer:frontend
scope:gest
priority:P3

## Risks / Notes
- Critical a11y: caregiver must be able to understand the degraded state without vision — screen reader must announce the alert

## Prioridade
P3
