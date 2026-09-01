## [TREI-FE-004] TreiRootComponent + barrel index.ts + TREI_MODULE_DESCRIPTOR

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/trei/shell-entry` expõe `TreiRootComponent` (smart, host da subtree + banner + router-outlet), o barrel `index.ts` com todos os exports públicos, e `TREI_MODULE_DESCRIPTOR` (composto de TOOL_KEY + MENU + PERMISSIONS + FLAGS + ROUTES) — o objeto que o shell consome para registrar TREI.

**Context / Problem statement:**
- `TreiRootComponent` é o componente raiz da subtree `/treinamento`: hospeda o `<trei-authoring-banner>` (INV-6, sempre on) e o `<router-outlet/>`. Ele não faz fetch próprio — apenas lê `TrainingSessionFacade.session()` para saber se há sessão ativa (SPEC-02 §1).
- `TREI_MODULE_DESCRIPTOR` é o único objeto que `scope:web` importa de `@neuroassist/trei`; consolida todos os contratos em um único ponto de entrada (SPEC-05 §4, SPEC-06 §1).
- O barrel `index.ts` expõe o descriptor, o módulo Angular, e os routes — nunca expõe componentes internos (encapsulamento de lib).
- Bloqueia: INT-FE-TREI-004 (shell adiciona TREI_MODULE_DESCRIPTOR ao array de descriptors).

**Approach (execution path):**
1. Criar `libs/trei/shell-entry/src/lib/trei-root.component.ts`: `standalone: true`, `changeDetection: OnPush`, injeta `TrainingSessionFacade`, renderiza `<trei-authoring-banner [session]="session()"/>` e `<router-outlet/>`.
2. Criar `libs/trei/shell-entry/src/lib/trei.module.ts` (NgModule vazio ou apenas com `RouterModule`) se a entrada lazy usar `loadChildren` com NgModule.
3. Criar `libs/trei/shell-entry/src/lib/trei.routes.ts` com as 3 rotas do master route table (SPEC-01 §2): redirect `/treinamento` → `/treinamento/sessao`, `TrainingSessionPageComponent`, `ProposalReviewPageComponent` com guards.
4. Montar `TREI_MODULE_DESCRIPTOR = { key: TREI_TOOL_KEY, menu: TREI_MENU_METADATA, permissions: TREI_PERMISSION_MAP, flags: TREI_FEATURE_FLAGS, routes: TREI_ROUTES }`.
5. Atualizar `index.ts` do barrel: exportar `TreiModule`, `TREI_ROUTES`, `TREI_MODULE_DESCRIPTOR` e os contratos (SPEC-05/06).

**Non-goals:**
- Não implementar os page components (`TrainingSessionPageComponent`, `ProposalReviewPageComponent`) aqui (TREI-FE-013/018).
- Não implementar o `TreiAuthoringBannerComponent` aqui (TREI-FE-021).

**Layer(s):**
frontend / libs/trei/shell-entry

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-01-route-map.md` (§2/§4)
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§1)
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-05-tool-contract.md` (§4)
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-06-shell-integration.md` (§1)

**Architecture alignment:**
- `standalone: true`, `changeDetection: OnPush` em `TreiRootComponent`
- `loadComponent` lazy para `TrainingSessionPageComponent` e `ProposalReviewPageComponent`
- Tag Nx: `scope:trei`, `type:app` (para `shell-entry`)

**Implementation checklist (by file)**

**libs/trei/shell-entry**
- [ ] `libs/trei/shell-entry/src/lib/trei-root.component.ts`
- [ ] `libs/trei/shell-entry/src/lib/trei.routes.ts` (3 rotas + redirect + wildcard)
- [ ] `libs/trei/shell-entry/src/lib/trei.module.ts` (entry NgModule se necessário)
- [ ] `libs/trei/shell-entry/src/index.ts` (barrel: TreiModule, TREI_ROUTES, TREI_MODULE_DESCRIPTOR + contratos de TREI-FE-001/002/003)

**Tests**
- [ ] `nx test trei-shell-entry` PASS
- [ ] `nx lint trei-shell-entry` PASS
- [ ] `nx build trei-shell-entry` PASS

**Milestone:**
Wave 1 / Foundation

**Labels:**
type:feature
layer:frontend
scope:trei
priority:P1

**Acceptance criteria:**
- `npx nx test trei-shell-entry` PASS
- `npx nx lint trei-shell-entry` PASS
- `npx nx build trei-shell-entry` PASS
- `TreiRootComponent` renderiza `<trei-authoring-banner>` em todas as rotas da subtree (verificável em teste DOM)
- Rota `/treinamento` redireciona para `/treinamento/sessao`
- Rota `/treinamento/sessao/propostas` usa `treiProporGuard`
- Wildcard `**` dentro da subtree redireciona para `/treinamento/sessao`

**Dependencies:**
- TREI-FE-001 (TREI_TOOL_KEY, TREI_MENU_METADATA, TREI_ROUTES)
- TREI-FE-002 (TREI_PERMISSION_MAP)
- TREI-FE-003 (TREI_FEATURE_FLAGS)
- TREI-FE-021 (TreiAuthoringBannerComponent — pode ser stub no início)
- TREI-FE-022/023 (guards — podem ser stubs no início com `() => true`)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum (rotas e contratos estáticos)

**Testing / Evidence required:**
- Teste de navegação: `router.navigate(['/treinamento'])` resulta em `/treinamento/sessao`
- Teste de snapshot do `TreiRootComponent`: presença de `<trei-authoring-banner>`

**Risks / Notes:**
- Decidir se a entrada lazy usa NgModule (`loadChildren`) ou standalone routes (`loadChildren` com array de Routes) — SPEC-01 diz `loadChildren: () => import('@neuroassist/trei').then(m => m.TreiModule)`; manter NgModule para compatibilidade.
