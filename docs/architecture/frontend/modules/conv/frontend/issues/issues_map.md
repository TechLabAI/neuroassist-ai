# Issues Map — CONV Frontend (NeuroAssist AI)

> Module: **CONV — Conversação por Árvore de Falas** · Backlog generated: 2026-08-31
> Total: **28 functional (FE)** + **6 integration (INT)** = **34 issues**

## Summary table

| Issue | Title | Kind | Priority | Status |
|---|---|---|---|---|
| CONV-FE-001 | conv.contract.ts + conv.permission-map.ts + conv.feature-flags.ts + CONV_MODULE_DESCRIPTOR (shell-entry barrel, patient-only) | FE | P1 | INTACT |
| CONV-FE-002 | CONV_BOOTSTRAP init/destroy lifecycle (seed locale, camera start/release, audio blob revoke, session verification) | FE | P1 | INTACT |
| CONV-FE-003 | PatientShellComponent + PatientTopbarComponent (restricted patient shell — topbar only, no caregiver sidebar) | FE | P1 | INTACT |
| CONV-FE-004 | patient-tool-descriptors.ts — register CONV in patient shell ONLY, not caregiver (apps/neuroassist-ai-web) | FE | P1 | INTACT |
| CONV-FE-005 | INPUT_CHANNEL_PORT token + ConvShellIntegrationService (gesture bridge + locale/playback orchestration, ADR-005) | FE | P1 | INTACT |
| CONV-FE-006 | SpeechTreePort + Http/MockAdapter + SpeechTreeFacade (getTree, listLevel, in-memory locale cache, ADR-005 single writer) | FE | P2 | INTACT |
| CONV-FE-007 | SemanticSearchPort + Http/MockAdapter + SemanticAssistFacade (POST /search, degraded-first OPS-003, no error on failure) | FE | P2 | INTACT |
| CONV-FE-008 | NodeSelectionPort + Http/MockAdapter (POST /select, clientEventId idempotency, retry-safe, SPEC-05 §4) | FE | P2 | INTACT |
| CONV-FE-009 | BindingPort + Http/MockAdapter (POST /bindings/resolve, opaque engineFeatureRef, bound:false = no-op, SPEC-04 §3) | FE | P2 | INTACT |
| CONV-FE-010 | AudioPort + Http Adapter + TtsPlaybackFacade (blob URL cache, 3-state machine cached→web-speech→text, SEC-002 no persist) | FE | P2 | INTACT |
| CONV-FE-011 | ProposalPort + MockAdapter + ProposalConfirmFacade (G-2: MockAdapter-only until TREI-BE endpoint, @fixme e2e tracked) | FE | P2 | INTACT |
| CONV-FE-012 | DTOs barrel (TreeNodeDto, SearchResponseDto, SelectRequestDto, BindingResponseDto, ProposalDto) + DATA_PROVIDERS binding | FE | P2 | INTACT |
| CONV-FE-013 | SpeechTreePageComponent (smart, signals — orchestrates tree + assist + playback, Tela 06) | FE | P3 | INTACT |
| CONV-FE-014 | SpeechTreeLevelComponent + SpeechNodeButtonComponent (presentational, channel-agnostic, pending-locale disabled) | FE | P3 | INTACT |
| CONV-FE-015 | InputChannelIndicatorComponent + BackButtonComponent + SemanticAssistPanelComponent (presentational, feature-speech-tree) | FE | P3 | INTACT |
| CONV-FE-016 | TtsPlayerComponent + PlaybackStatusComponent + ReplayButtonComponent (feature-tts, ADR-007 fallback chain) | FE | P3 | INTACT |
| CONV-FE-017 | ProposalConfirmPageComponent + PendingConfirmationListComponent + ProposalCardComponent + VariantPickerComponent (Tela 09) | FE | P3 | INTACT |
| CONV-FE-018 | patientSessionGuard — active SessaoPaciente JWT check, shell teardown on revoke (no caregiver redirect) | FE | P5 | INTACT |
| CONV-FE-019 | convNavegarGuard — conv.arvore.navegar scope check, forbidden → árvore forbidden state (never caregiver route) | FE | P5 | INTACT |
| CONV-FE-020 | PatientBreadcrumbComponent + tree-level breadcrumb state (libs/conv/navigation — patient paths only) | FE | P6 | INTACT |
| CONV-FE-021 | Speech tree loading / empty / error / forbidden states (SPEC-07 degradation-first, large 'tentar de novo' affordance) | FE | P7 | INTACT |
| CONV-FE-022 | TTS fallback chain (cached audio → Web Speech → prominent full-bleed text, voice-never-silent, OPS-004/ADR-007) | FE | P7 | INTACT |
| CONV-FE-023 | ProblemDetail → patient copy (toast/banner/inline/shell-exit surface mapping, i18n EN/PT, SPEC-07 §1 table, ADR-018) | FE | P7 | INTACT |
| CONV-FE-024 | Telemetry events via @neuroassist/shared/observability (ConvServedDegraded, session events, node selection audit trail) | FE | P8 | INTACT |
| CONV-FE-025 | Playwright e2e: falar-frase.feature + isolamento-patient-shell.feature (golden path + zero caregiver-surface leakage) | FE | P9 | INTACT |
| CONV-FE-026 | Playwright e2e: fallback-voz.feature + assist-e-degradacao.feature (degradation chains, silent fallbacks, OPS-003/004) | FE | P9 | INTACT |
| CONV-FE-027 | G-1/G-3 EARS BDD gap closure: 5 thematic scenario groups + locale-switch cache invalidation scenario | FE | P9 | INTACT |
| CONV-FE-028 | G-4 scaffold spec: fe-nx-register-module.py MUST emit test.fixme() with explicit message for 14 CONV e2e scenarios | FE | P9 | INTACT |
| CONV-INT-001 | shared/access patient-session JWT integration (SessaoPaciente token header on all conv-service calls via GWY) | INT | P4 | INTACT |
| CONV-INT-002 | shared/error-model ProblemDetail → AppError mapping (all CONV BE error codes, patient-safe copy, never raw HTTP status) | INT | P4 | INTACT |
| CONV-INT-003 | GEST InputChannelPort composition at scope:web (ADR-005 — zero scope:conv→scope:gest import enforced by Nx boundaries) | INT | P4 | INTACT |
| CONV-INT-004 | conv-service Elasticsearch kNN degradation contract (OPS-003: degraded:true = soft 200, never error, silent fallback) | INT | P4 | INTACT |
| CONV-INT-005 | shared/observability telemetry integration (ConvServedDegraded event, session lifecycle events) | INT | P4 | INTACT |
| CONV-INT-006 | TREI cross-module ProposalPort dependency (G-2: confirmar-proposta.feature @fixme — endpoint ABSENT from conv-service v1) | INT | P4 | INTACT |

---

## By priority wave

| Wave | Issues | Description |
|---|---|---|
| **P1 — Foundation** | CONV-FE-001..005 | Tool contract, bootstrap lifecycle, patient shell, web app wiring, gesture bridge token |
| **P2 — Data Access** | CONV-FE-006..012 | 5 Ports + adapters + facades + DTOs + DATA_PROVIDERS |
| **P3 — Feature Libs** | CONV-FE-013..017 | Tela 06 (SpeechTree) + feature-tts + Tela 09 (ProposalConfirm) |
| **P4 — Integration** | CONV-INT-001..006 | Auth, error model, GEST bridge, ES degradation, observability, TREI dep |
| **P5 — Guards** | CONV-FE-018..019 | patientSessionGuard + convNavegarGuard |
| **P6 — Navigation** | CONV-FE-020 | PatientBreadcrumbComponent |
| **P7 — UX/Error** | CONV-FE-021..023 | Loading/error states, TTS fallback chain, ProblemDetail → patient copy |
| **P8 — Observability** | CONV-FE-024 | Telemetry events |
| **P9 — e2e + Gaps** | CONV-FE-025..028 | Playwright specs + G-1/G-3 EARS gap closure + G-4 scaffold spec |

---

## By SPEC origin

| SPEC | Issues |
|---|---|
| SPEC-01 (Route Map) | CONV-FE-001, CONV-FE-004, CONV-FE-018, CONV-FE-019, CONV-FE-025, CONV-FE-027 |
| SPEC-02 (Component Hierarchy) | CONV-FE-003, CONV-FE-013, CONV-FE-014, CONV-FE-015, CONV-FE-016, CONV-FE-017 |
| SPEC-03 (View Models) | CONV-FE-006, CONV-FE-007, CONV-FE-010, CONV-FE-011, CONV-FE-013, CONV-FE-027 |
| SPEC-04 (Data Access) | CONV-FE-006..012, CONV-INT-001, CONV-INT-004, CONV-FE-027 (G-3) |
| SPEC-05 (Tool Contract) | CONV-FE-001, CONV-FE-008, CONV-FE-018, CONV-FE-019 |
| SPEC-06 (Shell Integration) | CONV-FE-002, CONV-FE-004, CONV-FE-005, CONV-INT-003 |
| SPEC-07 (Error Handling) | CONV-FE-021, CONV-FE-022, CONV-FE-023, CONV-INT-002 |
| SPEC-08 (E2E Scenarios) | CONV-FE-025, CONV-FE-026, CONV-FE-027, CONV-FE-028 |
| Generator-config.yml | CONV-FE-001..005 (libs/shell-entry) |

---

## By layer (Nx library)

| Layer | Path | Issues |
|---|---|---|
| `conv/shell-entry` | `libs/conv/shell-entry/` | CONV-FE-001, CONV-FE-002 |
| `conv/patient-shell` | `libs/conv/patient-shell/` | CONV-FE-003 |
| `conv/data-access` | `libs/conv/data-access/` | CONV-FE-006..012, CONV-INT-001, CONV-INT-002, CONV-INT-004 |
| `conv/access` | `libs/conv/access/` | CONV-FE-018, CONV-FE-019 |
| `conv/feature-speech-tree` | `libs/conv/feature-speech-tree/` | CONV-FE-013, CONV-FE-014, CONV-FE-015, CONV-FE-021 |
| `conv/feature-tts` | `libs/conv/feature-tts/` | CONV-FE-016, CONV-FE-022 |
| `conv/feature-proposal-confirm` | `libs/conv/feature-proposal-confirm/` | CONV-FE-017 |
| `conv/integration` | `libs/conv/integration/` | CONV-FE-005, CONV-FE-024, CONV-INT-003, CONV-INT-005 |
| `conv/navigation` | `libs/conv/navigation/` | CONV-FE-020 |
| `conv/ui` | `libs/conv/ui/` | CONV-FE-021 (loading-spinner), CONV-FE-023 (session-ended) |
| `apps/neuroassist-ai-web` | `apps/neuroassist-ai-web/` | CONV-FE-004, CONV-FE-023 (i18n), CONV-INT-003 |
| `apps/conv-e2e` | `apps/conv-e2e/` | CONV-FE-025, CONV-FE-026, CONV-FE-028 |

---

## Critical path (blocking sequence)

```
CONV-FE-001 (contract)
  └─► CONV-FE-002 (bootstrap)
        └─► CONV-FE-018 (patientSessionGuard)
  └─► CONV-FE-012 (DTOs + DATA_PROVIDERS)
        └─► CONV-FE-006..011 (Ports + Adapters + Facades)
              └─► CONV-FE-013 (SpeechTreePageComponent)
                    └─► CONV-FE-014 + CONV-FE-015 (presentational tree)
                    └─► CONV-FE-016 (TtsPlayer)
                    └─► CONV-FE-017 (ProposalConfirm)
                          └─► CONV-FE-025..026 (e2e specs)
  └─► CONV-FE-005 (INPUT_CHANNEL_PORT)
        └─► INT-CONV-003 (GEST composition @ scope:web)
CONV-FE-003 (patient shell) ─► CONV-FE-004 (app wiring) ─► CONV-FE-019 (navegar guard)
```

---

## Estimates (S/M/L)

| Size | Issues | Count |
|---|---|---|
| S (< 4h) | CONV-FE-001, 002, 004, 009, 019, 020, 024, CONV-INT-004, 005, 006 | 10 |
| M (4-6h) | CONV-FE-003, 005, 006, 007, 008, 010, 011, 012, 018, 021, 022, 023, CONV-INT-001, 002, 003 | 15 |
| L (6-8h) | CONV-FE-013, 014, 015, 016, 017, 025, 026, 027, 028 | 9 |
