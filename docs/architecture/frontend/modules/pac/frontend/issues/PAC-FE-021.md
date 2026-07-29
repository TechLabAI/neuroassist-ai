## [PAC-FE-021] Error surface pipeline — 12 tipos ProblemDetail → inline/toast/banner/modal (SPEC-07)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`pac-error.handler.ts` e `pac-problem-detail.mapper.ts` implementam o pipeline completo de mapeamento de 12 tipos de `ProblemDetail` BE para as 4 superfícies de UX (inline/toast/banner/modal), incluindo o tratamento safety-critical do emergency gate (blocking modal fail-closed para `urgencia=emergencia`, advisory banner para `rotina`).

**Context / Problem statement:**
- SPEC-07 §1 define 12 tipos de ProblemDetail com superfície e mensagem pt-BR.
- Safety-critical: `422 emergency-not-detectable` → **modal bloqueante** (não pode ser dismissed sem resolução); `503 eligibility-gate-unavailable` → toast retryable.
- Hierarquia de superfícies (SPEC-07 §3): inline (campo) < toast (transient) < banner (persistente de sessão) < modal (decisão consciente).
- O handler chama os serviços de feedback compartilhados (`@neuroassist/shared/ui-feedback`) para exibir toast/banner/modal; o inline é emitido via AppError para o componente pai.
- O facade (PAC-FE-011) já converte o ProblemDetail em AppError via o mapper (PAC-FE-013); este handler apenas orquestra a exibição.

**Approach (execution path):**
1. Criar `libs/pac/data-access/src/lib/error-surface/pac-error.handler.ts` com método `handle(error: AppError, context: 'catalog' | 'editor' | 'patient-register')`.
2. Switch em `error.code`:
   - `'validation'` → return (inline — componente pai trata via AppError no signal).
   - `'missing-tenant'` | `'tenant-mismatch'` → `FeedbackService.showBanner(message, { persistent: true })`.
   - `'unauthorized'` → `Router.navigate(['/login'])`.
   - `'insufficient-scope'` → `FeedbackService.showToast(message)`.
   - `'not-found'` → `Router.navigate(['/pac'])`.
   - `'conflict-precondition'` | `'idempotency-mismatch'` → `FeedbackService.showModal({ title, message, action: 'reload' })`.
   - `'invalid-lifecycle-transition'` → `FeedbackService.showToast(message)`.
   - `'duplicate-active-mapping'` → return (inline — gesture picker).
   - `'emergency-not-detectable'` → `FeedbackService.showModal({ blocking: true, message })` (FAIL-CLOSED).
   - `'eligibility-gate-unavailable'` → `FeedbackService.showToast(message, { retryable: true })`.
   - Advisory rotina warnings → `FeedbackService.showBanner(message, { advisory: true, dismissible: true })`.
3. Unit test: cada código de erro → assert que o método correto de `FeedbackService` é chamado.

**Non-goals:**
- Sem implementação dos componentes de toast/banner/modal (de `@neuroassist/shared/ui-feedback`).
- Sem retry logic (implementado no facade via botão explicit do usuário).

**Layer(s):**
frontend / libs/pac/data-access

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-07-error-handling.md` (§1/§2/§3/§4)

**Architecture alignment:**
- `@Injectable` puro (sem Angular signals)
- Tag Nx: `scope:pac`, `type:data-access`

**Implementation checklist (by file):**

**Lib: libs/pac/data-access**
- [ ] `libs/pac/data-access/src/lib/error-surface/pac-error.handler.ts`
- [ ] `libs/pac/data-access/src/lib/error-surface/pac-problem-detail.mapper.ts` (já definido em PAC-FE-013; usar/importar)
- [ ] `libs/pac/data-access/src/lib/error-surface/pac-error.handler.spec.ts`

**Tests**
- [ ] `npx nx test pac-data-access` PASS (12 código de erro cobertos)
- [ ] `npx nx lint pac-data-access` PASS

**Milestone:**
Wave 7 / P7 UX Polish

**Labels:**
type:feature
layer:frontend
scope:pac
priority:P7

**Acceptance criteria:**
- `npx nx test pac-data-access` PASS: `emergency-not-detectable` → `FeedbackService.showModal` com `blocking: true`.
- `npx nx test pac-data-access` PASS: `eligibility-gate-unavailable` → `FeedbackService.showToast` com `retryable: true`.
- `npx nx test pac-data-access` PASS: `missing-tenant` → `FeedbackService.showBanner` persistente.
- `npx nx test pac-data-access` PASS: `conflict-precondition` → `FeedbackService.showModal` com reload action.
- `npx nx test pac-data-access` PASS: advisory rotina warnings → banner dismissível (não blocking).
- Todos os 12 tipos de error cobertos no spec (nenhum caso unhandled).

**Dependencies:**
- PAC-FE-013 (AppError interface + mapper)
- `@neuroassist/shared/ui-feedback` (FeedbackService — toast/banner/modal)

**Data impact:**
Nenhum.

**API / Contract impact:**
- Reage a 12 tipos de ProblemDetail do BE api-contract §2.

**Testing / Evidence required:**
- Unit: spy em `FeedbackService`, assert correct method + params para cada error code.
- Screenshot do blocking modal para `emergency-not-detectable` (safety-critical evidence).

**Risks / Notes:**
- O modal `emergency-not-detectable` DEVE ser blocking (não pode ser fechado via ESC ou click fora) — safety-critical. Verificar que `FeedbackService.showModal({ blocking: true })` implementa esse comportamento em `@neuroassist/shared/ui-feedback`.
- EARS orphans mapeados: cenário "Any comando mutation failure preserves the draft and does not mark the comando saved or ativo" é comportamento do facade (PAC-FE-011), não do handler — referenciar aqui apenas como contexto.
