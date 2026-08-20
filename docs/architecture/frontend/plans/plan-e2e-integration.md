# Plano de Validação E2E — Integração Frontend ↔ Backend

> **Scope:** `neuroassist-ai-web` + 7 libs de módulo + 10 libs shared + 7 apps `*-e2e` (Playwright)
> **Espelho de:** `docs/architecture/backend/plans/plan-e2e-integration.md`
> **Status:** PLANEJADO — backend PRODUCTION-READY (7/7); frontend 100% em `adapter: 'mock'`
> **Criado em:** 2026-08-20
> **Autores:** fe-autopilot / equipe TechLab IA

---

## 1. Objetivo

O backend já validou os seus pontos de corte serviço-a-serviço. Este plano cobre o **último ponto de corte
do sistema**: o browser. Validar que o `neuroassist-ai-web` — hoje inteiramente servido por mock adapters —
opera contra os 7 microserviços reais, com JWT emitido pelo ACS, roteamento via GWY, propagação de
`X-Empresa-Id` / `X-Correlation-Id`, e comportamento correto de erro/degradação em cada superfície.

A diferença estrutural em relação ao plano BE: lá a integração já existia e precisava ser *verificada*.
Aqui a integração **ainda não existe** — os `*HttpAdapter` são stubs compiláveis nunca exercitados contra
um backend. Portanto o plano é metade **habilitação** (Fases 0–2) e metade **validação** (Fases 3–5).

---

## 2. Estado real do frontend (levantado em 2026-08-20)

### 2.1 Wiring de adapters

`apps/neuroassist-ai-web/src/app/app.config.ts` declara os 7 módulos com `{ adapter: 'mock' }`.
Cada `provide<Module>()` já implementa o switch `'mock' | 'http'` e os `*HttpAdapter` existem e compilam:

| Módulo | HTTP adapters presentes | Base path no adapter | Base path no controller BE | Alinhado? |
|---|---|---|---|---|
| ACS | `acs-tenant`, `acs-cuidador`, `acs-link` | `/api/v1/acs/tenants` | `/api/v1/acs/tenants`, `/api/v1/acs/caregivers`, `/api/v1/acs/directory` | ✅ |
| PAC | `paciente`, `comando`, `tipos-gesto-read` | `/api/pac/v1/pacientes` | `/api/pac/v1/pacientes` | ✅ |
| EVNT | `evnt-client` | `/api/v1/events` | `/api/v1/events`, `/api/v1/reports` | ✅ |
| HIST | `hist-client`, `hist-feedback` | `/api/v1/hist` | `/api/v1/hist/*` | ✅ |
| EDGE | `device`, `pairing` | (por rota) | `/api/v1/edge/devices`, `/api/v1/edge/heartbeat` | ✅ |
| DASH | `dash-summary`, `dash-notifications` | `/panel`, `/panel/notifications` | `/panel`, `/internal/notifications` | ✅ |
| GEST | `eligibility`, `edge-detection` | `GEST_SERVICE_BASE_URL` → `http://<host>:8084` | `/gest-service/eligibility` | ⚠️ bypassa GWY |

**Conclusão:** os paths estão corretos — o backend não precisa mudar. Mas eles **não são uniformes**
(`/api/pac/v1/...`, `/api/v1/events`, `/panel`, `/gest-service/...`), o que impede uma regra única
`/api/v1/<svc>/**` no gateway. As rotas do GWY terão de ser escritas por serviço.

### 2.2 Blockers de habilitação identificados

| # | Blocker | Evidência | Dono |
|---|---|---|---|
| B1 | GWY roteia **apenas** `/api/v1/edge/**`. Os outros 6 serviços não têm rota. | `neuroassist-ai-api-gateway/src/main/resources/application.yml` — uma única `edge-service-route` | BE (E2E-001) |
| B2 | ⚠️ *mitigado, não resolvido:* o `application.yml` do GWY tem default `KEYCLOAK_ISSUER_URI: http://localhost:8180/realms/neuroassist-ai`. O `docker-compose.dev.yml` sobrescreve para `http://acs-service:8088` (correto), mas fora do compose o default aponta para um Keycloak inexistente. Nome da variável induz a erro. | `application.yml` (`resourceserver.jwt.issuer-uri`) vs `docker/docker-compose.dev.yml` (env do gateway) | BE (E2E-002) |
| B3 | `shared/access-http-base` é um stub de 2 linhas (`export const ACCESS_HTTP_BASE = '/api'`). Não existe interceptor. | `libs/shared/access-http-base/src/lib/access-http-base.ts` | FE (FE-E2E-002) |
| B4 | `shared/auth-session` é um stub de 2 linhas (`interface AuthSession { token }`). Não há login, storage nem refresh. | `libs/shared/auth-session/src/lib/auth-session.ts` | FE (FE-E2E-003) |
| B5 | `provideHttpClient(withInterceptors([]))` — nenhum interceptor registrado. | `app.config.ts` | FE (FE-E2E-002) |
| B6 | Não existe `proxy.conf.json` nem arquivos de `environment`. O dev-server não alcança o GWY. | `find . -name "proxy*.json"` → vazio; `find -name "environment*"` → vazio | FE (FE-E2E-001) |
| B7 | GEST usa `GEST_SERVICE_BASE_URL` apontando direto para `:8084`, fora do GWY — quebra CORS e auth centralizada. | `libs/gest/src/lib/api/gest-service.config.ts` | FE (FE-E2E-004) |

### 2.3 Estado das suítes Playwright

| App e2e | `test()` ativos | `test.skip()` | Usa `page.route` (mock) | Situação |
|---|---|---|---|---|
| `acs-e2e` | 0 | 45 | não | stubs gerados, nunca implementados |
| `hist-e2e` | 0 | 13 | não | stubs gerados, nunca implementados |
| `dash-e2e` | 0 | 0 | não | 3 `describe` **vazios** (0 cenários) |
| `evnt-e2e` | 0 | 0 | não | 5 `describe` **vazios** (0 cenários) |
| `edge-e2e` | 18 | 0 | 1 arquivo | implementados contra mock adapter |
| `gest-e2e` | 14 | 0 | não | implementados contra mock adapter |
| `pac-e2e` | 12 | 0 | 3 arquivos | implementados contra mock adapter |

Total: **44 testes ativos** (verdes contra mocks), **58 skipped**, **8 `describe` sem cenário**
em DASH/EVNT. Nenhum teste exercita HTTP real hoje.

Todos os `playwright.config.ts` já leem `baseURL: process.env['APP_URL'] ?? 'http://localhost:4200'`,
o que permite apontar a suíte para um app servido em modo `http` sem alterar código de teste.

---

## 3. Estratégia — dois perfis de execução

A suíte E2E existente é valiosa e **não deve ser descartada** ao integrar. Adotamos dois perfis:

```
PERFIL "mock"  (default, CI a cada PR)
  nx serve neuroassist-ai-web                  → adapter: 'mock'
  nx e2e <mod>-e2e                             → rápido, hermético, sem Docker
  Garante: composição de UI, rotas, guards, estados de erro determinísticos

PERFIL "integrated"  (nightly + pré-release)
  docker compose up (stack BE completo)  +  nx serve -c integrated
                                               → adapter: 'http', proxy → GWY :8080
  APP_URL=... nx e2e <mod>-e2e                 → contratos reais, JWT real, dados reais
  Garante: shape de DTO, auth, roteamento GWY, fail-closed de verdade
```

A escolha do perfil é feita por `configuration` do Nx + token de DI, nunca por edição de arquivo.
É isso que torna o plano reversível: `nx serve` continua funcionando sem backend nenhum.

---

## 4. Fases de Validação

Ordem por dependência: nada downstream é testável antes do transporte e da autenticação.

---

### Fase 0 — Habilitação de Transporte

**Objetivo:** o browser consegue emitir uma request que chega a um microserviço.

| ID | Cenário | Critério |
|---|---|---|
| TRN-01 | `proxy.conf.json` roteia `/api/**`, `/panel/**`, `/oauth2/**`, `/gest-service/**` para `http://localhost:8080` | `nx serve` + `curl :4200/api/v1/hist/history` chega ao GWY |
| TRN-02 | Configuração `integrated` de build/serve existe e ativa `adapter: 'http'` | `nx serve neuroassist-ai-web -c integrated` sobe com HTTP adapters |
| TRN-03 | GWY tem rota para os 7 serviços (não só EDGE) | `GET :8080/api/v1/acs/tenants/x` → 401/404 do ACS, nunca 404 do gateway |
| TRN-04 | CORS permite origem `http://localhost:4200` | preflight `OPTIONS` retorna `Access-Control-Allow-Origin` |
| TRN-05 | GEST consumido via GWY (`GEST_SERVICE_BASE_URL` = `''` no perfil integrated) | request de eligibility sai para `:4200/gest-service/eligibility` |

**Bloqueia:** todas as demais fases. Depende de BE `E2E-001`.

---

### Fase 1 — Auth Backbone no Browser (espelha BE Fase 2)

**Objetivo:** o app obtém, guarda, injeta e renova um JWT emitido pelo ACS.

| ID | Cenário | Critério |
|---|---|---|
| AUTH-FE-01 | Login pela UI emite `POST /oauth2/token` e persiste o par de tokens | `AuthSession.token` populado; token RS256 |
| AUTH-FE-02 | Credencial inválida → `401` renderizado como erro de formulário, sem sessão | nenhum token persistido; mensagem PT-BR |
| AUTH-FE-03 | Interceptor injeta `Authorization: Bearer` em toda chamada de módulo | verificado em PAC, HIST, EVNT, EDGE, DASH, ACS, GEST |
| AUTH-FE-04 | Interceptor injeta `X-Empresa-Id` a partir de `shared/empresa-context` | header presente e igual ao tenant ativo |
| AUTH-FE-05 | Interceptor injeta `X-Correlation-Id` único por request | header presente; formato UUID |
| AUTH-FE-06 | `401` de backend dispara refresh e replay da request original | 1 refresh, 1 replay, usuário não vê erro |
| AUTH-FE-07 | Refresh falho → logout e redirect para `/entrada` | sessão limpa; navegação ocorre |
| AUTH-FE-08 | `403` **não** dispara refresh — renderiza estado sem-permissão | zero chamadas a `/oauth2/token/refresh` |
| AUTH-FE-09 | Requests concorrentes durante refresh são enfileiradas, não multiplicadas | exatamente 1 `POST /oauth2/token/refresh` |

**Depende de:** BE `E2E-002`; resolução do blocker **B2** (ACS vs Keycloak como issuer).

---

### Fase 2 — Contract Fidelity (paridade de shape mock ↔ http)

**Objetivo:** provar que cada `*MockAdapter` e o seu `*HttpAdapter` par produzem o **mesmo tipo de domínio**.
É a verificação mais barata e a que pega drift de contrato mais cedo.

| ID | Módulo | Verificação |
|---|---|---|
| CTR-01 | ACS | `TenantDto`, `CuidadorDto`, `LinkDto` — resposta real satisfaz o tipo do port |
| CTR-02 | PAC | `PacienteDto`, `ComandoDto`, `TipoGestoDto` |
| CTR-03 | EVNT | evento / report shapes |
| CTR-04 | HIST | `{ items, page, size, total }` + `AuditRecordResponse.payload.rotulo/urgencia` (gap G2 do `INT-FE-HIST-001`) |
| CTR-05 | EDGE | device, pairing, liveness (incl. `ETag`) |
| CTR-06 | DASH | summary + notifications |
| CTR-07 | GEST | eligibility response |

**Método:** um teste por port que roda a mesma asserção de shape contra os dois adapters
(mock em Jest; http contra o stack real em nightly). Divergência = issue de contrato, não de UI.

---

### Fase 3 — Superfícies de Módulo em modo integrado

Reexecução das suítes Playwright no perfil `integrated`, com seeds reais.

| ID | Suíte | Ação |
|---|---|---|
| MOD-01 | `pac-e2e` (12 ativos) | desativar `page.route` no perfil integrated; usar dados semeados |
| MOD-02 | `edge-e2e` (18 ativos) | idem; `pairing-code-expired` exige seed com TTL vencido |
| MOD-03 | `gest-e2e` (14 ativos) | specs `camera-*` permanecem mock-only (dependem de `getUserMedia`) |
| MOD-04 | `acs-e2e` (45 skipped) | implementar — é o maior débito da suíte |
| MOD-05 | `hist-e2e` (13 skipped) | implementar |
| MOD-06 | `dash-e2e` (0 cenários) | **escrever do zero** a partir das features BDD |
| MOD-07 | `evnt-e2e` (0 cenários) | **escrever do zero** a partir das features BDD |

---

### Fase 4 — Degradação e Fail-Closed na UI (espelha BE Fases 3/5/7)

O backend já valida que degrada corretamente. Aqui validamos que a UI **conta a verdade ao cuidador**.

| ID | Comportamento do backend | Expectativa na UI |
|---|---|---|
| DEG-01 | HIST `503 scope-unavailable` (ACS down — BE `DIR-03`) | banner "histórico indisponível", nunca lista vazia enganosa |
| DEG-02 | EDGE heartbeat `profilePointerDegraded: true` (BE `HB-03`) | chip de conectividade em estado degradado, não "online" |
| DEG-03 | EDGE liveness `offline` (BE `DSH-04`) | chip offline + timestamp de última vista |
| DEG-04 | EDGE liveness `304 Not Modified` (BE `DSH-05`) | sem re-render, sem flicker |
| DEG-05 | DASH `no_open_session` (BE `NTF-02`) | notificação não desaparece silenciosamente |
| DEG-06 | PAC `422` device inválido (BE `DEV-02`/`DEV-03`) | erro no campo do dispositivo, não toast genérico |
| DEG-07 | GWY/serviço indisponível (connection refused) | estado de erro retryable; app não trava |
| DEG-08 | `ProblemDetail` RFC 7807 | `title`/`detail` mapeados por `shared/error-model`, nunca JSON cru na tela |

---

### Fase 5 — Fluxos Ponta-a-Ponta (espelha BE Fase 9)

Os mesmos 4 flows do plano BE, agora dirigidos pelo browser em vez de por REST client.
São a evidência final de que o sistema funciona como produto.

#### FE-Flow-01: Onboarding completo pela UI  *(espelha BE Flow-01)*

```
/onboarding  →  OnboardingWizardPageComponent
  passo 1  criar empresa            → ACS  POST /api/v1/acs/tenants
  passo 2  cadastrar cuidadora      → ACS  POST /api/v1/acs/caregivers
  passo 3  parear dispositivo       → EDGE POST /api/v1/edge/devices (composto)
  passo 4  cadastrar paciente       → PAC  POST /api/pac/v1/pacientes (valida device no EDGE)
  passo 5  criar comando            → PAC  POST .../comandos  → GEST eligibility + HIST fact
  verificação  /historico           → HIST mostra ComandoPublicado
```

**Gate:** wizard conclui sem erro; `/historico` renderiza o fact real vindo do HIST.

#### FE-Flow-02: Pareamento + conectividade  *(espelha BE Flow-02)*

```
/edge/device  →  fluxo de pairing  →  heartbeat  →  chip de conectividade "online"
                                    →  /historico mostra DispositivoPareado
```

**Gate:** chip reflete o liveness real do EDGE; fact visível no HIST.

#### FE-Flow-03: Alarme → notificação → confirmação  *(espelha BE Flow-03 — o mais crítico)*

```
1. report de gesto injetado no EVNT (fixture de teste)
2. EVNT → ACS directory → DASH dispatch
3. DASH SSE push        → alarme de emergência aparece no shell outlet
4. cuidador confirma na UI → DASH → EVNT callback → EventoConfirmado
5. /historico            → timeline mostra os 2 fatos do evento
```

**Gate:** o alarme chega ao browser por SSE real; a confirmação persiste; 2 fatos em HIST.
**Nota:** exige transporte SSE real — hoje só existe no mock adapter do DASH (ver `FE-E2E-011`).

#### FE-Flow-04: LGPD — apagamento e trilha  *(espelha BE Flow-04)*

```
1. remover paciente na UI (PAC)  → erasure
2. /historico do paciente        → PII ausente, metadados de fato preservados
```

**Gate:** nenhuma PII renderizada; a UI não quebra com campos anonimizados/nulos.

---

## 5. Issues a Criar

| Issue ID | Título | Fase | Prioridade | Depende de |
|---|---|---|---|---|
| FE-E2E-001 | Transporte: `proxy.conf.json` + configuração Nx `integrated` | F0 | P1 | BE E2E-001 |
| FE-E2E-002 | `shared/access-http-base`: interceptor (auth, empresa, correlation, erro) | F0/F1 | P1 | — |
| FE-E2E-003 | `shared/auth-session`: login, storage, refresh rotation, logout | F1 | P1 | BE E2E-002 |
| FE-E2E-004 | GEST via GWY: `GEST_SERVICE_BASE_URL` configurável por perfil | F0 | P1 | FE-E2E-001 |
| FE-E2E-005 | Paridade de contrato mock ↔ http nos 7 módulos | F2 | P1 | FE-E2E-001 |
| FE-E2E-006 | Flip para `adapter: 'http'` por módulo, atrás de configuração | F0 | P1 | 002, 003 |
| FE-E2E-007 | `acs-e2e`: implementar os 45 cenários skipped | F3 | P2 | FE-E2E-006 |
| FE-E2E-008 | `hist-e2e`: implementar os 13 cenários skipped | F3 | P2 | FE-E2E-006 |
| FE-E2E-009 | `dash-e2e`: escrever os cenários (3 describes vazios) | F3 | P2 | FE-E2E-006 |
| FE-E2E-010 | `evnt-e2e`: escrever os cenários (5 describes vazios) | F3 | P2 | FE-E2E-006 |
| FE-E2E-011 | DASH SSE real: transporte de push de alarme fim-a-fim | F5 | P1 | FE-E2E-006 |
| FE-E2E-012 | Degradação e fail-closed na UI (DEG-01..08) | F4 | P2 | FE-E2E-006 |
| FE-E2E-013 | FE-Flow-01 Onboarding ponta-a-ponta pela UI | F5 | P1 | 006, 007 |
| FE-E2E-014 | FE-Flow-03 Alarme → confirmação → auditoria | F5 | P1 | 011, 010 |
| FE-E2E-015 | FE-Flow-02 Pareamento + FE-Flow-04 LGPD | F5 | P3 | FE-E2E-006 |
| FE-E2E-016 | Seed determinístico + `global-setup` Playwright (login uma vez) | F3 | P2 | FE-E2E-003 |
| FE-E2E-017 | CI: job nightly `integrated` com stack Docker completo | — | P2 | 013, 014 |

---

## 6. Gate Criteria (Definição de Pronto)

O módulo de integração E2E frontend está DONE quando:

- [ ] Fase 0 (TRN-01..05): **100%** — bloqueante, nada roda antes
- [ ] Fase 1 (AUTH-FE-01..09): **100%** — bloqueante
- [ ] Fase 2 (CTR-01..07): **100%** — divergência de contrato é bug de release
- [ ] Fase 3: **0 `test.skip` restantes**; DASH e EVNT com cenários reais escritos
- [ ] Fase 4 (DEG-01..08): **≥ 90%**, com issue aberto para o restante
- [ ] FE-Flow-01 e FE-Flow-03: **100%** — são os fluxos de produto
- [ ] FE-Flow-02 e FE-Flow-04: passando ou com workaround documentado
- [ ] Perfil `mock` continua verde (44 testes) — a integração **não pode** custar a suíte hermética
- [ ] Nenhuma regressão nos gates unitários dos 7 libs (`nx run-many --target=test --all`)
- [ ] `nx run-many --target=lint --all` limpo; fronteiras Nx (`nx-boundaries.md`) respeitadas

---

## 7. Ordem de Execução Recomendada

```
P1 — habilitação (sequencial, bloqueante):
  FE-E2E-001 → FE-E2E-002 → FE-E2E-003 → FE-E2E-004 → FE-E2E-006 → FE-E2E-005

P1 — fluxos críticos (após habilitação):
  FE-E2E-011 → FE-E2E-013 → FE-E2E-014

P2 — em paralelo após FE-E2E-006:
  FE-E2E-007 + FE-E2E-008 + FE-E2E-009 + FE-E2E-010 + FE-E2E-012 + FE-E2E-016

P3 — pós-validação principal:
  FE-E2E-015 + FE-E2E-017
```

---

## 8. Riscos

| Risco | Impacto | Mitigação |
|---|---|---|
| **B2** — issuer só correto dentro do compose; default do GWY aponta para Keycloak inexistente | Rodar o FE contra um GWY iniciado fora do compose falha toda a Fase 1 com `401`, sem causa óbvia | Sempre subir o stack pelo `docker-compose.dev.yml`; pedir ao BE a renomeação de `KEYCLOAK_ISSUER_URI` → `ACS_ISSUER_URI` com default para o ACS (E2E-002) |
| **B1** — GWY sem rotas | Fase 0 parada | FE-E2E-001 depende explicitamente de BE E2E-001; não iniciar em paralelo |
| Flip `mock`→`http` quebra os 44 testes verdes | Perda de cobertura hermética | Perfil duplo (§3): `http` é *configuração*, nunca substituição |
| Paths BE não-uniformes | Rotas GWY frágeis, uma por serviço | Documentar a tabela §2.1 como contrato; ADR de normalização **depois** do E2E |
| SSE do DASH nunca exercitado | FE-Flow-03 (fluxo mais crítico do produto) sem prova | FE-E2E-011 é P1 e antecede FE-E2E-014 |
| `getUserMedia` em CI headless | 3 specs de câmera do GEST | Permanecem mock-only; documentado, não é lacuna |
