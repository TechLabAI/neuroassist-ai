# Issues Map — GEST Frontend

> Module: **GEST — Detecção de Gesto (Edge)** | NeuroAssist AI
> Generated: 2026-07-28 | Atualizado: 2026-08-25 (W10 — estação do paciente)
> Total: 33 functional + 8 integration = 41 issues

## Master table

| Issue | Title | Kind | Priority | Wave | Status |
|---|---|---|---|---|---|
| GEST-FE-001 | gest.contract.ts — MODULE_KEY, MODULE_MENU_METADATA, MODULE_PERMISSION_MAP, MODULE_FEATURE_FLAGS | FE | P1 | W1 | ABERTA |
| GEST-FE-002 | GEST_ROUTES — thin host app entry (apps/gest, dev/e2e only) | FE | P1 | W1 | ABERTA |
| GEST-FE-003 | GestModule barrel — re-exports, Nx tags, project.json | FE | P1 | W1 | ABERTA |
| GEST-FE-004 | GEST_MODULE_DESCRIPTOR + gest.descriptor.ts (shell discovery entry) | FE | P1 | W1 | ABERTA |
| GEST-FE-005 | gest.bootstrap.ts — init (detection-contract negotiation) / destroy (stream teardown) | FE | P1 | W1 | ABERTA |
| GEST-FE-006 | tool-descriptors.ts — static GEST fallback entry at scope:web shell | FE | P1 | W1 | ABERTA |
| GEST-INT-007 | project-context.json registry fix — frontendLib, hasUi:false, isInfrastructure:false | INT | P1 | W1 | ABERTA |
| GEST-FE-007 | EdgeDetectionClient — openStream/mockStream/close + contractVersion negotiation | FE | P2 | W2 | ABERTA |
| GEST-FE-008 | GestureTestFacade — signals, VM state machine, EdgeRecognitionSignal → GestureTestVM | FE | P2 | W2 | ABERTA |
| GEST-FE-009 | GestEligibilityService + DetectionContractService — REST services | FE | P2 | W2 | ABERTA |
| GEST-FE-010 | ProblemDetail → AppError — pt-BR copy table for GEST error types | FE | P2 | W2 | ABERTA |
| GEST-FE-011 | GestureLiveTestComponent — smart container (signals, OnPush, @Input/@Output) | FE | P3 | W3 | ABERTA |
| GEST-FE-012 | PreviewFrameComponent — webcam <video> wrapper, no stream lifecycle | FE | P3 | W3 | ABERTA |
| GEST-FE-013 | TestStateBadgeComponent — 5-state badge + pt-BR messages | FE | P3 | W3 | ABERTA |
| GEST-FE-014 | CompositeStepIndicatorComponent — two-step progress, hidden when simple | FE | P3 | W3 | ABERTA |
| GEST-FE-015 | DegradedBannerComponent — honest degraded banner (4 reasons) | FE | P3 | W3 | ABERTA |
| GEST-FE-016 | LiveTestControlsComponent — Testar agora/Parar/Tentar de novo + RBAC hide | FE | P3 | W3 | ABERTA |
| GEST-INT-001 | GestShellIntegrationService — registerWithShell + resolveLiveTestSlot | INT | P4 | W4 | ABERTA |
| GEST-INT-002 | Nx-boundary ESLint rules — scope:gest import restrictions | INT | P4 | W4 | ABERTA |
| GEST-INT-003 | shared/contracts — EdgeRecognitionSignal verbatim binding (v1.0.0) | INT | P4 | W4 | ABERTA |
| GEST-INT-004 | Bridge transport auth — bridgeToken provider (JWT-device provisional; OPEN) | INT | P4 | W4 | ABERTA |
| GEST-INT-005 | PAC shell-page composition slot — pac-comando-editor-page hosts <gest-live-test> | INT | P4 | W4 | ABERTA |
| GEST-INT-006 | Onboarding shell-page composition slot — /onboarding/gesto hosts <gest-live-test> | INT | P4 | W4 | ABERTA |
| GEST-FE-017 | gestDeviceOnlineGuard — paired+online check → degraded, never blocking | FE | P5 | W5 | ABERTA |
| GEST-FE-018 | gest/navigation — breadcrumb 'Calibração › Teste ao vivo' (thin host only) | FE | P6 | W6 | ABERTA |
| GEST-FE-019 | Loading/empty states — spinner, idle placeholder, retry actions | FE | P7 | W7 | ABERTA |
| GEST-FE-020 | Error UI — camera-error first-class panel, toast/banner/modal routing | FE | P7 | W7 | ABERTA |
| GEST-FE-021 | Telemetry events — live-test start/stop/verdict/degraded | FE | P8 | W8 | ABERTA |
| GEST-FE-022 | Playwright e2e — live-test-simple-recognized + live-test-composite | FE | P9 | W9 | ABERTA |
| GEST-FE-023 | Playwright e2e — live-test-degraded-offline + live-test-timeout-not-recognized | FE | P9 | W9 | ABERTA |
| GEST-FE-024 | Playwright e2e — camera-permission-error + camera-lifecycle-teardown | FE | P9 | W9 | ABERTA |
| GEST-FE-025 | Playwright e2e — composition-boundary + backend-error-handling | FE | P9 | W9 | ABERTA |
| GEST-FE-026 | **[GAP CLOSURE]** stream-no-cache-between-runs.feature — SPEC-04 §5 (M4-fe mandatory) | FE | P9 | W9 | ABERTA |

<!-- ─── Estação do paciente (ADR-008 / SPEC-09) — W10 ─────────────────────────
     Onda distinta das anteriores por natureza, não por sequência: W1..W9
     constroem o TESTE AO VIVO (calibração, com uma cuidadora olhando). W10
     constrói o RUNTIME — a superfície que observa o paciente sem ninguém por
     perto. É o que hoje não existe: a cadeia gesto → relato → evento → alarme
     não tem execução, e as telas de alarme são alimentadas por mock.
     Ordem obrigatória: 027 → 028 → 029 → 030; 031/032 dependem de 030.        -->
| GEST-FE-027 | Sessão de dispositivo — DeviceSessionStore + POST /oauth2/token/device | FE | P10 | W10 | ABERTA |
| GEST-FE-028 | Perfil de detecção do dispositivo — GET condicional com ETag/304 | FE | P10 | W10 | ABERTA |
| GEST-FE-029 | Limiares vêm do perfil publicado, não de constantes de código | FE | P10 | W10 | ABERTA |
| GEST-FE-030 | Rota /estacao + laço always-on + POST /api/v1/reports (fecha a cadeia) | FE | P10 | W10 | ABERTA |
| GEST-FE-031 | Heartbeat, Wake Lock e o estado "parada" — detecção parada nunca em silêncio | FE | P11 | W10 | ABERTA |
| GEST-FE-032 | Fila de relatos em IndexedDB — "enfileirado" ≠ "não aconteceu" | FE | P11 | W10 | ABERTA |
| GEST-FE-033 | Playwright e2e da estação — os oito cenários da SPEC-09 §5 | FE | P12 | W10 | ABERTA |
| GEST-INT-008 | Guarda de drift dos limiares apontando para o BROWSER | INT | P12 | W10 | ABERTA |

---

## By priority (P1 → P9)

| Priority | Issues | Count |
|---|---|---|
| P1 (Foundation) | GEST-FE-001..006, GEST-INT-007 | 7 |
| P2 (Data access) | GEST-FE-007..010 | 4 |
| P3 (Feature libs) | GEST-FE-011..016 | 6 |
| P4 (Hub integrations) | GEST-INT-001..006 | 6 |
| P5 (Access guards) | GEST-FE-017 | 1 |
| P6 (Navigation) | GEST-FE-018 | 1 |
| P7 (UX polish) | GEST-FE-019..020 | 2 |
| P8 (Observability) | GEST-FE-021 | 1 |
| P9 (e2e + Hardening) | GEST-FE-022..026 | 5 |
| P10 Estação — identidade, perfil, limiares, laço | 4 | 
| P11 Estação — sobrevivência (heartbeat, wake lock, fila) | 2 | 
| P12 Estação — e2e + guarda de drift | 2 | 
| **TOTAL** | | **41** |

---

## By SPEC (traceability)

| SPEC | Issues |
|---|---|
| SPEC-01 (Route map) | FE-002, FE-017, FE-018, INT-005, INT-006 |
| SPEC-02 (Component hierarchy) | FE-003, FE-011, FE-012, FE-013, FE-014, FE-015, FE-016 |
| SPEC-03 (View models) | FE-008, FE-011, FE-013, FE-014 |
| SPEC-04 (Data access) | FE-007, FE-008, FE-009, FE-026 (gap closure), INT-003, INT-004 |
| SPEC-05 (Module contract) | FE-001, FE-016 |
| SPEC-06 (Shell integration) | FE-004, FE-005, FE-006, INT-001, INT-002, INT-005, INT-006 |
| SPEC-07 (Error handling) | FE-010, FE-015, FE-019, FE-020 |
| SPEC-08 (E2E scenarios) | FE-022, FE-023, FE-024, FE-025, FE-026 |
| Cross-link / Registry | INT-003, INT-007 |

---

## By layer (hex-FE)

| Layer | Path | Issues |
|---|---|---|
| Tool entry | `apps/gest/`, `libs/gest/shell-entry/` | FE-001..006, INT-007 |
| Data access | `libs/gest/data-access/` | FE-007..010 |
| Feature lib | `libs/gest/feature-live-test/`, `libs/gest/ui/` | FE-011..016 |
| Integration | `libs/gest/integration/`, `apps/web/` | INT-001..006 |
| Access guards | `libs/gest/access/` | FE-017 |
| Navigation | `libs/gest/navigation/` | FE-018 |
| UX polish | `libs/gest/feature-live-test/` (update) | FE-019..020 |
| Observability | `libs/gest/data-access/` (update) | FE-021 |
| E2E | `apps/gest-e2e/` | FE-022..026 |

---

## Critical dependency path

```
GEST-INT-007 (registry fix — P1 FIRST)
    ↓
GEST-FE-001 (contract.ts)
    ↓
GEST-FE-002 (routes) ── GEST-FE-003 (barrel) ── GEST-INT-003 (shared/contracts)
    ↓                                                      ↓
GEST-FE-004 (descriptor)                        GEST-FE-007 (EdgeDetectionClient)
    ↓                   ↓                                  ↓
GEST-FE-005 (bootstrap) GEST-FE-006 (fallback)  GEST-FE-008 (Facade + state machine)
                                                           ↓
GEST-FE-011 (GestureLiveTestComponent) ← GEST-FE-012..016 (child components)
    ↓                       ↓
GEST-INT-001 (shell service) GEST-FE-017 (guard)
    ↓
GEST-INT-005 + INT-006 (composition slots)
    ↓
GEST-FE-022..026 (e2e)
    ↑
GEST-FE-026 (stream-no-cache — gap SPEC-04 §5, mandatory before gate closure)
```

---

## Estimates by wave

| Wave | Issues | Estimate |
|---|---|---|
| W1 Foundation | 7 issues | 2–3 days |
| W2 Data access | 4 issues | 2 days |
| W3 Feature libs | 6 issues | 3 days |
| W4 Hub integrations | 6 issues | 2 days |
| W5 Access guards | 1 issue | 0.5 days |
| W6 Navigation | 1 issue | 0.5 days |
| W7 UX polish | 2 issues | 1 day |
| W8 Observability | 1 issue | 0.5 days |
| W9 e2e + Hardening | 5 issues | 2.5 days |
| W10 Estação do paciente (ADR-008) | 8 issues | ~5 days |
| **TOTAL** | **41** | **~19 days** |

---

## Open items / Blockers

| Item | Issue | Status |
|---|---|---|
| Bridge transport auth (mTLS vs OAuth2) | GEST-INT-004 | OPEN — joint decision with EDGE team |
| **Credencial de dispositivo no browser** — um `clientSecret` não pode viver ali | GEST-FE-027 | **OPEN — bloqueia uso fora de piloto fechado** (ADR-008 P1) |
| Guarda de drift aponta para `edge_agent_core.py`, runtime que a ADR-007 aposentou | GEST-INT-008 | OPEN — gate verde pelo lado errado (ADR-008 P2/P3) |
| Workflows de CI desativados desde 25/08/2026 | GEST-INT-008 | OPEN — o guarda não roda enquanto isso |
| stream-no-cache-between-runs.feature | GEST-FE-026 | MANDATORY gap closure (SPEC-04 §5) |
| Registry fields (frontendLib/hasUi) | GEST-INT-007 | Fix FIRST (blocks scaffold automation) |
| bddGate (BE) | BE GEST-023,024 | Deferred — run after BE scaffold |
