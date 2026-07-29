## [FE-EVNT-029] e2e — notificacao-rotina.feature (Playwright)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/evnt-e2e/src/e2e/notificacao-rotina.spec.ts` valida que uma notificação de rotina aparece de forma discreta (não full-bleed) e pode ser dispensada pelo cuidador (dismiss client-only).

**Context / Problem statement:**
- SPEC-08: `notificacao-rotina.feature` cobre SPEC-03 e SPEC-05.
- Cenário: SSE entrega `evento.created` com `urgencia=rotina` → `RoutineNoticeComponent` renderizado de forma discreta (não full-bleed) + botão de dispensar visível → clicar dismiss → notice removido do DOM.

**Approach (execution path):**
1) Criar `apps/evnt-e2e/src/e2e/notificacao-rotina.spec.ts`.
2) Mock SSE frame `evento.created(urgencia=rotina)`.
3) Verificar que NÃO há `[role="alertdialog"]` (emergência não foi triggada).
4) Verificar que `RoutineNoticeComponent` renderizou (discreta — verificar ausência de full-bleed/cor-alarm).
5) Clicar no botão de dismiss → verificar que o notice é removido do DOM.

**Non-goals:**
- Alarme de emergência (FE-EVNT-026).
- Feature flag routineNotices off (FE-EVNT-024 — UX).

**Layer(s):**
frontend / apps/evnt-e2e

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-08-e2e-scenarios.md` (notificacao-rotina.feature)
- `docs/architecture/frontend/modules/evnt/frontend/e2e/notificacao-rotina.feature`

**Architecture alignment:**
- Playwright spec
- Tag Nx: `scope:evnt`, `type:e2e`

**Implementation checklist (by file):**

**App evnt-e2e**
- [ ] `apps/evnt-e2e/src/e2e/notificacao-rotina.spec.ts`
- [ ] Mock SSE `evento.created(urgencia=rotina)`
- [ ] Verificar ausência de `[role="alertdialog"]` (não é emergência)
- [ ] Verificar `RoutineNoticeComponent` presente + estilo discreto
- [ ] Clicar dismiss → notice removido do DOM

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
- `npx nx e2e evnt-e2e --testFile=notificacao-rotina.spec.ts` PASS
- Sem `[role="alertdialog"]` para evento de rotina
- Dismiss remove o notice sem reload de página

**Dependencies:**
- FE-EVNT-014 (RoutineNoticeComponent)
- FE-EVNT-013 (EventFeedPageComponent)

**Data impact:**
- Nenhum (mock + dismiss client-only)

**API / Contract impact:**
- Nenhum (dismiss não faz server call)

**Testing / Evidence required:**
- Screenshot: notice discreto visível
- Screenshot: notice ausente após dismiss

**Risks / Notes:**
- Verificar que a animação de dismiss não deixa elementos residuais no DOM (`display:none` vs remoção do `@for`).
