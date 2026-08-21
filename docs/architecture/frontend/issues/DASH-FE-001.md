# DASH-FE-001 — Fix Component Selector Prefixes

**Type:** Bug  
**Priority:** P0 (Blocking)  
**Module:** DASH  
**Layer:** Frontend  
**Created:** 2026-08-19  
**Status:** OPEN

---

## Título
Component selectors must start with `lib-` prefix per Nx library conventions.

## Descrição
Three components in `@neuroassist/dash` have selectors starting with `dash-` instead of `lib-`:
- `EntradaComponent` → selector `dash-entrada` (should be `lib-entrada`)
- `HomeStatusComponent` → selector `dash-home-status` (should be `lib-home-status`)
- `NotificationCenterComponent` → selector `dash-notification-center` (should be `lib-notification-center`)

This causes 3 lint errors that block the build in strict mode:
```
@angular-eslint/component-selector: The selector should start with one of these prefixes: "lib"
```

## Aceitação
- [ ] All 3 component selectors updated to `lib-*`
- [ ] `npx nx lint @neuroassist/dash` returns 0 errors
- [ ] Lint warnings in adapters also cleaned (_waitSeconds, _cursor unused)
- [ ] Build passes: `npx nx build neuroassist-ai-web`

## Critério de Conclusão
```bash
npx nx lint @neuroassist/dash
# Result: 0 errors, 0 warnings
```

## Notas de Implementação
- Files to fix:
  - `frontend/libs/dash/src/lib/feature-entrada/entrada.component.ts`
  - `frontend/libs/dash/src/lib/feature-home/home-status.component.ts`
  - `frontend/libs/dash/src/lib/feature-notifications/notification-center.component.ts`
- Mock adapter warnings (feature-fixable):
  - `frontend/libs/dash/src/lib/api/dash-notifications-mock.adapter.ts:43-44` (_waitSeconds, _cursor)

## Referências
- Lint report: BASELINE validation (2026-08-19)
- Nx boundary rules: `docs/architecture/frontend/standards/nx-boundaries.md`
