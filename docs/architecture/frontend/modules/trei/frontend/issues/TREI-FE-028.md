## [TREI-FE-028] e2e Playwright: autoria-e-aprovacao-cuidadora.feature (idempotent re-approval G3)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/trei-e2e/src/autoria-e-aprovacao-cuidadora.spec.ts` cobre o fluxo de aprovação da cuidadora incluindo o cenário de re-aprovação idempotente (G3): a segunda aprovação do mesmo nó/locale não exibe erro de conflito.

**Context / Problem statement:**
- **G3 (gap do specs-validate):** SPEC-04 §4 especifica que "re-issuing the same approval is a server no-op — the FE shall treat idempotent success as final, NOT show conflict" — mas nenhum cenário e2e cobria o caminho de re-aprovação. Esta issue garante o cenário concreto.
- Cenários do feature: materializar proposta → aprovar (cuidadora) → verificar `DualConfirmationIndicator` → re-aprovar (idempotente) → sem erro de conflito.
- Também cobre: descartar rascunho (`DELETE /nos/{id}?locale=`), editar antes de aprovar.

**Approach (execution path):**
1. Criar `apps/trei-e2e/src/autoria-e-aprovacao-cuidadora.spec.ts`.
2. Mock de API:
   - `POST /api/v1/trei/nos` → `{ noId: 'no-1', subEstado: 'rascunho' }`.
   - `POST /api/v1/trei/nos/no-1/aprovacoes/cuidadora` → `{ confirmadoCuidadora: true, confirmadoPaciente: false, subEstado: 'aguardando_aprovacao' }` (primeira e segunda chamada, idempotente).
3. Cenário G3: clicar "Aprovar" duas vezes → segunda chamada retorna 200 → sem toast de erro.
4. Cenário de descarte: `DELETE` mock → card desaparece da lista.

**Non-goals:**
- Não testar o lado do paciente (TREI e2e é sempre do lado da cuidadora — INV-1).

**Layer(s):**
frontend / apps/trei-e2e

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/e2e/autoria-e-aprovacao-cuidadora.feature`
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-04-data-access.md` (§4 — idempotência G3)

**Architecture alignment:**
- Playwright + page.route()
- Tag Nx: `scope:trei`, `type:e2e`

**Implementation checklist (by file)**

**apps/trei-e2e**
- [ ] `apps/trei-e2e/src/autoria-e-aprovacao-cuidadora.spec.ts`
- [ ] Cenário G3: re-aprovação sem erro de conflito
- [ ] Cenário de descarte: card removido da lista

**Tests**
- [ ] `nx e2e trei-e2e --grep "autoria-e-aprovacao-cuidadora"` PASS
- [ ] G3: segundo click em "Aprovar" → nenhum toast de erro → `DualConfirmationIndicator` mostra ✅ cuidadora

**Milestone:**
Wave 9 / e2e + Hardening

**Labels:**
type:e2e
layer:frontend
scope:trei
priority:P9

**Acceptance criteria:**
- `npx nx e2e trei-e2e` PASS
- G3: re-aprovação não exibe `mat-snack-bar` ou similar com mensagem de conflito
- Descarte: card não mais visível após `DELETE` bem-sucedido

**Dependencies:**
- TREI-FE-019 (ProposedNodeCardComponent)
- TREI-FE-011 (ProposalReviewFacade — idempotência G3)

**Data impact:**
- Nenhum (mocks)

**API / Contract impact:**
- Mocks de `POST /nos`, `POST /nos/{id}/aprovacoes/cuidadora`, `DELETE /nos/{id}`

**Testing / Evidence required:**
- Screenshot: card com ✅ cuidadora / ⏳ paciente após primeira aprovação
- Screenshot: estado idêntico após segunda aprovação (G3 — sem erro)

**Risks / Notes:**
- G3: o mock da segunda chamada deve retornar o mesmo 200 (sem 409) — confirmar que o MSW/route handler não registra estado que cause 409 na segunda chamada.
