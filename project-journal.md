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

### 12:30:00 UTC — [CORRECTION] EVNT-011 e plan-EVNT.md estavam reportando bloqueadores já resolvidos

- **Ator:** Claude Code (raio-x geral do projeto)
- **EVNT-011** (Flyway V1..V4 ausentes) e o gap G2 do `plan-EVNT.md` (P8 BDD "25/46 falhando" por
  `CucumberSpringContext` sem Testcontainers) estavam documentados como abertos, mas o código já os
  havia resolvido — sem commit correspondente encontrado no journal, o que sugere que a correção
  aconteceu numa sessão anterior sem registro aqui.
- As três tabelas de EVNT (`evento`, `report_idempotency`, `notification_dispatch`) existem em
  `V0500__evnt_initial_schema.sql` (faixa V0500..V0599, não a numeração V1..V4 que a issue previa) e
  `pgcrypto` já foi instalado globalmente por `V0100__acs_global_schema.sql` (ACS). `CucumberSpringContext.java`
  já sobe um `PostgreSQLContainer` real com as migrations aplicadas via `SchemaMigrations.applyTo`.
- Verificado nesta sessão: `./gradlew :services:evnt-service:test` → 126/126 (inclui `CucumberIT` 23/23
  cenários BDD), `architectureCheck` verde.
- **Divergência real encontrada (tech-debt, não bloqueante):** a migration `V0500` não tem as
  constraints `FOREIGN KEY ... ON DELETE CASCADE` nem `CHECK` em `tier`/`status` que os critérios de
  aceite originais de EVNT-011 pediam — integridade garantida só na camada de aplicação. Registrado
  em `EVNT-011.md` para decisão futura (nova migration `V0503` ou aceitar como está).
- **Fechado:** EVNT-011 marcada DONE; `plan-EVNT.md` atualizado (P3 e P8 agora CONCLUIDA). Única
  prioridade real em aberto no EVNT: **P9 (hardening)**, ainda parcial.

### 12:45:00 UTC — [DECISION] INT-GEST-007 fechada — OAuth2 client_credentials definitivo (GEST↔EDGE)

- **Ator:** Claude Code (raio-x geral do projeto) — decisão do usuário via pergunta direta
- ADR-007 (`docs/architecture/backend/adr/ADR-007-edge-device-auth.md`) estava "Accepted (MVP) —
  Under Review", aguardando decisão entre mTLS e OAuth2 `client_credentials` para o transporte
  serviço-a-serviço `gest-service` → `edge-service`. Pergunta feita diretamente ao usuário: manter
  OAuth2, adiar formalmente, ou migrar para mTLS agora.
- **Decisão do usuário:** manter OAuth2 `client_credentials` e fechar a decisão como definitiva —
  não há requisito de segurança hoje que justifique o custo de mTLS (projeto pessoal/pré-produção,
  ambos os serviços no mesmo realm Keycloak).
- Gatilho de revisão documentado no ADR (não uma data, uma condição): reabrir se `edge-service`
  passar a atender dispositivos de terceiros, migrar de realm/IdP, ou surgir requisito explícito de
  compliance exigindo mTLS.
- **Fechado:** ADR-007 → "Accepted (final)"; INT-GEST-007 → DONE; `plan-GEST.md` (R5) atualizado.
  Nenhuma mudança de código necessária — `EdgePairingAdapter` já isolava a auth.
- **Nota separada:** esta decisão cobre apenas o transporte serviço-a-serviço. A autenticação de
  *dispositivo da estação do paciente* (browser, `clientSecret`, ADR-008 no FE) é um problema
  diferente e continua com pendência própria (P1, armazenamento seguro de credencial).

### 13:00:00 UTC — [FIX] EVNT-011 — FK/CHECK constraints ausentes desde V0500 fechadas em V0503

- **Ator:** Claude Code (revisão do EVNT a pedido do usuário)
- Ao reconciliar EVNT-011 mais cedo hoje, ficou registrada uma divergência não-bloqueante: a
  migration `V0500` criava `report_idempotency` e `notification_dispatch` sem `FOREIGN KEY` para
  `evento(id)` e sem `CHECK` em `tier`/`status`, apesar dos critérios de aceite originais pedirem
  isso. O usuário pediu para tratar especificamente esse ponto.
- Antes de escrever a migration, li os valores reais do código (não suposição):
  `Tier.java` (`full_screen_alarm`, `discreet`), `DispatchStatuses.java` (`QUEUED`, `DELIVERED`,
  `NO_SESSION`, `ACKNOWLEDGED`, `ESCALATED`, `FAILED`), `Feedback.java` (`NAO_AVALIADO`, `REAL`,
  `FALSO_POSITIVO`), e confirmei em `EventConfirmationService` que `feedback`/`confirmado_por`/
  `confirmado_at` são sempre escritos juntos na mesma transação.
- Verifiquei os 5 schemas de tenant do dev (`t_emp_dev` + 4 outros) antes de aplicar: zero linhas
  violando as novas constraints.
- Criada `V0503__evnt_referential_and_value_constraints.sql`: FK `report_idempotency.evento_id` e
  `notification_dispatch.evento_id` → `evento(id) ON DELETE CASCADE`; `CHECK` em `evento.feedback`;
  `CHECK` composto garantindo que `confirmado_por`/`confirmado_at` andam junto com o feedback;
  `CHECK` em `notification_dispatch.tier` e `.status`.
- **Achado ao rodar a suíte com a nova migration:** `NotificationDispatchSweepOrderingIT` (4
  métodos) inseria `notification_dispatch` com `eventoId(UUID.randomUUID())` sem `evento`
  correspondente — violava a FK nova. Não era um bug de produção (o teste nunca precisou de um
  evento real porque não havia FK), mas expôs um teste que não refletia a invariante real do
  schema. Corrigido: o teste agora cria um `Evento` de verdade por linha de dispatch
  (`criarEvento()`), e o `@BeforeEach` limpa `notification_dispatch` antes de `evento`.
- Aplicado ao dev local via `docker compose up migrations-global migrations-empresa` (rebuild do
  jar antes — `gradlew :services:neuroassist-ai-migrations:bootJar`): os 6 schemas convergiram para
  v0503/0800 sem erro.
- Reverificado: `./gradlew :services:evnt-service:test` → 126/126 (era 126/126 antes também, mas
  agora com a constraint realmente testada), `architectureCheck` verde.

