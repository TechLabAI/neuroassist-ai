## [INT-FE-HIST-002] Integração: hist-service GET /audit-facts/{id} (detalhe de registro, post-MVP)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`HistClient.getRecord` integrado ao `hist-service` endpoint de detalhe; 404 mapeado corretamente para UI de "Registro não encontrado".

**Context / Problem statement:**
- Backend: `GET /api/v1/hist/audit-facts/{id}` → 200 `AuditRecordResponse` | 404 (api-contract §2.2).
- Feature gated por `detailRoute` flag (default off no MVP).
- SPEC-06 §1: 404 → inline (detail): "Registro não encontrado." + back.

**Approach (execution path):**
1. Confirmar que `AuditRecordResponse` do endpoint de detalhe tem mesmos campos do list (ou superset).
2. `HistClient.getRecord` usa `HttpClient.get<AuditRecordResponse>`.
3. `EventDetailComponent` trata 404 → inline error + botão voltar.
4. Unit test com `HttpClientTestingModule`: 200 e 404.

**Non-goals:**
- Não ativar `detailRoute` flag no MVP.

**Layer(s):**
frontend / data-access (integration)

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/contracts/backend-api-cross-link.md` (§1)
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-04-api-integration.md` (§2.2)

**Implementation checklist (by file):**
- 🔲 Confirmar schema de `AuditRecordResponse` (detail vs list)
- 🔲 Unit test: GET /audit-facts/{id} → 200, 404
- 🔲 `nx test hist-data-access` PASS

**Milestone:** Wave P4 / Hub Integrations

**Labels:**
type:integration · layer:frontend · scope:hist · priority:P4

**Acceptance criteria:**
- 200 → `EventDetailComponent` renderiza campos.
- 404 → "Registro não encontrado." inline + back.

**Dependencies:**
- FE-HIST-005, FE-HIST-015
