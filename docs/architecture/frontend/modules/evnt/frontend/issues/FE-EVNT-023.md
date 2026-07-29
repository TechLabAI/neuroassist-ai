## [FE-EVNT-023] Error handler — 404, 403, tenant-mismatch (ERR-01/02, GUARD-04)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
O `EventDetailPageComponent` e o interceptor de erros tratam corretamente os erros 404/403 do EVNT: redirecionam para `/eventos` em not-found, exibem mensagem inline em forbidden, e não revelam existência de eventos de outros tenants.

**Context / Problem statement:**
- ERR-01 gap (M3.5): `404 /errors/event-not-found` → redirect `/eventos` + toast "Evento não encontrado." (SPEC-07 §1).
- ERR-02 gap (M3.5): `403 /errors/forbidden` → mostrar "Você não é responsável por este paciente." inline (SPEC-07 §1); NÃO limpar alarme ativo.
- GUARD-04 gap (M3.5): `403 /errors/tenant-mismatch` → redirect sem revelar existência (SPEC-07 §1).
- Estes erros chegam via `AppError` do shared interceptor (INT-FE-EVNT-006).

**Approach (execution path):**
1) No `EventDetailPageComponent` (FE-EVNT-016): no `ngOnInit`, tratar o resultado de `loadDetail(id)` com `catchError`.
2) `AppError.kind === 'NotFound'` → `router.navigate(['/eventos'])` + toast (via `@neuroassist/shared/ui-feedback`) "Evento não encontrado." (ERR-01).
3) `AppError.kind === 'Forbidden'` com type `tenant-mismatch` → `router.navigate(['/'])` sem toast (GUARD-04) — não revelar.
4) `AppError.kind === 'Forbidden'` sem tenant-mismatch → mensagem inline "Você não é responsável por este paciente." (ERR-02) — sem redirect.
5) Garantir que `AppError` inclui o `type` do ProblemDetail para distinguir os casos.

**Non-goals:**
- Erros de confirmação de alarme (FE-EVNT-012).
- Erros de stream degradado (FE-EVNT-024).

**Layer(s):**
frontend / libs/evnt/feature-routine (EventDetailPageComponent) + shared interceptor

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-07-error-handling.md` (§1)

**Architecture alignment:**
- Inline errors: nunca toast no caminho safety; toast OK para not-found (baixo risco)
- Tag Nx: `scope:evnt`, `type:feature`

**Implementation checklist (by file):**

**Lib feature-routine (EventDetailPageComponent)**
- [ ] `catchError` em `loadDetail()`: `NotFound` → navigate `/eventos` + toast (ERR-01)
- [ ] `catchError`: `Forbidden` com `type=tenant-mismatch` → navigate `/` sem toast (GUARD-04)
- [ ] `catchError`: `Forbidden` sem mismatch → inline error message (ERR-02); NOT redirect, NOT clear alarm
- [ ] `error` signal no Facade retornado para o template via `EventDetailVm.error`
- [ ] Template: `@if (error())` → mensagem inline de erro em pt-BR

**Tests**
- [ ] `nx test evnt-feature-routine` PASS
- [ ] Unit: `loadDetail` lança `NotFound` → router navega para `/eventos`
- [ ] Unit: `loadDetail` lança `Forbidden(tenant-mismatch)` → router navega para `/`
- [ ] Unit: `loadDetail` lança `Forbidden(forbidden)` → inline error, sem navigate
- [ ] `nx lint evnt-feature-routine` PASS

**Milestone:**
Wave P7 / UX polish

**Labels:**
type:feature
layer:frontend
scope:evnt
priority:P7

**Acceptance criteria:**
- `npx nx test evnt-feature-routine` PASS
- ERR-01: URL 404 → redirect `/eventos` + toast visível
- GUARD-04: tenant-mismatch → redirect `/` sem toast
- ERR-02: 403 forbidden → mensagem inline, página não redireciona

**Dependencies:**
- FE-EVNT-008 (EventDeliveryFacade — `error` signal)
- FE-EVNT-016 (EventDetailPageComponent — host)
- INT-FE-EVNT-006 (ProblemDetail → AppError mapper)

**Data impact:**
- Nenhum

**API / Contract impact:**
- `GET /api/v1/events/{id}` — respostas 404 e 403 com `type` discriminante

**Testing / Evidence required:**
- Unit: mock de `facade.loadDetail` rejeita com `AppError({ kind: 'NotFound' })` → navigate chamado

**Risks / Notes:**
- ERR-02 gap (M3.5): a distinção entre `403/forbidden` e `403/tenant-mismatch` exige que o `AppError` preserve o `type` do BE ProblemDetail. Verificar o mapeamento em INT-FE-EVNT-006.
