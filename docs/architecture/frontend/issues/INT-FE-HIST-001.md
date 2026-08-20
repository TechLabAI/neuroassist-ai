## [INT-FE-HIST-001] Integração: hist-service GET /history (period→from/to, paginação, scope)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`HistClient.listHistory` integrado ao `hist-service` porta 8089, com mapeamento correto de `period→from/to`, paginação e restrição de scope ACS.

**Context / Problem statement:**
- Backend: `GET /api/v1/hist/history?from={ISO}&to={ISO}&order=desc&page={n}&size={n}` (api-contract §3.1).
- Scope: `hist:read` → resultados restritos ao escopo ACS do caregiver; `hist:read:all` → todos.
- SPEC-04 §2.1: facade mapeia `period` para `from`/`to` em locale do tenant.
- Gap G2 (cross-link §7): confirmar shape de paginação `{ items, page, size, total }` e se `rotulo`/`urgencia`/`confirmadoPor` já vêm resolvidos no read model.

**Approach (execution path):**
1. Confirmar URL base de `hist-service` via REG (porta 8089 local; GWY em produção).
2. Verificar response shape: `{ items: AuditRecordResponse[], page: number, size: number, total: number }`.
3. Confirmar que `AuditRecordResponse.payload.rotulo` e `payload.urgencia` já estão no read model (se não, documentar fallback A5).
4. Implementar mock `page.route('/api/v1/hist/history*', ...)` nos testes e2e.
5. Testar edge: `subjectId` sem `subjectType` → BE retorna `400 /errors/incomplete-subject-filter`.

**Non-goals:**
- Não alterar o backend.

**Layer(s):**
frontend / data-access (integration)

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/contracts/backend-api-cross-link.md` (§1, §4)
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-04-api-integration.md` (§2.1)

**Architecture alignment:**
- Tag Nx: `scope:hist`, `type:data-access`

**Implementation checklist (by file):**
- 🔲 Confirmar response shape vs `HistListResponse` DTO
- 🔲 Atualizar `hist.models.ts` se necessário
- 🔲 Mock e2e: `apps/hist-e2e/src/support/hist-mocks.ts`
- 🔲 `nx test hist-data-access` PASS (HistClient unit tests com mock)

**Milestone:** Wave P4 / Hub Integrations

**Labels:**
type:integration · layer:frontend · scope:hist · priority:P4

**Acceptance criteria:**
- `HistClient.listHistory` com `period='hoje'` envia `from/to` corretos (start-of-day..now no locale do tenant).
- `subjectId` sem `subjectType` → client omite o facet, não envia 400.
- Response com `total=0` → Facade seta `status=empty`.

**Dependencies:**
- FE-HIST-005, FE-HIST-008

**Open items:**
- G2: confirmar se `rotulo`/`urgencia` já resolvidos server-side ou se client-join é necessário.
