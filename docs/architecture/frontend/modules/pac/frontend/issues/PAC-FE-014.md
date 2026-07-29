## [PAC-FE-014] feature-command-catalog — CommandCatalogComponent + KpiBar + EmptyState + ErrorCard

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/feature-command-catalog` implementa `CommandCatalogComponent` (smart) e seus três filhos apresentacionais (`CatalogKpiBarComponent`, `CatalogEmptyStateComponent`, `CatalogErrorCardComponent`) com todos os estados de loading/loaded/empty/error e o KPI de pendentes de sincronização derivado do catálogo, conforme SPEC-02 §1 e SPEC-03.

**Context / Problem statement:**
- `CommandCatalogComponent` lê signals do `PacComandoFacade`: `catalog()`, `loadState()`, `kpis()` e despacha `load(pid)`, `newCommand()`, `editCommand(id)`.
- Estados mutuamente exclusivos: loading → spinner (não stale rows), error → `CatalogErrorCardComponent` + retry, empty → `CatalogEmptyStateComponent` + addFirst CTA, loaded → lista de `CommandRowComponent` (PAC-FE-019 — libs/pac/ui).
- `SyncStatusBadge` por linha deriva de `row.syncState` (signal derivado do facade — nunca 'synced' sem device-ack — ADR-005).
- KPI: `CatalogKpiBarComponent` mostra total, emergências e pendentesSync calculados pelo facade.
- Ações por linha: edit, recalibrate, retire — emitidas como @Output para o smart component despachar.
- Bloqueia: PAC-FE-023 (e2e command-catalog)

**Approach (execution path):**
1. Criar `libs/pac/feature-command-catalog/src/lib/command-catalog.component.ts` (standalone, OnPush).
2. Injetar `PacComandoFacade`, `Router`; no `ngOnInit` chamar `facade.load(patient.id)`.
3. Template: `@switch (loadState())` → loading / error / empty / loaded branches.
4. Criar `catalog-kpi-bar.component.ts` (presentacional): `@Input kpis: CatalogKpiVM`, renderiza 3 KPIs em badges.
5. Criar `catalog-empty-state.component.ts` (presentacional): `@Output addFirst` — botão "Adicionar primeiro comando".
6. Criar `catalog-error-card.component.ts` (presentacional): `@Input error: AppError`, `@Output retry`.
7. No loaded state: `@for (row of catalog(); track row.id)` usando `CommandRowComponent` de `libs/pac/ui` (PAC-FE-019).
8. Unit tests: mock facade signals; verificar que cada estado renderiza o componente correto.

**Non-goals:**
- `CommandRowComponent` é de `libs/pac/ui` (PAC-FE-019), não implementado aqui.
- GEST live-test NÃO aparece no catalog page — apenas no editor.

**Layer(s):**
frontend / libs/pac/feature-command-catalog

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§1)
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-03-view-models.md` (§1 CatalogLoadState)

**Architecture alignment:**
- Standalone + `ChangeDetectionStrategy.OnPush`
- Signals-only reads (sem `async` pipe)
- Tag Nx: `scope:pac`, `type:feature`

**Implementation checklist (by file):**

**Lib: libs/pac/feature-command-catalog**
- [ ] `libs/pac/feature-command-catalog/src/lib/command-catalog.component.ts`
- [ ] `libs/pac/feature-command-catalog/src/lib/catalog-kpi-bar.component.ts`
- [ ] `libs/pac/feature-command-catalog/src/lib/catalog-empty-state.component.ts`
- [ ] `libs/pac/feature-command-catalog/src/lib/catalog-error-card.component.ts`
- [ ] `libs/pac/feature-command-catalog/src/lib/command-catalog.component.spec.ts`

**Tests**
- [ ] `npx nx test pac-feature-command-catalog` PASS
- [ ] `npx nx lint pac-feature-command-catalog` PASS
- [ ] `npx nx build pac-feature-command-catalog` PASS

**Milestone:**
Wave 3 / P3 Feature Libs

**Labels:**
type:feature
layer:frontend
scope:pac
priority:P3

**Acceptance criteria:**
- `npx nx test pac-feature-command-catalog` PASS: loadState=loading → loading spinner presente no DOM.
- `npx nx test pac-feature-command-catalog` PASS: loadState=error → `CatalogErrorCardComponent` com retry.
- `npx nx test pac-feature-command-catalog` PASS: catalog vazio → `CatalogEmptyStateComponent`.
- `npx nx test pac-feature-command-catalog` PASS: 2 rows com syncState='pending' → `kpis().pendentesSync === 2`.
- `npx nx lint pac-feature-command-catalog` PASS (sem imports de `scope:gest`, `scope:web`).

**Dependencies:**
- PAC-FE-011 (PacComandoFacade — signals)
- PAC-FE-013 (CatalogKpiVM, CatalogLoadState, AppError)
- PAC-FE-019 (CommandRowComponent de libs/pac/ui)

**Data impact:**
Nenhum.

**API / Contract impact:**
- Load chama `PacComandoFacade.load(pid)` → `GET /api/pac/v1/pacientes/{pid}/comandos`.

**Testing / Evidence required:**
- Screenshot de cada estado (loading, error, empty, loaded com KPI) — evidência de coverage visual (SPEC-02).

**Risks / Notes:**
- EARS orphans mapeados para esta issue: cenários "Device-acknowledged comando shows ativo no dispositivo" e "KPI counter derives comandos pendentes" requerem que o signal `syncState` seja `'pending'` por default; o cenário de "ativo no dispositivo" só é verde quando INT-FE-PAC-001 implementar device-ack real.
