## [FE-DASH-001] dash.contract.ts — MODULE_KEY, menus, permissions e feature-flags

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/dash/shell-entry/src/lib/dash.contract.ts` exporta todas as constantes de contrato do módulo DASH: `MODULE_KEY`, `DASH_MENU_METADATA`, `DASH_PERMISSION_MAP`, `DASH_FEATURE_FLAGS` e `DASH_MODULE_DESCRIPTOR` (com `shellOutletRegions`).

**Context / Problem statement:**
- O Shell precisa do descriptor completo antes de registrar o módulo. Sem esse contrato o módulo não monta.
- Fonte: SPEC-05 §1..§4; ADR-005/006.
- Bloqueia: FE-DASH-002, FE-DASH-004, FE-DASH-019

**Approach (execution path):**
1. Criar `libs/dash/shell-entry/src/lib/dash.contract.ts` com `MODULE_KEY = 'dash'`.
2. Definir `DASH_MENU_METADATA` (label "Status", icon "shield-check", order 0, route "/", showInNav true).
3. Definir `DASH_PERMISSION_MAP` (`dash.view` → todas as roles; `dash.onboarding.entry` → coordenador_clinica).
4. Definir `DASH_FEATURE_FLAGS` (`dash.serverSummary`, `dash.notificationCenter`, `dash.sseStream` — todos "on").
5. Definir `DASH_MODULE_DESCRIPTOR` com `loadChildren: () => import('@neuroassist/dash').then(m => m.DashModule)` e `shellOutletRegions: ['dash.global-interrupt']`.
6. Exportar tudo pelo barrel `index.ts` de `dash/shell-entry`.

**Non-goals:**
- Não implementar a lógica de guard (responsabilidade de FE-DASH-017..018).
- Não registrar o descriptor no shell (responsabilidade de FE-DASH-004).

**Layer(s):**
frontend / libs/dash/shell-entry (type:domain)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-05-tool-contract.md` (§1..§4)

**Architecture alignment:**
- Standalone + OnPush + Signals
- Tag Nx: `scope:dash`, `type:domain`

**Implementation checklist (by file):**

**Lib: libs/dash/shell-entry**
- 🔲 `libs/dash/shell-entry/src/lib/dash.contract.ts`
- 🔲 `libs/dash/shell-entry/src/index.ts` (barrel re-export)

**Tests**
- 🔲 `npx nx test dash-shell-entry` PASS (Jest unit — verificar que todas as constantes estão definidas e tipadas)
- 🔲 `npx nx lint dash-shell-entry` PASS

**Milestone:**
Wave 1 / Foundation

**Labels:**
type:domain
layer:frontend
scope:dash
priority:P1

**Acceptance criteria:**
- `npx nx test dash-shell-entry` PASS
- `npx nx lint dash-shell-entry` PASS
- `npx nx build dash-shell-entry` PASS
- `DASH_MODULE_DESCRIPTOR.shellOutletRegions` contém `'dash.global-interrupt'`
- `DASH_PERMISSION_MAP['dash.view'].grantedTo` inclui `'cuidadora'` e `'coordenador_clinica'`
- `DASH_FEATURE_FLAGS['dash.sseStream'].defaultState === 'on'`

**Dependencies:**
- (nenhuma — issue raiz P1)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum (contrato estático de configuração)

**Testing / Evidence required:**
- Snapshot do objeto exportado no test

**Risks / Notes:**
- `shellOutletRegions: ['dash.global-interrupt']` é o contrato com EVNT (INT-FE-EVNT-001); não remover nem renomear a string.
