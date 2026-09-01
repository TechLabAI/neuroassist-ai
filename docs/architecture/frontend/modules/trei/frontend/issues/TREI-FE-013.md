## [TREI-FE-013] TrainingSessionPageComponent (smart, reads TrainingSessionFacade signals)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/trei/feature-training-session/src/lib/training-session-page.component.ts` implementa o componente smart da Tela 08: lê `session()` e `aiDisclosed()` da `TrainingSessionFacade`, roteia outputs para os métodos da facade, e compõe os componentes presentacionais da sessão de treino.

**Context / Problem statement:**
- `TrainingSessionPageComponent` é o único componente smart desta feature lib — nenhum outro injecta a facade directamente (SPEC-02 §2).
- Exibe: `EmptyStateComponent` quando `status = 'idle'`, `ChatConversationPanelComponent` com os turns, `ConversationErrorPanelComponent` quando `status = 'error'`, `SessionEndedNoticeComponent` quando `status = 'ended' && hasPendingProposals`.
- O `aiDisclosed` do `session()` determina o estado do badge "gerado por IA" no `ChatConversationPanelComponent` (ADR-017).
- Bloqueia: TREI-FE-014/015/016/017 (usados por este componente).

**Approach (execution path):**
1. Criar `libs/trei/feature-training-session/src/lib/training-session-page.component.ts`.
2. `standalone: true`, `changeDetection: OnPush`.
3. Injectar `TrainingSessionFacade`, `ProposalReviewFacade` (para `proposals()` count → `hasPendingProposals`).
4. Expor `session = inject(TrainingSessionFacade).session` e `aiDisclosed = inject(TrainingSessionFacade).aiDisclosed`.
5. Template:
   - `@if (session().status === 'idle') { <trei-empty-state (start)="startSession()" /> }`
   - `@if (session().status !== 'idle') { <trei-locale-tabs ... /> <trei-chat-conversation-panel ... /> }`
   - `@if (session().status === 'error') { <trei-conversation-error-panel ... /> }`
   - `@if (session().status === 'ended' && session().hasPendingProposals) { <trei-session-ended-notice /> }`
   - `<trei-end-session-button (end)="facade.endSession()" />`
6. Output handlers: `startSession()`, `submitTurn(instrucao)`, `retryTurn()`.

**Non-goals:**
- Não renderizar as proposals (pertence ao `ProposalReviewPageComponent`).
- Não implementar componentes presentacionais aqui (TREI-FE-014/015/016/017).

**Layer(s):**
frontend / libs/trei/feature-training-session

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§2)
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-03-view-models.md` (§2)

**Architecture alignment:**
- `standalone: true`, `OnPush`, `@if` com Angular 17+ control flow
- Tag Nx: `scope:trei`, `type:feature`

**Implementation checklist (by file)**

**libs/trei/feature-training-session**
- [ ] `libs/trei/feature-training-session/src/lib/training-session-page.component.ts`
- [ ] `libs/trei/feature-training-session/src/lib/training-session.routes.ts`

**Tests**
- [ ] `nx test trei-feature-training-session` PASS
- [ ] `EmptyStateComponent` renderizado quando `status = 'idle'`
- [ ] `ConversationErrorPanelComponent` renderizado quando `status = 'error'`
- [ ] `startSession()` chama `TrainingSessionFacade.startSession()`
- [ ] `nx lint trei-feature-training-session` PASS

**Milestone:**
Wave 3 / Feature Libs

**Labels:**
type:feature
layer:frontend
scope:trei
priority:P3

**Acceptance criteria:**
- `npx nx test trei-feature-training-session` PASS
- `npx nx lint trei-feature-training-session` PASS
- `npx nx build trei-feature-training-session` PASS
- Nenhum `inject(HttpClient)` no componente smart (apenas facades)
- Nenhum `BehaviorSubject` no componente

**Dependencies:**
- TREI-FE-010 (TrainingSessionFacade)
- TREI-FE-014/015/016/017 (componentes presentacionais — podem ser stubs)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Indirecto via facade

**Testing / Evidence required:**
- Screenshots dos 4 estados: idle, active, error, ended
- Teste DOM: `<trei-authoring-banner>` está presente via pai `TreiRootComponent`

**Risks / Notes:**
- Confirmar o mecanismo de comunicação entre `TrainingSessionFacade` e `ProposalReviewFacade` para `hasPendingProposals` — pode ser um `computed` da `ProposalReviewFacade` injectado aqui.
