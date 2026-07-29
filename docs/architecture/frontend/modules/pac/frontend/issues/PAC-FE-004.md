## [PAC-FE-004] PAC_MODULE_DESCRIPTOR — pac.descriptor.ts (shell fallback)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/shell-entry/src/lib/pac.descriptor.ts` exporta `PAC_MODULE_DESCRIPTOR` do tipo `ModuleDescriptor` com todos os campos especificados em SPEC-06 §1, permitindo que o shell `scope:web` registre o item de nav "Calibração" e o lazy route de PAC antes de qualquer carregamento do bundle PAC.

**Context / Problem statement:**
- O shell precisa saber key, label, icon, route, loadChildren e permissions de cada módulo para construir a navegação e o roteamento lazy — tudo isso ANTES do bundle lazy carregar.
- O `PAC_MODULE_DESCRIPTOR` é o objeto estático que o shell usa como fallback de registro.
- SPEC-06 §1 especifica o objeto exato: `key: 'pac'`, `label: 'Calibração'`, `icon: 'tune'`, `route: '/pac'`, `loadChildren: () => import('@neuroassist/pac').then(m => m.PacModule)`, `permissions: ['pac.comando.read']`, `showInNav: true`, `order: 30`.
- Bloqueia: INT-FE-PAC-002 (PacShellIntegrationService.register usa este descriptor)

**Approach (execution path):**
1. Criar `libs/pac/shell-entry/src/lib/pac.descriptor.ts`.
2. Importar o tipo `ModuleDescriptor` de `@neuroassist/shared/contracts`.
3. Definir `export const PAC_MODULE_DESCRIPTOR: ModuleDescriptor = { key: 'pac', label: 'Calibração', icon: 'tune', route: '/pac', loadChildren: () => import('@neuroassist/pac').then(m => m.PacModule), permissions: ['pac.comando.read'], showInNav: true, order: 30 }`.
4. Re-exportar via `index.ts` (PAC-FE-003).
5. Unit test: assert todos os campos do descriptor.

**Non-goals:**
- Não registrar o descriptor no shell (responsabilidade do `PacShellIntegrationService` — INT-FE-PAC-002).

**Layer(s):**
frontend / libs/pac/shell-entry

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-06-shell-integration.md` (§1)

**Architecture alignment:**
- Objeto `const` puro; sem lógica
- Tag Nx: `scope:pac`, `type:domain`

**Implementation checklist (by file):**

**Lib: libs/pac/shell-entry**
- [ ] `libs/pac/shell-entry/src/lib/pac.descriptor.ts`
- [ ] `libs/pac/shell-entry/src/lib/pac.descriptor.spec.ts`

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
- `PAC_MODULE_DESCRIPTOR.key === 'pac'` e `PAC_MODULE_DESCRIPTOR.order === 30` (unit test).
- `PAC_MODULE_DESCRIPTOR.permissions` contém `'pac.comando.read'`.
- `npx nx build pac-shell-entry` PASS.

**Dependencies:**
- PAC-FE-003 (barrel re-export)
- `@neuroassist/shared/contracts` (tipo `ModuleDescriptor`)

**Data impact:**
Nenhum.

**API / Contract impact:**
Nenhum — descriptor é estático.

**Testing / Evidence required:**
- Unit: assert key, label, icon, route, order, permissions.

**Risks / Notes:**
- `loadChildren` referencia `@neuroassist/pac` — o path alias deve estar em `tsconfig.base.json` (PAC-FE-003) antes de qualquer build que inclua este descriptor.
