## [FE-EVNT-027] e2e — confirmar-atendimento.feature (Playwright)

**Status:** DONE
**GitHub:** [#TBD]

**Outcome:**
`apps/evnt-e2e/src/e2e/confirmar-atendimento.spec.ts` valida os dois cenários: confirmação bem-sucedida limpa o alarme após 200 + falha de rede mantém o alarme ativo com retry inline.

**Context / Problem statement:**
- SPEC-08: `confirmar-atendimento.feature` cobre SPEC-04 e SPEC-07 — o caminho de safety mais crítico.
- Cenário 1: confirmar → `POST /confirmation` retorna 200 → overlay desmontado, cuidadora retorna à tela anterior.
- Cenário 2: confirmar → erro de rede → overlay permanece ativo + mensagem inline "Não foi possível registrar agora. Tentar de novo." visível.

**Approach (execution path):**
1) Criar `apps/evnt-e2e/src/e2e/confirmar-atendimento.spec.ts`.
2) Cenário 1: mock SSE → alarme ativo → clicar "Confirmar" → mock `POST /confirmation` 200 → verificar overlay desmontado.
3) Cenário 2: mock `POST /confirmation` → timeout/erro de rede → verificar overlay ainda montado + texto de retry inline visível.
4) Verificar que o botão fica desabilitado durante o POST em voo (CONCUR-01).

**Non-goals:**
- Cenário 409 (FE-EVNT-028).
- Rotinas (FE-EVNT-029).

**Layer(s):**
frontend / apps/evnt-e2e

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-08-e2e-scenarios.md` (confirmar-atendimento.feature)
- `docs/architecture/frontend/modules/evnt/frontend/e2e/confirmar-atendimento.feature`

**Architecture alignment:**
- Playwright spec
- Tag Nx: `scope:evnt`, `type:e2e`

**Implementation checklist (by file):**

**App evnt-e2e**
- [ ] `apps/evnt-e2e/src/e2e/confirmar-atendimento.spec.ts`
- [ ] Cenário 1: confirm 200 → overlay ausente no DOM
- [ ] Cenário 2: confirm network error → overlay presente + retry inline visível
- [ ] Verificação CONCUR-01: botão `[disabled]` durante POST em voo

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
- `npx nx e2e evnt-e2e --testFile=confirmar-atendimento.spec.ts` PASS
- Cenário 2: nenhum toast visível, apenas mensagem inline no overlay

**Dependencies:**
- FE-EVNT-011 (AlarmOverlayComponent)
- FE-EVNT-012 (ConfirmAttendanceButton + fluxo inline)
- FE-EVNT-008 (EventDeliveryFacade — non-optimistic confirm)

**Data impact:**
- Nenhum (mock)

**API / Contract impact:**
- Mock de `POST /api/v1/events/{id}/confirmation`

**Testing / Evidence required:**
- Screenshot: overlay com spinner (confirm em voo)
- Screenshot: overlay com retry inline após erro de rede

**Risks / Notes:**
- Simular erro de rede no Playwright: `page.route('/api/v1/events/*/confirmation', route => route.abort('failed'))`.
