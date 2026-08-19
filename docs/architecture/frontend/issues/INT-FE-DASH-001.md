# INT-FE-DASH-001 — Create Injectable Tokens for Cross-Module Signals

**Type:** Integration  
**Priority:** P1 (Blocker for P4)  
**Module:** DASH  
**Related Modules:** EVNT, EDGE  
**Layer:** Frontend  
**Created:** 2026-08-19  
**Status:** OPEN

---

## Título
Define injectable tokens for EVNT `activeEvent` and EDGE `deviceBadge` signals consumed by DASH.

## Descrição
Per plan-FE-DASH.md P4 INT-FE-DASH-001/002, DASH components must receive external signals from sibling modules (EVNT, EDGE) via dependency injection tokens. These tokens are declared at `scope:web` (neuroassist-ai-web) but **consumed** in DASH scope via `inject()`.

### Requirement
1. **EVNT activeEvent token** — `DASH_ACTIVE_EVENT_TOKEN`
   - Type: `Signal<ActiveEvent | null>`
   - Default: `signal(null)` (no active alarm)
   - Used by: `DashHomeFacade.calm` computed signal
   - Source: EVNT FE module (requires EVNT scaffolding)

2. **EDGE deviceBadge token** — `DASH_DEVICE_BADGE_TOKEN`
   - Type: `Signal<DeviceBadgeVm | null>`
   - Default: `signal(null)` (device status unknown)
   - Used by: `PatientHeaderComponent.deviceBadge`
   - Source: EDGE FE module (requires EDGE scaffolding)

### Injection Sites
- `DashHomeFacade` (app.config.ts) — injects DASH_ACTIVE_EVENT_TOKEN to compute calm state
- `PatientHeaderComponent` (feature-home subcomponent) — injects DASH_DEVICE_BADGE_TOKEN to render badge

### Safe Defaults
Until EVNT/EDGE FE modules export their signals, DASH must provide mock defaults:
```typescript
// In app.config.ts (scope:web):
export const appConfig: ApplicationConfig = {
  providers: [
    provideDash(),
    // Safe defaults — will be overridden by EVNT/EDGE when available
    { provide: DASH_ACTIVE_EVENT_TOKEN, useValue: signal(null) },
    { provide: DASH_DEVICE_BADGE_TOKEN, useValue: signal(null) },
  ],
};
```

## Aceitação
- [ ] `DASH_ACTIVE_EVENT_TOKEN` defined in `dash/access/dash-access-tokens.ts`
- [ ] `DASH_DEVICE_BADGE_TOKEN` defined in `dash/access/dash-access-tokens.ts`
- [ ] Types exported (DashActiveEvent, DashDeviceBadgeVm)
- [ ] Default mock providers created for tests
- [ ] `DashHomeFacade.calm` computed uses `inject(DASH_ACTIVE_EVENT_TOKEN)`
- [ ] `PatientHeaderComponent` injects `DASH_DEVICE_BADGE_TOKEN`
- [ ] Build passes; lint OK; no unused injection warnings

## Critério de Conclusão
```bash
npx nx lint @neuroassist/dash  # 0 errors
npx nx test @neuroassist/dash  # injection tests pass
npx nx build neuroassist-ai-web  # no build errors
```

## Notas de Implementação
- **Token location:** `frontend/libs/dash/src/lib/access/dash-access-tokens.ts` (add to existing file)
- **Export from index.ts:** Add to barrel export in `frontend/libs/dash/src/index.ts`
- **app.config.ts:** Located in `frontend/apps/neuroassist-ai-web/src/app/` (owns provider setup)
- **Testing:** Mock tokens in jest fixtures; verify `inject()` returns defaults
- **Backwards compatibility:** Safe until EVNT/EDGE export signals (no breaking change)

## Blocker Removal
- ✅ DASH FE scaffolded (allows token consumption)
- ⏳ EVNT FE must scaffold first (provides activeEvent signal export)
- ⏳ EDGE FE must scaffold first (provides deviceBadge signal export)
- ⏳ Shared ShellOutletRegistry (INT-FE-DASH-003) must stabilize in @neuroassist/shared/ui-components

## Referências
- Plan: `docs/architecture/frontend/plans/plan-FE-DASH.md` P4 INT-FE-DASH-001/002
- Spec: `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-03-view-models.md` (calm signal definition)
- ADR: `docs/architecture/backend/services/dash-service/contracts/api-contract.md` (activeEvent semantics)
