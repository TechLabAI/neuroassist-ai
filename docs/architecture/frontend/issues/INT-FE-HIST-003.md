## [INT-FE-HIST-003] Integração: hist-service GET /history/verify (badge de verificação de cadeia)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`HistClient.verifyChain` integrado ao `hist-service`; badge de verificação exibido apenas para scope `hist:read:all`; estados `intact`/`broken`/`empty` renderizados com texto+ícone.

**Context / Problem statement:**
- Backend: `GET /api/v1/hist/history/verify?subjectType={t}&subjectId={id}` → `{ status: 'intact'|'broken'|'empty', brokenAtRecordId? }` (api-contract §3.2).
- Scope `hist:read:all` (coordinator/DPO) → badge visível; `hist:read` → badge oculto.
- SPEC-05 §6: `intact` ("Cadeia íntegra"), `broken` ("Quebrada no registro N"), `empty` ("Sem registros").
- SPEC-07 §2: estados com texto+ícone (não cor só).

**Approach (execution path):**
1. `HistClient.verifyChain(subjectType, subjectId)` → `Observable<ChainVerificationResult>`.
2. `HistoryFacade.verify()` — chamado condicionalmente quando `permission=hist:read:all`.
3. Chain badge component (inline em `HistoryTimelineComponent`) exibe o resultado.
4. Unit test: 3 estados de response.

**Non-goals:**
- Não implementar escalação de auditoria (responsabilidade do coordinator workflow).

**Layer(s):**
frontend / data-access (integration)

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/contracts/backend-api-cross-link.md` (§1)
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-05-ui-ux-spec.md` (§6)

**Implementation checklist (by file):**
- 🔲 `HistClient.verifyChain` implementado
- 🔲 Facade `verify()` method com permission check
- 🔲 Badge inline em `history-timeline.component.html`
- 🔲 Unit tests para 3 estados
- 🔲 `nx test hist-data-access` PASS

**Milestone:** Wave P4 / Hub Integrations

**Labels:**
type:integration · layer:frontend · scope:hist · priority:P4

**Acceptance criteria:**
- `hist:read:all` → badge visível com estado correto (text+icon).
- `hist:read` → badge oculto.
- `broken` → "Quebrada no registro N" exibido.

**Dependencies:**
- FE-HIST-005, FE-HIST-009
