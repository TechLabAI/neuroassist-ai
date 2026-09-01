## [TREI-FE-010] TrainingSessionFacade (signals + startSession/submitTurn/retryLastTurn/switchLocale/endSession)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/trei/data-access/src/lib/training-session.facade.ts` implementa `TrainingSessionFacade` com `session: Signal<TrainingSessionVM>`, `aiDisclosed: Signal<boolean>`, e os métodos de mutação — gere o estado da sessão de treino, mapeia erros via `TreiErrorMapper`, e expõe `seedLocale`, `loadHealth`, `clearTurns` para o bootstrap.

**Context / Problem statement:**
- A facade é o único produtor de estado da sessão; componentes lêem sinais via `.asReadonly()` (doc 05 §1).
- `submitTurn` chama `TrainingSessionHttpAdapter.propose()`, aguarda a resposta, adiciona o turno do assistente apenas quando o BE retorna (INV-2 — o FE nunca fabrica texto de assistente).
- `switchLocale` muda `activeLocale` com uma escritura única (INV-3) e notifica `ProposalReviewFacade` via o mesmo sinal ou um token compartilhado.
- `endSession` muda `status` para `'ended'` mas não descarta rascunhos — apenas limpa `turns` do estado local (INV-4).
- `loadHealth` (best-effort): popula `llmModelId` e actualiza qualquer indicador de degradação — swallows error (SPEC-06 §3).
- Bloqueia: TREI-FE-013 (page component lê este facade), TREI-FE-005 (bootstrap chama seedLocale/loadHealth).

**Approach (execution path):**
1. Criar `libs/trei/data-access/src/lib/training-session.facade.ts`.
2. Injectar `TrainingSessionHttpAdapter`, `TreiErrorMapper`.
3. Declarar `private readonly _session = signal<TrainingSessionVM>(initialState)`.
4. Expor `session = this._session.asReadonly()`, `aiDisclosed = computed(() => this._session().aiDisclosed)`.
5. Implementar:
   - `startSession(patientId: string)`: chama `adapter.start()`, muda status para `'active'`, armazena `sessaoId` e `llmModelId`.
   - `submitTurn(instrucao: string)`: status → `'assistant-thinking'`, chama `adapter.propose()`, na resposta adiciona `ConversationTurnVM` de assistente + actualiza proposals signal (delega a `ProposalReviewFacade`).
   - `retryLastTurn()`: re-submete o último `instrucao`.
   - `switchLocale(locale: Locale)`: escreve `activeLocale` (única escritura — INV-3).
   - `endSession()`: status → `'ended'`, não descarta rascunhos (INV-4).
   - `seedLocale(locale: string)`: normaliza para `{pt,en}`, escreve `activeLocale`.
   - `loadHealth()`: chama `adapter.readiness()`, best-effort (catch swallow).
   - `clearTurns()`: limpa array de `turns` (chamado em destroy).

**Non-goals:**
- Não gerir o estado de proposals aqui (pertence à `ProposalReviewFacade`).
- Não implementar SSE (deferred — `streamingConversation: false`).

**Layer(s):**
frontend / libs/trei/data-access

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-03-view-models.md` (§2)
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-04-data-access.md` (§2/§4)

**Architecture alignment:**
- `signal<T>`, `computed<T>`, `asReadonly()` — zero `BehaviorSubject`
- Tag Nx: `scope:trei`, `type:data-access`

**Implementation checklist (by file)**

**libs/trei/data-access**
- [ ] `libs/trei/data-access/src/lib/training-session.facade.ts`
- [ ] `startSession`, `submitTurn`, `retryLastTurn`, `switchLocale`, `endSession`
- [ ] `seedLocale`, `loadHealth` (best-effort), `clearTurns` (para bootstrap/destroy)

**Tests**
- [ ] `nx test trei-data-access` PASS
- [ ] `submitTurn`: status = `'assistant-thinking'` enquanto aguarda; assistant turn adicionado só após resposta (INV-2)
- [ ] `endSession`: `hasPendingProposals` não se altera (INV-4)
- [ ] `loadHealth`: swallows HTTP error, não altera status
- [ ] `nx lint trei-data-access` PASS

**Milestone:**
Wave 2 / Data Access

**Labels:**
type:feature
layer:frontend
scope:trei
priority:P2

**Acceptance criteria:**
- `npx nx test trei-data-access` PASS
- `npx nx lint trei-data-access` PASS
- `session()` após `submitTurn` com resposta BE: inclui turno de assistente com `text` derivado das `propostas[]`
- `switchLocale` é o único método que escreve `activeLocale` (INV-3 — verificável por grep)
- `endSession` não chama `ProposalReviewFacade.discardDraft`

**Dependencies:**
- TREI-FE-007 (TrainingSessionHttpAdapter)
- TREI-FE-009 (TrainingSessionVM, SessionStatus, Locale)
- TREI-FE-012 (TreiErrorMapper)

**Data impact:**
- Nenhum (estado em memória, limpo em destroy)

**API / Contract impact:**
- Consumes: POST /sessoes, POST /sessoes/{id}/propostas, GET /health

**Testing / Evidence required:**
- Spy em `adapter.propose()` — chamado com último `instrucao` em `retryLastTurn()`
- `session().status` = `'ended'` após `endSession()` com `hasPendingProposals` inalterado

**Risks / Notes:**
- A comunicação entre `TrainingSessionFacade` e `ProposalReviewFacade` (para proposals do turno) pode ser via Angular DI injection ou via um signal compartilhado no data-access barrel — decidir no scaffold.
