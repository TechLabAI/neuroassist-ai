## [FE-HIST-008] hist data-access: HistoryFacade (signals-first)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`HistoryFacade` em `libs/hist/data-access` implementa a camada de estado signals-first: `#items`, `#period`, `#status`, `#page`, `#hasMore`, `#error`, `#markings`, `screenVm` (computed), e métodos `load`, `setPeriod`, `loadMore`, `markFeedback`, `retry`.

**Context / Problem statement:**
- SPEC-03 §3/§4: shape dos signals e API pública da Facade.
- `setPeriod` atualiza URL via `Router.navigate` com `replaceUrl: true`; reseta página para 0; chama `load`.
- `markFeedback` — non-optimistic: `marking=marking` → POST → `marking=marked` (ou `mark-error`/409 reconcile).
- `hasMore` = `(page + 1) * size < total` (api-contract §1 pagination).
- RxJS vive apenas em `HistClient`/`FeedbackClient`; Facade converte observables para signals com `toSignal` ou subscriptions gerenciadas.
- Bloqueia: FE-HIST-009 (smart component) e toda a wave P3.

**Approach (execution path):**
1. `HistoryFacade` — `@Injectable({ providedIn: 'root' })`.
2. Signals privados (WritableSignal): `#items`, `#period`, `#status`, `#page`, `#hasMore`, `#error`, `#markings`.
3. `screenVm = computed<HistoryScreenVm>(...)` — monta VM a partir dos signals.
4. `load(period)`: `status=loading`, `page=0`; GET history via `HistClient`; no success `status=loaded/empty`; no error mapa para `AppError` via SPEC-06.
5. `setPeriod(p)`: `Router.navigate(['.'], { queryParams: { period: p }, replaceUrl: true })`; `load(p)`.
6. `loadMore()`: `page+1`; GET; `#items.update(prev => [...prev, ...newItems])`.
7. `markFeedback(id, verdict)`: atualizar `#markings` → POST via `FeedbackClient` → success: `marked` + set item verdict; 409: reconcile; error: `mark-error`.
8. `retry()`: re-executa `load(#period())`.
9. Expõe sinais como `.asReadonly()`.

**Non-goals:**
- Não implementar componentes — wave P3.
- Não implementar error UI — FE-HIST-017..019.

**Layer(s):**
frontend / data-access

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-03-state-contracts.md` (§3, §4, §5)
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-04-api-integration.md` (§2)

**Architecture alignment:**
- Signals-first, sem NgRx, sem BehaviorSubject
- Tag Nx: `scope:hist`, `type:data-access`

**Implementation checklist (by file):**
- 🔲 `libs/hist/data-access/src/lib/history.facade.ts`
- 🔲 `libs/hist/data-access/src/lib/history.facade.spec.ts` (fakeAsync, provideRouter, mock HistClient + FeedbackClient)
- 🔲 Atualizar `src/index.ts`
- 🔲 `nx test hist-data-access` PASS
- 🔲 `nx lint hist-data-access` PASS

**Milestone:** Wave P2 / Data Access

**Labels:**
type:feature · layer:frontend · scope:hist · priority:P2

**Acceptance criteria:**
- `npx nx test hist-data-access` PASS incluindo Facade tests.
- `markFeedback` não atualiza `verdict` do item antes de receber resposta do backend.
- `409` reconcilia o item ao veredicto do backend sem override client-side.
- `setPeriod` reseta `page` para 0 e reflete `period` no URL.
- `#items` retém páginas carregadas; `setPeriod` limpa e reinicia.

**Dependencies:**
- FE-HIST-005, FE-HIST-006, FE-HIST-007

**Risks / Notes:**
- Gerenciamento de assinaturas: usar `takeUntilDestroyed` ou `DestroyRef` para cleanup.
- Concorrência: `loadMore` simultâneos devem ser ignorados se `status=loading`.
