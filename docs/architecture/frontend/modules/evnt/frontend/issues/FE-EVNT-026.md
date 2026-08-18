## [FE-EVNT-026] e2e — alarme-emergencia-preempcao.feature (Playwright)

**Status:** DONE
**GitHub:** [#TBD]

**Outcome:**
`apps/evnt-e2e/src/e2e/alarme-emergencia-preempcao.spec.ts` implementa os dois cenários do feature file: alarme preempta a tela ativa + recarregar durante alarme ativo re-exibe o alarme (hidratação).

**Context / Problem statement:**
- SPEC-08: `alarme-emergencia-preempcao.feature` cobre SPEC-01 (AlarmOutlet), SPEC-03 e SPEC-06.
- Cenário 1: SSE entrega `evento.created` emergência → `AlarmOverlayComponent` preempta a tela atual.
- Cenário 2: Recarregar página durante alarme ativo → hidratação via `GET /events/active` → alarme re-exibido antes do primeiro frame SSE.
- Estes e2e validam a safety invariant central da plataforma.

**Approach (execution path):**
1) Criar `apps/evnt-e2e/src/e2e/alarme-emergencia-preempcao.spec.ts`.
2) Usar Mock Service Worker (MSW) ou interceptor Playwright para simular SSE frame.
3) Cenário 1: navegar para `/eventos`, disparar SSE frame mock `evento.created(urgencia=emergencia)`, verificar `role="alertdialog"` no DOM.
4) Cenário 2: setar mock `GET /events/active` retornando evento ativo, recarregar página, verificar overlay antes de conexão SSE.
5) Usar `page.waitForSelector('[role="alertdialog"]')` como assertion principal.

**Non-goals:**
- Confirmação de alarme (FE-EVNT-027).
- Notificação de rotina (FE-EVNT-029).

**Layer(s):**
frontend / apps/evnt-e2e

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-08-e2e-scenarios.md` (alarme-emergencia-preempcao.feature)
- `docs/architecture/frontend/modules/evnt/frontend/e2e/alarme-emergencia-preempcao.feature`

**Architecture alignment:**
- Playwright spec (não Jest)
- Tag Nx: `scope:evnt`, `type:e2e`

**Implementation checklist (by file):**

**App evnt-e2e**
- [ ] `apps/evnt-e2e/src/e2e/alarme-emergencia-preempcao.spec.ts`
- [ ] Cenário 1: SSE mock → `[role="alertdialog"]` visível sobre a rota ativa
- [ ] Cenário 1: URL não muda durante o alarme (sem navigate)
- [ ] Cenário 2: `GET /events/active` mock com evento ativo → reload → overlay visível antes de SSE

**Tests**
- [ ] `nx e2e evnt-e2e` PASS (ou status diferido se infra SSE não disponível)
- [ ] `nx lint evnt-e2e` PASS

**Milestone:**
Wave P9 / e2e + Hardening

**Labels:**
type:e2e
layer:frontend
scope:evnt
priority:P9

**Acceptance criteria:**
- `npx nx e2e evnt-e2e --testFile=alarme-emergencia-preempcao.spec.ts` PASS
- Overlay renderizado acima do router-outlet (z-index verificado via DOM order)

**Dependencies:**
- FE-EVNT-011 (AlarmOverlayComponent)
- FE-EVNT-005 (bootstrap — hidratação)
- FE-EVNT-021 (EvntShellIntegrationService)

**Data impact:**
- Nenhum (mock)

**API / Contract impact:**
- Mock de `GET /api/v1/events/active` e SSE `GET /api/v1/events/stream`

**Testing / Evidence required:**
- Screenshot: overlay full-bleed visível acima da rota `/eventos`
- Screenshot: overlay após reload (hidratação sem SSE)

**Risks / Notes:**
- SSE em Playwright requer interceptor customizado — verificar `page.route()` com chunked response para simular EventSource.
