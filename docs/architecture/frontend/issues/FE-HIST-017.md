## [FE-HIST-017] hist data-access: error mapping (ProblemDetail→AppError, todos os cenários SPEC-06)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Função `mapHistError(err: HttpErrorResponse): AppError` em `libs/hist/data-access` cobrindo todos os cenários de SPEC-06: 400, 401, 403, 404, 409, 503, 5xx, network. Toda superfície de erro ecoa o `correlationId` da resposta.

**Context / Problem statement:**
- SPEC-06 §1: tabela completa de mapeamento `BE type` → surface → copy pt-BR.
- FE-GAP-09..12 (Specs-Validate): cenários 403/401/503/correlationId eram ausentes nas specs — esta issue fecha esses gaps na camada de dados.
- `correlationId` presente em TODA resposta de erro (SPEC-04 §4, cross-link §6).
- RFC 7807 `application/problem+json` → `AppError` via `@neuroassist/shared/error-model`.

**Approach (execution path):**
1. `hist-error.mapper.ts` — `mapHistError(err: HttpErrorResponse): AppError`.
2. Extrair `type`, `correlationId`, `detail` do body `application/problem+json`.
3. Tabela de casos:
   - `400 /errors/validation` → retry
   - `400 /errors/incomplete-subject-filter` → auto-retry (prevented client-side)
   - `400 /errors/invalid-time-window` → reset period
   - `401 /errors/unauthorized` → redirect `/login`
   - `403 /errors/forbidden` → access message
   - `403 /errors/tenant-mismatch` → re-auth
   - `404` → inline (detail)
   - `503 /errors/scope-unavailable` → fail-closed + retry
   - `409` → reconcile (feedback-specific)
   - network/timeout → retry
   - `5xx` → retry (backoff)
4. Usar no `HistoryFacade` (FE-HIST-008) e `FeedbackClient` (FE-HIST-006).
5. Unit tests: um caso por linha da tabela.

**Non-goals:**
- Não implementar UI de error — FE-HIST-018/019.

**Layer(s):**
frontend / data-access

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-06-error-states.md` (§1, §2)
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-04-api-integration.md` (§4)

**Architecture alignment:**
- Tag Nx: `scope:hist`, `type:data-access`

**Implementation checklist (by file):**
- 🔲 `libs/hist/data-access/src/lib/hist-error.mapper.ts`
- 🔲 `libs/hist/data-access/src/lib/hist-error.mapper.spec.ts` (11+ casos)
- 🔲 Atualizar `src/index.ts`
- 🔲 `nx test hist-data-access` PASS
- 🔲 `nx lint hist-data-access` PASS

**Milestone:** Wave P5 / Error States

**Labels:**
type:feature · layer:frontend · scope:hist · priority:P5

**Acceptance criteria:**
- 11 casos de erro mapeados corretamente (unit tests individuais).
- `correlationId` presente em todo `AppError` gerado.
- `401` retorna ação `redirectLogin`; `403` retorna ação `showAccessMessage`.

**Dependencies:**
- FE-HIST-008

**Risks / Notes:**
- FE-GAP-11: `503 scope-unavailable` fail-closed — garantir que o mapper sinaliza `action: 'failClosed'` e não `action: 'ignore'`.
