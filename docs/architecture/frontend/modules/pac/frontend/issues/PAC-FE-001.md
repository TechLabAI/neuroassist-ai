## [PAC-FE-001] pac.contract.ts — MODULE_KEY, permission map, feature flags

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/shell-entry/src/lib/pac.contract.ts` exists and exports `MODULE_KEY`, `MODULE_MENU_METADATA`, `MODULE_PERMISSION_MAP` and `MODULE_FEATURE_FLAGS` exactly as specified in SPEC-05, enabling the shell to wire PAC permissions and flags without importing any PAC feature lib.

**Context / Problem statement:**
- PAC is a top-level nav destination ("Calibração / Catálogo"); the shell must know its key, route, icon and required permissions before the lazy bundle loads (SPEC-05 §1/§2/§3).
- `MODULE_PERMISSION_MAP` maps five UI keys to BE OAuth2 scopes (`pac:patient:read|write`, `pac:command:read|write|publish`); the BE is authoritative, the UI enforces defense-in-depth.
- Three feature flags govern emergent behaviors: `pac.inlineNewTipoGesto` (inline new-type affordance), `pac.deferredEdgeSync` (syncState honest-pending surface), `pac.emergencyGateAdvisoryHint` (advisory banner for rotina publishes).
- Bloqueia: PAC-FE-002, PAC-FE-003, PAC-FE-004, PAC-FE-005

**Approach (execution path):**
1. Create `libs/pac/shell-entry/src/lib/pac.contract.ts` with `export const MODULE_KEY = 'pac' as const`.
2. Add `MODULE_MENU_METADATA` — `{ key, label: 'Calibração', icon: 'tune', route: '/pac', showInNav: true, order: 30 }`.
3. Add `MODULE_PERMISSION_MAP` constant with 5 entries as per SPEC-05 §2 (copy the exact TS from the spec).
4. Add `MODULE_FEATURE_FLAGS` constant with 3 flags all defaulting to `'on'` (SPEC-05 §3).
5. Export all four as `const` named exports — no default export.
6. Add unit test: import each exported symbol, assert `MODULE_KEY === 'pac'`, assert all permission map values match `pac:*` scopes.

**Non-goals:**
- No Angular component or service logic here — this file is pure `const` declarations.
- Runtime flag evaluation (reading from remote config) is outside this issue.

**Layer(s):**
frontend / apps/pac + libs/pac/shell-entry

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-05-tool-contract.md` (§1/§2/§3)
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-06-shell-integration.md` (§1)

**Architecture alignment:**
- Standalone constants (no class, no `@NgModule` needed here)
- Tag Nx: `scope:pac`, `type:domain`
- No `BehaviorSubject` or signals — pure `const`

**Implementation checklist (by file):**

**Lib: libs/pac/shell-entry**
- [ ] `libs/pac/shell-entry/src/lib/pac.contract.ts`
- [ ] `libs/pac/shell-entry/src/lib/pac.contract.spec.ts` (smoke: assert key values)

**Tests**
- [ ] `npx nx test pac-shell-entry` PASS
- [ ] `npx nx lint pac-shell-entry` PASS

**Milestone:**
Wave 1 / P1 Foundation

**Labels:**
type:feature
layer:frontend
scope:pac
priority:P1

**Acceptance criteria:**
- `npx nx test pac-shell-entry` PASS (unit: MODULE_KEY, all permission map entries, all flag defaults).
- `npx nx lint pac-shell-entry` PASS (ESLint boundaries: no import of `scope:gest` or `scope:web`).
- `npx nx build pac-shell-entry` PASS (no TS errors).
- `MODULE_PERMISSION_MAP['pac.paciente.read'] === 'pac:patient:read'` (and all 4 other entries — verified in unit test).
- `MODULE_FEATURE_FLAGS['pac.deferredEdgeSync'] === 'on'` (per ADR-005 default surface).

**Dependencies:**
- Nenhuma (é o ponto de partida da camada PAC).

**Data impact:**
Nenhum (constantes de configuração, não persistem dados).

**API / Contract impact:**
Nenhum direto — define a string-map UI→BE scope que os guards e a UI usam para pedir ao token provider.

**Testing / Evidence required:**
- Unit test output screenshot (nx test pac-shell-entry).
- TSC output clean.

**Risks / Notes:**
- Se o `MODULE_KEY` for alterado após PAC-FE-004 (descriptor) ou PAC-FE-003 (barrel), todos os registros no shell quebram. Tratar como contrato estável desde P1.
- O flag `pac.deferredEdgeSync` default `'on'` é necessário para a honest-state ADR-005 (EDGE device-ack não contratado ainda — ver INT-FE-PAC-001).
