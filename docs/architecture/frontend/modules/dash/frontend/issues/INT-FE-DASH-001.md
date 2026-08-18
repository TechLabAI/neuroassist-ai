## [INT-FE-DASH-001] Wire activeEvent (EVNT) → DashHomeFacade.calm via scope:web

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/neuroassist-ai-web` (scope:web) injeta o signal `activeEvent` de EVNT na `DashHomeFacade` via `ACTIVE_EVENT_TOKEN`, sem que `scope:dash` importe `scope:evnt`. A honestidade do `calm` depende desta wiring.

**Context / Problem statement:**
- `calm = computed(() => !(ev !== null && ev.urgencia === 'emergencia' && ev.confirmadoEm === null))` usa EVNT `activeEvent`.
- `scope:dash` NUNCA importa `scope:evnt` — a composição é feita em `scope:web`.
- Esta é a integração mais crítica: um "está bem" falso é o pior defeito possível (ADR-005).
- Fonte: SPEC-03 §1; SPEC-06 §2; ADR-005; INT-FE-EVNT-001.
- Referenciada em: FE-DASH-006 (DashHomeFacade usa o token)

**Approach (execution path):**
1. Em `apps/neuroassist-ai-web`: importar `EvntFacade` de `@neuroassist/evnt` e `ACTIVE_EVENT_TOKEN` de `@neuroassist/dash`.
2. Prover `ACTIVE_EVENT_TOKEN` no `AppModule` ou via `provideEnvironmentInitializer`:
   ```ts
   { provide: ACTIVE_EVENT_TOKEN, useFactory: () => inject(EvntFacade).activeEvent }
   ```
3. `DashHomeFacade` injeta `ACTIVE_EVENT_TOKEN` via `inject()`.
4. Verificar que o lint boundary Nx não viola `scope:dash → scope:evnt` (a dependência existe apenas em `scope:web`).
5. Testar o token no AppModule com mocks de EvntFacade.

**Non-goals:**
- Não importar scope:evnt dentro de libs/dash/*.
- Não modificar EVNT (apenas consumir o signal existente).

**Layer(s):**
frontend / apps/neuroassist-ai-web (scope:web) + libs/dash/data-access (token consumer)

**Bounded context:**
dash ↔ evnt (composição em scope:web)

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-03-view-models.md` (§1 — calm invariant)
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-06-shell-integration.md` (§2)

**Architecture alignment:**
- Injection token pattern (ADR-005); Nx boundary preservada
- Tag Nx: `scope:web`, `type:app`

**Implementation checklist (by file):**

**App: apps/neuroassist-ai-web**
- 🔲 `apps/neuroassist-ai-web/src/app/app.config.ts` ou `app.module.ts` (prover ACTIVE_EVENT_TOKEN)

**Lib: libs/dash/data-access**
- 🔲 `libs/dash/data-access/src/lib/tokens/active-event.token.ts` (InjectionToken — já criado em FE-DASH-006)

**Tests**
- 🔲 `npx nx test neuroassist-ai-web` PASS (verificar que token é providenciado e DashHomeFacade.calm reage)
- 🔲 `npx nx lint neuroassist-ai-web` PASS (boundary: dash não importa evnt)

**Milestone:**
Wave 4 / Hub Integrations

**Labels:**
type:integration
layer:frontend
scope:web
priority:P4

**Acceptance criteria:**
- `npx nx test neuroassist-ai-web` PASS
- `npx nx lint neuroassist-ai-web` PASS (sem violação dash→evnt)
- `calm()` é `false` quando EVNT tem emergência ativa não confirmada
- `calm()` é `true` quando EVNT não tem emergência ativa

**Dependencies:**
- FE-DASH-006 (DashHomeFacade + ACTIVE_EVENT_TOKEN)
- EVNT FE scaffold (EvntFacade deve exportar `activeEvent` signal)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum (signal local, sem HTTP)

**Testing / Evidence required:**
- Test do AppModule com EvntFacade mockado: activeEvent(emergência) → calm=false; activeEvent(null) → calm=true

**Risks / Notes:**
- Pré-requisito CRÍTICO: EVNT FE deve ter exportado `activeEvent` como signal antes desta integração.
- Esta é a integração referenciada como INT-FE-EVNT-001 no state.json do autopilot.
