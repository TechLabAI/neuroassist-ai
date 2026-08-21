# Issues Map — FE-HIST (Histórico e Auditoria)

**Módulo:** `@neuroassist/hist` · **Prefixo:** HIST · **Stack:** Angular 21, Nx, signals-first
**Total:** 33 issues (27 funcionais + 6 integração)
**Gerado em:** 2026-08-16

---

## Sumário por Onda

| Onda | Foco | Issues | Estimativa |
|---|---|---|---|
| P1 — Foundation | Shell-entry, integration, navigation | FE-HIST-001..004 | 4 issues · ~2 dias |
| P2 — Data Access | HTTP clients, mapper, Facade | FE-HIST-005..008 | 4 issues · ~3 dias |
| P3 — Feature Timeline | Smart component + 7 presentacionais | FE-HIST-009..015 | 7 issues · ~4 dias |
| P4 — Hub Integrations | INT-FE-HIST-001..006 | INT-FE-HIST-001..006 | 6 issues · ~2 dias |
| P4 — Access Guards | histGuard (ACS, fail-closed) | FE-HIST-016 | 1 issue · ~0,5 dia |
| P5 — Error States | Error mapping, UI, 409 reconcile | FE-HIST-017..019 | 3 issues · ~1,5 dias |
| P6 — Accessibility | ARIA, teclado, contraste (HIGHEST) | FE-HIST-020..022 | 3 issues · ~2 dias |
| P7 — Observability | Telemetry via @hub/observability | FE-HIST-023 | 1 issue · ~0,5 dia |
| P8 — Performance | OnPush, budget, virtual scroll | FE-HIST-024..025 | 2 issues · ~1 dia |
| P9 — e2e + Hardening | Playwright, axe a11y gate | FE-HIST-026..027 | 2 issues · ~1,5 dias |
| **Total** | | **33 issues** | **~18 dias-homem** |

---

## Índice Completo

### Funcionais (FE-HIST-001..027)

| # | Título | Onda | Tamanho | Depende de |
|---|---|---|---|---|
| FE-HIST-001 | hist shell-entry: contract, routes e module | P1 | M | — |
| FE-HIST-002 | hist shell-entry: permission-map e feature-flags | P1 | S | 001 |
| FE-HIST-003 | hist integration: descriptor e HistShellIntegrationService | P1 | S | 001 |
| FE-HIST-004 | hist navigation: breadcrumb state | P1 | S | 003 |
| FE-HIST-005 | hist data-access: HistClient (HTTP hist-service) | P2 | M | 001, INT-001 |
| FE-HIST-006 | hist data-access: FeedbackClient (POST evnt-service) | P2 | S | 002, INT-005 |
| FE-HIST-007 | hist data-access: domain types e DTO→VM mapper | P2 | M | 005 |
| FE-HIST-008 | hist data-access: HistoryFacade (signals-first) | P2 | L | 005, 006, 007 |
| FE-HIST-009 | hist feature-timeline: HistoryTimelineComponent (smart) | P3 | M | 008 |
| FE-HIST-010 | hist ui: FilterTabsComponent | P3 | S | 009, 020 |
| FE-HIST-011 | hist ui: EventListItemComponent | P3 | M | 009, 007 |
| FE-HIST-012 | hist ui: UrgencyBadgeComponent | P3 | S | 011, 020 |
| FE-HIST-013 | hist ui: FeedbackActionComponent (4 estados) | P3 | M | 009, 020, 021 |
| FE-HIST-014 | hist ui: ListSkeleton, EmptyState, LoadMore | P3 | M | 009 |
| FE-HIST-015 | hist feature-timeline: EventDetailComponent (post-MVP) | P3 | S | 001, 008, INT-002 |
| FE-HIST-016 | hist access: histGuard (ACS, fail-closed) | P4 | S | 001, 002 |
| FE-HIST-017 | hist data-access: error mapping (ProblemDetail→AppError) | P5 | M | 008 |
| FE-HIST-018 | hist feature-timeline: inline error card + retry | P5 | S | 009, 017 |
| FE-HIST-019 | hist ui: per-item mark-error + 409 reconcile | P5 | M | 013, 017 |
| FE-HIST-020 | hist a11y: ARIA semantics (FE-GAP-13..17) | P6 | L | 009, 010, 011, 014 |
| FE-HIST-021 | hist a11y: keyboard operation + focus management (FE-GAP-18) | P6 | M | 013, 010, 014 |
| FE-HIST-022 | hist a11y: hit targets + focus ring + contraste AA (FE-GAP-19) | P6 | M | 013, 010, 012 |
| FE-HIST-023 | hist observability: telemetry @hub/observability | P7 | S | 008 |
| FE-HIST-024 | hist performance: OnPush, trackBy, paginação, virtual scroll | P8 | M | 008, 009 |
| FE-HIST-025 | hist performance: chunk budget ≤ 120KB + skeleton timing | P8 | S | 024, 026 |
| FE-HIST-026 | hist e2e: Playwright history-timeline.feature | P9 | M | 009, 018, 020 |
| FE-HIST-027 | hist e2e: Playwright feedback-verdict.feature + axe gate | P9 | M | 013, 019, 020..022, 026 |

### Integração (INT-FE-HIST-001..006)

| # | Título | Onda | Serviço |
|---|---|---|---|
| INT-FE-HIST-001 | hist-service GET /history (period→from/to, paginação, scope) | P4 | hist-service :8089 |
| INT-FE-HIST-002 | hist-service GET /audit-facts/{id} (detalhe, post-MVP) | P4 | hist-service :8089 |
| INT-FE-HIST-003 | hist-service GET /history/verify (coordinator badge) | P4 | hist-service :8089 |
| INT-FE-HIST-004 | hist-service GET /fact-types (vocabulário, cache) | P4 | hist-service :8089 |
| INT-FE-HIST-005 | evnt-service POST /events/{id}/feedback (feedbackTarget G1) | P4 | evnt-service |
| INT-FE-HIST-006 | access-http-base headers + correlationId em toda superfície | P4 | shared interceptor |

---

## Mapeamento SPEC → Issues

| SPEC | Título | Issues Derivadas |
|---|---|---|
| SPEC-01 | Route Map | FE-HIST-001, 002, 003, 004, 016 |
| SPEC-02 | Component Tree | FE-HIST-009..015 |
| SPEC-03 | State Contracts | FE-HIST-007, 008 |
| SPEC-04 | API Integration | FE-HIST-005, 006, INT-FE-HIST-001..006 |
| SPEC-05 | UI/UX Spec | FE-HIST-009..015, 017, 018 |
| SPEC-06 | Error States | FE-HIST-017, 018, 019 |
| SPEC-07 | Accessibility | FE-HIST-020, 021, 022, 027 |
| SPEC-08 | Performance | FE-HIST-024, 025, 026 |

---

## Gaps da Specs-Validate → Issues de Cobertura

| Gap | Descrição | Issue(s) |
|---|---|---|
| FE-GAP-09 | 403 forbidden (lista + feedback) | FE-HIST-017, 019 |
| FE-GAP-10 | 401 unauthorized redirect | FE-HIST-017, 018 |
| FE-GAP-11 | 503 scope-unavailable fail-closed | FE-HIST-017, 018 |
| FE-GAP-12 | correlationId em toda superfície de erro | FE-HIST-017, 018, INT-FE-HIST-006 |
| FE-GAP-13 | Accessible name por item de lista | FE-HIST-020, 011 |
| FE-GAP-14 | ARIA tablist/tab/aria-selected | FE-HIST-020, 010 |
| FE-GAP-15 | aria-live para transições de status | FE-HIST-020 |
| FE-GAP-16 | role=alert para erros de carregamento | FE-HIST-020, 018 |
| FE-GAP-17 | Skeleton aria-hidden + aria-busy | FE-HIST-020, 014 |
| FE-GAP-18 | Keyboard operation + focus management | FE-HIST-021 |
| FE-GAP-19 | Hit targets ≥ 44px + focus ring + contraste AA | FE-HIST-022 |

---

## Caminho Crítico (caminho mais longo)

```
FE-HIST-001 (shell-entry)
  └── FE-HIST-005 (HistClient)
        └── FE-HIST-007 (mapper)
              └── FE-HIST-008 (Facade)
                    └── FE-HIST-009 (smart component)
                          ├── FE-HIST-014 (skeleton/empty/loadmore)
                          │     └── FE-HIST-020 (ARIA semantics)
                          │           └── FE-HIST-026 (e2e timeline)
                          │                 └── FE-HIST-027 (e2e feedback + axe)
                          └── FE-HIST-013 (FeedbackAction)
                                ├── FE-HIST-019 (mark-error/409)
                                └── FE-HIST-021 (teclado)
                                      └── FE-HIST-022 (contraste/targets)
```

Comprimento do caminho crítico: **8 issues** (FE-HIST-001 → 005 → 007 → 008 → 009 → 014 → 020 → 026/027).

---

## Estimativas por Tamanho

| Tamanho | Issues | Contagem |
|---|---|---|
| S (≤ 0,5 dia) | 002, 003, 004, 006, 015, 016, 023, 025, INT-002, INT-003, INT-004 | 11 |
| M (≈ 1 dia) | 001, 005, 007, 009, 010, 011, 013, 014, 017, 018, 021, 022, 024, 026, 027, INT-001, INT-005, INT-006 | 18 |
| L (1,5-2 dias) | 008, 019, 020 | 4 |

**Total estimado:** ~18 dias-homem (assumindo 1 dev FE).
