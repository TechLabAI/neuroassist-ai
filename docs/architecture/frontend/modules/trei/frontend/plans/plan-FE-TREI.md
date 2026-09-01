# Plano de Execução FE — TREI (Treinamento Assistido por IA)

> **Módulo:** TREI · **Shell:** caregiver (`neuroassist-ai-web`) · **Natureza:** novo módulo, retrofit
> **Próxima fase:** `/fe-architect-scaffold neuroassist-ai TREI`
> **Gerado:** 2026-09-01 · **Issues:** 31 FE + 6 INT = 37 total

---

## 1. Resumo do projecto

TREI é o **plano de autoria** da árvore de falas que o módulo CONV serve em runtime. Enquanto o CONV é retrieval-only (lê o que o TREI escreveu), o TREI fornece uma sessão de treino interactiva onde a cuidadora conversa com um LLM (Claude/Anthropic via `trei-service`, nunca directamente) e recebe propostas de novas frases/categorias para a árvore de falas do paciente. Apenas conteúdo aprovado por ambos (cuidadora + paciente de forma assíncrona) é publicado — indexado no Elasticsearch e sintetizado em áudio via ElevenLabs pelo `trei-service`.

O frontend TREI vive **exclusivamente no shell da cuidadora** (`neuroassist-ai-web`) — nunca serve uma superfície ao paciente. A invariante INV-1 (sem aprovação do paciente pelo FE TREI) é estrutural: não existe adapter, método, ou output de componente que escreva `confirmadoPaciente`.

Cinco gaps identificados pelo `fe-architect-specs-validate` (G1..G5) foram convertidos em issues atómicas com cenários e2e dedicados.

---

## 2. Mapa de ondas P1-P9

### Wave 1 — Foundation (P1) · ~3 dias

| Issue | Descrição | Bloqueia |
|---|---|---|
| TREI-FE-001 | TREI_TOOL_KEY + TREI_MENU_METADATA | TREI-FE-004, INT-004 |
| TREI-FE-002 | TREI_PERMISSION_MAP | TREI-FE-022/023 |
| TREI-FE-003 | TREI_FEATURE_FLAGS | TREI-FE-010, INT-004 |
| TREI-FE-004 | TreiRootComponent + barrel + TREI_MODULE_DESCRIPTOR | INT-004 |
| TREI-FE-005 | trei.bootstrap.ts | TREI-FE-006 |
| TREI-FE-006 | nav.config + TreiShellIntegrationService | TREI-FE-005 |
| INT-001 | ACS shared/access integration | TREI-FE-022/023 |
| INT-004 | Shell module-descriptors.ts + kill-switch | —smoke test |

**Definition of Done (P1):** `nx build neuroassist-ai-web` PASS; sidebar item "Treinamento" visível; kill-switch funcional.

---

### Wave 2 — Data Access (P2) · ~3 dias

| Issue | Descrição | Bloqueia |
|---|---|---|
| TREI-FE-009 | View models (tipos Locale, SessionStatus, NodeSubEstado, VMs) | 007, 008, 010, 011 |
| TREI-FE-007 | TrainingSessionHttpAdapter | TREI-FE-010 |
| TREI-FE-008 | ProposalHttpAdapter | TREI-FE-011 |
| TREI-FE-012 | TreiErrorMapper (G5 correlationId) | 010, 011 |
| TREI-FE-010 | TrainingSessionFacade | 013, 025 |
| TREI-FE-011 | ProposalReviewFacade (G3 idempotência) | 018, 024 |
| INT-002 | GWY correlation echoing (G5) | TREI-FE-012 |
| INT-003 | shared/error-model adoption | TREI-FE-012 |

**Definition of Done (P2):** `nx test trei-data-access` PASS; `grep -r 'aprovacoes/paciente' libs/trei/` = vazio (INV-1); re-aprovação não lança erro (G3).

---

### Wave 3 — Feature Libs (P3) · ~4 dias

| Issue | Descrição | Bloqueia |
|---|---|---|
| TREI-FE-013 | TrainingSessionPageComponent (smart) | 014..017 |
| TREI-FE-014 | ChatConversationPanel + ChatBubble + AssistantThinkingIndicator | 013 |
| TREI-FE-015 | ChatComposerComponent | 013 |
| TREI-FE-016 | ConversationErrorPanel + EmptyState + EndSessionBtn + SessionEndedNotice | 013 |
| TREI-FE-017 | LocaleTabsComponent | 013 |
| TREI-FE-018 | ProposalReviewPageComponent (G2 deep-link) | 019, 020 |
| TREI-FE-019 | ProposalList + ProposedNodeCard + DualConfirmation (INV-1) | 018 |
| TREI-FE-020 | ProposalEditDialog + ApprovalSummaryBar + ProposalEmptyState | 018 |

**Definition of Done (P3):** MVP shippable em dev — fluxo completo de sessão + revisão de propostas funcional com mocks de API. `nx test trei-feature-*` PASS.

---

### Wave 4 — UI Primitives (P4) · ~0.5 dias

| Issue | Descrição |
|---|---|
| TREI-FE-021 | TreiAuthoringBannerComponent + StatusChipComponent |

**Definition of Done (P4):** banner visível em todas as rotas TREI (INV-6); zero CSS hardcoded.

---

### Wave 5 — Access Guards (P5) · ~1 dia

| Issue | Descrição |
|---|---|
| TREI-FE-022 | caregiverSessionGuard |
| TREI-FE-023 | treiGuard + treiProporGuard (G1) |

**Definition of Done (P5):** guards integrados nas rotas; `treiProporGuard` redireciona para `/treinamento/sessao` sem bloquear review (G1).

---

### Wave 7 — UX Polish (P7) · ~1.5 dias

| Issue | Descrição |
|---|---|
| TREI-FE-024 | Per-card pending spinner + optimistic approval + rollback |
| TREI-FE-025 | patient-not-found banner (G4) + health-degraded notice |
| INT-005 | CONV G5 invariant: embedModelId display |

**Definition of Done (P7):** `patient-not-found` exibe banner sem criar sessão (G4); `correlationId` nunca visível na UI (G5); rollback de aprovação funcional.

---

### Wave 8 — Observability (P8) · ~1 dia

| Issue | Descrição |
|---|---|
| TREI-FE-026 | Telemetry events (session-started, turn-submitted, node-approved) |
| INT-006 | shared/observability + shared/ui-* tokens |

**Definition of Done (P8):** 3 eventos emitidos; zero PHI nos payloads (SEC-002); lint limpo.

---

### Wave 9 — e2e + Hardening (P9) · ~3 dias

| Issue | Feature coberta | Gaps |
|---|---|---|
| TREI-FE-027 | ciclo-de-sessao + geracao-server-mediated | — |
| TREI-FE-028 | autoria-e-aprovacao-cuidadora | G3 |
| TREI-FE-029 | apenas-lado-cuidadora | G1, G2, INV-1 |
| TREI-FE-030 | erros-e-degradacao | G4, G5 |
| TREI-FE-031 | independencia-de-locale | — |

**Definition of Done (P9):** `nx e2e trei-e2e` PASS; todos os 5 gaps G1..G5 cobertos por cenários e2e.

---

## 3. Caminho crítico

```
[P1] TREI-FE-009 → FE-007 → FE-010 → FE-013 → FE-014..017 [P3]
                 → FE-008 → FE-011 → FE-018 → FE-019..020 [P3]
                 → FE-012 → (error surfaces)

[P1] FE-001/002/003 → FE-004 → INT-004 (smoke P1)
                    → FE-022/023 → (guards protegem rotas P5)

[P7] FE-025 (G4) depends on FE-010 (startSession) + FE-012 (mapper)
[P9] FE-030 (G4+G5 e2e) depends on FE-025 + FE-012
[P9] FE-029 (G1+G2 e2e) depends on FE-023 + FE-018
[P9] FE-028 (G3 e2e) depends on FE-011
```

---

## 4. Top-3 riscos (de docs/architecture/frontend/modules/trei/frontend/06-risks-gaps-assumptions.md)

| # | Risco | Mitigação |
|---|---|---|
| R1 | `@neuroassist/shared/access` pode não ter `hasScope()` com a assinatura exacta | Verificar antes do sprint P1; escalada se ausente |
| R2 | `trei-service` não online em dev → Facades sem dados reais | Usar MockAdapter (feature flag guard `isMock: true`) para P1-P3; unlock em P9 |
| R3 | G5 violado acidentalmente (correlationId renderizado) | Lint rule + `expect(body).not.toContainText(correlationId)` no e2e P9 |

---

## 5. Definition of Done por marco

| Marco | Critério |
|---|---|
| **MVP dev (P3 completo)** | Fluxo de sessão + revisão funcional com mocks; `nx build` + `nx test` PASS; nenhuma invariante violada (INV-1..INV-6) |
| **Guards activos (P5 completo)** | Autenticação e scopes RBAC validados; G1 confirmado no navegador |
| **Production-ready (P9 completo)** | `nx e2e` PASS; G1..G5 todos cobertos; zero CSS hardcoded; zero PHI em telemetria |

---

## 6. Approval criteria por marco

- **P3 done = MVP shippable em dev?** SIM — com MockAdapter, o fluxo de treino completo (sessão + conversa + materializar + aprovar + descartar) é demonstrável.
- **P5 done = seguro para staging?** SIM — guards impedem acesso não autorizado; INV-1 estruturalmente garantido.
- **P9 done = ready for production?** SIM — todos os gaps e2e cobertos; observabilidade activa; polimento UX completo.
