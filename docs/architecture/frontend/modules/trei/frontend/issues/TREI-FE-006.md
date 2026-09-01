## [TREI-FE-006] trei.nav.config.ts + libs/trei/navigation breadcrumb state

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/trei/navigation` publica `TreiNavConfig` com a árvore de breadcrumb (`Treinamento › Sessão`, `Treinamento › Propostas`) via `@neuroassist/shared/navigation`; `TreiShellIntegrationService` regista/cancela o feed no shell durante init/destroy.

**Context / Problem statement:**
- O shell da cuidadora renderiza o breadcrumb global a partir de contribuições registadas por cada módulo (SPEC-06 §2).
- TREI tem dois níveis de breadcrumb: `[Treinamento]` no root e `[Treinamento › Sessão]` / `[Treinamento › Propostas]` nas sub-rotas (SPEC-01 §2 master route table, coluna Breadcrumb).
- `TreiShellIntegrationService` é o único ponto de toque entre `scope:trei` e o shell chrome — nunca importa componentes `scope:web` directamente (ADR-006).
- Bloqueia: TREI-FE-005 (bootstrap chama `registerNav`).

**Approach (execution path):**
1. Criar `libs/trei/navigation/src/lib/trei-nav.config.ts`:
   - Definir `TREI_NAV_ENTRIES: BreadcrumbConfig[]` com 3 entradas: root (`/treinamento`, "Treinamento"), session (`/treinamento/sessao`, "Sessão"), proposals (`/treinamento/sessao/propostas`, "Propostas").
   - Usar i18n keys: `'trei.nav.breadcrumb.root'`, `'trei.nav.breadcrumb.session'`, `'trei.nav.breadcrumb.proposals'` (ADR-018).
2. Criar `libs/trei/integration/src/lib/trei-shell-integration.service.ts`:
   - Injectable, `providedIn: 'root'`.
   - `registerNav(ctx)`: publica `TREI_NAV_ENTRIES` no `BreadcrumbService` (shared).
   - `deregisterNav()`: remove as entradas TREI do feed.
   - Reporta `treinamentoEnabled` e scope `treinamento.sessao.conduzir` ao shared `MenuGatingService` para visibilidade reactiva do item "Treinamento" na sidebar.
3. Exportar `TreiShellIntegrationService` do barrel `libs/trei/integration/src/index.ts`.

**Non-goals:**
- Não renderizar o breadcrumb (responsabilidade do shell).
- Não implementar `BreadcrumbService` (pertence a `@neuroassist/shared/navigation`).

**Layer(s):**
frontend / libs/trei/navigation · libs/trei/integration

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-06-shell-integration.md` (§2)
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-01-route-map.md` (§2 — coluna Breadcrumb)

**Architecture alignment:**
- `scope:trei` → `scope:shared` apenas (nunca `scope:web`)
- Tag Nx: `scope:trei`, `type:domain`

**Implementation checklist (by file)**

**libs/trei/navigation**
- [ ] `libs/trei/navigation/src/lib/trei-nav.config.ts`
- [ ] `libs/trei/navigation/src/index.ts`

**libs/trei/integration**
- [ ] `libs/trei/integration/src/lib/trei-shell-integration.service.ts`
- [ ] `libs/trei/integration/src/index.ts`

**Tests**
- [ ] `nx test trei-integration` PASS
- [ ] `nx lint trei-integration` PASS — sem import de `scope:web`

**Milestone:**
Wave 1 / Foundation

**Labels:**
type:feature
layer:frontend
scope:trei
priority:P1

**Acceptance criteria:**
- `npx nx lint trei-integration` PASS (sem violação de boundary `scope:trei → scope:web`)
- `npx nx build trei-navigation` PASS
- `TreiShellIntegrationService.registerNav()` chama `BreadcrumbService.register(TREI_NAV_ENTRIES)`
- `TreiShellIntegrationService.deregisterNav()` remove entradas TREI sem afectar outros módulos

**Dependencies:**
- TREI-FE-003 (TREI_FEATURE_FLAGS para `treinamentoEnabled`)
- TREI-FE-002 (TREI_PERMISSION_MAP para scope `conduzir`)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Spy em `BreadcrumbService.register` — chamado com 3 entries TREI
- `nx lint` confirma sem violação de boundary

**Risks / Notes:**
- Verificar nomes exactos dos métodos de `BreadcrumbService` e `MenuGatingService` no `@neuroassist/shared/navigation` antes de implementar.
