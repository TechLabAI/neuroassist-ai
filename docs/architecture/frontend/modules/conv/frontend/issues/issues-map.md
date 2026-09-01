# Issues Map -- issues

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
| CONV-FE-027 | G-1/G-3 EARS BDD gap closure: 5 thematic scenario groups (routing, VM states, data-access, shell-integration, error-handling) + locale-switch cache invalidation scenario | FE | P9 | INTACT |
| CONV-FE-028 | G-4 scaffold spec: fe-nx-register-module.py MUST emit test.fixme() with explicit message for all 14 CONV e2e scenarios (not empty describe) | FE | P9 | INTACT |
| CONV-INT-001 | shared/access patient-session JWT integration (SessaoPaciente token header on all conv-service calls via GWY) | INT | P4 | INTACT |
| CONV-INT-002 | shared/error-model ProblemDetail → AppError mapping (all CONV BE error codes, patient-safe copy, never raw HTTP status) | INT | P4 | INTACT |
| CONV-INT-003 | GEST InputChannelPort composition at scope:web (ADR-005 — zero scope:conv→scope:gest import enforced by Nx boundaries) | INT | P4 | INTACT |
| CONV-INT-004 | conv-service Elasticsearch kNN degradation contract (OPS-003: degraded:true = soft 200, never error, silent fallback) | INT | P4 | INTACT |
| CONV-INT-005 | shared/observability telemetry integration (ConvServedDegraded event, session lifecycle events) | INT | P4 | INTACT |
| CONV-INT-006 | TREI cross-module ProposalPort dependency (G-2: confirmar-proposta.feature @fixme — endpoint ABSENT from conv-service v1, belongs to TREI authoring flow) | INT | P4 | INTACT |
