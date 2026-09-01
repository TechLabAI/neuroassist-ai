# Issues Map -- TREI (Frontend Backlog)

> Módulo: **TREI** — Treinamento Assistido por IA · Shell: caregiver (`neuroassist-ai-web`)
> Gerado: 2026-09-01 · Total: **31 issues funcionais + 6 issues de integração = 37 issues**
> Nota: 5 gaps do fe-architect-specs-validate (G1..G5) cobertos como issues atómicas (ver §4).

---

## §1. Índice por prioridade (ondas P1-P9)

| Issue | Title | Kind | Priority | Status |
|---|---|---|---|---|
| TREI-FE-001 | TREI_TOOL_KEY + TREI_MENU_METADATA (tool.contract.ts) | FE | P1 | ABERTA |
| TREI-FE-002 | TREI_PERMISSION_MAP (tool.permission-map.ts) | FE | P1 | ABERTA |
| TREI-FE-003 | TREI_FEATURE_FLAGS (tool.feature-flags.ts) | FE | P1 | ABERTA |
| TREI-FE-004 | TreiRootComponent + barrel index.ts + TREI_MODULE_DESCRIPTOR | FE | P1 | ABERTA |
| TREI-FE-005 | trei.bootstrap.ts init/destroy lifecycle | FE | P1 | ABERTA |
| TREI-FE-006 | trei.nav.config.ts + libs/trei/navigation breadcrumb state | FE | P1 | ABERTA |
| TREI-INT-001 | Integration com ACS (@neuroassist/shared/access) | INT | P1 | ABERTA |
| TREI-INT-004 | Integration com shell neuroassist-ai-web | INT | P1 | ABERTA |
| TREI-FE-007 | TrainingSessionHttpAdapter | FE | P2 | ABERTA |
| TREI-FE-008 | ProposalHttpAdapter | FE | P2 | ABERTA |
| TREI-FE-009 | View model interfaces + DTO types | FE | P2 | ABERTA |
| TREI-FE-010 | TrainingSessionFacade | FE | P2 | ABERTA |
| TREI-FE-011 | ProposalReviewFacade (idempotent approval G3) | FE | P2 | ABERTA |
| TREI-FE-012 | Error mapping ProblemDetail -> AppError (G5) | FE | P2 | ABERTA |
| TREI-INT-002 | Integration com GWY — X-Correlation-Id (G5) | INT | P2 | ABERTA |
| TREI-INT-003 | Integration com @neuroassist/shared/error-model | INT | P2 | ABERTA |
| TREI-FE-013 | TrainingSessionPageComponent (smart) | FE | P3 | ABERTA |
| TREI-FE-014 | ChatConversationPanelComponent + ChatBubbleComponent + AssistantThinkingIndicatorComponent | FE | P3 | ABERTA |
| TREI-FE-015 | ChatComposerComponent | FE | P3 | ABERTA |
| TREI-FE-016 | ConversationErrorPanelComponent + EmptyStateComponent + EndSessionButtonComponent + SessionEndedNoticeComponent | FE | P3 | ABERTA |
| TREI-FE-017 | LocaleTabsComponent | FE | P3 | ABERTA |
| TREI-FE-018 | ProposalReviewPageComponent (smart, deep-link G2) | FE | P3 | ABERTA |
| TREI-FE-019 | ProposalListComponent + ProposedNodeCardComponent + DualConfirmationIndicatorComponent (INV-1) | FE | P3 | ABERTA |
| TREI-FE-020 | ProposalEditDialogComponent + ApprovalSummaryBarComponent + ProposalEmptyStateComponent | FE | P3 | ABERTA |
| TREI-FE-021 | TreiAuthoringBannerComponent + StatusChipComponent (libs/trei/ui) | FE | P4 | ABERTA |
| TREI-FE-022 | caregiverSessionGuard | FE | P5 | ABERTA |
| TREI-FE-023 | treiGuard + treiProporGuard (G1) | FE | P5 | ABERTA |
| TREI-FE-024 | Per-card pending spinner + optimistic approval + rollback | FE | P7 | ABERTA |
| TREI-FE-025 | patient-not-found start-surface banner (G4) + health-degraded affordance | FE | P7 | ABERTA |
| TREI-INT-005 | Integration com CONV — G5 invariant: embedModelId readiness display | INT | P7 | ABERTA |
| TREI-FE-026 | Telemetry events via @neuroassist/shared/observability | FE | P8 | ABERTA |
| TREI-INT-006 | Integration com @neuroassist/shared/observability + @neuroassist/shared/ui-* | INT | P8 | ABERTA |
| TREI-FE-027 | e2e: ciclo-de-sessao.feature + geracao-server-mediated.feature | FE | P9 | ABERTA |
| TREI-FE-028 | e2e: autoria-e-aprovacao-cuidadora.feature (idempotent G3) | FE | P9 | ABERTA |
| TREI-FE-029 | e2e: apenas-lado-cuidadora.feature (INV-1) + guard G1 + deep-link G2 | FE | P9 | ABERTA |
| TREI-FE-030 | e2e: erros-e-degradacao.feature (G4 + G5) | FE | P9 | ABERTA |
| TREI-FE-031 | e2e: independencia-de-locale.feature | FE | P9 | ABERTA |

---

## §2. Índice por SPEC de origem

| SPEC | Issues que alimenta |
|---|---|
| SPEC-01 (Route Map) | TREI-FE-001, TREI-FE-004, TREI-FE-018, TREI-FE-022, TREI-FE-023 (G1), TREI-FE-029 (G2) |
| SPEC-02 (Component Hierarchy) | TREI-FE-013..020, TREI-FE-021 |
| TREI-FE-003 (View Models) | TREI-FE-009, TREI-FE-010, TREI-FE-011 |
| SPEC-04 (Data Access) | TREI-FE-007, TREI-FE-008, TREI-FE-011 (G3), TREI-FE-024 |
| SPEC-05 (Tool Contract) | TREI-FE-001, TREI-FE-002, TREI-FE-003, TREI-FE-004, INT-004 |
| SPEC-06 (Shell Integration) | TREI-FE-005, TREI-FE-006, INT-004 |
| SPEC-07 (Error Handling) | TREI-FE-012 (G5), TREI-FE-025 (G4), TREI-FE-016, INT-002, INT-003 |
| SPEC-08 (e2e Scenarios) | TREI-FE-027..031 |

---

## §3. Índice por camada

| Camada | Issues |
|---|---|
| `libs/trei/shell-entry` | TREI-FE-001, 002, 003, 004, 005, 006 |
| `libs/trei/access` | TREI-FE-022, 023 · INT-001 |
| `libs/trei/data-access` | TREI-FE-007, 008, 009, 010, 011, 012, 024 (parcial), 025 (parcial), 026 · INT-002, 003, 005 |
| `libs/trei/feature-training-session` | TREI-FE-013, 014, 015, 016, 017, 025 (parcial) |
| `libs/trei/feature-proposal-review` | TREI-FE-018, 019, 020, 024 (parcial) |
| `libs/trei/integration` | TREI-FE-006 (TreiShellIntegrationService) · INT-004 |
| `libs/trei/navigation` | TREI-FE-006 |
| `libs/trei/ui` | TREI-FE-021 · INT-006 |
| `apps/neuroassist-ai-web` | INT-004 |
| `apps/trei-e2e` | TREI-FE-027, 028, 029, 030, 031 |

---

## §4. Gaps M3.5 → issues atómicas (cobertura obrigatória)

| Gap | Issue que o cobre |
|---|---|
| G1: treiProporGuard mantém review surface acessível sem scope propor | TREI-FE-023 (guard) + TREI-FE-029 (e2e) |
| G2: deep-link /treinamento/sessao/propostas sem turn activo | TREI-FE-018 (page component) + TREI-FE-029 (e2e) |
| G3: re-aprovação idempotente sem exibir conflito | TREI-FE-011 (facade) + TREI-FE-028 (e2e) |
| G4: patient-not-found 404 ao iniciar sessão | TREI-FE-025 (banner) + TREI-FE-030 (e2e) |
| G5: correlationId em metadata, nunca exibido como id bruto | TREI-FE-012 (mapper) + INT-002 (GWY) + TREI-FE-030 (e2e) |

---

## §5. Dependências críticas (caminho mais longo)

```
TREI-FE-009 (tipos)
  └─ TREI-FE-007 (adapter session)
  └─ TREI-FE-008 (adapter proposal)
       └─ TREI-FE-012 (error mapper)
            └─ TREI-FE-010 (TrainingSessionFacade)
            └─ TREI-FE-011 (ProposalReviewFacade)  ← G3 idempotência
                 └─ TREI-FE-013 (TrainingSessionPageComponent)
                 │    └─ TREI-FE-014/015/016/017 (apresentacionais)
                 └─ TREI-FE-018 (ProposalReviewPageComponent) ← G2 deep-link
                      └─ TREI-FE-019/020 (apresentacionais)

TREI-FE-001/002/003 (contratos)
  └─ TREI-FE-004 (barrel + root component)
       └─ TREI-FE-005 (bootstrap)
       └─ TREI-INT-004 (shell registration) ← CAMINHO CRÍTICO P1

TREI-FE-022/023 (guards) ← G1 treiProporGuard
  └─ TREI-FE-004 (rotas usam guards)
```

---

## §6. Estimativas agregadas por onda

| Onda | Issues | Estimativa |
|---|---|---|
| P1 — Foundation | 001-006 + INT-001 + INT-004 | 8 issues · ~3 dias |
| P2 — Data Access | 007-012 + INT-002 + INT-003 | 8 issues · ~3 dias |
| P3 — Feature Libs | 013-020 | 8 issues · ~4 dias |
| P4 — UI Primitives | 021 | 1 issue · ~0.5 dias |
| P5 — Guards | 022-023 | 2 issues · ~1 dia |
| P7 — UX Polish | 024-025 + INT-005 | 3 issues · ~1.5 dias |
| P8 — Observability | 026 + INT-006 | 2 issues · ~1 dia |
| P9 — e2e | 027-031 | 5 issues · ~3 dias |
| **Total** | **37 issues** | **~17 dias úteis** |
