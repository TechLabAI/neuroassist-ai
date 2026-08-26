# E2E Frontend — Module State
> Module: `frontend/apps/*-e2e/`, `frontend/apps/neuroassist-ai-web/` | Modulo: `E2E` (transversal)
> **Revalidated:** 2026-08-22 (BASELINE)

---

## 0. Visão Geral do Módulo FE

**Módulo transversal** — integração ponta-a-ponta do `neuroassist-ai-web` com os 7 microserviços via GWY.

### Responsabilidades

- Validar que o app funciona contra os 7 backends reais (não só mocks)
- Validar fluxos críticos: onboarding + gesto → alarme → confirmação → auditoria
- Validar degradação (erro, perda de conectividade, timeout)
- Validar multi-empresa: isolamento de dados por tenant

### Sublibsmodule / Estrutura

**Não tem lib Nx própria.** Operam sobre:
- `frontend/apps/*-e2e/` — 7 suítes Playwright (acs, dash, edge, evnt, gest, hist, pac)
- `frontend/apps/neuroassist-ai-web/` — app principal, routing, wiring
- `frontend/libs/shared/*` — interceptor HTTP, sessão, error-model, access-decision, empresa-context
- `frontend/libs/{gest,evnt,pac,acs,edge,hist,dash}/src/lib/api/*-http.adapter.ts` — os 15 adapters

### Issues Ativas

**17 issues mapeadas** (FE-E2E-001..017):
- P1 (Fundação HTTP): FE-E2E-002, FE-E2E-003
- P2 (Transporte): FE-E2E-001, FE-E2E-004, FE-E2E-006
- P3 (Paridade): FE-E2E-005
- P4 (Seed): FE-E2E-016
- P5 (Suítes): FE-E2E-007, FE-E2E-008, FE-E2E-009, FE-E2E-010
- P6 (SSE): FE-E2E-011, FE-E2E-013, FE-E2E-014
- P7 (Degradação): FE-E2E-012, FE-E2E-015, FE-E2E-017

---

## 1. Arquitetura e Estrutura

### Stack (Angular + Nx)

- **Angular:** 18+
- **Nx:** 19+
- **Playwright:** 1.60 (E2E framework)
- **Backend gateway:** GWY em `localhost:18080` (host), 8080 (container)

### Organização de Suítes E2E

7 apps Playwright **paralelos**, um por módulo:

| App | Specs | Tests | Status |
|---|---|---|---|
| `acs-e2e` | 3 | 41 | verde (mock) |
| `dash-e2e` | 6 | 21 | verde (mock) |
| `edge-e2e` | 9 | 52 | 8 skipped (condicional HTTP_MODE) |
| `evnt-e2e` | 5 | 9 | verde (mock) |
| `gest-e2e` | 9 | 14 | verde (mock) |
| `hist-e2e` | 2 | 13 | verde (mock) |
| `pac-e2e` | 3 | 12 | verde (mock) |
| **TOTAL** | **37** | **162** | **154 verde, 8 skipped** |

### Regras Arquiteturais (Nx Boundaries)

- `shared/*` não importa `scope:<módulo>` — e P1 consome apenas `shared` libs ✅
- `scope:web` (apps/neuroassist-ai-web) pode depender de **todos** os módulos ✅
- PAC **não** importa GEST (ADR-002) ✅
- **Nenhuma lib E2E nova** — opera sobre as 7 existentes

### Multi-empresa: Interceptor `@neuroassist/shared/access-http-base`

**Situação:** stub de 2 linhas.
```ts
export const ACCESS_HTTP_BASE = '/api';
```

**Implementação necessária (P1):**
- InjectionToken `ACCESS_HTTP_BASE` → host base (`/api/v1` após P2)
- `accessHttpInterceptor` funcional → injeta `X-Empresa-Id`, `X-Correlation-Id`, `Authorization`
- Teste obrigatório: request sem tenant ativo **falha explicitamente**, nunca silenciosamente

**Status:** ❌ MISSING

---

## 2. Estado Atual da Implementação

### Por Issue

| Issue | Título | Prioridade | Status | Evidência |
|---|---|---|---|---|
| FE-E2E-001 | Transporte: proxy.conf.json + integrated | P2 | OPEN | Não existe proxy, nenhuma config integrated |
| FE-E2E-002 | Fundação: AuthSessionService | P1 | ✅ DONE | AuthSessionService com login/refresh/logout, 12/12 testes PASS, sessionStorage OK |
| FE-E2E-003 | Fundação: accessHttpInterceptor | P1 | ✅ DONE | accessHttpInterceptor implementado, 16/16 testes PASS, registrado em app.config.ts |
| FE-E2E-004 | Adapter mode token + DI | P2 | OPEN | Hardcoded `{ adapter: 'mock' }` em 7 módulos |
| FE-E2E-005 | Contract parity specs | P3 | OPEN | Nenhum spec de contrato escrito |
| FE-E2E-006 | build config integrated | P2 | OPEN | `build` só tem `production` |
| FE-E2E-007 | EVNT SSE: escolher transporte | P6 | OPEN | `openStream()` erra de propósito (TODO[FE-EVNT-007]) |
| FE-E2E-008 | DASH SSE | P6 | OPEN | Idem EVNT |
| FE-E2E-009 | DASH describes vazios | P5 | OPEN | 3 describes vazios |
| FE-E2E-010 | EVNT describes vazios | P5 | OPEN | 5 describes vazios |
| FE-E2E-011 | Alarme ponta-a-ponta | P6 | OPEN | SSE mock, fluxo não exercitado |
| FE-E2E-012 | DEG-01..08 (degradação) | P7 | OPEN | Nenhuma asserção de erro |
| FE-E2E-013 | Flow-01 (onboarding completo) | P6 | OPEN | Specs ausentes ou mock |
| FE-E2E-014 | Flow-03 (gesto → alarme) | P6 | OPEN | Specs ausentes ou mock |
| FE-E2E-015 | Nightly workflow | P7 | OPEN | Não existe `.github/workflows/e2e-integrated.yml` |
| FE-E2E-016 | Seed determinístico | P4 | OPEN | Nenhum globalSetup, nenhum seed SQL |
| FE-E2E-017 | CI contínuo | P7 | OPEN | `playwright-report/` não está em .gitignore |

**Resumo:** 2 DONE / 15 OPEN / 0 PARTIAL / 0 BLOCKED

### Estado das Prioridades (Plan-FE-E2E.md)

Revalidado em 2026-08-22 (VALIDACAO de P1).

| P | Título | Status | Bloqueante | Desbloqueado em |
|---|---|---|---|---|
| P1 | Fundação HTTP | ✅ DONE | ❌ não | 2026-08-22 (implementação + validação) |
| P2 | Transporte | pendente | ❌ não | 2026-08-21 (E2E-014/G8 resolvido) |
| P3 | Paridade contrato | pendente | — | P2 DONE |
| P4 | Seed + global-setup | pendente | — | P2 DONE |
| P5 | Suítes: skips | pendente | — | P3 DONE |
| P6 | SSE + fluxos críticos | pendente | ⚠️ EVNT não pronto | EVNT backend real |
| P7 | Degradação + CI | pendente | — | P6 DONE |

**Decisões resolvidas:**
- ✅ **G8 (rotas do GWY)** — ADR-008, opção (a): `RewritePath` implementado em E2E-014 (2026-08-21)
- ✅ **P1 desbloqueada** — nenhuma dependência externa bloqueante
- ✅ **P2 desbloqueada** — G8 resolvido

---

## 3. Qualidade e Evidências

### Gates (Validação P1 — 2026-08-22)

| Gate | Status | Resultado |
|---|---|---|
| **lint** | ✅ PASS | shared-auth-session + shared-access-http-base |
| **test** | ✅ PASS | 28/28 novos testes (12 auth + 16 interceptor), 0 regressões (9 projetos verificados) |
| **build** | ✅ PASS | neuroassist-ai-web + 8 dependências |
| **e2e** | ✅ 154/162 verde | Suítes verdes, 8 skipped (HTTP_MODE) |

### Cobertura Funcional (Observado após P1)

- **Estrutura:** ✅ 7 apps criadas, 37 specs, 162 testes, wiring DI completo
- **Mocks:** ✅ Adapters mock funcionam (100% das suítes verdes, sem regressão)
- **HTTP real:** ⏳ Bloqueado por P2 (proxy não existe)
- **Interceptor:** ✅ Implementado e registrado (AUTH-FE-03..09, DEG-08 cobertos)
- **Sessão:** ✅ Implementada com signals, login/refresh/logout (AUTH-FE-01/02/07 cobertos)
- **Proxy:** ❌ Não existe (P2)
- **Config integrated:** ❌ Não existe (P2)
- **SSE:** ❌ Mock; transporte não escolhido (P6, bloqueado por EVNT backend)
- **Degradação:** ❌ Nenhum teste de erro (P7)
- **Seed E2E:** ❌ Não existe (P4, bloqueado por P2)

### Conformidade UI (Arch-Conformance)

Não rodado nesta rodada. Expetativas do protocolo VALIDACAO:
- ✅ `standalone: true` componentes (padrão Angular 18+)
- ✅ `ChangeDetectionStrategy.OnPush` (padrão Nx scaffolds)
- ✅ `takeUntilDestroyed()` ou `async` pipe
- ✅ Facades exponem signals `.asReadonly()`
- ✅ Sem `HttpClient` direto em components
- ✅ `loadChildren` via path mappings
- ✅ Sem permissão inline
- ❌ **Multi-empresa:** sem `X-Empresa-Id` real ainda

---

## 4. Gaps Atuais

### Críticos (P1)

1. **Sem interceptor HTTP funcional** — nenhuma chamada real sai com `Authorization`, `X-Empresa-Id`, `X-Correlation-Id`
2. **Sem sessão de autenticação** — `AuthSessionService` não existe; login/refresh/logout não implementados
3. **Sem transporte** — não há proxy, não há config `integrated`, app não alcança o GWY

### Operacionais (P2-P5)

4. **GEST aponta direto para `:8084`** — precisa usar proxy/GWY (G4)
5. **58 test.skip() removidos (antigos)** — hoje 8 skips condicionais (melhoria)
6. **8 describe() vazios** — DASH (3) + EVNT (5)
7. **SSE nunca exercitado** — EVNT `/events/stream` + DASH `/panel/stream` (G7)

### Externos (BE)

8. ✅ **G8 resolvido em 2026-08-21** — E2E-014/ADR-008 implementou `RewritePath`; as 7 rotas alcançam controller

### Decisões Pendentes (Fora do FE)

1. **Mecanismo de auth do canal SSE (P6)** — afeta DASH-service; 3 opções em aberto (FE-EVNT-007)
2. **Renomear `KEYCLOAK_ISSUER_URI` → `ACS_ISSUER_URI`** (higiene)
3. **Gap G2:** `rotulo`/`urgencia` server-side ou client-join? (afeta HIST)

---

## 5. Issues Relacionadas ao Módulo FE

### FE-E2E (Diretas)

```
FE-E2E-001  OPEN  P2  Transporte: proxy.conf.json + integrated
FE-E2E-002  OPEN  P1  Fundação: AuthSessionService
FE-E2E-003  OPEN  P1  Fundação: accessHttpInterceptor
FE-E2E-004  OPEN  P2  Adapter mode token + DI
FE-E2E-005  OPEN  P3  Contract parity specs
FE-E2E-006  OPEN  P2  build config integrated
FE-E2E-007  OPEN  P6  EVNT SSE: transporte
FE-E2E-008  OPEN  P6  DASH SSE
FE-E2E-009  OPEN  P5  DASH describes vazios
FE-E2E-010  OPEN  P5  EVNT describes vazios
FE-E2E-011  OPEN  P6  Alarme ponta-a-ponta
FE-E2E-012  OPEN  P7  Degradação (DEG-01..08)
FE-E2E-013  OPEN  P6  Flow-01 (onboarding)
FE-E2E-014  OPEN  P6  Flow-03 (gesto→alarme)
FE-E2E-015  OPEN  P7  Nightly workflow
FE-E2E-016  OPEN  P4  Seed determinístico
FE-E2E-017  OPEN  P7  CI contínuo + .gitignore
```

### Transversais (Dependências)

**Do backend (bloqueantes resolvidas):**
- ✅ BE `E2E-001` DONE — Eureka, roteamento, JWK Set
- ✅ BE `E2E-002` DONE — JWT validado pelos resource servers (P1 desbloqueada)
- ✅ BE `E2E-014` DONE (2026-08-21) — RewritePath (G8 resolvido, P2 desbloqueada)
- ⏳ BE EVNT — adapters mock, tenant hardcoded `"poc"`, NotificationOrchestration 501 (P6 bloqueada até pronto)
- ⏳ BE DASH — agregador puro, depende do EVNT (P6 bloqueada)

**Do FE (cross-módulo):**
- `@neuroassist/shared/access-http-base` — P1 exige implementação
- `@neuroassist/shared/auth-session` — P1 exige implementação
- `@neuroassist/shared/error-model` — herda de P1, P3, P7
- `@neuroassist/shared/empresa-context` — consumido por interceptor P1
- **Todos os 7 módulos** — precisam ativar HTTP adapters em P2 (via token DI)

---

## 6. Score de Maturidade (0-100)

### Cálculo (Heurística — Validação P1)

- **30 pts: Estrutura** → 30/30 ✅
  - ✅ 7 apps E2E criadas, tags Nx ok, boundaries ok
  - ✅ Wiring de DI completo (P1 finalizado)
  - ✅ Interceptor registrado em app.config.ts
  
- **30 pts: Funcionalidade** → 25/30
  - ✅ 7 adapters mock funcionam, 154/162 testes verdes
  - ✅ Interceptor HTTP implementado (16 testes, AUTH-FE-03..09)
  - ✅ Sessão implementada com signals (12 testes, AUTH-FE-01/02/07)
  - ⏳ Chamadas HTTP reais bloqueadas por P2 (transporte ausente)
  
- **20 pts: Testes (unit + lint)** → 20/20 ✅
  - ✅ 28 testes unitários novos PASS (12 auth + 16 interceptor)
  - ✅ Lint PASS em shared-auth-session + shared-access-http-base
  - ✅ Build PASS em neuroassist-ai-web
  - ✅ 0 regressões (9 projetos verificados)
  
- **10 pts: E2E cenário principal** → 0/10
  - ❌ Nenhum fluxo crítico validado contra backend real (P2 bloqueado)
  - ❌ Transporte não existe

- **10 pts: Observability + a11y + multi-empresa** → 8/10
  - ✅ `X-Empresa-Id` enforcement implementado e testado
  - ✅ `X-Correlation-Id` gerado por request (UUID único)
  - ✅ `ProblemDetail` mapeado por error-model
  - ⚠️ Observability sem instrumentação de latência/métricas

### Score Total: **83 / 100**

**Categoria:** Consistente (60-79)

### Breakdown

```
Estrutura:      30/30 (100%) — Apps criadas, wiring DI completo
Funcionalidade: 25/30 (83%)  — Interceptor + sessão DONE, HTTP real bloqueado por P2
Testes:         20/20 (100%) — 28 testes novos PASS, lint/build PASS, 0 regressões
E2E cenário:     0/10 (0%)   — Bloqueado por P2 (transporte)
Observability:   8/10 (80%)  — Multi-empresa + correlação OK, latência não medida

Total: 83/100 (Consistente)
```

---

## 7. Histórico de Evolução (append-only)

### 2026-08-22 — BASELINE Validator Execution

**Modo:** BASELINE  
**Estado:** Primeira consolidação, ES indisponível  
**Revalidação contra BE:** 2026-08-20 (plano) + 2026-08-21 (E2E-014/G8)

#### Achados

- **7 suítes E2E** (37 specs, 154 testes verdes)
- **100% mock** — nenhuma chamada HTTP real
- **P1-P7 pendentes** — nenhuma prioridade iniciada
- **Interceptor/Sessão** — stubs de 2 linhas (Wave-0)
- **Proxy/config integrated** — não existem
- **G8 resolvido** — E2E-014/ADR-008 (2026-08-21) implementou RewritePath

#### Decisões Resolvidas

- ✅ G8 normalizar (a) vs espelhar (b) → ADR-008 = (a)
- ✅ P1 desbloqueada (nenhuma dependência externa)
- ✅ P2 desbloqueada (G8 resolvido)
- ⏳ P6 depende de BE EVNT produzir notificação real

#### Próximo Checkpoint

Implementação de P1 (Fundação HTTP: interceptor + sessão).

### 2026-08-22 — VALIDACAO de P1 (Modo 3)

**Modo:** VALIDACAO  
**Estado:** Implementação P1 completada, validação de gates realizada  
**Implementador:** fe-implementer-agent  

#### Gates Executados e Passados

- ✅ `npx nx test shared-auth-session` → 12/12 PASS (AUTH-FE-01, 02, 07, 09, storage, TTL)
- ✅ `npx nx test shared-access-http-base` → 16/16 PASS (AUTH-FE-03..09, DEG-08, multi-empresa enforcement)
- ✅ `npx nx lint shared-auth-session` → OK
- ✅ `npx nx lint shared-access-http-base` → OK
- ✅ `npx nx build neuroassist-ai-web` → OK (1 warning não-crítico: RouterLink não utilizado)
- ✅ `npx nx run-many --target=test --all` → 9 projetos, 0 regressões

#### Implementação Confirmada

**FE-E2E-002 (AuthSessionService):**
- ✅ Login contra `/oauth2/token` com POST
- ✅ Claims mapeados: `sub`, `empresaId`, `scopes` baseados em `authorities`
- ✅ Refresh com single-flight (3 requests concorrentes = 1 chamada refresh)
- ✅ Logout limpa storage e signals
- ✅ Storage em sessionStorage (justificado para LGPD)
- ✅ Sessão restaurada ao carregar a página se válida

**FE-E2E-003 (accessHttpInterceptor):**
- ✅ Injeta `Authorization: Bearer <token>` em toda requisição autenticada
- ✅ Injeta `X-Empresa-Id` de `EMPRESA_ID_TOKEN` signal
- ✅ Falha explicitamente com `EMPRESA_REQUIRED` se empresa está null
- ✅ Injeta `X-Correlation-Id` UUID único por request
- ✅ Injeta `Idempotency-Key` em POST/PUT
- ✅ Isenta `/oauth2/**` de Authorization
- ✅ Isenta `/oauth2/**` de X-Empresa-Id
- ✅ Isenta `**/devices/enroll` de Authorization
- ✅ 401 → 1 refresh + 1 replay (single-flight comprovado)
- ✅ 403 → sem refresh (testado explicitamente)
- ✅ ProblemDetail (RFC 7807) mapeado para AppError

**Wiring em app.config.ts:**
- ✅ Registrado `withInterceptors([accessHttpInterceptor, acsHttpInterceptorFn])`
- ✅ `EMPRESA_ID_TOKEN` wired a `AcsEmpresaContextService.empresaId`
- ✅ Interceptor aplicado a todas as requisições

#### Multi-Empresa Enforcement Validado

- ✅ Request sem empresa ativa **falha explicitamente** antes de sair do browser
- ✅ Erro com código `EMPRESA_REQUIRED`
- ✅ Nenhuma requisição HTTP é feita para o servidor
- ✅ Teste: `fails immediately with EMPRESA_REQUIRED error when empresa signal is null`

#### Score de Maturidade (antes/depois)

- Estrutura: 25/30 → 30/30 ✅
- Funcionalidade: 8/30 → 25/30 (+17)
- Testes: 15/20 → 20/20 ✅
- E2E cenário: 0/10 → 0/10 (P2 bloqueado)
- Observability: 0/10 → 8/10 (+8)
- **Total: 48/100 → 83/100 (Parcial → Consistente)**

#### Issues Resolvidas

- ✅ FE-E2E-002 → DONE
- ✅ FE-E2E-003 → DONE

#### Próxima Ação Recomendada

**PLANNER** — Refinar plano-FE-E2E.md para P2 (Transporte).
- P1 está DONE, nenhuma regressão
- P2 pode iniciar (G8 resolvido em 2026-08-21)
- Transporte (proxy + config integrated) é bloqueante para P3-P7

---

## 8. Estado Atual Resumido (para agentes)

```
STRUCTURAL:        OK       — 7 apps, 37 specs, wiring DI completo (P1 DONE)
FUNCTIONAL:        PARTIAL  — Interceptor + sessão implementados, HTTP real bloqueado por P2
INTEGRATION:       PARTIAL  — Transporte (proxy + config) ausente (P2)
OBSERVABILITY:     GOOD     — Correlação + multi-empresa enforcement OK

MULTI-EMPRESA:     OK       — Enforcement implementado e testado; request sem empresa falha explicitamente

BLOCKERS:          NONE     — P1 DONE, G8 resolvida, P2 pronto para começar
EXTERNAL DEPS:     CLOSED   — BE E2E-001/002/014 DONE; EVNT PoC (P6 bloqueada)

SCORE: 83/100 (Consistente) — +35 pontos vs baseline
```

---

## 9. Próxima Atenção Recomendada

### Ação Imediata (Após P1 DONE)

**Modalidade:** PLANNER  
**Motivo:** P1 está DONE; P2 (Transporte) está desbloqueada por G8 resolvido em 2026-08-21

**Próximos passos:**
1. Revalidar plan-FE-E2E.md contra estado real pós-P1 (multi-empresa OK, nenhuma mudança crítica)
2. Confirmar decisão (a)/(b) com backend — P2 base paths dependem disso
3. Gerar issues detalhadas para P2 (proxy, config integrated, adapter mode flip)
4. Iniciar P2 com fe-implement

### Critério de Sucesso (P1 validado)

```bash
✅ npx nx test shared-access-http-base        # 16/16 PASS
✅ npx nx test shared-auth-session            # 12/12 PASS
✅ npx nx build neuroassist-ai-web            # PASS
✅ No regressões (9 projetos)

Gates confirmados:
- ✅ Authorization, X-Empresa-Id, X-Correlation-Id presentes em cada request
- ✅ 401 → 1 refresh + 1 replay (single-flight comprovado)
- ✅ 403 → sem refresh
- ✅ 3 requests concorrentes durante refresh → 1 POST /oauth2/token/refresh
- ✅ Multi-empresa enforcement: request sem empresa falha explicitamente (EMPRESA_REQUIRED)
```

### Bloqueantes Removidos para P2

- ✅ G8 resolvido (E2E-014, ADR-008, 2026-08-21)
- ✅ P1 implementado e validado
- ✅ Nenhuma dependência externa bloqueante

---

END DOCUMENT
