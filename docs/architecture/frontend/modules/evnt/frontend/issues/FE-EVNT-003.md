## [FE-EVNT-003] EvntModule — entry lib shell-entry com exports públicos

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/evnt/shell-entry` exporta `EvntModule` (ou `EVNT_ROUTES` standalone), `EVNT_MODULE_DESCRIPTOR`, `EVNT_ALARM_OUTLET` e o signal `activeEvent` como readonly — a superfície pública que o Shell e outros módulos consomem via `@neuroassist/evnt`.

**Context / Problem statement:**
- ADR-006 define entry-as-library: o EVNT não tem um `apps/evnt` heavy — o Shell usa `loadChildren` apontando para a lib.
- O Shell precisa do `EVNT_ALARM_OUTLET` (import dinâmico de `AlarmOverlayComponent`) e do signal `activeEvent` para montar o outlet global (ADR-005).
- Bloqueia: FE-EVNT-004 (tool-descriptor), FE-EVNT-021 (EvntShellIntegrationService).

**Approach (execution path):**
1) Criar `libs/evnt/shell-entry/src/lib/evnt.module.ts` (NgModule wrapper se necessário) ou configurar rotas standalone.
2) Exportar `EVNT_ALARM_OUTLET` (import dinâmico de `AlarmOverlayComponent` de `feature-alarm`).
3) Re-exportar `activeEvent` signal (readonly) de `EventDeliveryFacade` via `libs/evnt/shell-entry/src/index.ts`.
4) Garantir que `@neuroassist/evnt` não expõe internals de feature libs — apenas o contrato público.
5) Configurar `project.json` Nx com `tags: ["scope:evnt", "type:app"]` e `importPath: "@neuroassist/evnt"`.

**Non-goals:**
- Implementação de componentes (feature libs FE-EVNT-011..018).
- Providers de facade ou serviços (FE-EVNT-007/008).

**Layer(s):**
frontend / shell-entry

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-05-tool-contract.md` (§4)
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-06-shell-integration.md` (§1)

**Architecture alignment:**
- `importPath: "@neuroassist/evnt"` no `project.json`
- Tag Nx: `scope:evnt`, `type:app`
- Fronteira: `scope:web` pode importar `@neuroassist/evnt`; outros módulos (`scope:pac`, `scope:acs`) não devem

**Implementation checklist (by file):**

**Lib shell-entry**
- [ ] `libs/evnt/shell-entry/project.json` — `tags: ["scope:evnt","type:app"]`, `importPath: "@neuroassist/evnt"`
- [ ] `libs/evnt/shell-entry/src/lib/evnt.module.ts` — exports routes, providers
- [ ] `libs/evnt/shell-entry/src/index.ts` — `EvntModule`, `EVNT_ROUTES`, `EVNT_MODULE_DESCRIPTOR`, `EVNT_ALARM_OUTLET`, `activeEvent`

**Tests**
- [ ] `nx test evnt-shell-entry` PASS
- [ ] `nx lint evnt-shell-entry` PASS (nx-boundaries: só `scope:web` importa esta lib)

**Milestone:**
Wave P1 / Foundation

**Labels:**
type:feature
layer:frontend
scope:evnt
priority:P1

**Acceptance criteria:**
- `npx nx test evnt-shell-entry` PASS
- `npx nx lint evnt-shell-entry` PASS
- `npx nx build evnt-shell-entry` PASS
- `import { EVNT_MODULE_DESCRIPTOR } from '@neuroassist/evnt'` resolve sem erro em `scope:web`

**Dependencies:**
- FE-EVNT-001 (contrato)
- FE-EVNT-002 (routes)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Lint de boundaries: tentativa de importar `@neuroassist/evnt` de `scope:pac` → erro de lint

**Risks / Notes:**
- Se o projeto usa standalone components (Angular 21), `EvntModule` pode ser apenas um barrel com `providers` — validar com o padrão do projeto.
