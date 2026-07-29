## [PAC-FE-019] libs/pac/ui — CommandRowComponent, UrgencyBadgeComponent, SensitivitySelectorComponent, SyncStatusBadgeComponent

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/ui` contém os quatro componentes apresentacionais reutilizáveis do módulo PAC: `CommandRowComponent` (linha do catálogo com ações), `UrgencyBadgeComponent`, `SensitivitySelectorComponent` (label-only, sem threshold) e `SyncStatusBadgeComponent` (honest state 'pendente'/'ativo') — todos puros (@Input/@Output, sem facade access).

**Context / Problem statement:**
- SPEC-02 §1/§4: `CommandRowComponent` é consumido por `CommandCatalogComponent` (PAC-FE-014); os outros badges são compartilhados entre catalog e editor.
- `SensitivitySelectorComponent` mostra APENAS `baixa|media|alta` — NUNCA o threshold numérico (SPEC-03 §1, SPEC-05 constraint).
- `SyncStatusBadgeComponent` recebe `syncState: 'synced' | 'pending'`; quando `pac.deferredEdgeSync=off` (SPEC-05), o smart component não passa este badge ao template.
- `UrgencyBadgeComponent` diferencia emergencia (styling de alerta) de rotina (neutro).

**Approach (execution path):**
1. Criar `libs/pac/ui/src/lib/command-row.component.ts` (standalone, OnPush). `@Input row: ComandoRowVM`; `@Output edit`, `@Output recalibrate`, `@Output retire`. Renderiza: rótulo, gestoDescricao, `UrgencyBadgeComponent`, `SensitivitySelectorComponent`, `SyncStatusBadgeComponent`.
2. Criar `urgency-badge.component.ts` (standalone, OnPush). `@Input urgencia: 'emergencia' | 'rotina'`. Classe CSS `urgency--emergencia` / `urgency--rotina`.
3. Criar `sensitivity-selector.component.ts` (standalone, OnPush). `@Input sensibilidade: 'baixa' | 'media' | 'alta'`. Renderiza label PT-BR; zero números.
4. Criar `sync-status-badge.component.ts` (standalone, OnPush). `@Input syncState: 'synced' | 'pending'`. Label "ativo no dispositivo" / "pendente de sincronização".
5. `libs/pac/ui/src/index.ts` re-exporta todos.
6. Unit tests para cada componente.

**Non-goals:**
- Sem acesso a facades ou stores — puramente apresentacional.
- `SensitivitySelectorComponent` aqui é display-only (no editor, pode ter @Output se for interativo — mas o display puro fica aqui).

**Layer(s):**
frontend / libs/pac/ui

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§1 CommandRowComponent e badges)
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-03-view-models.md` (§1 syncState)

**Architecture alignment:**
- Standalone + OnPush; puro @Input/@Output
- Tag Nx: `scope:pac`, `type:ui`

**Implementation checklist (by file):**

**Lib: libs/pac/ui**
- [ ] `libs/pac/ui/src/lib/command-row.component.ts`
- [ ] `libs/pac/ui/src/lib/urgency-badge.component.ts`
- [ ] `libs/pac/ui/src/lib/sensitivity-selector.component.ts`
- [ ] `libs/pac/ui/src/lib/sync-status-badge.component.ts`
- [ ] `libs/pac/ui/src/lib/command-row.component.spec.ts`
- [ ] `libs/pac/ui/src/index.ts`

**Tests**
- [ ] `npx nx test pac-ui` PASS
- [ ] `npx nx lint pac-ui` PASS
- [ ] `npx nx build pac-ui` PASS

**Milestone:**
Wave 7 / P7 UX Polish

**Labels:**
type:feature
layer:frontend
scope:pac
priority:P7

**Acceptance criteria:**
- `npx nx test pac-ui` PASS: `SensitivitySelectorComponent` com `sensibilidade='baixa'` renderiza label 'Baixa', não nenhum número.
- `npx nx test pac-ui` PASS: `SyncStatusBadgeComponent` com `syncState='pending'` renderiza 'pendente de sincronização'.
- `npx nx test pac-ui` PASS: `UrgencyBadgeComponent` com `urgencia='emergencia'` tem classe `urgency--emergencia`.
- `npx nx test pac-ui` PASS: `CommandRowComponent` emite `@Output edit` ao click no botão editar.
- `npx nx lint pac-ui` PASS.

**Dependencies:**
- PAC-FE-013 (ComandoRowVM, tipos de sensibilidade, urgência, syncState)

**Data impact:**
Nenhum.

**API / Contract impact:**
Nenhum.

**Testing / Evidence required:**
- Unit: label rendering para cada variante de cada badge.

**Risks / Notes:**
- `SensitivitySelectorComponent` pode também ser usado com `@Output change` na feature-command-editor (modo interativo); garantir que o componente em `libs/pac/ui` suporta ambos os modos via `@Input readonly = false`.
