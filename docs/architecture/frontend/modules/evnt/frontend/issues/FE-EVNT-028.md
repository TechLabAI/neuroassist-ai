## [FE-EVNT-028] e2e — confirmacao-concorrente.feature (Playwright)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/evnt-e2e/src/e2e/confirmacao-concorrente.spec.ts` valida que `409 /errors/feedback-terminal` (evento já confirmado por outro cuidador) é tratado como sucesso-equivalente: overlay limpo + mensagem "Já confirmado por {nome} às {hora}" exibida.

**Context / Problem statement:**
- SPEC-08: `confirmacao-concorrente.feature` cobre SPEC-07 (409).
- Cenário: cuidadora tenta confirmar → `POST /confirmation` retorna 409 com payload `{ confirmadoPor, confirmadoAt }` → overlay fechado + mensagem inline (sucesso-equivalente).

**Approach (execution path):**
1) Criar `apps/evnt-e2e/src/e2e/confirmacao-concorrente.spec.ts`.
2) Mock `POST /confirmation` → 409 com body `{ type: '/errors/feedback-terminal', confirmadoPor: 'Ana', confirmadoAt: '2026-07-28T14:32:00Z' }`.
3) Verificar que o overlay é desmontado (meta de segurança alcançada: alguém confirmou).
4) Verificar que a mensagem "Já confirmado por Ana às 14:32" aparece (toast ou inline de sucesso).

**Non-goals:**
- Outros cenários de erro (FE-EVNT-027).

**Layer(s):**
frontend / apps/evnt-e2e

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-08-e2e-scenarios.md` (confirmacao-concorrente.feature)
- `docs/architecture/frontend/modules/evnt/frontend/e2e/confirmacao-concorrente.feature`

**Architecture alignment:**
- Playwright spec
- Tag Nx: `scope:evnt`, `type:e2e`

**Implementation checklist (by file):**

**App evnt-e2e**
- [ ] `apps/evnt-e2e/src/e2e/confirmacao-concorrente.spec.ts`
- [ ] Mock `POST /confirmation` → 409 com `feedback-terminal` payload
- [ ] Verificar overlay desmontado após 409
- [ ] Verificar mensagem "Já confirmado por {nome} às {hora}" visível

**Tests**
- [ ] `nx e2e evnt-e2e` PASS
- [ ] `nx lint evnt-e2e` PASS

**Milestone:**
Wave P9 / e2e + Hardening

**Labels:**
type:e2e
layer:frontend
scope:evnt
priority:P9

**Acceptance criteria:**
- `npx nx e2e evnt-e2e --testFile=confirmacao-concorrente.spec.ts` PASS
- Overlay desmontado após 409
- Mensagem de "já confirmado" visível (toast ou banner transitório)

**Dependencies:**
- FE-EVNT-008 (EventDeliveryFacade — 409 como sucesso)
- FE-EVNT-012 (fluxo inline de confirmação)

**Data impact:**
- Nenhum (mock)

**API / Contract impact:**
- Mock 409 com body RFC 7807

**Testing / Evidence required:**
- Screenshot: overlay desmontado + mensagem "Já confirmado" visível

**Risks / Notes:**
- O 409 é tratado no Facade como sucesso (overlay limpo) — a mensagem pode ser um toast curto (diferente dos erros de segurança que são sempre inline).
