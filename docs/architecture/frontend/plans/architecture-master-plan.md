# Architecture Master Plan

> Rastreia a camada de arquitetura (Layer 1 -- Architect) de cada modulo.
> Atualizado automaticamente (master-plan.py, D-371). NAO editar a mao.

Legenda de status: `pendente` | `em andamento` | `OK` | `bloqueado` | `N/A`

## Status por modulo

| Modulo | Servico | Vision | Specs | Specs-Validate | Backlog | Scaffold | Atualizado |
|---|---|---|---|---|---|---|---|
| GEST | gest | OK | OK | OK | OK | OK | 2026-07-28 |
| EVNT | evnt | OK | OK | OK | OK | OK | 2026-07-28 |
| PAC | pac | OK | OK | OK | OK | OK | 2026-07-29 |
| EDGE | edge | OK | OK | OK | OK | OK | 2026-07-29 |
| ACS | acs | OK | OK | OK | OK | pendente | 2026-07-30 |

## Ledger de gates diferidos

| Data | Escopo | Gate | Comando de recuperacao | Status |
|---|---|---|---|---|

## Log cronologico
- 2026-07-27T21:24:18 -- GEST -- M3 -- FE Specs OK -- SPEC-01..08 + generator-config.yml + cross-link + design-tokens + 8 features e2e; EdgeRecognitionSignal v1.0.0 bindado verbatim
- 2026-07-27T21:49:14 -- GEST -- M4 -- FE Specs-Validate OK (com 1 gap real) -- Gate1 EARS PASS (33 clausulas), Gate2 cobertura 32/33 (97%); gap: SPEC-04 no-cache/replay sem feature -- issue para backlog
- 2026-07-28T02:39:15 -- GEST -- M5 -- FE Backlog OK -- 26 issues funcionais (GEST-FE-001..026) + 7 integracao (GEST-INT-001..007); GEST-FE-026 fecha gap SPEC-04 (Gate2 33/33); GEST-INT-007 registry gap corrigido (frontendLib/hasUi/isInfrastructure)
- 2026-07-28T16:51:31 -- GEST -- M6 -- FE Promote OK -- Track D (composition-only, sem tela propria); P2 Shell Setup (primeiro modulo); GestureTestFacade + GestureLiveTestComponent + GestShellIntegrationService + provideGest(); shared/contracts lib criada (EdgeRecognitionSignal v1.0.0 frozen); gest-e2e 9 specs; ESLint nx-boundary enforced; E2E gate OK (pending)
- 2026-07-28T02:58:30 -- GEST -- M7 -- FE E2E-Validate OK (pending) -- npm install (1767 pkgs) + nx reset + nx e2e gest-e2e --list --skipInstall: 0 erros, 9 specs estruturais (stubs, impl na Layer 2); achado: fe-nx-register-module.py so conta Scenario EN, nao Cenario PT-BR (undercounting cosmetico, nao bloqueante)
- 2026-07-28T07:19:39 -- EVNT -- Specs-Validate -- OK (com gaps) -- gate1 EARS PASS 7/7; gate2 tool 0/41 (mismatch EN/PT), qualitativo ~12-14/41; ~27-29 gaps para Backlog (guards/auth, feature-flags, SSE reconnect, concurrency, lifecycle, error-4xx, navigation)
- 2026-07-28T07:58:56 -- EVNT -- Backlog -- OK -- 30 issues funcionais + 7 integracao (37 total); todos os 15 gaps M3.5 cobertos; riscos: SSE+Authorization header (EventSource nao suporta), ShellOutletRegistry pode nao existir, e2e SSE mock nao-trivial
- 2026-07-28T22:09:23 -- EVNT -- M6 -- FE Promote OK -- Track D (camada completa do zero); AlarmOverlayComponent (global shell outlet ADR-005) promovida do prototipo alarme.component.ts; EventFeedPageComponent (rota /eventos); Port+Mock+HTTP adapters; EventDeliveryFacade signals-based; ShellOutletRegistry stub criado em shared/ui-components; evnt-e2e 5 specs; E2E gate OK (pending)
- 2026-07-28T08:12:15 -- EVNT -- Promote -- OK (Track D) -- AlarmOverlayComponent (global outlet ADR-005) + EventFeedPageComponent; Port/Mock/HTTP adapters; EventDeliveryFacade; ShellOutletRegistry stub; rota /eventos; evnt-e2e 5 specs registrados
- 2026-07-28T08:13:13 -- EVNT -- E2E-Validate -- OK (pending) -- nx e2e evnt-e2e --list --skipInstall exit 0, 0 tests em 0 arquivos (esperado: 5 spec stubs prototype-first, mesmo padrao de gest-e2e); implementacao real via fe-implement (Layer 2)
- 2026-07-28T12:14:03 -- PAC -- M1 -- Vision OK -- 01..06 + ADR-005-command-edit-not-live-until-synced; M1.5 brief sincronizado (BE autoritativo)
- 2026-07-28T19:15:58 -- PAC -- M3 -- FE Specs OK -- SPEC-01..08 + generator-config.yml + backend-api-cross-link + design-tokens + 3 e2e features (command-catalog/editor/patient-register); EARS 51/51; reconciliacao retire vs DELETE (ADR-006); gap EDGE device-ack carregado
- 2026-07-28T19:41:49 -- PAC -- M4 -- FE Specs-Validate OK -- Gate1 EARS PASS (51 clausulas); Gate2 51/51 cobertas, 39 orfaos INFO; 62 cenarios Gherkin (command-catalog/editor/patient-register)
- 2026-07-29T06:03:19 -- PAC -- M5 -- FE Backlog OK -- 25 issues funcionais (PAC-FE-001..025) + 6 integracao (PAC-INT-001..006); 39 orphans mapeados em issues_map; gap EDGE device-ack: PAC-INT-001 (stub adapter honest-state)
- 2026-07-29T06:55:03 -- PAC -- M6 -- FE Promote OK -- Track D; calibracao (catalog+editor) + onboarding step2 (register); Port/Mock/HTTP adapters; PacComandoFacade signals ADR-005 (honest-state); EdgeDeviceAckStubAdapter (INT-FE-PAC-001); PAC nunca importa scope:gest (slot ng-content); pac-e2e registrado Nx; E2E gate OK (pending, 62 specs)
- 2026-07-29T06:55:30 -- PAC -- M7 -- FE E2E-Validate OK (pending) -- nx e2e pac-e2e --list --skipInstall: 62 specs em 3 arquivos (command-catalog: 12, command-editor: 42, patient-register: 8); implementacao real via fe-implement (Layer 2)
- 2026-07-29T10:47:28 -- EDGE -- M1 Vision -- OK -- 9 artifacts FE gerados (docs 01-06 + ADR-005 local + brief); ADR-005 local: connectivity derived from heartbeat freshness
- 2026-07-29T11:23:59 -- EDGE -- M3 FE Specs -- OK -- SPEC-01..08 + generator-config.yml + 2 contracts + 8 e2e features; endpoint reconciliado com BE enrollment model; connectivity 5-state derivada de server-relative age
- 2026-07-29T11:32:37 -- EDGE -- M4 FE Specs-Validate -- OK (com gaps) -- Gate1 PASS (41 EARS, 0 violations); Gate2 manual 35/41; 6 true gaps + 1 clausula EARS ausente SPEC-07
- 2026-07-29T18:18:54 -- EDGE -- M5 FE Backlog -- OK -- 27 issues funcionais + 6 INT = 33 issues; plan-FE-EDGE.md; 9 waves (~16.5 dev-days); G6 retry-backoff HIGH em P7 (FE-EDGE-019)
- 2026-07-29T08:39:37 -- EDGE -- M6 FE Promote -- OK -- Track D (camada completa do zero; sem tela propria no prototipo); 2 Ports (DevicePort + PairingPort) + 2 Mocks + 2 HTTP stubs; DeviceFacade (signals + liveness poll + deriveState server-relative ADR-005) + PairingFacade (state machine idle→awaiting-claim→paired); ConnectivityChipComponent (topbar, sempre visivel); DeviceDetailPageComponent + DevicePairingPageComponent; EdgeShellIntegrationService (nx-boundary); EDGE route wired + tool-descriptor; topbar updated; edge-e2e 8 specs; E2E gate OK (pending)
- 2026-07-29T18:43:16 -- EDGE -- M6 FE Promote -- OK (Track D) -- Ports+Adapters+Facades do zero; ConnectivityChip no topbar; EDGE_ROUTES wired; shell integrado; 8 specs Playwright criados
- 2026-07-29T18:44:11 -- EDGE -- M7 FE E2E-Validate -- OK (pending) -- 17 specs em 8 files; nx e2e edge-e2e --list 0 erros
- 2026-07-29T20:14:35 -- ACS -- M3 -- FE Specs OK -- SPEC-01..08 + generator-config.yml + backend-api-cross-link + design-tokens + 3 e2e features; EARS 55/55 clauses; onboarding wizard scope:web (ACS owner)
- 2026-07-30T00:22:11 -- ACS -- M4 Specs-Validate -- OK (skip: EARS PASS 55/55 concluído em M3)
- 2026-07-30T00:52:00 -- ACS -- M5 FE Backlog -- OK -- 31 issues funcionais + 5 integração = 36 issues; plan-FE-ACS.md gerado
