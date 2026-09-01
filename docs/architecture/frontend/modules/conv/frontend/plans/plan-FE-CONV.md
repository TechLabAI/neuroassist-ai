# Plan FE-CONV — Conversação por Árvore de Falas (Frontend)

> **Project:** NeuroAssist AI · **Module:** CONV · **Layer:** Frontend Angular 21 / Nx 22
> **Generated:** 2026-08-31 · **Trace:** tr_a8429fa2a45f
> **Anchor:** `docs/architecture/frontend/modules/conv/frontend/01-architectural-vision.md`

---

## Project summary

CONV é o módulo de comunicação por voz do NeuroAssist AI — uma árvore de falas navegável por pacientes com doenças neurodegenerativas (ALS/MND) usando apenas pequenos movimentos voluntários residuais (gesto via webcam) ou toque/mouse. O módulo vive num **patient shell dedicado** (`/paciente`, guarded, sem sidebar de cuidador), consome o `conv-service` via GWY, e tem três requisitos de segurança inegociáveis:

1. **Zero leakage de superfície de cuidador** — nenhuma rota de cuidador é acessível pelo patient shell.
2. **Zero camera leaks** — a câmera é liberada em todo cenário de saída (destroy, session-revoke, route-leave).
3. **Voz nunca em silêncio** — a cadeia de fallback ADR-007 garante que a seleção de uma frase sempre resulta em saída (áudio cacheado → Web Speech → texto proeminente).

O módulo tem Elasticsearch para assist semântico (kNN retrieval-only), TTS com áudio pré-sintetizado (ElevenLabs via GWY, fallback Web Speech) e bilinguismo EN/PT com autoria independente por locale.

---

## Wave map (P1–P9)

| Wave | Focus | Issues | Estimated time |
|---|---|---|---|
| **P1 — Foundation** | Tool contract + bootstrap + patient shell + web wiring + gesture token | CONV-FE-001..005 | ~2 dias |
| **P2 — Data Access** | Ports + Http/Mock Adapters + Facades + DTOs + DATA_PROVIDERS | CONV-FE-006..012 | ~3 dias |
| **P3 — Feature Libs** | Tela 06 (SpeechTree) + feature-tts + Tela 09 (ProposalConfirm) | CONV-FE-013..017 | ~3 dias |
| **P4 — Integration** | Auth JWT, error model, GEST bridge, ES degradation, observability, TREI dep | CONV-INT-001..006 | ~2 dias |
| **P5 — Guards** | patientSessionGuard + convNavegarGuard | CONV-FE-018..019 | ~0.5 dia |
| **P6 — Navigation** | PatientBreadcrumbComponent + state | CONV-FE-020 | ~0.5 dia |
| **P7 — UX/Error** | Loading/error states + TTS fallback + ProblemDetail mapping | CONV-FE-021..023 | ~1.5 dias |
| **P8 — Observability** | Telemetry events | CONV-FE-024 | ~0.5 dia |
| **P9 — e2e + Gaps** | Playwright + G-1/G-3 EARS closure + G-4 scaffold spec | CONV-FE-025..028 | ~2 dias |
| **Total** | | **34 issues** | **~15 dias** |

---

## Critical path

```
P1: CONV-FE-001 → CONV-FE-002 → CONV-FE-012 → CONV-FE-006..011
                                                   ↓
P3: CONV-FE-013 → CONV-FE-014, 015, 016, 017
     ↓
P9: CONV-FE-025, 026 (e2e golden path)

P1: CONV-FE-005 → CONV-INT-003 (GEST bridge @ scope:web)
P5: CONV-FE-018 → CONV-FE-019 (guards — prerequisite for all route rendering)
```

**Shortest shippable path (dev demo):** P1 + P2 + P3 + P5 = ~8.5 dias → Tela 06 funcionando com MockAdapters, patient shell isolado, guards ativos.

---

## Definition of Done by wave

| After wave | Milestone |
|---|---|
| **P1 done** | Patient shell monta, CONV_MODULE_DESCRIPTOR exportado, nenhum link de cuidador em rota `/paciente`, camera token injectável. |
| **P2 done** | Todos os Ports + Facades funcionando com MockAdapters; cache in-memory de árvore por locale verificado. |
| **P3 done** | **MVP shippable em dev:** Tela 06 navegável por toque com seleção, voz e assist (mock); Tela 09 navegável (mock proposals). |
| **P4 done** | Auth JWT em todas as chamadas HTTP; ProblemDetail mapeado; GEST bridge wired @ scope:web; ES degradação testada. |
| **P5 + P6 done** | Guards ativos; breadcrumb correto em navegação profunda. |
| **P7 done** | Estados de erro/loading/empty completos; TTS fallback chain testada end-to-end. |
| **P8 done** | Telemetria emitida em degradações e eventos de sessão. |
| **P9 done** | 4 feature files e2e passando (falar-frase, isolamento, fallback-voz, assist-e-degradacao); G-1/G-3 EARS cobertos; confirmar-proposta mantém `@fixme` rastreável. |

---

## Top-3 risks

| # | Risk | Mitigation |
|---|---|---|
| R1 | **Gesture channel reliability** (ADR-014): câmera/Wake Lock negados pelo dispositivo → `gestureChannelEnabled: false` → touch-only imediato, sem regressão de função. `InputChannelIndicatorComponent` mostra estado honesto. |
| R2 | **ProposalPort sem endpoint BE** (gap G-2): `confirmar-proposta.feature` fica `@fixme` com MockAdapter; o backlog TREI criará o endpoint quando gerado. Risco de atraso no TREI se o endpoint for necessário antes. |
| R3 | **Elasticsearch kNN indisponível** (OPS-003): assist silenciosamente degrada para navegação manual; o `SemanticSearchHttpAdapter` trata 5xx como `{ degraded: true }`. Risco: se o comportamento mudar para 4xx no BE, atualizar o mapper. |

---

## Approval criteria by milestone

| Milestone | Gate |
|---|---|
| **P3 done (MVP dev)** | `nx test conv-*` PASS; `nx lint conv-*` PASS (zero boundary violations); Tela 06 navegável por toque no browser; patient shell não expõe nenhuma rota de cuidador. |
| **P9 done (full)** | `nx e2e conv-e2e` PASS (4 de 5 feature files — `confirmar-proposta` permanece `@fixme`); 14 cenários e2e com `test.fixme()` ou implementação real (nenhum `describe` vazio). |

---

## Gap tracking

| Gap | Status | Tracking |
|---|---|---|
| **G-1** EARS sem Gherkin formal (41 cláusulas) | Materializado como CONV-FE-027 (5 feature files temáticos) | P9 |
| **G-2** Proposals endpoint ausente do api-contract v1 | Documentado em CONV-INT-006 + `@fixme` no feature; responsabilidade do backlog TREI | Bloqueado externamente |
| **G-3** Locale-switch cache invalidation (SPEC-04 §5 novo EARS) | Materializado como cenário em `data-access-ears.feature` (CONV-FE-027) | P9 |
| **G-4** Scaffold `test.fixme()` com mensagem explícita | Materializado como CONV-FE-028 (spec de scaffold); Learning Layer sinalizado | P9 |

---

## Pitfalls proativos (Learning Layer — tr_a8429fa2a45f)

- **`test.fixme()` vs `describe` vazio:** qualquer cenário ainda não buildável deve usar `test.fixme('Aguarda CONV-FE-NNN', ...)` — nunca `describe('', () => {})` (produziria suite falsamente verde). Aplicado em CONV-FE-028.
- **Design system não vendorizado (fe-architect-promote):** o promote deve copiar `design-reference/{theme.css,fonts.html}` SOBRE o fallback gerado do P5, nunca deixar o tema do Estágio 0 sobreviver quando existe design-reference. Aplicar no M6 promote.
- **fonts.html derivado do theme.css:** gerar `<link>` de fonts.html a partir dos tokens `--font-*` do theme.css do app, não copiar cegamente. Aplicar no M6.
