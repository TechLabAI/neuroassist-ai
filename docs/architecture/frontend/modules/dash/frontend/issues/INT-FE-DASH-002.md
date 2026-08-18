## [INT-FE-DASH-002] Wire DeviceBadge (EDGE signal) → PatientHeaderComponent via scope:web

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/neuroassist-ai-web` (scope:web) injeta o signal `deviceBadge` de EDGE na `DashShellIntegrationService` via token/input, que o disponibiliza para `PatientHeaderComponent`, sem que `scope:dash` importe `scope:edge`.

**Context / Problem statement:**
- `PatientHeaderComponent` recebe `device: DeviceBadgeVm` como `input()` — sem conhecer EDGE.
- O signal EDGE é resolvido em `scope:web` (topbar device badge — SPEC-02 shell tree).
- Fonte: SPEC-02 (feature-home → PatientHeaderComponent); SPEC-06 §2.

**Approach (execution path):**
1. Em `apps/neuroassist-ai-web`: importar `EdgeFacade` de `@neuroassist/edge` e `DEVICE_BADGE_TOKEN` de `@neuroassist/dash`.
2. Prover `DEVICE_BADGE_TOKEN`:
   ```ts
   { provide: DEVICE_BADGE_TOKEN, useFactory: () => inject(EdgeFacade).deviceBadge }
   ```
3. `DashShellIntegrationService.bindDeviceBadge()` recebe o signal e o expõe internamente.
4. `HomeStatusPageComponent` injeta o token e passa como `input()` para `PatientHeaderComponent`.
5. Verificar lint boundary: `scope:dash` não importa `scope:edge`.

**Non-goals:**
- Não criar lógica de device no DASH.
- Não modificar EdgeFacade.

**Layer(s):**
frontend / apps/neuroassist-ai-web (scope:web)

**Bounded context:**
dash ↔ edge (composição em scope:web)

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (shell tree)
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-06-shell-integration.md` (§2)

**Architecture alignment:**
- Injection token pattern (ADR-005)
- Tag Nx: `scope:web`, `type:app`

**Implementation checklist (by file):**

**App: apps/neuroassist-ai-web**
- 🔲 `apps/neuroassist-ai-web/src/app/app.config.ts` (prover DEVICE_BADGE_TOKEN)

**Lib: libs/dash/data-access**
- 🔲 `libs/dash/data-access/src/lib/tokens/device-badge.token.ts` (InjectionToken<Signal<DeviceBadgeVm>>)

**Tests**
- 🔲 `npx nx test neuroassist-ai-web` PASS
- 🔲 `npx nx lint neuroassist-ai-web` PASS (boundary: dash não importa edge)

**Milestone:**
Wave 4 / Hub Integrations

**Labels:**
type:integration
layer:frontend
scope:web
priority:P4

**Acceptance criteria:**
- `npx nx test neuroassist-ai-web` PASS
- `npx nx lint neuroassist-ai-web` PASS
- `PatientHeaderComponent` exibe o estado do device sem importar scope:edge
- `device.status='offline'` → PatientHeader exibe "offline"

**Dependencies:**
- FE-DASH-012 (PatientHeaderComponent)
- FE-DASH-018 (DashShellIntegrationService.bindDeviceBadge)
- EDGE FE scaffold (EdgeFacade deve exportar `deviceBadge` signal)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum (signal local)

**Testing / Evidence required:**
- Test com EdgeFacade mockado: device offline → PatientHeader mostra "offline"

**Risks / Notes:**
- Pré-requisito: EDGE FE deve exportar `deviceBadge` signal.
