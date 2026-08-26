# NeuroAssist AI — Project Journal

> Append-only activity log (D-011). Append with `scripts/journal.py append`.
> Never edit past entries; corrections are new `[CORRECTION]` entries referencing the original timestamp.
## 2026-07-26

### 23:55:46 UTC — [PROJECT-INIT] Projeto pronto para desenvolvimento

- **Ator:** C:/Program Files/Git/new-project

## 2026-08-15

### 10:45:54 UTC — [FE-CYCLE] 5 módulos frontend fecharam ciclo de desenvolvimento (GEST, EVNT, PAC, ACS, EDGE)

- **Ator:** Claude Code (sessão 2026-08-13 a 2026-08-15)
- **App-shell bootstrap:** `frontend/apps/neuroassist-ai-web/` não tinha `main.ts`/`app.config.ts`/`index.html`/`project.json` — `nx serve`/`build`/e2e falhavam para todos os módulos. Criado do zero (BLOCKER-1, cross-module). Instalados `@angular/build` + `@angular-devkit/build-angular` (peer deps faltantes do `@nx/angular`).
- **PAC:** 51→88/100 (Maduro). P6-P9: testes unitários, UX polish (blocking modal do emergency gate, safety-critical), telemetria, e2e do catálogo (12/12). PR #2+#3.
- **EVNT:** confirmado 90/100 (Maduro), 37/37 issues, de sessão anterior. Commit próprio + PR #2.
- **EDGE:** 45→93/100 (Maduro) em 2 invocações. P1-P7: infra Nx/Jest, invariantes de segurança testados (honest device-state, no premature "paired"), 6 componentes UI, integração real no app-shell (topbar + onboarding), guards RBAC, G6 retry-backoff fechado. PR #3+#4.
- **ACS:** 41→80/100 (Maduro) numa invocação (safety cap atingido, P6-P9 restantes). P1-P5: lint gate, guards RBAC fechados (os 3 eram stubs abertos retornando `true` incondicional — gap de segurança real, corrigido), 4 componentes funcionais completos, 170 testes (84.9% cobertura). Plano externo referenciado não existia nesta máquina — reconstruído localmente. PR #3.
- **GEST:** 80/100 (Maduro) mantido — retomado de checkpoint anterior bloqueado (P6 e2e sem dev server). Com o app-shell disponível, os 9 specs e2e stub foram implementados de verdade (14/14 passando, incl. gate obrigatório GEST-FE-026). PR #4.
- **Bug cross-module corrigido:** `libs/edge/src/lib/ui/relative-time.component.ts` — campo `_isoDate` era `private` mas referenciado no template, causando overlay de erro do Vite que bloqueava TODOS os cliques da aplicação em modo dev (achado via e2e do GEST, não do EDGE).
- **Investigação backend GEST:** tentativa de integrar FE↔BE real revelou que nenhum serviço backend passou pelo próprio ciclo de desenvolvimento (`/be-validator` → planner → implement) — todos permanecem "scaffolded" (ver project-checklist.md). `gest-service` tem P1-P3 com lógica real, mas P4/P6/P8 ausentes e P5 parcial; bring-up local tem drift de config (docker-compose próprio do gest-service usa banco isolado `gest_dev`, incompatível com a convenção centralizada de `neuroassist-ai-migrations`). Integração adiada.
- **Pendências conhecidas:** ACS P6 (e2e, ~25 cenários, MVP-gate blocker real) + P6b/P7/P8/P9; EDGE P8/P9 (observability + e2e completo); PAC (lifecycle buttons do editor + composição patient-register, deferred pendente ACS); HIST/DASH não iniciados (nem scaffold); ciclo de desenvolvimento backend não iniciado para nenhum serviço.

## 2026-08-26

### 12:00:00 UTC — [FE-CYCLE] GEST encerrado — 98/100 (Maduro), P1-P9 completas

- **Ator:** Claude Code (raio-x geral do projeto + fechamento do GEST)
- **GEST:** replanejado em 2026-08-25 (51/100, onda W10 "estação do paciente" arquitetada e não implementada) e encerrado em 2026-08-26 com score 98/100. P1-P9 completas, 41/41 issues DONE: wiring multi-empresa, identidade de dispositivo, perfil/limiares + guarda de drift, rota `/estacao` always-on com relato idempotente, heartbeat/wake-lock/fila offline, UX polish, e2e de calibração (5 specs) e de estação (8 cenários SPEC-09 §5). Validator Modo 3 (rodada 2) confirmou de forma independente: `nx e2e gest-e2e` 26/26, `nx test gest` 292/292, lint 0 erros, build OK; mutações E1/E2/E6 executadas de fato (quebrar → vermelho → reverter → verde).
- **Fechamento dos 4 pontos abertos do P6** (commit `042273a`, revalidado em `c4c76c5`): `heartbeat.service.ts` cancelava e reagendava incorretamente o timer quando o servidor recomendava um intervalo diferente do atual no primeiro ciclo (bug de bootstrap, e2e E4); `gest.providers.ts` não registrava `DeviceAuthHttpAdapter`/`DetectionProfileHttpAdapter`/`ReportIngressHttpAdapter` como providers explícitos, causando `NullInjectorError` no `estacaoDeviceGuard`; dois specs e2e (`estacao-fila-offline`, `estacao-privacidade`) tinham técnicas de mock/offline incompatíveis com `page.route()` (corrigido com `route.abort()` condicional e `route.fallback()`).
- **Tech-debt menor não bloqueante:** `empresa-context` DI ainda não totalmente wired em todos os pontos.
- **Pendência real remanescente:** decisão mTLS vs OAuth2 para o bridge de autenticação GEST↔EDGE (ADR-007, P10) — decisão conjunta com EDGE, ainda em aberto.
- `docs/architecture/frontend/plans/plan-FE-GEST.md` e `project-checklist.md` estavam desatualizados (mostravam P7-P9 pendentes) — atualizados nesta sessão para refletir o encerramento real.

