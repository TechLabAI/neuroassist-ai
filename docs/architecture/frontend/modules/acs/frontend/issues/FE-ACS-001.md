## [FE-ACS-001] Module contract — acs.contract.ts (MODULE_KEY, PERMISSION_MAP, FEATURE_FLAGS, MENU_METADATA)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/acs/shell-entry/src/lib/acs.contract.ts` exists and exports all four constants that govern the ACS
module's identity within the shell: `MODULE_KEY`, `MODULE_MENU_METADATA`, `MODULE_PERMISSION_MAP`, and
`MODULE_FEATURE_FLAGS`. The shell can resolve nav metadata and permission mappings without loading ACS eagerly.

**Context / Problem statement:**
- SPEC-05 defines the full module contract shape; the shell reads these constants at registration time.
- `MODULE_PERMISSION_MAP` collapses BE authorities (`acs.cuidador.criar`/`desativar`) into UI-level write keys
  (`acs.cuidador.write`) so guards can reason about UI-level capabilities without knowing the exact BE authority.
- `MODULE_FEATURE_FLAGS` controls `acs.coverageGuard`, `acs.inviteByEmail`, and `acs.showSemCoberturaBanner`
  defaults — toggling any of them changes feature branch behavior in feature libs and data-access.
- Bloqueia: FE-ACS-002, FE-ACS-004, FE-ACS-005, FE-ACS-022, FE-ACS-023, INT-FE-ACS-002

**Approach (execution path):**
1. Create `libs/acs/shell-entry/src/lib/acs.contract.ts` with all four constants as typed `as const` objects.
2. Declare `MODULE_KEY = 'acs' as const` and `MODULE_MENU_METADATA` (label, icon, route, showInNav, order).
3. Declare `MODULE_PERMISSION_MAP` mapping four UI keys to their canonical BE authority strings (SPEC-05 §2).
4. Declare `MODULE_FEATURE_FLAGS` with defaults: `coverageGuard=on`, `inviteByEmail=off`,
   `showSemCoberturaBanner=on` (SPEC-05 §3).
5. Re-export all four from `libs/acs/shell-entry/src/index.ts` (public API barrel).
6. Add Jest unit test asserting keys and defaults are present (type-level + runtime shape).

**Non-goals:**
- No runtime flag evaluation logic (that belongs to the shell's feature-flag service).
- No component code.
- No guard logic.

**Layer(s):**
frontend / libs/acs/shell-entry

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-05-tool-contract.md` (all sections)

**Architecture alignment:**
- Standalone + OnPush + Signals (components only; this file is pure TS constants)
- Tag Nx: `scope:acs`, `type:domain`
- No eagerly-loaded Angular module; contract is tree-shaken if not referenced

**Implementation checklist (by file):**

**Lib path:** `libs/acs/shell-entry/`
- 🔲 `src/lib/acs.contract.ts` — MODULE_KEY, MODULE_MENU_METADATA, MODULE_PERMISSION_MAP, MODULE_FEATURE_FLAGS
- 🔲 `src/index.ts` — re-exports acs.contract (+ future barrel)

**Tests**
- 🔲 `src/lib/acs.contract.spec.ts` — shape/value assertions (Jest)
- 🔲 `nx test acs-shell-entry` PASS
- 🔲 `nx lint acs-shell-entry` PASS

**Milestone:**
Wave 1 / P1 Foundation

**Labels:**
type:feature
layer:frontend
scope:acs
priority:P1

**Acceptance criteria:**
- `npx nx test acs-shell-entry` PASS
- `npx nx lint acs-shell-entry` PASS
- `npx nx build acs-shell-entry` PASS (sem erros)
- `MODULE_PERMISSION_MAP['acs.cuidador.write']` === `'acs.cuidador.criar'`
- `MODULE_FEATURE_FLAGS['acs.coverageGuard']` === `'on'`
- `MODULE_MENU_METADATA.route` === `'/acs'`

**Dependencies:**
- Nenhuma (é a primeira issue — sem predecessoras funcionais)

**Data impact:**
- Nenhum (FE não persiste dados client-side além de auth-session storage)

**API / Contract impact:**
- Nenhum direto; `MODULE_PERMISSION_MAP` referencia BE authorities (api-contract §7.2) — estáveis após ACS Layer 1

**Testing / Evidence required:**
- Arquivo acs.contract.ts com todos os 4 exports
- Teste de shape/valor passando

**Risks / Notes:**
- `MODULE_PERMISSION_MAP` colapsa authorities — ao expandir para o BE no call-time, usar o map reverso
  corretamente (deactivate expande para `acs.cuidador.desativar`). Documentar isso no JSDoc do campo.
- Se o shell muda a forma do `ModuleDescriptor`, este contrato pode necessitar atualização.
