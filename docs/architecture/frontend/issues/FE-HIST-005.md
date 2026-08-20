## [FE-HIST-005] hist data-access: HistClient (HTTP para hist-service)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`HistClient` em `libs/hist/data-access` encapsula todos os calls HTTP read-only ao `hist-service` (GET /history, GET /audit-facts/{id}, GET /history/verify, GET /fact-types). RxJS presente apenas aqui.

**Context / Problem statement:**
- SPEC-04 §2: HistClient built on `@neuroassist/shared/access-http-base`; attaches Authorization, X-Empresa-Id, X-Correlation-Id.
- Endpoints: GET /api/v1/hist/history (paged), GET /api/v1/hist/audit-facts/{id}, GET /api/v1/hist/history/verify, GET /api/v1/hist/fact-types.
- FE **nunca** chama POST /audit-facts — imutabilidade (ADR-004, cross-link §2).
- Bloqueia: FE-HIST-008 (HistoryFacade).

**Approach (execution path):**
1. Criar `libs/hist/data-access` com `project.json` Nx (tags `scope:hist, type:data-access`).
2. `HistClient` — 4 métodos retornando `Observable<T>`:
   - `listHistory(params: HistListParams): Observable<PagedResponse<AuditRecordResponse>>`
   - `getRecord(id: string): Observable<AuditRecordResponse>`
   - `verifyChain(subjectType, subjectId): Observable<ChainVerificationResult>`
   - `listFactTypes(): Observable<FactType[]>`
3. Base path `/api/v1/hist` via `@neuroassist/shared/access-http-base`.
4. `HistListParams` inclui `from`, `to`, `order`, `page`, `size`; opcionalmente `subjectType`, `subjectId`, `factType`.
5. Nunca enviar `subjectId` sem `subjectType` (SPEC-04 §2.1 / SPEC-01 §2).
6. `nx test hist-data-access` PASS.

**Non-goals:**
- Não implementar FeedbackClient — FE-HIST-006.
- Não implementar Facade — FE-HIST-008.
- Sem lógica de mapeamento DTO→VM — FE-HIST-007.

**Layer(s):**
frontend / data-access

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-04-api-integration.md` (§1, §2)

**Architecture alignment:**
- Tag Nx: `scope:hist`, `type:data-access`
- RxJS apenas em data-access; componentes nunca subscrevem HTTP diretamente

**Implementation checklist (by file):**
- 🔲 `libs/hist/data-access/src/lib/hist.client.ts`
- 🔲 `libs/hist/data-access/src/lib/hist.models.ts` (AuditRecordResponse, HistListParams, PagedResponse, ChainVerificationResult, FactType)
- 🔲 `libs/hist/data-access/src/index.ts`
- 🔲 `libs/hist/data-access/project.json`
- 🔲 `libs/hist/data-access/src/lib/hist.client.spec.ts`
- 🔲 `nx test hist-data-access` PASS
- 🔲 `nx lint hist-data-access` PASS

**Milestone:** Wave P2 / Data Access

**Labels:**
type:feature · layer:frontend · scope:hist · priority:P2

**Acceptance criteria:**
- `npx nx test hist-data-access` PASS (HttpClientTestingModule mocks)
- `HistClient.listHistory` nunca envia `subjectId` sem `subjectType`.
- `HistClient` nunca chama POST (unit test confirma ausência do método).

**Dependencies:**
- FE-HIST-001, INT-FE-HIST-001

**API / Contract impact:**
- Consome GET /api/v1/hist/history (§3.1), /audit-facts/{id} (§2.2), /history/verify (§3.2), /fact-types (§5).

**Risks / Notes:**
- Verificar que o interceptor de `access-http-base` está registrado no provider do módulo antes dos testes.
