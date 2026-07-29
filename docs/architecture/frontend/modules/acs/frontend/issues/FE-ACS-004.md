## [FE-ACS-004] Module descriptor ACS_MODULE_DESCRIPTOR (libs/acs/shell-entry/acs.descriptor.ts)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/acs/shell-entry/src/lib/acs.descriptor.ts` exports `ACS_MODULE_DESCRIPTOR: ModuleDescriptor` — the static
fallback the `scope:web` shell uses to render nav **before** the lazy ACS module loads. The descriptor is
tree-shaken from the feature bundle.

**Context / Problem statement:**
- SPEC-06 §1 defines the descriptor shape: key, label, icon, route, loadChildren, permissions, showInNav, order.
- The shell reads this constant at startup (synchronous, no network), then lazy-loads `@neuroassist/acs` on nav.
- `permissions: ['acs.cuidador.read']` gates the Cuidadores nav item — a cuidadora (non-coordinator) never
  sees the entry even if the module is already loaded.
- Bloqueia: INT-FE-ACS-002 (AcsShellIntegrationService uses the descriptor in register())

**Approach (execution path):**
1. Create `libs/acs/shell-entry/src/lib/acs.descriptor.ts`.
2. Implement `ACS_MODULE_DESCRIPTOR` matching SPEC-06 §1 shape with `loadChildren` arrow.
3. Ensure `loadChildren` points to `import('@neuroassist/acs').then(m => m.AcsModule)`.
4. Re-export from the barrel `src/index.ts`.
5. Unit test: descriptor fields match SPEC-06 §1 table; `permissions` includes `acs.cuidador.read`.

**Non-goals:**
- No shell registration logic (INT-FE-ACS-002).
- No guard logic.

**Layer(s):**
frontend / libs/acs/shell-entry

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-06-shell-integration.md` (§1)

**Architecture alignment:**
- Static descriptor (no DI, no signal) — safe to read synchronously at shell init
- Tags Nx: `scope:acs`, `type:domain`

**Implementation checklist (by file):**

**Lib path:** `libs/acs/shell-entry/`
- 🔲 `src/lib/acs.descriptor.ts` — ACS_MODULE_DESCRIPTOR const
- 🔲 `src/index.ts` — re-exports ACS_MODULE_DESCRIPTOR

**Tests**
- 🔲 `src/lib/acs.descriptor.spec.ts` — field assertions (key, route, permissions, showInNav)
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
- `ACS_MODULE_DESCRIPTOR.permissions` includes `'acs.cuidador.read'`
- `ACS_MODULE_DESCRIPTOR.route` === `'/acs'`
- `ACS_MODULE_DESCRIPTOR.showInNav` === `true`

**Dependencies:**
- FE-ACS-001 (MODULE_KEY referenced)
- FE-ACS-003 (lib skeleton must exist)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum direto

**Testing / Evidence required:**
- Teste de shape passando

**Risks / Notes:**
- `ModuleDescriptor` é um tipo da shell pública (`@neuroassist/shared/contracts`) — verificar a shape com
  a definição atual antes de implementar.
