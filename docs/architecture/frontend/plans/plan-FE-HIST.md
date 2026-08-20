# plan-FE-HIST — Plano de Execução Frontend: Histórico e Auditoria

**Projeto:** NeuroAssist AI · **Módulo:** HIST (`@neuroassist/hist`)
**Stack:** Angular 21, Nx, Signals-first (sem NgRx)
**Rota Shell:** `/historico` · **Porta BE:** 8089 (hist-service)
**Gerado em:** 2026-08-16 · **Fase:** M5 (FE Backlog)

---

## 1. Resumo do Projeto

O módulo HIST entrega ao cuidador (e ao coordenador/DPO) uma **timeline de auditoria period-filtrada** dos eventos de assistência resolvidos — uma superfície de revisão, não de entrada de dados. O ledger é imutável por design (ADR-004): o FE **nunca chama POST /audit-facts**; a única escrita que o frontend realiza é o veredicto de feedback, roteado ao `evnt-service` (gap G1 resolvido via flag `feedbackTarget`).

A plataforma é de saúde assistiva — acessibilidade WCAG 2.1 AA não é diferenciador, é requisito de segurança do paciente. Os gaps FE-GAP-13..19 (WCAG AA) identificados na Specs-Validate têm prioridade mais alta do que features de polish.

O módulo é entregue em 9 ondas (P1–P9) sobre um tax-map de 7 libs (`libs/hist/{shell-entry, data-access, feature-timeline, access, integration, navigation, ui}`). A façade de signals é o único ponto de estado; componentes não têm estado local mutável.

---

## 2. Mapa de Ondas P1–P9

### Wave P1 — Foundation (≈ 2 dias)

**Objetivo:** libs e contratos prontos; Shell pode compilar referenciando `@neuroassist/hist`.

| Issue | Título | Dep |
|---|---|---|
| FE-HIST-001 | shell-entry: contract, routes, module | — |
| FE-HIST-002 | shell-entry: permission-map + feature-flags | 001 |
| FE-HIST-003 | integration: descriptor + HistShellIntegrationService | 001 |
| FE-HIST-004 | navigation: breadcrumb state | 003 |

**Definition of Done P1:** `nx lint neuroassist-ai-web` PASS; Shell pode referenciar `@neuroassist/hist` sem erro de boundary.

---

### Wave P2 — Data Access (≈ 3 dias)

**Objetivo:** toda a camada HTTP e de estado pronta; componentes podem ser implementados de forma isolada com mock da Facade.

| Issue | Título | Dep |
|---|---|---|
| FE-HIST-005 | HistClient (GET hist-service) | 001, INT-001 |
| FE-HIST-006 | FeedbackClient (POST evnt-service) | 002, INT-005 |
| FE-HIST-007 | domain types + DTO→VM mapper | 005 |
| FE-HIST-008 | HistoryFacade (signals) | 005, 006, 007 |

**Definition of Done P2:** `nx test hist-data-access` PASS; Facade unit-testada com mock de HistClient e FeedbackClient; garantia non-optimistic coberta por teste.

---

### Wave P3 — Feature Timeline (≈ 4 dias)

**Objetivo:** todos os componentes visuais implementados; timeline renderizável com Facade mockada.

| Issue | Título | Dep |
|---|---|---|
| FE-HIST-009 | HistoryTimelineComponent (smart) | 008 |
| FE-HIST-010 | FilterTabsComponent | 009 |
| FE-HIST-011 | EventListItemComponent | 009, 007 |
| FE-HIST-012 | UrgencyBadgeComponent | 011 |
| FE-HIST-013 | FeedbackActionComponent (4 estados) | 009 |
| FE-HIST-014 | ListSkeleton + EmptyState + LoadMore | 009 |
| FE-HIST-015 | EventDetailComponent (post-MVP) | 001, 008 |

**Definition of Done P3:** `nx test hist-feature-timeline` PASS; `nx test hist-ui` PASS; screenshot dos 4 estados (loading/loaded/empty/error) disponível.

> **Marco P3 = MVP demonstrável em ambiente de dev** (com backend mockado).

---

### Wave P4 — Hub Integrations + Access Guards (≈ 2,5 dias)

**Objetivo:** integração real com backends; guard de rota operacional.

| Issue | Título |
|---|---|
| INT-FE-HIST-001 | GET /history (period→from/to, paginação) |
| INT-FE-HIST-002 | GET /audit-facts/{id} (detalhe, post-MVP) |
| INT-FE-HIST-003 | GET /history/verify (coordinator badge) |
| INT-FE-HIST-004 | GET /fact-types (vocabulário, cache) |
| INT-FE-HIST-005 | POST /events/{id}/feedback (feedbackTarget G1) |
| INT-FE-HIST-006 | access-http-base headers + correlationId |
| FE-HIST-016 | histGuard (ACS, fail-closed) |

**Definition of Done P4:** integração com hist-service e evnt-service testada via mocks de HTTP; guard rejeita acesso sem vínculo; correlationId presente em todas as superfícies de erro.

---

### Wave P5 — Error States (≈ 1,5 dias)

| Issue | Título |
|---|---|
| FE-HIST-017 | Error mapping (11 casos SPEC-06) |
| FE-HIST-018 | Inline error card + retry (load failures) |
| FE-HIST-019 | Per-item mark-error + 409 reconcile |

**Definition of Done P5:** todos os 11 casos de erro mapeados e testados; nenhum blank screen ou spinner infinito possível no fluxo de lista.

---

### Wave P6 — Accessibility (≈ 2 dias) — PRIORIDADE MAIS ALTA

**Objetivo:** WCAG 2.1 AA em todos os componentes interativos. Bloqueia promote para produção.

| Issue | Título | Gaps |
|---|---|---|
| FE-HIST-020 | ARIA semantics (role=list, tablist, aria-live, alert) | FE-GAP-13..17 |
| FE-HIST-021 | Keyboard operation + focus management | FE-GAP-18 |
| FE-HIST-022 | Hit targets ≥ 44px + focus ring + contraste AA | FE-GAP-19 |

**Definition of Done P6:** axe-playwright 0 violations WCAG AA em todos os 4 estados do timeline.

> **Esta wave NÃO pode ser diferida.** Plataforma de saúde: WCAG AA é requisito de conformidade, não polish.

---

### Wave P7 — Observability (≈ 0,5 dia)

| Issue | Título |
|---|---|
| FE-HIST-023 | Telemetria via @hub/observability (5 eventos) |

---

### Wave P8 — Performance (≈ 1 dia)

| Issue | Título |
|---|---|
| FE-HIST-024 | OnPush, trackBy, paginação, virtual scroll flag |
| FE-HIST-025 | Chunk budget ≤ 120KB gzip + skeleton timing < 100ms |

---

### Wave P9 — e2e + Hardening (≈ 1,5 dias)

| Issue | Título |
|---|---|
| FE-HIST-026 | Playwright: history-timeline.feature |
| FE-HIST-027 | Playwright: feedback-verdict.feature + axe gate |

**Definition of Done P9 (= Definition of Done do módulo):**
- `nx test` PASS em todas as libs de `scope:hist`.
- `nx lint` PASS (0 boundary violations).
- `nx build neuroassist-ai-web` PASS dentro do budget.
- `nx e2e hist-e2e` PASS (todos os cenários + axe 0 violations).

---

## 3. Caminho Crítico

```
FE-HIST-001 → FE-HIST-005 → FE-HIST-007 → FE-HIST-008 → FE-HIST-009
                                                              |
                                             FE-HIST-014 → FE-HIST-020 → FE-HIST-026/027
                                             FE-HIST-013 → FE-HIST-021 → FE-HIST-022
```

**Duração do caminho crítico:** 8 issues encadeadas (≈ 9 dias no caminho mais longo).

**Itens que podem ser paralelizados:** FE-HIST-002..004 (P1), FE-HIST-005..007 (P2), FE-HIST-010..015 (P3, após 009), INT-FE-HIST-001..006 (P4).

---

## 4. Riscos Top-3

| # | Risco | Probabilidade | Impacto | Mitigação |
|---|---|---|---|---|
| R1 | **Gap G1 (evnt-service feedback owner) não resolvido antes da P4** — se o endpoint exato do evnt-service não estiver no api-contract EVNT, INT-FE-HIST-005 fica bloqueado. | Média | Alto | Usar `feedbackTarget` flag e mock; confirmar endpoint antes do wiring final. Se G1 resolver para proxy hist-service, apenas a flag muda (DTO idêntico). |
| R2 | **Tokens de urgência sem validação AA de contraste** — se `@neuroassist/shared/ui-tokens` não tiver a paleta de urgência validada, FE-HIST-022 e o axe gate falharão. | Média | Alto (bloqueia promote) | Auditar tokens no início da P6; criar variante validada se necessário antes de avançar. |
| R3 | **Gap G2 (read model — campos resolvidos server-side)** — se `rotulo`/`urgencia`/`confirmadoPor` não vierem pré-resolvidos no `AuditRecordResponse`, joins client-side PAC/ACS são necessários, aumentando a complexidade de P2 e introduzindo N+1 risk. | Baixa | Médio | Confirmar response shape de `/history` com o time BE antes de fechar FE-HIST-005/007; documentar fallback client-join em `hist-item-vm.mapper.ts` se necessário. |

---

## 5. Approval Criteria por Marco

| Marco | Critério | Wave |
|---|---|---|
| **MVP Dev** | Smart component + Facade operacionais com backend mockado; 4 estados renderizáveis. | P3 done |
| **Integração Completa** | Guards, HTTP real, todos os cenários de erro cobertos. | P5 done |
| **A11y Gate** | axe-playwright 0 violations AA; teclado e focus management verificados. | P6 done |
| **Promote Ready** | Todos os testes passam; budget de chunk OK; e2e completo. | P9 done |

---

## 6. Artefatos de Entrada

| Artefato | Localização |
|---|---|
| FE Specs (SPEC-01..08) | `docs/architecture/frontend/modules/hist/frontend-spec/` |
| generator-config.yml | `docs/architecture/frontend/modules/hist/frontend-spec/generator-config.yml` |
| E2e features | `docs/architecture/frontend/modules/hist/e2e/` |
| Backend cross-link | `docs/architecture/frontend/modules/hist/contracts/backend-api-cross-link.md` |
| hist-service api-contract | `docs/architecture/backend/services/hist-service/contracts/api-contract.md` |
| Issues map | `docs/architecture/frontend/issues/issues_map-FE-HIST.md` |

---

## 7. Open Items Carregados para a Execução

| Item | Descrição | Bloqueia |
|---|---|---|
| G1 | Confirmar endpoint e scope JWT do `evnt-service` para POST feedback | INT-FE-HIST-005 |
| G2 | Confirmar se `rotulo`/`urgencia`/`confirmadoPor` vêm pré-resolvidos no read model de `/history` | FE-HIST-007 |
| LGPD erasure | Estratégia de "redacted record" para LGPD ainda aberta (M2 warning) | Não bloqueia MVP FE, mas afeta rendering futuro |
