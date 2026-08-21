## [FE-EVNT-030] e2e — entrega-degradada.feature + BRIDGE-01 backtick slugs (Playwright)

**Status:** DONE
**GitHub:** [#TBD]

**Outcome:**
`apps/evnt-e2e/src/e2e/entrega-degradada.spec.ts` valida que queda do canal live ativa modo degradado sem perder alarme ativo. Adicionalmente, BRIDGE-01 é resolvido: backtick slugs adicionados nos cenários `.feature` e clausulas EARS para habilitar o matcher automático de cobertura.

**Context / Problem statement:**
- SPEC-08: `entrega-degradada.feature` cobre SPEC-04, SPEC-06 e SPEC-07 — resiliência da entrega.
- Cenário: stream SSE cai → `deliveryMode='degraded'` → banner visível → alarme ativo não perdido → poll continua → stream reconecta → banner desaparece.
- BRIDGE-01 gap (M3.5): títulos de cenário em PT vs clausulas EARS em EN causam 0/41 no gate automático. Solução: adicionar backtick slugs nos títulos de cenário (`\`alarme-emergencia\``) e verificar que o matcher `fe-ears-coverage.py` resolve corretamente.

**Approach (execution path):**
1) Criar `apps/evnt-e2e/src/e2e/entrega-degradada.spec.ts`.
2) Mock SSE → alarme ativo (`activeEvent` não-null).
3) Simular queda do stream (`page.route` → abort SSE).
4) Verificar `deliveryMode='degraded'` → banner "Modo degradado" visível.
5) Verificar `activeEvent` ainda não-null (alarme não perdido).
6) Restaurar SSE → banner desaparece.
7) **BRIDGE-01**: editar os 5 `.feature` existentes em `docs/architecture/frontend/modules/evnt/frontend/e2e/` adicionando backtick slugs nos títulos de Cenário (ex: `Cenário: \`alarme-emergencia\` Alarme de emergência preempta a tela ativa`).
8) Rodar `python scripts/fe-ears-coverage.py check --module EVNT` e verificar melhoria no score.

**Non-goals:**
- Confirmação de alarme (FE-EVNT-027).
- Implementação de degradação (FE-EVNT-010).

**Layer(s):**
frontend / apps/evnt-e2e + docs/architecture/frontend/modules/evnt/frontend/e2e/

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-08-e2e-scenarios.md` (entrega-degradada.feature)
- `docs/architecture/frontend/modules/evnt/frontend/e2e/entrega-degradada.feature`

**Architecture alignment:**
- Playwright spec
- BRIDGE-01: padrão de slug nos Gherkin titles para EARS coverage matcher
- Tag Nx: `scope:evnt`, `type:e2e`

**Implementation checklist (by file):**

**App evnt-e2e**
- [ ] `apps/evnt-e2e/src/e2e/entrega-degradada.spec.ts`
- [ ] SSE mock → alarme ativo → abort SSE → banner degradado visível
- [ ] `activeEvent` não-null durante modo degradado
- [ ] Restaurar SSE → banner desaparece + `deliveryMode='live'`

**Feature files (BRIDGE-01)**
- [ ] `docs/architecture/frontend/modules/evnt/frontend/e2e/alarme-emergencia-preempcao.feature` → backtick slugs nos títulos de Cenário
- [ ] `docs/architecture/frontend/modules/evnt/frontend/e2e/confirmar-atendimento.feature` → backtick slugs
- [ ] `docs/architecture/frontend/modules/evnt/frontend/e2e/confirmacao-concorrente.feature` → backtick slugs
- [ ] `docs/architecture/frontend/modules/evnt/frontend/e2e/notificacao-rotina.feature` → backtick slugs
- [ ] `docs/architecture/frontend/modules/evnt/frontend/e2e/entrega-degradada.feature` → backtick slugs
- [ ] Rodar `python scripts/fe-ears-coverage.py check --module EVNT` → score > 0/41

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
- `npx nx e2e evnt-e2e --testFile=entrega-degradada.spec.ts` PASS
- BRIDGE-01: `fe-ears-coverage.py check --module EVNT` retorna cobertura > 0 (não mais 0/41)
- Banner degradado visível; alarme não perdido na queda do stream

**Dependencies:**
- FE-EVNT-010 (polling fallback — deliveryMode signal)
- FE-EVNT-024 (DegradedModeBanner)
- FE-EVNT-026..029 (outros e2e — BRIDGE-01 afeta todos os features)

**Data impact:**
- Nenhum (mock)

**API / Contract impact:**
- Mock de abort do SSE stream

**Testing / Evidence required:**
- Screenshot: banner degradado com `activeEvent` overlay ainda visível
- Output de `fe-ears-coverage.py`: antes/depois do BRIDGE-01

**Risks / Notes:**
- BRIDGE-01 gap (M3.5): o formato de backtick slug deve ser acordado com o `fe-ears-coverage.py` — verificar o padrão esperado antes de aplicar em todos os 5 features.
- Root cause: o matcher buscava keywords EN no título PT — os backtick slugs fornecem o identificador neutro que o matcher usa.
