# Plano FE — E2E (neuroassist-ai-web)

**Projeto:** NeuroAssist AI · **Módulo:** E2E (transversal — sem lib Nx própria)
**Stack:** Angular 21, Nx, Signals-first (sem NgRx) · **Playwright** 1.60
**Backends:** os 7 microserviços via GWY (host `:18080`) · **Gerado em:** 2026-08-20
**Revalidado em:** 2026-08-20 contra a integração BE em andamento (`E2E-001` DONE)

> Módulo transversal: opera sobre `libs/{gest,evnt,pac,acs,edge,hist,dash}`,
> `libs/shared/*` e `apps/{neuroassist-ai-web,*-e2e}`. Não cria lib nova.
> Aceita apenas Pipeline B (`/fe-autopilot E2E`).

## Status das prioridades

- P1 -- pendente
- P2 -- pendente
- P3 -- pendente
- P4 -- pendente
- P5 -- pendente
- P6 -- pendente
- P7 -- pendente

---

## 0. Leitura executiva do estado atual

### Papel do módulo FE

E2E é o último ponto de corte do sistema: o browser. Os 7 microserviços já validaram
os seus pontos de corte entre si (`docs/architecture/backend/plans/plan-e2e-integration.md`).
Este módulo valida que o `neuroassist-ai-web` opera contra eles de verdade — JWT emitido
pelo ACS, roteamento via GWY, propagação de `X-Empresa-Id` / `X-Correlation-Id` e
comportamento correto de degradação em cada superfície.

### Estado atual observado

Levantamento direto do código em 2026-08-20:

- `app.config.ts` declara os 7 módulos com `{ adapter: 'mock' }`. **Nenhuma** chamada HTTP real.
- Os 15 `*HttpAdapter` existem e compilam, mas nunca foram exercitados contra um backend
  (todos carregam `TODO[INT-FE-<MOD>-NNN]: wire once <svc> is deployed`).
- `provideHttpClient(withInterceptors([]))` — array vazio.
- `shared/access-http-base` e `shared/auth-session` são stubs de 2 linhas (Wave-0, D-328/FE-4).
- Não existe `proxy.conf.json` nem arquivos de `environment`.
- Suíte Playwright: **44 testes ativos** (edge 18, gest 14, pac 12 — todos contra mocks),
  **58 `test.skip()`** (acs 45, hist 13) e **8 `describe` vazios** (dash 3, evnt 5).

### Estado do backend (revalidação de 2026-08-20)

BE `E2E-001` está **DONE**: INF-01 (8 registros no Eureka), INF-02 (`/api/v1/<svc>/actuator/health`
→ `200` nos 7) e INF-03 (`/oauth2/jwks` via GWY, 1 chave RSA). Isso muda três premissas
deste plano:

1. **O GWY agora roteia os 7 serviços** (`application-docker.yml`, perfil `docker`), não
   mais só `/api/v1/edge/**`.
2. **O GWY mudou de porta no host: `18080`**, não `8080` (o container continua em 8080).
   `evnt` foi para `18085` e `dash` para `18090` — irrelevante para o FE, que fala só com o GWY.
3. **O issuer foi corrigido**: o GWY ganhou `jwk-set-uri: ${ACS_JWK_URI:...:8088/oauth2/jwks}`
   e o compose define `ACS_JWK_URI` + `KEYCLOAK_ISSUER_URI` apontando para o ACS.

### ⚠️ Achado da revalidação: 4 das 7 rotas do GWY não alcançam o controller

O GWY normalizou o roteamento para `/api/v1/<svc>/**`, **sem filtro de rewrite** nas rotas de
API (só as rotas de *health* têm `StripPrefix=3`). Como o path é encaminhado inalterado, ele
precisa bater com o `@RequestMapping` do serviço — e em 4 casos não bate:

| Serviço | Rota no GWY | `@RequestMapping` real | Alcança? |
|---|---|---|---|
| ACS | `/api/v1/acs/**` | `/api/v1/acs/{tenants,caregivers,directory}` | ✅ |
| HIST | `/api/v1/hist/**` | `/api/v1/hist/*` | ✅ |
| EDGE | `/api/v1/edge/**` | `/api/v1/edge/{devices,heartbeat}` | ✅ |
| PAC | `/api/v1/pac/**` | `/api/pac/v1/pacientes` | ❌ 404 no serviço |
| EVNT | `/api/v1/evnt/**` | `/api/v1/events`, `/api/v1/reports` | ❌ 404 no serviço |
| DASH | `/api/v1/dash/**` | `/panel`, `/internal/notifications` | ❌ 404 no serviço |
| GEST | `/api/v1/gest/**` | `/gest-service/*` | ❌ 404 no serviço |

Nenhum serviço declara `server.servlet.context-path` (verificado nos 7), então não há
prefixo implícito que reconcilie a diferença.

**Por que INF-02 passou mesmo assim:** as rotas de health têm `StripPrefix=3`, então
`/api/v1/pac/actuator/health` vira `/actuator/health` e responde `200`. O check prova que o
GWY resolve o serviço no Eureka — **não** prova que as rotas de API alcançam os controllers.
São predicados diferentes.

**Decisão necessária antes de P2** (é do backend, não do FE):

- **(a) Normalizar** — o GWY ganha `RewritePath` por serviço (`/api/v1/pac/**` → `/api/pac/v1/**`,
  `/api/v1/evnt/**` → `/api/v1/events`, `/api/v1/dash/**` → `/panel`, `/api/v1/gest/**` →
  `/gest-service/**`) e o FE passa a chamar os paths normalizados. Superfície pública uniforme;
  exige mudar 4 dos 15 adapters do FE.
- **(b) Espelhar** — o GWY ganha rotas com os paths reais (`/api/pac/v1/**`, `/api/v1/events/**`,
  `/panel/**`, `/gest-service/**`). Zero mudança no FE; mantém a API pública inconsistente.

**Recomendação: (a)** — é o que a normalização já iniciada no `application-docker.yml` indica
como intenção, e resolve de uma vez o problema de o proxy do FE precisar de 9 prefixos distintos
(passaria a precisar de 2: `/api/v1/**` e `/oauth2/**`).

### Continuidade da última rodada

Primeira rodada do módulo. Não há estado em ES. O insumo é o levantamento acima, revalidado
contra a integração BE em andamento.

### Diagnóstico executivo

A diferença estrutural em relação ao plano BE: lá a integração já existia e precisava ser
*verificada*; aqui ela **ainda não existe**. Por isso as prioridades são metade
**habilitação** (P1–P3) e metade **validação** (P4–P7).

Os base paths dos adapters do FE batem com os `@RequestMapping` reais dos 7 serviços — ou
seja, **em acesso direto ao serviço** eles estão corretos. O que a revalidação mostrou é que
isso não basta: com o GWY no caminho, o que importa é o path *do gateway*, e ele diverge em
4 serviços (ver acima). Rota voltou a ser risco; *shape* continua sendo (P3).

### Multi-empresa: interceptor `@neuroassist/shared/access-http-base` configurado?

**Não.** É o gap mais crítico do módulo e a razão de P1 existir. O arquivo inteiro é:

```ts
export const ACCESS_HTTP_BASE = '/api';
```

Nenhum interceptor injeta `X-Empresa-Id` hoje. Todos os `*HttpAdapter` documentam no
cabeçalho que o interceptor faria isso — o contrato está escrito e nunca foi implementado.
Enquanto P1 não fechar, **nenhuma** chamada real é multi-empresa-safe.

---

## 1. Principais achados

### Pontos fortes

- O switch `'mock' | 'http'` já está implementado nos 7 `provide<Module>()` — o flip é
  configuração, não reescrita.
- Base paths dos adapters batem com os `@RequestMapping` dos 7 serviços (acesso direto).
- Stack BE de pé e validado: `E2E-001` DONE — Eureka, roteamento e JWK Set verificados.
- GWY já roteia os 7 serviços e expõe `/oauth2/**` sem autenticação.
- Os `playwright.config.ts` já leem `baseURL: process.env['APP_URL'] ?? 'http://localhost:4200'`,
  o que permite apontar a suíte para um app integrado sem tocar em código de teste.
- `docker/docker-compose.dev.yml` já sobe os 9 componentes com healthcheck.
- 44 testes E2E verdes e 3 specs de tratamento de erro (`backend-error-handling`) servindo de modelo.

### Gaps

| # | Gap | Evidência |
|---|---|---|
| G1 | Sem interceptor HTTP (auth, empresa, correlation, idempotência, erro) | `withInterceptors([])` |
| G2 | Sem sessão de autenticação (login, storage, refresh, logout) | `auth-session` stub de 2 linhas |
| G3 | Sem transporte: nem proxy, nem environment, nem configuração `integrated` | `find` → vazio |
| G4 | GEST aponta direto para `:8084`, fora do GWY | `gest-service.config.ts` |
| G5 | 58 `test.skip()` com asserção vazia (`toHaveURL(/.*/)`) | acs-e2e, hist-e2e |
| G6 | 8 `describe` vazios que passam sem testar nada | dash-e2e, evnt-e2e |
| G7 | SSE do DASH nunca exercitado — só simulado por mock | `dash-notifications-http.adapter.ts` |
| G8 | 4 rotas de API do GWY não alcançam o controller (PAC, EVNT, DASH, GEST) | `application-docker.yml` sem `RewritePath` vs `@RequestMapping` |

Gaps encerrados pela revalidação de 2026-08-20:

| # | Gap anterior | Situação |
|---|---|---|
| ~~G0a~~ | GWY roteava apenas `/api/v1/edge/**` | **Resolvido** — os 7 têm rota (`application-docker.yml`) |
| ~~G0b~~ | Issuer apontando para Keycloak inexistente | **Resolvido no compose** — `jwk-set-uri` + `ACS_JWK_URI`/`KEYCLOAK_ISSUER_URI` → ACS |

### Riscos

| Risco | Impacto | Mitigação |
|---|---|---|
| **G8** — 4 rotas do GWY caem em `404` do serviço | P2 e P3 falham de forma confusa: o gateway resolve, o serviço nega | Fechar a decisão (a)/(b) com o BE **antes** de iniciar P2 |
| INF-02 valida health, não API — pode dar falsa confiança de que o roteamento está pronto | Começar P2 sobre premissa falsa | Critério de aceite de P2 exige `200` num endpoint de **negócio** de cada serviço, não em `/actuator/health` |
| Fora do compose, `issuer-uri` ainda cai no default Keycloak | `401` sem causa óbvia ao rodar o GWY solto | Subir sempre pelo compose; pedir renomeação `KEYCLOAK_ISSUER_URI` → `ACS_ISSUER_URI` |
| Porta do GWY mudou para `18080` no host | Proxy do FE apontando para `8080` falha em silêncio | Porta parametrizada no `proxy.conf.json` (P2), não hard-coded |
| Flip `mock`→`http` quebrar os 44 testes verdes | Perda da suíte hermética | P2 entrega o flip como **configuração**, nunca substituição |
| `EventSource` não envia headers customizados | P6 (SSE) sem auth possível | Decisão conjunta com BE antes de implementar |
| Remover `.skip` sem trocar a asserção | Teste verde que não testa nada | Critério de aceite de P5 proíbe `toHaveURL(/.*/)` |
| `getUserMedia` em CI headless | 3 specs de câmera do GEST | Permanecem mock-only; documentado, não é lacuna |

### Aderência às regras Nx (boundaries, tax-map, naming)

- `shared/*` **não pode** importar `scope:<módulo>` — o interceptor de P1 consome
  `empresa-context` e `auth-session`, ambos `shared`. Direção legal.
- As alterações de P2 concentram-se em `apps/neuroassist-ai-web` (scope:web), que pode
  depender de todos os módulos. Direção legal.
- Nenhuma prioridade cria lib nova → nenhum generator Nx obrigatório neste plano.
- Specs E2E vivem em `apps/*-e2e` com `implicitDependencies` já declaradas.

---

## 2. Estratégia geral

### Objetivo macro

Levar o `neuroassist-ai-web` de 100% mock a um app que roda contra os 7 backends reais,
com auth funcional e degradação honesta, **sem perder** a suíte hermética que hoje roda
sem Docker em todo PR.

Princípio que atravessa o plano: **perfil duplo**. `mock` continua o default (CI a cada PR);
`integrated` é uma configuração Nx adicional (nightly / pré-release). O modo é resolvido
por token de DI, nunca por edição de arquivo.

### Ordem das prioridades

| P | Título | Motivo | Issues cobertas |
|---|---|---|---|
| P1 | Fundação HTTP: interceptor + sessão | Sem headers e sem token nada real funciona; é o único P1 sem dependência externa | FE-E2E-002, FE-E2E-003 |
| P2 | Transporte e flip de adapter | Habilita a chamada sair do browser e chegar ao serviço | FE-E2E-001, FE-E2E-004, FE-E2E-006 |
| P3 | Paridade de contrato mock ↔ http | Verificação mais barata do plano; pega drift antes de investir em UI | FE-E2E-005 |
| P4 | Seed determinístico + global-setup | Todo teste integrado depende de dados e sessão previsíveis | FE-E2E-016 |
| P5 | Suítes de módulo: skips e describes vazios | Zera o débito de 58 skips + 8 describes vazios | FE-E2E-007, 008, 009, 010 |
| P6 | SSE real + fluxos ponta-a-ponta críticos | É o fluxo que justifica o produto | FE-E2E-011, 013, 014 |
| P7 | Degradação, fluxos secundários e CI | Fecha o gate e torna a verificação contínua | FE-E2E-012, 015, 017 |

---

## 3. Plano por prioridade

### Prioridade P1 -- Fundação HTTP: interceptor + sessão de autenticação

Status: pendente
Issues cobertas: FE-E2E-002, FE-E2E-003
Categoria: ESTRUTURAL

#### Objetivo / Problema / Resultado esperado

**Objetivo:** transformar `shared/access-http-base` e `shared/auth-session` de stubs em
infraestrutura real de transporte autenticado.

**Problema:** os dois libs são placeholders de 2 linhas e o `HttpClient` não tem interceptor.
Nenhuma chamada real sai com `Authorization` nem com `X-Empresa-Id`.

**Resultado esperado:** qualquer `*HttpAdapter` dos 7 módulos, quando ativado, emite request
autenticada, tenant-scoped e rastreável, com erro RFC 7807 já normalizado.

#### Problemas identificados

1. `libs/shared/access-http-base/src/lib/access-http-base.ts` — só `export const ACCESS_HTTP_BASE = '/api'`.
2. `libs/shared/auth-session/src/lib/auth-session.ts` — só `interface AuthSession { token }`.
3. `app.config.ts` — `provideHttpClient(withInterceptors([]))`.
4. ~~Blocker externo do issuer~~ — **resolvido na revalidação de 2026-08-20**: o GWY ganhou
   `jwk-set-uri: ${ACS_JWK_URI:http://localhost:8088/oauth2/jwks}` e o compose define
   `ACS_JWK_URI` + `KEYCLOAK_ISSUER_URI` apontando para o `acs-service`. `/oauth2/**` também
   passou a ser `permitAll` no `SecurityConfig` do GWY. Ressalva: fora do compose o
   `issuer-uri` ainda cai no default Keycloak — subir sempre pelo compose.

**P1 não tem mais dependência externa bloqueante.** É a prioridade a iniciar.

#### Correções mandatórias

- Interceptor funcional (Angular 21) registrado em `withInterceptors([...])`.
- `AuthSessionService` com signals, login/refresh/logout contra o ACS (`@RequestMapping("/oauth2")`).
- Refresh **single-flight**: um único refresh em voo; requests concorrentes enfileiradas.
- `403` **não** dispara refresh (só `401`).
- Isenção de `Authorization` nos endpoints públicos: `POST /oauth2/token` e
  `POST /api/v1/edge/devices/enroll` (usa `enrollmentToken` — ver `application.yml` do GWY).

#### Arquivos-alvo

```
frontend/libs/shared/access-http-base/src/lib/access-http.interceptor.ts        (novo)
frontend/libs/shared/access-http-base/src/lib/access-http-base.ts               (reescrever)
frontend/libs/shared/access-http-base/src/index.ts                              (exportar)
frontend/libs/shared/auth-session/src/lib/auth-session.service.ts               (novo)
frontend/libs/shared/auth-session/src/lib/auth-session.ts                       (reescrever)
frontend/libs/shared/auth-session/src/index.ts                                  (exportar)
frontend/apps/neuroassist-ai-web/src/app/app.config.ts                          (registrar)
```

#### Contrato de tipo

```ts
// @neuroassist/shared/access-http-base
export const ACCESS_HTTP_BASE = new InjectionToken<string>('ACCESS_HTTP_BASE');
export const accessHttpInterceptor: HttpInterceptorFn;

// @neuroassist/shared/auth-session
export interface AuthTokens { accessToken: string; refreshToken: string; expiresAt: number; }
export interface AuthClaims  { sub: string; empresaId: string; scopes: readonly string[]; }

export class AuthSessionService {
  readonly isAuthenticated: Signal<boolean>;
  readonly claims:          Signal<AuthClaims | null>;
  readonly accessToken:     Signal<string | null>;
  login(username: string, password: string): Promise<void>;
  refresh(): Promise<void>;   // single-flight
  logout(): void;
}
```

#### Sequência de execução

1. `data-access`: `AuthSessionService` (login, storage, refresh, logout).
2. `data-access`: `accessHttpInterceptor` consumindo a sessão + `empresa-context`.
3. `wiring`: registrar em `app.config.ts` (`withInterceptors`).
4. `testes`: unit com `HttpTestingController` — obrigatório.

#### Generators Nx obrigatórios

Nenhum — as duas libs já existem.

#### Política de testes

Unit obrigatório (`HttpTestingController`). Sem e2e nesta prioridade: não há transporte
ainda (P2) para exercitar ponta-a-ponta.

#### Multi-empresa

**É o núcleo desta prioridade.** O interceptor injeta `X-Empresa-Id` a partir do signal de
`@neuroassist/shared/empresa-context`. Teste obrigatório: request sem empresa ativa **não**
pode sair sem o header — deve falhar explicitamente, nunca silenciosamente.

#### Critérios de aceite

```bash
npx nx test shared-access-http-base     # PASS
npx nx test shared-auth-session         # PASS
npx nx lint shared-access-http-base     # PASS
npx nx lint shared-auth-session         # PASS
npx nx build neuroassist-ai-web         # PASS
npx nx run-many --target=test --all     # PASS (sem regressão nos 7 módulos)
```

- `Authorization`, `X-Empresa-Id`, `X-Correlation-Id` presentes em request de cada um dos 7 módulos.
- `401` → 1 refresh + 1 replay; `403` → 0 refresh.
- 3 requests concorrentes durante refresh → exatamente 1 `POST /oauth2/token/refresh`.
- `ProblemDetail` mapeado por `shared/error-model`.

#### Riscos

- **Storage do token.** É produto de saúde sob LGPD — a escolha (`sessionStorage` vs memória +
  refresh em cookie httpOnly) precisa ser justificada por escrito, não assumida.
- **Onde fica a tela de login?** Hoje só existe `/entrada`, que é a entrada de marca do DASH,
  não um formulário de auth. Definir antes de implementar.
- Claims do ACS (`hist:read`, `hist:read:all`) precisam ser mapeados para `shared/access-decision` —
  confirmar os nomes exatos emitidos.

#### Instrução para implementador

Comece por `AuthSessionService` (o interceptor depende dele). Não invente os claims: leia o
que o `acs-service` emite de fato antes de tipar `AuthClaims`. O single-flight do refresh é o
ponto mais fácil de errar — teste com 3 requests concorrentes antes de considerar pronto.

---

### Prioridade P2 -- Transporte: proxy, configuração `integrated` e flip de adapter

Status: pendente
Issues cobertas: FE-E2E-001, FE-E2E-004, FE-E2E-006
Categoria: WIRING

#### Objetivo / Problema / Resultado esperado

**Objetivo:** fazer a request sair do browser e chegar ao microserviço, com o modo de adapter
resolvido por configuração.

**Problema:** não há proxy, não há configuração `integrated`, e o GEST usa URL absoluta que
nenhum proxy alcança.

**Resultado esperado:** `nx serve -c integrated` emite HTTP real; `nx serve` continua em mock
sem backend nenhum.

#### Problemas identificados

1. Sem `proxy.conf.json`; sem `environment*`.
2. `serve` só tem `development` e `production`, ambas em mock.
3. `GEST_SERVICE_BASE_URL` → `http://<host>:8084`, absoluta, fora do GWY.
4. **Externo (G8):** 4 rotas de API do GWY não alcançam o controller — PAC, EVNT, DASH e GEST
   respondem `404` porque o gateway encaminha `/api/v1/<svc>/**` sem rewrite.
5. **Externo:** o GWY escuta em `18080` no host, não `8080`.

#### Pré-condição bloqueante

A decisão **(a) normalizar** vs **(b) espelhar** (ver §0) precisa estar fechada com o backend
antes de escrever o proxy. Ela determina se o FE muda 4 adapters ou nenhum — e escrever o
proxy antes significa reescrevê-lo depois.

O restante desta prioridade assume a recomendação **(a)**.

#### Correções mandatórias

- Proxy com **2 prefixos** (na hipótese (a), o GWY passa a expor superfície uniforme):

```
/api/v1/**   →  http://localhost:${GWY_PORT:-18080}
/oauth2/**   →  http://localhost:${GWY_PORT:-18080}
```

  Na hipótese (b), voltam a ser 9 prefixos — mais frágil, e é o argumento a favor de (a).

- Porta do GWY **parametrizada**, nunca hard-coded: já mudou de `8080` para `18080` uma vez.
- Token `APP_ADAPTER_MODE: 'mock' | 'http'`; `app.config.ts` deriva os 7 `provide<Module>()` dele.
- Na hipótese (a), **4 adapters mudam de base path**:

| Módulo | Base path hoje | Base path normalizado |
|---|---|---|
| PAC | `/api/pac/v1/pacientes` | `/api/v1/pac/pacientes` |
| EVNT | `/api/v1/events` | `/api/v1/evnt/events` |
| DASH | `/panel`, `/panel/notifications` | `/api/v1/dash/panel...` |
| GEST | `<abs>:8084` + `/gest-service/eligibility` | `/api/v1/gest/eligibility` |

  ACS, HIST e EDGE ficam inalterados.

- `GEST_SERVICE_BASE_URL` = `''` (URL relativa) no perfil `integrated`, por override de provider —
  **não** editando o factory default.
- **Não tocar** em `EDGE_BRIDGE_WS_URL`: a ponte WS do edge-agent local nunca passa pelo GWY, por design.

#### Arquivos-alvo

```
frontend/apps/neuroassist-ai-web/proxy.conf.json                    (novo)
frontend/apps/neuroassist-ai-web/project.json                       (configuration: integrated)
frontend/apps/neuroassist-ai-web/src/app/app.config.ts              (APP_ADAPTER_MODE)
frontend/apps/neuroassist-ai-web/src/app/config/adapter-mode.ts     (novo — token)
frontend/libs/gest/src/lib/api/eligibility-http.adapter.spec.ts     (caso base-url vazia)
frontend/apps/neuroassist-ai-web/README.md                          (documentar os 2 perfis)
```

Somente na hipótese (a) — normalização de base path:

```
frontend/libs/pac/src/lib/api/{paciente,comando,tipos-gesto-read}-http.adapter.ts
frontend/libs/evnt/src/lib/api/evnt-client-http.adapter.ts
frontend/libs/dash/src/lib/api/{dash-summary,dash-notifications}-http.adapter.ts
frontend/libs/gest/src/lib/api/{eligibility,edge-detection}-http.adapter.ts
```

#### Contrato de tipo

```ts
export type AdapterMode = 'mock' | 'http';
export const APP_ADAPTER_MODE = new InjectionToken<AdapterMode>('APP_ADAPTER_MODE', {
  providedIn: 'root', factory: () => 'mock',      // default seguro
});
```

#### Sequência de execução

1. `wiring`: `proxy.conf.json` + `configuration: integrated` no `serve`/`build`.
2. `wiring`: token `APP_ADAPTER_MODE` + derivação dos 7 `provide<Module>()`.
3. `data-access`: override do `GEST_SERVICE_BASE_URL` no perfil integrated.
4. `testes`: unit do gest adapter; e2e de fumaça nos dois perfis.

#### Generators Nx obrigatórios

Nenhum.

#### Política de testes

Unit obrigatório (adapter GEST). E2E de fumaça obrigatório: **toca o fluxo principal** —
provar que os 44 testes continuam verdes no perfil `mock` é critério de aceite desta prioridade.

#### Multi-empresa

Herda o interceptor de P1. Nada novo aqui, mas o smoke test do perfil `integrated` deve
confirmar `X-Empresa-Id` na request real.

#### Critérios de aceite

```bash
npx nx serve neuroassist-ai-web                      # mock, sem backend, sobe
npx nx serve neuroassist-ai-web -c integrated        # http, requests reais
npx nx test gest                                     # PASS
npx nx run-many --target=test --all                  # PASS
npx nx run-many --target=lint --all                  # PASS
npx nx e2e edge-e2e && npx nx e2e gest-e2e && npx nx e2e pac-e2e   # 44/44 verdes (perfil mock)
```

**TRN-03 revisado — health não conta.** O critério é `200` (ou `401`/`403`, nunca `404`) num
endpoint de **negócio** de cada um dos 7 serviços através do GWY:

```bash
GWY=http://localhost:${GWY_PORT:-18080}
curl -si $GWY/api/v1/acs/tenants/x       # ACS
curl -si $GWY/api/v1/pac/pacientes       # PAC   ← hoje 404 (G8)
curl -si $GWY/api/v1/evnt/events         # EVNT  ← hoje 404 (G8)
curl -si $GWY/api/v1/hist/history        # HIST
curl -si $GWY/api/v1/edge/devices        # EDGE
curl -si $GWY/api/v1/dash/panel          # DASH  ← hoje 404 (G8)
curl -si $GWY/api/v1/gest/eligibility    # GEST  ← hoje 404 (G8)
```

`404` reprova a prioridade, mesmo com `/actuator/health` verde.

#### Riscos

- **Dependência externa bloqueante:** a decisão (a)/(b) sobre G8. `E2E-001` está DONE, mas
  cobriu Eureka + health + JWK Set — **não** a alcançabilidade das rotas de API.
- Ordem de flip sugerida, do menos ao mais acoplado, para isolar falhas:
  `HIST → ACS → EDGE → PAC → GEST → EVNT → DASH`. Os 3 primeiros são justamente os que já
  alcançam o controller hoje — dão sinal verde real antes de mexer nos 4 afetados por G8.

#### Instrução para implementador

O critério que não pode ser negociado: `nx serve` sem flag continua funcionando **sem Docker**.
Se em algum momento os 44 testes precisarem de backend para passar, a abordagem está errada —
volte para o token de DI.

---

### Prioridade P3 -- Paridade de contrato mock ↔ http

Status: pendente
Issues cobertas: FE-E2E-005
Categoria: VALIDACAO

#### Objetivo / Problema / Resultado esperado

**Objetivo:** provar que cada `*MockAdapter` e o seu `*HttpAdapter` par satisfazem o mesmo
tipo de port.

**Problema:** o compilador garante que o adapter satisfaz a interface; ninguém garante que o
**JSON que o backend devolve** satisfaz essa interface.

**Resultado esperado:** drift de contrato detectado antes de qualquer investimento em specs de UI.

#### Problemas identificados

Os 15 HTTP adapters nunca receberam uma resposta real. Gap G2 de `INT-FE-HIST-001` continua
aberto: não se sabe se `rotulo` / `urgencia` vêm resolvidos no read model do HIST ou se exigem
client-join.

#### Correções mandatórias

Um teste por port, parametrizado pelo adapter:

```ts
describe.each([['mock', MockAdapter], ['http', HttpAdapter]])('%s satisfaz o port', ...)
```

O braço `http` roda apenas no perfil `integrated`; o braço `mock` roda em todo PR.

#### Arquivos-alvo

```
frontend/libs/acs/src/lib/api/*.contract.spec.ts     (3 ports)
frontend/libs/pac/src/lib/api/*.contract.spec.ts     (3 ports)
frontend/libs/evnt/src/lib/api/*.contract.spec.ts    (1 port)
frontend/libs/hist/src/lib/api/*.contract.spec.ts    (2 ports — resolve G2)
frontend/libs/edge/src/lib/api/*.contract.spec.ts    (2 ports)
frontend/libs/dash/src/lib/api/*.contract.spec.ts    (2 ports)
frontend/libs/gest/src/lib/api/*.contract.spec.ts    (2 ports)
```

#### Contrato de tipo

Nenhum tipo novo. Esta prioridade **verifica** os tipos existentes dos ports.
Se um tipo precisar mudar, isso é resultado da prioridade, não premissa.

#### Sequência de execução

1. `testes`: specs de contrato dos 7 módulos (15 ports).
2. Executar o braço `http` contra o stack Docker.
3. Registrar divergências como issue de contrato.

#### Generators Nx obrigatórios

Nenhum.

#### Política de testes

Unit obrigatório. Sem e2e — esta prioridade é inteiramente de contrato.

#### Multi-empresa

Indireto: o braço `http` só funciona com o interceptor de P1 ativo, o que valida o
`X-Empresa-Id` de ponta a ponta como efeito colateral.

#### Critérios de aceite

```bash
npx nx run-many --target=test --all                  # PASS (braço mock)
# perfil integrated, com stack Docker no ar:
npx nx run-many --target=test --all --configuration=integrated   # PASS (braço http)
```

- CTR-01..07 cobertos (15 ports).
- Gap G2 de `INT-FE-HIST-001` **respondido** e registrado.

#### Riscos

**Divergência de shape é bug de contrato, não de UI.** Se o backend devolve algo diferente do
port, a correção é decidida entre BE e FE. Adaptar o tipo do FE em silêncio esconde o problema
e quebra a paridade com o mock.

#### Instrução para implementador

Comece por HIST (CTR-04): é o módulo verificador de todos os fluxos das prioridades seguintes
e é onde o gap G2 está aberto. Se o shape do HIST divergir, P6 inteira é afetada.

---

### Prioridade P4 -- Seed determinístico e global-setup do Playwright

Status: pendente
Issues cobertas: FE-E2E-016
Categoria: ESTRUTURAL

#### Objetivo / Problema / Resultado esperado

**Objetivo:** dar ao perfil `integrated` dados previsíveis e uma sessão autenticada reutilizável.

**Problema:** no perfil `integrated` os testes compartilham um PostgreSQL real. Sem seed,
passam a depender da ordem de execução — e todos os configs têm `fullyParallel: true`.

**Resultado esperado:** suíte integrada determinística e rápida (login uma vez, não por teste).

#### Problemas identificados

1. `fullyParallel: true` em todos os `playwright.config.ts` → interferência entre specs.
2. Login por UI em cada teste custa segundos × 100+ testes.
3. `retries: CI ? 2 : 0` mascara flakiness causada por dados compartilhados.

#### Correções mandatórias

- Seed idempotente: 1 tenant, 2 cuidadoras (papéis distintos: `hist:read` e `hist:read:all`),
  2 pacientes, 1 device pareado + 1 com código expirado, comandos publicados.
- `globalSetup` do Playwright: login uma vez, `storageState` por papel.
- Estratégia de isolamento decidida: reset por suíte **ou** namespacing por tenant.

#### Arquivos-alvo

```
frontend/apps/*-e2e/playwright.config.ts            (globalSetup + storageState)
frontend/e2e-support/global-setup.ts                (novo)
frontend/e2e-support/seed.ts                        (novo)
docker/seed-e2e.sql                                 (novo, ou script equivalente)
```

#### Contrato de tipo

```ts
export interface SeededTenant   { empresaId: string; nome: string; }
export interface SeededCaregiver{ id: string; username: string; password: string;
                                  scopes: readonly string[]; storageStatePath: string; }
export interface SeedFixture    { tenant: SeededTenant; caregivers: readonly SeededCaregiver[];
                                  patients: readonly { id: string; deviceId: string | null }[]; }
```

#### Sequência de execução

1. Script de seed idempotente.
2. `globalSetup` emitindo `POST /oauth2/token` e persistindo `storageState`.
3. `wiring`: apontar os 7 `playwright.config.ts` para o setup (perfil integrated apenas).
4. `testes`: rodar a suíte duas vezes seguidas — resultado idêntico.

#### Generators Nx obrigatórios

Nenhum.

#### Política de testes

A própria prioridade é infraestrutura de teste. Critério: rodar o seed duas vezes não duplica
nem falha.

#### Multi-empresa

O seed cria **um** tenant e os dois papéis dentro dele. Um segundo tenant é desejável para
provar isolamento — mas isso é escopo de P7, não daqui.

#### Critérios de aceite

```bash
# duas execuções seguidas, mesmo resultado:
npx nx run-many --target=e2e --all   # PASS
npx nx run-many --target=e2e --all   # PASS (idêntico)
```

- `storageState` gerado para os dois papéis.
- Perfil `mock` **não** usa nada disto — continua hermético e sem setup.

#### Riscos

Se um teste do perfil `integrated` só passa no retry, o problema é o seed, não o teste.
Reavaliar `fullyParallel` no perfil integrated (pode ter de ser `workers: 1`).

#### Instrução para implementador

Não reutilize o seed no perfil `mock`. O valor da suíte mock é justamente não depender de nada.

---

### Prioridade P5 -- Suítes de módulo: zerar skips e describes vazios

Status: pendente
Issues cobertas: FE-E2E-007, FE-E2E-008, FE-E2E-009, FE-E2E-010
Categoria: FUNCIONAL

#### Objetivo / Problema / Resultado esperado

**Objetivo:** eliminar o débito de 58 `test.skip()` e 8 `describe` vazios.

**Problema:** ACS (45) e HIST (13) têm stubs gerados nunca implementados; DASH (3) e EVNT (5)
têm `describe` **vazios** que passam sem testar nada.

**Resultado esperado:** zero skips, zero describes vazios, zero asserções vazias.

#### Problemas identificados

| Suíte | Situação |
|---|---|
| `acs-e2e` | 45 skips com `await expect(page).toHaveURL(/.*/)` — asserção que passa contra qualquer coisa |
| `hist-e2e` | 13 skips idem |
| `dash-e2e` | 3 `describe` vazios (`0 scenarios` no cabeçalho gerado) |
| `evnt-e2e` | 5 `describe` vazios |

DASH e EVNT exigem **passo zero**: auditar se as features BDD correspondentes existem e têm
cenários. Se não têm, os cenários precisam ser derivados da spec do módulo — isso é trabalho
de especificação, não de teste.

#### Correções mandatórias

- Nenhum `test.skip()` remanescente.
- Nenhuma asserção `toHaveURL(/.*/)` remanescente.
- `data-testid` adicionados aos componentes onde faltarem.
- Cenários de EVNT que não podem faltar: `confirmacao-concorrente` (dois cuidadores, duas
  `browserContext`) e `alarme-emergencia-preempcao` (outlet global do shell, não rota).

#### Arquivos-alvo

```
frontend/apps/acs-e2e/src/{tenant-onboarding,caregiver-roster,patient-linkage}.spec.ts
frontend/apps/hist-e2e/src/{history-timeline,feedback-verdict}.spec.ts
frontend/apps/dash-e2e/src/{home-status-calm,notification-delivery,calibration-writethrough}.spec.ts
frontend/apps/evnt-e2e/src/{notificacao-rotina,confirmar-atendimento,confirmacao-concorrente,
                            alarme-emergencia-preempcao,entrega-degradada}.spec.ts
frontend/libs/{acs,hist,dash,evnt}/src/lib/feature-*/           (data-testid)
```

#### Contrato de tipo

Nenhum tipo novo — a prioridade consome os contratos validados em P3.

#### Sequência de execução

1. Auditar features BDD de DASH e EVNT (existem? têm cenários?).
2. `feature-*`: adicionar `data-testid` faltantes.
3. `testes`: ACS (45) → HIST (13) → DASH → EVNT.
4. Rodar nos dois perfis.

#### Generators Nx obrigatórios

Nenhum.

#### Política de testes

E2E obrigatório — é o objeto da prioridade. Unit inalterado.

#### Multi-empresa

`acs-e2e/tenant-onboarding` cobre provisionamento de empresa e é onde o `X-Empresa-Id` real
é exercitado pela primeira vez ponta-a-ponta.

#### Critérios de aceite

```bash
npx nx e2e acs-e2e && npx nx e2e hist-e2e && npx nx e2e dash-e2e && npx nx e2e evnt-e2e
grep -r "test.skip(" frontend/apps/*-e2e/src/      # sem resultado
grep -r "toHaveURL(/.\*/)" frontend/apps/*-e2e/src/ # sem resultado
```

#### Riscos

Um `test.skip` com asserção vazia **passa** ao remover o `.skip`. Fica verde sem testar nada —
pior que skipped, porque some do radar. Mesma armadilha nos `describe` vazios, que já estão
"verdes" hoje.

#### Instrução para implementador

Toda remoção de `.skip` exige asserção real no mesmo commit. Se o cenário não puder ser
asserido ainda, deixe o `.skip` e registre o porquê — é mais honesto que um verde falso.

---

### Prioridade P6 -- SSE real e fluxos ponta-a-ponta críticos

Status: pendente
Issues cobertas: FE-E2E-011, FE-E2E-013, FE-E2E-014
Categoria: FUNCIONAL

#### Objetivo / Problema / Resultado esperado

**Objetivo:** provar pelo browser os dois fluxos que definem o produto — onboarding completo
e gesto → alarme → confirmação.

**Problema:** o transporte SSE do DASH nunca foi exercitado; o push é simulado pelo mock.

**Resultado esperado:** o alarme chega ao browser por SSE real, é confirmado, e fica auditado
no HIST.

#### Problemas identificados

1. `dash-notifications-http.adapter.ts` existe, mas o push real nunca rodou. É a maior lacuna
   de risco do plano: todo o resto do sistema existe para alimentar esse push.
2. `EventSource` **não** envia headers customizados — o interceptor de P1 não se aplica ao SSE.

#### Correções mandatórias

Decidir e registrar o mecanismo de auth do canal SSE. Opções, com o trade-off:

| Opção | Custo |
|---|---|
| Token em query param | Registra JWT em log de acesso — ruim para dados de saúde/LGPD |
| Cookie de sessão httpOnly | Exige mudança no `dash-service` |
| `fetch` + `ReadableStream` | Permite headers; abandona `EventSource` |
| WebSocket | Maior mudança; afeta o contrato do BE |

**Esta decisão envolve o backend — não é do FE sozinho.** ADR se mudar o contrato.

#### Arquivos-alvo

```
frontend/libs/dash/src/lib/api/dash-notifications-http.adapter.ts   (transporte SSE real)
frontend/libs/dash/src/lib/api/dash-notifications.port.ts           (se o contrato mudar)
frontend/apps/dash-e2e/src/notification-delivery.spec.ts            (SSE-01..06)
frontend/apps/neuroassist-ai-web/src/app/shell/app-shell.component.ts (outlet do alarme)
frontend/apps/acs-e2e/src/tenant-onboarding.spec.ts                 (FE-Flow-01)
frontend/apps/evnt-e2e/src/confirmar-atendimento.spec.ts            (FE-Flow-03)
```

#### Contrato de tipo

```ts
export interface AlarmPush {
  dispatchId: string; eventId: string; patientId: string;
  urgencia: 'rotina' | 'emergencia'; rotulo: string; emittedAt: string;
}
export interface NotificationStream {
  connect(): void; disconnect(): void;
  readonly pushes:  Signal<readonly AlarmPush[]>;
  readonly status:  Signal<'connecting' | 'open' | 'closed' | 'degraded'>;
}
```

#### Sequência de execução

1. Decisão de auth do canal SSE (com o BE) — **bloqueia o resto**.
2. `data-access`: transporte SSE real + reconexão com backoff.
3. `feature-*`: outlet de alarme consumindo o stream.
4. `testes`: SSE-01..06 → FE-Flow-01 → FE-Flow-03.

#### Generators Nx obrigatórios

Nenhum.

#### Política de testes

E2E obrigatório — **toca o fluxo principal do produto**. Unit obrigatório para reconexão,
idempotência de `dispatchId` e teardown.

#### Multi-empresa

O canal SSE precisa ser tenant-scoped. Um cuidador **não pode** receber push de outro tenant —
teste obrigatório com os dois tenants (ver P4/P7).

#### Critérios de aceite

```bash
npx nx test dash                    # PASS (reconexão, idempotência, teardown)
npx nx e2e dash-e2e                 # SSE-01..06 PASS
npx nx e2e acs-e2e                  # FE-Flow-01 PASS
npx nx e2e evnt-e2e                 # FE-Flow-03 PASS
```

- FE-Flow-01: os 5 passos do wizard concluem; `/historico` mostra o `ComandoPublicado` real.
- FE-Flow-03: alarme por SSE real; confirmação persiste; **2 fatos** em HIST; `feedback = real`.
- Latência entre o report e o push medida e registrada.

#### Riscos

Este é o único fluxo em que **atraso é falha**, mesmo com todos os passos retornando 2xx.
Uma regressão de latência não aparece como teste vermelho — por isso a medição é critério de aceite.

#### Instrução para implementador

Não comece a implementar o SSE antes da decisão de auth estar fechada com o backend. Refazer o
transporte depois custa mais do que esperar. E teste o teardown: `gest-e2e/camera-lifecycle-teardown.spec.ts`
já é precedente de vazamento de recurso neste projeto.

---

### Prioridade P7 -- Degradação, fluxos secundários e CI contínuo

Status: pendente
Issues cobertas: FE-E2E-012, FE-E2E-015, FE-E2E-017
Categoria: VALIDACAO

#### Objetivo / Problema / Resultado esperado

**Objetivo:** garantir que a UI conta a verdade quando o backend degrada, fechar os dois fluxos
secundários e tornar a verificação contínua.

**Problema:** o backend já prova que degrada corretamente (BE Fases 3/5/7); ninguém provou que
a UI comunica isso.

**Resultado esperado:** nenhum estado de erro renderizado como estado vazio; nightly integrado no ar.

#### Problemas identificados

Este é um produto de tecnologia assistiva para pessoas com ALS/MND. Uma UI que mostra
"nenhum evento" quando o serviço está fora, ou um chip "online" quando o dispositivo está
degradado, não é um bug de UX — é falha de segurança do paciente.

#### Correções mandatórias

| ID | Backend | Expectativa na UI |
|---|---|---|
| DEG-01 | HIST `503 scope-unavailable` | banner de indisponibilidade; **nunca** lista vazia |
| DEG-02 | `profilePointerDegraded: true` | chip degradado, não "online" |
| DEG-03 | liveness `offline` | chip offline + última vista |
| DEG-04 | liveness `304` | sem re-render, sem flicker |
| DEG-05 | DASH `no_open_session` | notificação não some silenciosamente |
| DEG-06 | PAC `422` device inválido | erro no campo, não toast genérico |
| DEG-07 | connection refused | estado retryable; app não trava |
| DEG-08 | `ProblemDetail` | mapeado; nunca JSON cru |

Mais FE-Flow-02 (pareamento + conectividade) e FE-Flow-04 (LGPD: PII ausente, UI sobrevive a
campos anonimizados).

#### Arquivos-alvo

```
frontend/libs/shared/error-model/src/lib/                        (mapeamento PT-BR)
frontend/libs/shared/ui-feedback/src/lib/                        (banners/estados)
frontend/apps/{edge,gest}-e2e/src/backend-error-handling.spec.ts (modelo existente)
frontend/apps/*-e2e/src/*degrada*.spec.ts
.github/workflows/e2e-integrated.yml                             (novo)
.gitignore                                                       (playwright-report)
```

#### Contrato de tipo

```ts
export type SurfaceState<T> =
  | { kind: 'loading' }
  | { kind: 'loaded';      data: T }
  | { kind: 'empty' }                                   // não há dados
  | { kind: 'unavailable'; reason: string; retryable: boolean }   // não foi possível saber
  | { kind: 'degraded';    data: T; reason: string };
```

**`empty` e `unavailable` são estados distintos.** Colapsá-los é exatamente o bug que esta
prioridade existe para impedir.

#### Sequência de execução

1. `data-access`: `SurfaceState` distinguindo `empty` de `unavailable`.
2. `feature-*`: auditar **todas** as telas com lista.
3. `testes`: DEG-01..08 no perfil mock (via `page.route`) e os reproduzíveis no integrated
   (derrubando o serviço no compose).
4. `wiring`: workflow nightly.

#### Generators Nx obrigatórios

Nenhum.

#### Política de testes

E2E obrigatório. Cada cenário forçado de duas formas: `page.route` (determinístico, todo PR)
e derrubando o serviço real (prova o comportamento de verdade).

#### Multi-empresa

Fechamento: teste de isolamento com **dois** tenants — cuidador do tenant A não vê nada do
tenant B em nenhuma superfície. Complementa o seed de P4.

#### Critérios de aceite

```bash
npx nx run-many --target=e2e --all      # PASS (perfil mock)
npx nx run-many --target=e2e --all      # PASS (perfil integrated, stack no ar)
npx nx run-many --target=lint --all     # PASS
```

- DEG-01..08 cobertos; DEG-01/02/03/07 reproduzidos derrubando o serviço real.
- Nenhum cenário de erro renderiza estado vazio (auditoria de todos os `empty state`).
- Nenhum JSON cru ou stack trace visível na UI.
- FE-Flow-02 e FE-Flow-04 passando ou com workaround documentado.
- Job de PR **não** regride: continua sem Docker.

#### Riscos

- Crypto-shredding no backend não apaga o que o browser já guardou: auditar signal stores e
  storage por PII de paciente apagado (FE-Flow-04).
- `frontend/playwright-report/index.html` (504 KB, gerado) está **versionado** e fora do
  `.gitignore` — vai crescer a cada rodada do CI.

#### Instrução para implementador

Regra de ouro: **estado vazio ≠ estado de erro**. Toda tela com lista precisa distinguir
"não há dados" de "não foi possível saber se há dados". Comece pela auditoria das telas
existentes — provavelmente várias colapsam os dois hoje.

---

## 4. Dependências externas

### Endpoints BE consumidos

O FE fala **apenas com o GWY** (host `:18080`). As portas de serviço estão aqui só para
diagnóstico; nenhum adapter deve apontar direto para elas.

| Módulo | Rota no GWY | `@RequestMapping` no serviço | Alcança hoje? | Porta (diag.) |
|---|---|---|---|---|
| ACS | `/api/v1/acs/**` + `/oauth2/**` | `/api/v1/acs/{tenants,caregivers,directory}`, `/oauth2` | ✅ | 8088 |
| HIST | `/api/v1/hist/**` | `/api/v1/hist/{history,audit-facts,audit-records,patients}` | ✅ | 8089 |
| EDGE | `/api/v1/edge/**` | `/api/v1/edge/{devices,heartbeat}` | ✅ | 8087 |
| PAC | `/api/v1/pac/**` | `/api/pac/v1/pacientes` | ❌ G8 | 8082 |
| EVNT | `/api/v1/evnt/**` | `/api/v1/events`, `/api/v1/reports` | ❌ G8 | 18085 |
| DASH | `/api/v1/dash/**` | `/panel`, `/internal/notifications` | ❌ G8 | 18090 |
| GEST | `/api/v1/gest/**` | `/gest-service/{eligibility,detection-profile}` | ❌ G8 | 8084 |

Rotas de *health* (`/api/v1/<svc>/actuator/**`, com `StripPrefix=3`) funcionam nos 7 — é o
que `INF-02` validou. As rotas de **API** não têm rewrite, daí o G8.

### Issues BE — estado revalidado

| Issue BE | Estado | Efeito aqui |
|---|---|---|
| `E2E-001` | ✅ DONE | Desbloqueou o roteamento base e o JWK Set. **Não** cobriu alcançabilidade das rotas de API (G8) |
| `E2E-002` | OPEN | P1 pode começar: o `jwk-set-uri` já aponta para o ACS no compose |

Nenhum issue BE bloqueia P1 hoje. **P2 está bloqueada pela decisão (a)/(b) sobre G8** —
que ainda não tem issue BE própria e talvez precise de uma.

### Decisões pendentes com o backend

1. **G8 — normalizar (a) ou espelhar (b) as rotas do GWY.** Bloqueia P2. Recomendação: (a).
2. Mecanismo de auth do canal SSE (P6) — afeta o `dash-service`, não só o FE.
3. Claims exatos emitidos pelo ACS (`hist:read`, `hist:read:all`) → `shared/access-decision`.
4. Gap G2 de `INT-FE-HIST-001`: `rotulo` / `urgencia` resolvidos server-side ou client-join?
5. Renomear `KEYCLOAK_ISSUER_URI` → `ACS_ISSUER_URI` com default no ACS (higiene; não bloqueia).

### Contratos de tipo dependentes

`INT-FE-HIST-001..006` já mapeiam a integração com o `hist-service` e devem ser reconciliados
com os resultados de P3 (CTR-04).

---

## 5. Restrições arquiteturais

- **Nx boundaries:** `shared/*` não importa `scope:<módulo>`. O interceptor de P1 consome
  apenas `auth-session`, `empresa-context` e `error-model` — todos `shared`. Direção legal.
- **scope:web** (`apps/neuroassist-ai-web`) pode depender de todos os módulos; é onde vivem
  o token `APP_ADAPTER_MODE` e as composições de página (P2).
- **PAC nunca importa scope:gest** (GEST-INT-005 / ADR-002) — a composição do live-test vive
  em `scope:web`, como já ocorre em `pac-comando-editor-page.component.ts`. P5 e P6 não podem
  quebrar isso.
- **Nenhuma lib nova** neste plano → nenhum generator Nx obrigatório.
- `apps/*-e2e` mantêm `implicitDependencies` para a lib do módulo correspondente.
- Adapters mock e http **coexistem** permanentemente. O mock não é andaime descartável: é a
  suíte hermética do CI de PR.

---

## 6. Referências

| Documento | Uso |
|---|---|
| `docs/architecture/backend/plans/plan-e2e-integration.md` | Plano espelho; fases BE e os 4 flows |
| `docs/architecture/frontend/issues/FE-E2E-001..017.md` | Issues detalhadas por prioridade |
| `docs/architecture/backend/issues/E2E-001..013.md` | Issues BE, incluindo as 2 bloqueantes |
| `docs/architecture/frontend/issues/INT-FE-HIST-001..006.md` | Integração HIST pré-existente (gap G2) |
| `docs/architecture/frontend/standards/nx-boundaries.md` | Tags, direções permitidas, ESLint |
| `docs/architecture/frontend/standards/access-layer-contracts.md` | `AuthSession`, `AccessDecision` |
| `docs/architecture/shared/blueprint-core.md` | Modelo mental app + módulos + shared |
| `docker/docker-compose.dev.yml` | Stack de 9 componentes do perfil `integrated` |
| `backend/services/neuroassist-ai-api-gateway/src/main/resources/application.yml` | Rotas e issuer do GWY |
