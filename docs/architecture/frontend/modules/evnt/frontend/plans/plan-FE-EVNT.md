# plan-FE-EVNT — Plano de Execução Frontend

> Module: **EVNT — Eventos e Alarmes** · Project: NeuroAssist AI
> Layer: Frontend (Angular 21, Nx 22, Signals, Standalone Components)
> Generated: 2026-07-28 · Status: **PLANEJADO**

---

## Resumo do projeto

O módulo EVNT é a superfície de alarme e notificação do NeuroAssist AI: quando Márcio faz um gesto de emergência, toda a tela se torna um alarme full-bleed vermelho ("Márcio precisa de ajuda") que só fecha com confirmação explícita da cuidadora. Comandos de rotina chegam como notificações discretas e dispensáveis.

A arquitetura FE é signals-first (Angular 21, sem NgRx), lazy via `@neuroassist/evnt` (entry-as-library, ADR-006), com o `AlarmOutlet` global montado pelo Shell acima do router-outlet (ADR-005) — não é uma rota. O canal live é SSE primário com fallback automático para polling. A confirmação é **não-otimista** (safety invariant): o alarme não fecha até o POST retornar 200.

Âncora arquitetural: `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/` (SPEC-01..08) + `docs/architecture/backend/services/evnt-service/contracts/api-contract.md` (fonte autoritativa de contratos BE).

---

## Mapa de ondas P1-P9

| Onda | Foco | Issues | Estimativa | MVP gate? |
|---|---|---|---|---|
| **P1** | Foundation | FE-EVNT-001..006 | 3-4 dias | Sim — sem P1 nada funciona |
| **P2** | Data access | FE-EVNT-007..010 | 3-4 dias | Sim — Facade + Cliente |
| **P3** | Feature libs | FE-EVNT-011..018 | 5-6 dias | Sim — UI do alarme e feed |
| **P4** | Hub integrations | INT-FE-EVNT-001..007 | 3-4 dias | Sim — sem integração, sem dados reais |
| **P5** | Access guards | FE-EVNT-019..020 | 1 dia | Sim — segurança mínima |
| **P6** | Shell integration | FE-EVNT-021 | 1 dia | Sim — AlarmOutlet no Shell |
| **P7** | UX polish | FE-EVNT-022..024 | 2-3 dias | Não — mas necessário para aceite |
| **P8** | Observability | FE-EVNT-025 | 1 dia | Não — best-effort |
| **P9** | e2e + Hardening | FE-EVNT-026..030 | 3-4 dias | Não — mas gate de entrega |
| **Total** | | **37 issues** | **~22-27 dias** | |

---

## Caminho crítico

```
P1: FE-EVNT-001 → 002 → 003 → 004 (descriptor)
P1: FE-EVNT-001 → 005 (bootstrap) → 021 (shell integration)
P2: FE-EVNT-009 (DTO) → 007 (client) → 008 (facade) → 010 (degraded)
P3: FE-EVNT-018 (UI) → 011 (alarm overlay) → 012 (confirm button)
P3: FE-EVNT-008 (facade) → 013 (feed page) → 014 (routine notice)
P3: FE-EVNT-008 (facade) → 016 (detail page) → 017 (verdict control)
P4: INT-FE-EVNT-006 (error mapper) → 012 (inline error) → 023 (handler)
P5: FE-EVNT-020 (evntGuard) → routes protegidas
P6: FE-EVNT-021 (shell integration) → e2e P9
P9: FE-EVNT-030 inclui BRIDGE-01 (backtick slugs — todos os 5 features)
```

**Sequência mínima para MVP shippable em dev (P3 done):**
`P1 completo → P2 completo → P3 completo → P4 (002+003+006 no mínimo) → P5 → P6`

---

## Definition of Done por onda

### P1 done (Foundation)
- `evnt.contract.ts` exporta contrato completo
- Rotas `/eventos` e `/eventos/:eventoId` declaradas com guards
- `EvntModule` exporta públicos (contrato, routes, alarm outlet hook)
- Descriptor EVNT registrado no Shell
- Bootstrap `init`/`destroy` implementados e testados
- `EVNT_NAV_CONFIG` com menu e breadcrumbs

### P2 done (Data access)
- `EvntClient` com 6 métodos + SSE reconnect com `Last-Event-ID`
- `EventDeliveryFacade` com todos os signals + CONCUR-01 guard + non-optimistic confirm
- DTO types + mapper puro testado
- Degradation/polling logic com feature-flag liveChannel

### P3 done (Feature libs) = **MVP shippable em dev**
- `AlarmOverlayComponent` full-bleed, `role="alertdialog"`, focus trap
- Confirm flow inline (spinner, erro inline, 409 como sucesso)
- `EventFeedPageComponent` com rotina queue + degraded banner
- `RoutineNoticeComponent` discreta + dismiss optimistic
- `EventListComponent` histórico
- `EventDetailPageComponent` + `FeedbackVerdictControl`
- `UrgencyBadge` + `EventTimestamp` UI

### P4 done (Hub integrations)
- AlarmOutlet registrado no Shell via `ShellOutletRegistry`
- SSE com `Authorization` header funcionando
- ProblemDetail → AppError com `type` preservado
- Permissões e feature flags lendo de serviços reais (ou stubs)

### P5 done (Guards)
- `authGuard` verificado para rotas EVNT
- `evntGuard` bloqueia sem `evnt.view`

### P6 done (Shell)
- AlarmOutlet renderizado acima do router-outlet em runtime

### P7-P9 done (Polish + e2e)
- Loading/empty states + error handlers 404/403
- Banner degradado com pollInterval visível
- 5 specs e2e PASS (ou diferidos com razão documentada)
- BRIDGE-01: `fe-ears-coverage.py` > 0 hits

---

## Riscos top-3

### Risco 1 — SSE com header `Authorization` (INT-FE-EVNT-002)
**Probabilidade:** Alta · **Impacto:** Alto
`EventSource` nativo não suporta headers customizados. A solução (fetch + ReadableStream) é mais complexa e aumenta o risco de bugs de reconnect. Mitigação: decidir a abordagem técnica em P2 antes de FE-EVNT-007; alternativa é autenticar via query param (inseguro — evitar) ou cookie (se viável no stack).

### Risco 2 — ShellOutletRegistry não existe (INT-FE-EVNT-001)
**Probabilidade:** Média · **Impacto:** Alto
Se `@neuroassist/shared` não tem o `ShellOutletRegistry`, o AlarmOutlet fica sem ponto de integração. Mitigação: criar stub minimal como parte de FE-EVNT-021/INT-FE-EVNT-001 e escalar para o time de shared imediatamente (bloqueia P9).

### Risco 3 — e2e com mock SSE (FE-EVNT-026..030)
**Probabilidade:** Média · **Impacto:** Médio
Playwright não suporta SSE nativamente via `page.route` com chunked streaming de forma trivial. Mitigação: usar MSW (Mock Service Worker) ou uma rota de `page.route` com `fulfill({ body: chunkedStream })` — pesquisar antes de P9.

---

## Approval criteria por marco

| Marco | Critério |
|---|---|
| P2 done | `nx test evnt-data-access` PASS + Facade signals funcionando em unit tests |
| P3 done (MVP dev) | `nx build evnt-feature-alarm evnt-feature-routine evnt-ui` PASS + screenshot de alarme funcional |
| P4 done | SSE real (ou mock) entrega evento de emergência → alarme visible em browser local |
| P6 done | AlarmOutlet renderizado acima de qualquer rota no Shell local |
| P9 done (Gate) | `nx e2e evnt-e2e` PASS (ou diferido com documentação) + `fe-ears-coverage.py` > 5/41 hits |

---

## Referências

- Specs: `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-01..08`
- Issues: `docs/architecture/frontend/modules/evnt/frontend/issues/`
- BE contrato autoritativo: `docs/architecture/backend/services/evnt-service/contracts/api-contract.md`
- Gaps M3.5: `tmp/architecture-autopilot/EVNT/state.json` → `handoff.M3_5.gaps_para_backlog`
- ADR-005 (AlarmOutlet global): `docs/architecture/frontend/modules/evnt/frontend/adr/`
- ADR-006 (entry-as-library): `docs/architecture/frontend/adr/ADR-006-module-entry-as-library.md`
