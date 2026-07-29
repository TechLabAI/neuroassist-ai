# GEST-FE-019 -- Loading/empty states — GestureLiveTestComponent UX polish

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens
**GitHub:** [#TBD]

## Outcome
`GestureLiveTestComponent` shows a loading spinner while the edge bridge opens, an idle placeholder when no test is running, and a camera-permission error panel with "Tentar de novo" when `state === 'camera-error'`. All states visually distinct and accessible.

## Context / Problem statement
- The basic component skeleton (GEST-FE-011) implements state wiring. This issue adds the polish: spinner on `testing` initial phase, idle illustration/placeholder, and the first-class camera-error panel.
- Source: SPEC-07 §2 (camera-error = first-class error panel, not a toast).

## Approach (execution path)
1. Update `gesture-live-test.component.html` to show spinner overlay when `state === 'testing'` and `stream === null` (bridge connecting).
2. Add idle placeholder (camera icon + "Aguardando início do teste") for `state === 'idle'`.
3. Add camera-error panel: full-panel message "Não foi possível acessar a webcam. Verifique a permissão do navegador." with "Tentar de novo" button wired to `facade.retry()`.
4. All states use design tokens; no hardcoded colors or spacing.

## Non-goals
- Not responsible for the degraded banner (GEST-FE-015).
- Not responsible for toast/banner for backend errors (GEST-FE-020).

## Layer(s)
frontend / feature lib

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-07-error-handling.md` (§2, §3, §4)

## Architecture alignment
- `standalone: true`, `changeDetection: OnPush`
- Tag Nx: `scope:gest`, `type:feature`

## Implementation checklist (by layer/file)

**Feature lib**
- [ ] `libs/gest/feature-live-test/src/lib/gesture-live-test/gesture-live-test.component.html`

**Tests**
- [ ] `npx nx test gest-feature-live-test` PASS (each loading/empty state renders)
- [ ] `npx nx lint gest-feature-live-test` PASS
- [ ] `npx nx build gest-feature-live-test` PASS

## Acceptance criteria
- Screenshots of all distinct states (idle, loading/bridge-connecting, camera-error) captured in evidence
- Camera-error panel has ARIA `role="alert"` and "Tentar de novo" action
- `npx nx build gest-feature-live-test` PASS

## Dependencies
- GEST-FE-011 (base component)
- GEST-FE-008 (facade state signals)

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
- Camera-error is NOT a toast — it replaces the preview area with a full recovery panel (SPEC-07 §4)

## Prioridade
P7
