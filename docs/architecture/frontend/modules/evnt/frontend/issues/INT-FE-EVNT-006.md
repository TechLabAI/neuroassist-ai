## [INT-FE-EVNT-006] Integração com error interceptor — ProblemDetail → AppError (EVNT)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
O interceptor HTTP de `@neuroassist/shared/error-model` mapeia corretamente os `ProblemDetail` (RFC 7807) do `evnt-service` para `AppError` com `kind` e `type` preservados, habilitando o tratamento diferenciado em FE-EVNT-012 e FE-EVNT-023.

**Context / Problem statement:**
- SPEC-07 §1 lista 8 tipos de erro EVNT com `type` discriminante.
- O interceptor shared deve preservar o `type` do ProblemDetail no `AppError` (ex: `/errors/tenant-mismatch` vs `/errors/forbidden`).
- GUARD-04 gap (M3.5): `403/tenant-mismatch` → redirect sem toast (diferente de `403/forbidden` que é inline).
- ERR-02 gap (M3.5): `403/forbidden` → mensagem inline "Você não é responsável por este paciente."
- Sem o `type` preservado no `AppError`, a distinção entre os dois 403 é impossível.

**Approach (execution path):**
1) Verificar `@neuroassist/shared/error-model` — interface de `AppError` inclui `type?: string`?
2) Se não incluir, adicionar `type?: string` ao `AppError` (ou subtipo `ProblemDetailError`).
3) Garantir que o interceptor HTTP copia o `type` do body da response para o `AppError`.
4) Testar mapeamento para cada tipo EVNT relevante: `feedback-terminal`, `event-not-found`, `tenant-mismatch`, `forbidden`.
5) `409 feedback-terminal`: `AppError({ kind: 'Conflict', type: '/errors/feedback-terminal', confirmadoPor, confirmadoAt })`.

**Non-goals:**
- Tratamento de UI dos erros (FE-EVNT-012/023).
- Outros módulos que também usam o interceptor.

**Layer(s):**
frontend / @neuroassist/shared/error-model → libs/evnt/data-access

**Bounded context:**
evnt (consumidor) + shared (provedor)

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-07-error-handling.md` (§1)

**Architecture alignment:**
- `AppError.type` deve ser o `type` URI do ProblemDetail
- Tag Nx: `scope:shared` (mudança no shared) + `scope:evnt` (consumidor)

**Implementation checklist (by file):**

**Shared / error-model**
- [ ] `AppError` inclui `type?: string` (URI do ProblemDetail)
- [ ] Interceptor HTTP copia `response.body.type` para `AppError.type`
- [ ] `409` body → `AppError({ kind: 'Conflict', type: '/errors/feedback-terminal', confirmadoPor, confirmadoAt })`

**Lib data-access (EvntClient)**
- [ ] Confirmar que os erros chegam como `AppError` (não como `HttpErrorResponse` cru) via catchError

**Tests**
- [ ] `nx test evnt-data-access` PASS
- [ ] Unit: mock 403 com body `{ type: '/errors/tenant-mismatch' }` → `AppError.type === '/errors/tenant-mismatch'`
- [ ] Unit: mock 403 com body `{ type: '/errors/forbidden' }` → `AppError.type === '/errors/forbidden'`
- [ ] `nx lint evnt-data-access` PASS

**Milestone:**
Wave P4 / Hub integrations

**Labels:**
type:integration
layer:frontend
scope:evnt
priority:P4

**Acceptance criteria:**
- `npx nx test evnt-data-access` PASS
- `AppError.type` distingue `tenant-mismatch` de `forbidden` (ambos 403)
- `AppError.type` para 409 preserva `confirmadoPor`/`confirmadoAt`

**Dependencies:**
- FE-EVNT-012 (confirmação inline — consome `AppError`)
- FE-EVNT-023 (error handler 404/403 — distingue por type)

**Data impact:**
- Nenhum

**API / Contract impact:**
- ProblemDetail RFC 7807: `{ type, title, status, detail, ... }` do evnt-service

**Testing / Evidence required:**
- Unit: tabela de mapping para todos os 8 tipos EVNT de SPEC-07 §1

**Risks / Notes:**
- Mudança no `@neuroassist/shared/error-model` pode impactar outros módulos — verificar antes de adicionar `type` ao `AppError` se outros consumidores são afetados (breaking change potencial).
