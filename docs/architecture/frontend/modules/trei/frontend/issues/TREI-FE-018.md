## [TREI-FE-018] ProposalReviewPageComponent (smart, deep-linkable /treinamento/sessao/propostas G2)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/trei/feature-proposal-review/src/lib/proposal-review-page.component.ts` é o componente smart da superfície de revisão de propostas, acessível via deep-link `/treinamento/sessao/propostas` mesmo sem um turn activo — exibe rascunhos pendentes para o locale activo (G2).

**Context / Problem statement:**
- **G2 (gap do specs-validate):** a SPEC-01 §1 diz "ProposalReviewPageComponent tem a sua própria rota de forma que a cuidadora pode fazer deep-link para a revisão sem um turno de conversa activo" — nenhum cenário e2e testava este caminho directamente. Esta issue garante que o componente funciona sem sessão activa (exibe rascunhos pré-existentes do `ProposalReviewFacade`).
- O componente lê `proposals()`, `summary()`, e `activeLocale()` da `ProposalReviewFacade` (SPEC-02 §3).
- Em deep-link sem sessão: `TrainingSessionFacade.session().status = 'idle'` mas `proposals()` pode ter itens de turns anteriores — o componente renderiza-os normalmente (doc 02 §3 edge case).
- A rota `/treinamento/sessao/propostas` usa `treiProporGuard` para acesso à edição mas NÃO bloqueia a leitura/revisão (G1).

**Approach (execution path):**
1. Criar `proposal-review-page.component.ts`: `standalone: true`, `OnPush`.
2. Injectar `ProposalReviewFacade`, `TrainingSessionFacade`.
3. Expor: `proposals = inject(ProposalReviewFacade).proposals`, `summary = inject(ProposalReviewFacade).summary`, `activeLocale = inject(TrainingSessionFacade).session`.
4. Template: `<trei-proposal-list [proposals]="proposals()" />` + `<trei-approval-summary-bar [summary]="summary()" />` + `<trei-proposal-empty-state />` quando lista vazia.
5. Output handlers: `onApprove({nodeId,locale})` → `facade.approveCaregiverSide()`, `onDiscard({nodeId,locale})` → `facade.discardDraft()`, `onEdit({nodeId,locale})` → abre `ProposalEditDialog`.
6. Criar `proposal-review.routes.ts` com a rota `/treinamento/sessao/propostas`.

**Non-goals:**
- Não implementar os sub-componentes presentacionais (TREI-FE-019/020).
- Não bloquear acesso de leitura quando `treiProporGuard` falha — só as acções de editar/propor são bloqueadas (G1).

**Layer(s):**
frontend / libs/trei/feature-proposal-review

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§3)
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-01-route-map.md` (§1 — deep-link)

**Architecture alignment:**
- `standalone: true`, `OnPush`
- Tag Nx: `scope:trei`, `type:feature`

**Implementation checklist (by file)**

**libs/trei/feature-proposal-review**
- [ ] `proposal-review-page.component.ts`
- [ ] `proposal-review.routes.ts`
- [ ] Deep-link funciona sem `sessaoId` activo (G2)

**Tests**
- [ ] `nx test trei-feature-proposal-review` PASS
- [ ] Deep-link: navegar para `/treinamento/sessao/propostas` com `session.status = 'idle'` → proposals pré-existentes renderizadas (G2)
- [ ] `ProposalEmptyStateComponent` exibido quando `proposals().length === 0`
- [ ] `nx lint trei-feature-proposal-review` PASS

**Milestone:**
Wave 3 / Feature Libs

**Labels:**
type:feature
layer:frontend
scope:trei
priority:P3

**Acceptance criteria:**
- `npx nx test trei-feature-proposal-review` PASS
- `npx nx lint trei-feature-proposal-review` PASS
- `npx nx build trei-feature-proposal-review` PASS
- Navegação directa para `/treinamento/sessao/propostas` sem sessão activa → renderiza lista (G2)
- Nenhum `inject(HttpClient)` no componente smart

**Dependencies:**
- TREI-FE-011 (ProposalReviewFacade)
- TREI-FE-019/020 (componentes presentacionais — podem ser stubs)
- TREI-FE-023 (treiProporGuard — protege acções mas não a visualização)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Indirecto via facade (GET /nos/{id} via `refreshForLocale`)

**Testing / Evidence required:**
- Teste de navegação: `/treinamento/sessao/propostas` carrega sem erro quando `status = 'idle'` (G2)
- Screenshot: lista de rascunhos em deep-link

**Risks / Notes:**
- **G2:** o `ProposalReviewFacade` deve ter proposals em memória de turns anteriores da sessão actual. Se a cuidadora navegar de `/sessao` para `/sessao/propostas` e voltar, os proposals devem persistir (working state na facade, não na rota).
