## [FE-EVNT-004] Shell tool-descriptor — registro EVNT + fallback descriptor

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/neuroassist-ai-web/src/app/tool-descriptors.ts` inclui a entrada EVNT (`EVNT_MODULE_DESCRIPTOR` primário + `EVNT_FALLBACK_DESCRIPTOR` estático), tornando o módulo visível ao Shell na navegação e habilitando o lazy load de `@neuroassist/evnt`.

**Context / Problem statement:**
- O Shell descobre módulos via `tool-descriptors.ts` (SPEC-06 §1); sem esta entrada, `/eventos` nunca aparece no menu.
- O `EVNT_FALLBACK_DESCRIPTOR` garante que o feed de rotina fique acessível mesmo quando a descoberta dinâmica falha.
- Bloqueia: nenhuma issue funcional FE, mas bloqueia o teste de integração de shell (INT-FE-EVNT-001).

**Approach (execution path):**
1) Importar `EVNT_MODULE_DESCRIPTOR` de `@neuroassist/evnt` em `tool-descriptors.ts`.
2) Adicionar `EVNT_MODULE_DESCRIPTOR` ao array de descritores do Shell.
3) Adicionar o `EVNT_FALLBACK_DESCRIPTOR` estático conforme SPEC-06 §1.
4) Verificar que o menu "Eventos" (icon `notifications_active`, order 20) aparece no Shell após este registro.

**Non-goals:**
- Implementação do AlarmOutlet (FE-EVNT-021).
- Qualquer lógica de routing além do `loadChildren` do descriptor.

**Layer(s):**
frontend / apps/neuroassist-ai-web (shell integration)

**Bounded context:**
evnt (consumido por scope:web)

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-06-shell-integration.md` (§1)
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-05-tool-contract.md` (§4)

**Architecture alignment:**
- Apenas `scope:web` pode importar `@neuroassist/evnt`
- Tag Nx: `scope:web`, `type:feature`

**Implementation checklist (by file):**

**Shell app**
- [ ] `apps/neuroassist-ai-web/src/app/tool-descriptors.ts` — `EVNT_MODULE_DESCRIPTOR` adicionado ao array
- [ ] `EVNT_FALLBACK_DESCRIPTOR` definido inline no mesmo arquivo
- [ ] Menu "Eventos" com `icon: 'notifications_active'`, `route: '/eventos'`, `order: 20`

**Tests**
- [ ] `nx test neuroassist-ai-web` PASS (existência da entrada EVNT no descriptors array)
- [ ] `nx lint neuroassist-ai-web` PASS

**Milestone:**
Wave P1 / Foundation

**Labels:**
type:feature
layer:frontend
scope:evnt
priority:P1

**Acceptance criteria:**
- `npx nx test neuroassist-ai-web` PASS
- `npx nx lint neuroassist-ai-web` PASS
- `npx nx build neuroassist-ai-web` PASS
- Shell renderiza item "Eventos" no menu lateral após build

**Dependencies:**
- FE-EVNT-001 (contrato)
- FE-EVNT-003 (entry lib)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Screenshot: menu Shell com entrada "Eventos" visível (loading state aceitável)

**Risks / Notes:**
- Verificar se o Shell já tem um mecanismo de descoberta dinâmica que exige um formato diferente do descriptor.
