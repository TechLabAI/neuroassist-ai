## [TREI-FE-011] ProposalReviewFacade (signals + materialize/approveCuidadora idempotent/discard/refresh)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/trei/data-access/src/lib/proposal-review.facade.ts` implementa `ProposalReviewFacade` com `proposals: Signal<ProposedNodeVM[]>`, `summary: Signal<ApprovalSummaryVM>`, e os métodos de mutação — gere o estado dos nós rascunho para o locale activo, incluindo aprovação idempotente (G3), rollback de optimistic update, e descarte.

**Context / Problem statement:**
- A facade agrupa o working state de proposals: construída a partir dos `propostas[]` de cada turno e enriquecida por `GET /nos/{noId}` após materializar/aprovar (SPEC-04 §3 — sem list endpoint).
- **Aprovação idempotente (G3):** re-submeter a mesma `{noId, locale}` para um nó já aprovado pela cuidadora é um no-op no BE — o FE deve tratar como sucesso, não exibir conflito (SPEC-04 §4, `api-contract §2.4`).
- **Optimistic update + rollback:** `approveCaregiverSide` faz flip optimista de `caregiverApproved`, depois confirma com a resposta ou faz rollback + `refetch` + `SPEC-07` retriable error (SPEC-04 §4).
- `activeLocale` é lido do `TrainingSessionFacade` (INV-3 — uma única escritura no sistema).
- `proposals()` é projected para o `activeLocale` activo — muda de locale re-projecta sem alterar flags do outro locale.
- Bloqueia: TREI-FE-016/018/019/020 (page components e cards lêem esta facade), TREI-FE-024 (optimistic/pending states).

**Approach (execution path):**
1. Criar `libs/trei/data-access/src/lib/proposal-review.facade.ts`.
2. Injectar `ProposalHttpAdapter`, `TrainingSessionFacade`, `TreiErrorMapper`.
3. `private readonly _nodes = signal<ProposedNodeVM[]>([])` — working state de todos os nós.
4. `proposals = computed(() => this._nodes().filter(n => n.locale === this._session().activeLocale))`.
5. `summary = computed<ApprovalSummaryVM>(() => { ... })` — derivado de `proposals()`.
6. Implementar:
   - `addProposalsFromTurn(propostas: PropostaDto[])`: adiciona nós inerts (sem `nodeId`) após cada turno.
   - `materializeOrEditProposal({ propostaId, nodeId, locale, texto })`: chama `adapter.materialize()`, marca card `pending: true`, actualiza `nodeId` e `subEstado` na resposta.
   - `approveCaregiverSide({ nodeId, locale })`:
     1. Optimistic flip: `caregiverApproved = true`, `pending: true`.
     2. Chama `adapter.approveCuidadora(nodeId, locale)`.
     3. On success: reconcilia com flags retornados (idempotente — qualquer resposta de sucesso confirma `caregiverApproved = true`).
     4. On error: rollback `caregiverApproved`, `pending: false`, `refetch(nodeId)`, emite `AppError` retriable.
   - `discardDraft({ nodeId, locale })`: chama `adapter.discard()`, remove nó da lista activa.
   - `refreshForLocale(locale: Locale)`: chama `adapter.inspect()` para cada nó da projecção.
7. Garantir que `patientApproved` nunca é escrito (INV-1).

**Non-goals:**
- Não implementar o endpoint de aprovação do paciente.
- Não gerir o estado de turns (pertence ao `TrainingSessionFacade`).

**Layer(s):**
frontend / libs/trei/data-access

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-03-view-models.md` (§3/§4)
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-04-data-access.md` (§3/§4/§6)

**Architecture alignment:**
- `signal<T>`, `computed<T>` — zero `BehaviorSubject`
- Tag Nx: `scope:trei`, `type:data-access`

**Implementation checklist (by file)**

**libs/trei/data-access**
- [ ] `libs/trei/data-access/src/lib/proposal-review.facade.ts`
- [ ] `approveCaregiverSide`: optimistic + idempotent + rollback
- [ ] `patientApproved` nunca é escrito por nenhum método
- [ ] `proposals` é `computed` filtrado por `activeLocale`
- [ ] `summary` é `computed` de `proposals()`

**Tests**
- [ ] `nx test trei-data-access` PASS
- [ ] `approveCaregiverSide` em nó já aprovado → sem erro (G3 idempotente)
- [ ] `approveCaregiverSide` com falha HTTP → rollback + refetch
- [ ] `switchLocale` (via facade) → `proposals()` re-projectado para novo locale
- [ ] Nenhum método escreve `patientApproved` (INV-1)
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
- Re-aprovação do mesmo nó não exibe erro (G3)
- Rollback de optimistic update: `caregiverApproved` volta a `false` após falha
- `grep -n 'patientApproved\s*=' libs/trei/data-access/` retorna vazio (nunca escrito)

**Dependencies:**
- TREI-FE-008 (ProposalHttpAdapter)
- TREI-FE-009 (ProposedNodeVM, ApprovalSummaryVM)
- TREI-FE-010 (TrainingSessionFacade para activeLocale)
- TREI-FE-012 (TreiErrorMapper)

**Data impact:**
- Nenhum (working state em memória)

**API / Contract impact:**
- Consumes: POST /nos, GET /nos/{id}, DELETE /nos/{id}, POST /nos/{id}/aprovacoes/cuidadora, GET /nos/{id}/publicacao

**Testing / Evidence required:**
- Teste: `approveCaregiverSide` chamado 2x sobre mesmo nó → 2ª chamada não altera estado nem emite erro
- Teste: locale switch → `proposals()` length muda para o novo locale

**Risks / Notes:**
- A idempotência (G3) deve ser confirmada pelo BE retornando `200 OK` com `confirmadoCuidadora: true` — o FE trata qualquer `2xx` como sucesso.
