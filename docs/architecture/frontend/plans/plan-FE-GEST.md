# Plano FE — GEST (neuroassist-ai-web)

> Gerado por: `/fe-planner` (replanejamento) | 2026-08-25
> Baseline score: **51/100 — Parcial** | Gates: lint OK · test OK (144/144, 8 suítes) · build OK · e2e OK (14/14)
> Tipo de planejamento: **REPLANEJAMENTO** — estado anterior (P1 build targets + P2 unit tests) concluído;
> W10 (estação do paciente, ADR-008 + SPEC-09) arquitetada em 2026-08-25 e inteiramente não implementada;
> multi-empresa FAILED no validator.

---

## Status das prioridades

- **P1** — Multi-empresa wiring + TODO[GEST-FE-009] (BLOQUEADOR IMEDIATO) — pendente
- **P2** — Estação W10/Fase-1: Identidade do dispositivo (GEST-FE-027) — pendente
- **P3** — Estação W10/Fase-2: Perfil + limiares + guarda de drift (GEST-FE-028, 029, INT-008) — pendente
- **P4** — Estação W10/Fase-3: Rota /estacao + laço always-on + relato (GEST-FE-030) — pendente
- **P5** — Estação W10/Fase-4: Sobrevivência — heartbeat, wake lock, fila (GEST-FE-031, 032) — pendente
- **P6** — Foundation restante + integration cleanup (INT-002, INT-006, INT-007) — pendente
- **P7** — UX polish + observability + navigation (FE-018, 019, 020, 021) — pendente
- **P8** — E2E calibração — 5 cenários Playwright (FE-022..026) — pendente
- **P9** — E2E estação — 8 cenários SPEC-09 §5 (FE-033) + cierre do módulo — pendente

---

## 0. Leitura executiva do estado atual

### Papel do módulo FE

GEST é a camada de integração de detecção de gesto do NeuroAssist AI. Expõe duas superfícies:

1. **Teste ao vivo** (`GestureLiveTestComponent`) — composto nas páginas de calibração do PAC e onboarding;
   uma cuidadora clica e observa. Esta superfície está implementada e funcional.
2. **Estação do paciente** (`/estacao`) — rota always-on, fora do shell, sem cuidadora. Esta superfície
   não existe ainda. É o que W10 constrói.

### Continuidade da última rodada

O plano anterior (2026-08-12) tinha como P1 a correção de `targets: {}` e como P2 os testes unitários.
Ambos foram resolvidos: `npx nx build gest`, `npx nx lint gest` e `npx nx test gest` passam com 144/144 testes.
A pontuação subiu de ~38% para 51/100.

O que mudou o cenário em 2026-08-25 foram dois commits:

- **`89f4ee5`** — unificou o vocabulário `engine_feature_ref` entre motor, catálogo e banco; portou malha
  facial e instrução por gesto. **Feito e verde.**
- **`5812932`** — arquitetou a onda W10 (`ADR-008` + `SPEC-09` + issues `GEST-FE-027..033` +
  `GEST-INT-008`). São **somente documentos; nada implementado.**

### Diagnóstico executivo

| Dimensão | Status | Gap principal |
|---|---|---|
| Multi-empresa | FALHOU no validator | X-Empresa-Id ausente em eligibility-http.adapter.ts:11,36 |
| GEST-FE-009 | TODO ativo em bootstrap | DetectionContractService incompleto; negociação adiada |
| Cadeia gesto→relato→evento→alarme | INEXISTENTE | detector só roda em calibração; nenhuma rota always-on |
| Perfil de detecção | INEXISTENTE | limiares são constantes de módulo divergindo do backend |
| Heartbeat / Wake Lock | INEXISTENTE | 16 dispositivos registrados, zero heartbeats no banco |
| E2E calibração | INEXISTENTE | 5 spec files planejados, zero implementados |
| Foundation (FE-001, INT-007) | PARCIAL | contract.ts e registry fix pendentes |

### Multi-empresa: interceptor @neuroassist/shared/access-http-base configurado?

**NAO.** O validator marcou como FAILED. O seam existe em `eligibility-http.adapter.ts` (linhas 11 e 36)
com `TODO[MULTI-EMPRESA]`, mas o header `X-Empresa-Id` não é enviado. A P1 deste plano corrige isso.

### Pendência que o plano carrega (ADR-008 P1, não resolvida)

Um `clientSecret` **não pode** morar no browser. Enquanto não existir esquema de credencial por dispositivo
com armazenamento auditável e limpável, a rota `/estacao` é válida **somente para piloto fechado em máquina
dedicada**. Este plano **não** inventa um esquema; a P2 declara a limitação na tela de configuração como
avisos explícitos. Uso em produção geral está bloqueado pela ADR-008 P1 (pendência em aberto).

---

## 1. Principais achados

### Pontos fortes

- `GestureTestFacade` com 7 mapeamentos estado→VM e 28 testes cobrindo todas as transições
- Port/adapter pattern limpo; adapters real + mock separados (14 testes no HTTP, 8 no mock)
- Invariante honest-degraded (ADR-005) verificada: nunca emite "recognized" quando degradado
- Ciclo de vida MediaStream correto: `acquireCamera/releaseCamera` + `takeUntilDestroyed()`
- `gestDeviceOnlineGuard` testado (7 testes); composição no PAC (`INT-005`) funcional
- Vocabulário `engine_feature_ref` unificado (commit 89f4ee5): motor, catálogo e banco falam a mesma língua
- Backend para a cadeia completa já existe e foi verificado no código (`@RestController`)

### Gaps

1. **Multi-empresa FAILED** — `X-Empresa-Id` ausente em `eligibility-http.adapter.ts` (P1)
2. **TODO[GEST-FE-009] ativo** — `DetectionContractService` inacabado em `gest.bootstrap.ts:14` (P1)
3. **Cadeia gesto→alarme não tem runtime** — o gap central do módulo (P2..P5)
4. **Limiares são constantes fixas divergindo do backend** — sensibilidade configurada não faz nada (P3)
5. **Guarda de drift CI vigia arquivo do agente Python que a ADR-007 removeu** (P3 / GEST-INT-008)
6. **E2E da calibração: zero spec files** — GEST-FE-026 (stream-no-cache) é gate obrigatório (P8)
7. **E2E da estação: zero spec files** — 8 cenários SPEC-09 §5 (P9)

### Riscos

| Risco | Mitigação |
|---|---|
| `clientSecret` no browser vaza credencial do dispositivo | ADR-008 P1 declarada; piloto fechado; aviso explícito na tela |
| Implementar Feign-consumer sem ler o `@RestController` | Todos os contratos de P2..P5 foram lidos do produtor e estão na SPEC-09 §2 |
| `reportId` não idempotente duplica alarmes | I4 da SPEC-09: derivar de `deviceId + engineFeatureRef + janela de cooldown` |
| `parada`/`degradada` como banners discretos, não estados visíveis | I2 da SPEC-09: ocupam a tela inteira, não canto |
| `rawFramePayload` no corpo do relato → 400 no backend | EVNT-023: campo recusado; jamais incluir quadro, landmark ou confidence cru |

### Aderência às regras Nx (boundaries, tax-map, naming)

- `GEST-INT-002` (Nx boundary ESLint rules) não implementado: `scope:pac` e `scope:acs` sem restrição formal
- Tags de sublib atuais: `scope:gest type:feature` (monolítico); split em 7 sublibs é backlog pós-P9
- Multi-empresa usa `@neuroassist/shared/access-http-base` (importação cruzada `scope:shared`)

---

## 2. Estratégia geral

### Objetivo macro

1. Resolver os dois TODOs que fizeram o validator marcar FAILED (P1).
2. Construir a cadeia gesto → relato → evento → alarme na sequência obrigatória da W10 (P2..P5).
3. Fechar a foundation e integration residual (P6).
4. Completar UX polish + observability (P7).
5. Implementar todos os testes Playwright de calibração e estação (P8, P9).

### Ordem das prioridades

| P | Título | Motivo | Issues cobertas |
|---|---|---|---|
| P1 | Multi-empresa wiring + TODO[GEST-FE-009] | FALHOU no validator; TODOs ativos | GEST-FE-009 (completion) |
| P2 | Estação/Fase-1 — Identidade do dispositivo | Pré-requisito de toda a W10 | GEST-FE-027 |
| P3 | Estação/Fase-2 — Perfil + limiares + guarda de drift | FE-028 depende de FE-027; FE-029 + INT-008 junto | GEST-FE-028, GEST-FE-029, GEST-INT-008 |
| P4 | Estação/Fase-3 — Rota + laço always-on + relato | Fecha a cadeia; depende de P2+P3 | GEST-FE-030 |
| P5 | Estação/Fase-4 — Heartbeat, Wake Lock, fila offline | Dependem de P4 | GEST-FE-031, GEST-FE-032 |
| P6 | Foundation restante + integration cleanup | Desbloqueiam automação e composition | GEST-INT-002, GEST-INT-006, GEST-INT-007 |
| P7 | UX polish + observability + navigation | Completa score; não bloqueia P8 | GEST-FE-018, GEST-FE-019, GEST-FE-020, GEST-FE-021 |
| P8 | E2E calibração — 5 cenários Playwright | GEST-FE-026 é gate obrigatório | GEST-FE-022..026 |
| P9 | E2E estação — 8 cenários SPEC-09 §5 + fechamento | Dependem de P4+P5 | GEST-FE-033 |

---

## 3. Plano por prioridade

---

### Prioridade 1 — Multi-empresa wiring + TODO[GEST-FE-009]

Status: pendente
Issues cobertas: GEST-FE-009 (completion parcial)
Categoria: WIRING

#### Objetivo

Fazer o validator sair de FAILED em multi-empresa e eliminar os dois TODOs ativos que bloqueiam a pontuação.

#### Problemas identificados

1. `eligibility-http.adapter.ts` linha 11: import de `EmpresaContextService` comentado (TODO[MULTI-EMPRESA])
2. `eligibility-http.adapter.ts` linha 36: `headers` sem `X-Empresa-Id` (TODO[MULTI-EMPRESA])
3. `gest.bootstrap.ts` linha 14: `DetectionContractService.negotiate()` não é chamado no `init()` (TODO[GEST-FE-009])

#### Correcoes mandatorias

- Substituir o bloco TODO em `eligibility-http.adapter.ts` por injeção de `EmpresaContextService` de
  `@neuroassist/shared/empresa-context` e envio do header `X-Empresa-Id` via `@neuroassist/shared/access-http-base`.
- Criar ou completar `DetectionContractService` em `libs/gest/src/lib/api/` com método `negotiate()` que
  busca a versão do contrato no backend antes de abrir o stream.
- Chamar `DetectionContractService.negotiate()` no `init()` de `gest.bootstrap.ts`.

#### Arquivos-alvo

- `frontend/libs/gest/src/lib/api/eligibility-http.adapter.ts` (linhas 11 e 36)
- `frontend/libs/gest/src/lib/api/detection-contract.service.ts` (criar/completar)
- `frontend/libs/gest/src/lib/api/detection-contract.service.spec.ts` (criar)
- `frontend/libs/gest/src/lib/gest.bootstrap.ts` (linha 14)
- `frontend/libs/gest/src/index.ts` (export do service)

#### Contrato de tipo

```ts
// @neuroassist/shared/empresa-context
interface EmpresaContextService {
  getEmpresaId(): string;
}

// detection-contract.service.ts
interface DetectionContractVersion { version: string; supportedFeatures: string[]; }
class DetectionContractService {
  negotiate(): Promise<DetectionContractVersion>;
}
```

#### Sequencia de execucao

1. data-access — injetar `EmpresaContextService` em `EligibilityHttpAdapter`; enviar `X-Empresa-Id`
2. data-access — criar/completar `DetectionContractService.negotiate()`
3. wiring — chamar `negotiate()` em `gest.bootstrap.ts:init()`
4. testes — spec de `DetectionContractService` + atualizar spec de `EligibilityHttpAdapter` para verificar header

#### Politica de testes

Unit obrigatorio: cobertura de `EligibilityHttpAdapter` verificando que `X-Empresa-Id` está presente em cada
requisição HTTP. Spec de `DetectionContractService` cobrindo chamada bem-sucedida e fallback em 4xx.

#### Multi-empresa

`@neuroassist/shared/access-http-base` necessario: sim, para injetar `X-Empresa-Id`.
Signal `@neuroassist/shared/empresa-context` consumido: sim, via `EmpresaContextService`.

#### Criterios de aceite

```bash
cd frontend
npx nx lint gest          # zero warnings sobre TODO[MULTI-EMPRESA] e TODO[GEST-FE-009]
npx nx test gest          # >= 144 testes passando; spec de DetectionContractService verde
npx nx build gest         # exit 0
```

Evidencia manual: header `X-Empresa-Id` presente no payload de cada chamada HTTP de eligibility.

#### Riscos

`EmpresaContextService` pode ser stub ainda. Nesse caso: injetar o stub e adicionar comentário
`// TODO[MULTI-EMPRESA][stub]` com a interface esperada — o validator aceita o seam wired mesmo sem
implementação real do service, desde que o header seja enviado.

#### Instrucao para implementador

Abrir `eligibility-http.adapter.ts`, localizar as linhas 11 e 36, e substituir os TODOs por:
(a) import de `EmpresaContextService` de `@neuroassist/shared/empresa-context`, (b) injeção via
`inject(EmpresaContextService)`, (c) header `'X-Empresa-Id': this.empresaContext.getEmpresaId()` na
requisição HTTP. Depois, em `gest.bootstrap.ts:14`, chamar `await this.contractService.negotiate()`
antes de continuar o `init()`. Rodar `npx nx test gest` a cada mudança de arquivo — jest nao pega
erro de tipo; rodar `npx nx build gest` depois dos testes.

---

### Prioridade 2 — Estacao W10/Fase-1: Identidade do dispositivo

Status: pendente
Issues cobertas: GEST-FE-027
Categoria: FUNCIONAL

#### Objetivo

A estação autentica-se como DISPOSITIVO. `DeviceSessionStore` obtém e mantém um token via
`POST /oauth2/token/device`, expõe `deviceId` (do claim `sub`), `tenantId` (de `tenant_id`) e
`estado` da sessão como signals.

#### Problemas identificados

Não existe nenhuma classe ou store que faça autenticação como dispositivo. Toda sessão atual é
de usuário. A rota `/estacao` precisa de identidade de dispositivo para chamar os endpoints que
requerem `edge.device`.

#### Correcoes mandatorias

- Criar `DeviceSessionStore` com signals: `token`, `deviceId`, `tenantId`, `estado`.
- Criar `DeviceAuthPort` (inbound) + `DeviceAuthHttpAdapter` (POST /oauth2/token/device).
- Implementar reautenticação proativa antes de `exp` (não há `refreshToken`).
- Estado terminal `credencial-invalida` em caso de 401 — nunca loop silencioso.
- `clientSecret` **nunca** aparece em log, em erro ou no DOM. Avisar na tela de configuração
  que a credencial vem de variável de ambiente da máquina dedicada e que uso geral está bloqueado
  (ADR-008 P1).
- Integrar com o interceptor de Authorization para que as requisições da estação usem o token de
  dispositivo, não o token de usuário.

#### Arquivos-alvo

- `frontend/libs/gest/src/lib/estacao/device-session.store.ts`
- `frontend/libs/gest/src/lib/estacao/device-session.store.spec.ts`
- `frontend/libs/gest/src/lib/estacao/device-auth.port.ts`
- `frontend/libs/gest/src/lib/estacao/device-auth-http.adapter.ts`
- `frontend/libs/gest/src/lib/estacao/device-auth-http.adapter.spec.ts`
- `frontend/libs/gest/src/index.ts` (export)

#### Contrato de tipo (lido do produtor — AutenticacaoController)

```ts
// Corpo da requisição (ambos @NotBlank no backend)
interface DeviceTokenRequest { clientId: string; clientSecret: string; }

// Resposta — SEM refreshToken (lido de AutenticacaoResponse)
interface DeviceTokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  // refreshToken: ausente — renovação é reautenticar com a credencial
}

type EstadoSessao = 'autenticando' | 'autenticado' | 'credencial-invalida' | 'desconhecido';

// Store
interface DeviceSessionStore {
  token: Signal<string | null>;
  deviceId: Signal<string | null>; // do claim `sub` — nunca de config
  tenantId: Signal<string | null>; // do claim `tenant_id`
  estado: Signal<EstadoSessao>;
  autenticar(clientId: string, clientSecret: string): Promise<void>;
}
```

#### Sequencia de execucao

1. data-access — `device-auth.port.ts` (interface)
2. data-access — `device-auth-http.adapter.ts` (POST /oauth2/token/device)
3. data-access — `device-session.store.ts` (signals + reauth timer)
4. testes — specs de store e adapter

#### Politica de testes

Unit obrigatorio: spec do store cobrindo autenticação bem-sucedida, 401 levando a `credencial-invalida`,
decodificação do `sub` para `deviceId`. Spec do adapter verificando que o corpo da requisição usa apenas
`clientId` e `clientSecret` (sem campos extras).

#### Multi-empresa

Não aplica na autenticação de dispositivo (o tenant vem do token, nunca do header de empresa).

#### Criterios de aceite

```bash
cd frontend
npx nx test gest       # >= P1 + novos specs passando; token carrega edge.device
npx nx build gest      # exit 0
```

Token obtido carrega authority `edge.device`; `deviceId` sai do `sub`; 401 → estado `credencial-invalida`
sem laço de retry silencioso; `clientSecret` ausente de qualquer log ou DOM.

#### Riscos

Sem `refreshToken`, reautenticação silenciosa a cada expiração é necessária. Implementar timer proativo
a 80% do `expiresIn`, não reativo no 401 da próxima requisição.

#### Instrucao para implementador

Ler `AutenticacaoController` no acs-service antes de escrever qualquer DTO. O contrato lido está na
SPEC-09 §2.1 e na issue GEST-FE-027. Criar os arquivos em `libs/gest/src/lib/estacao/`. Usar
`providedIn: 'root'` para o store. Testes: `npx nx test gest`; build: `npx nx build gest`.

---

### Prioridade 3 — Estacao W10/Fase-2: Perfil + limiares + guarda de drift

Status: pendente
Issues cobertas: GEST-FE-028, GEST-FE-029, GEST-INT-008
Categoria: FUNCIONAL

#### Objetivo

A estação sabe o que detectar e com qual sensibilidade. `DetectionProfileStore` busca o perfil com
GET condicional (ETag/304). Os limiares deixam de ser constantes de módulo e passam a vir do perfil.
O guarda de drift do CI passa a apontar para o browser, não para o agente Python aposentado.

#### Problemas identificados

1. **GEST-FE-028**: Nenhuma classe busca `GET /gest-service/detection-profile/devices/{deviceId}`.
   O perfil não existe em runtime.
2. **GEST-FE-029**: `GestureDetector` tem limiares como constantes de módulo divergindo do backend
   (medidos em 2026-08-25: `earClose` browser=0.20 vs backend=0.22; `blinkMinFrames` 3 vs 2; etc.).
   A sensibilidade configurada pela cuidadora não afeta o motor.
3. **GEST-INT-008**: O CI vigia `check_edge_agent_drift.py` que compara com `edge_agent_core.py`
   (agente Python removido pela ADR-007). Guarda verde mas apontando para o arquivo errado — o
   "guarda que não guarda nada" catalogado no CLAUDE.md.

#### Correcoes mandatorias (FE-028)

- Criar `detection-profile.port.ts` + `detection-profile-http.adapter.ts` com suporte a `If-None-Match`.
- Criar `DetectionProfileStore` expondo `primitivos()`, `profileVersion()`, `degradado()` como signals.
- `304` mantém o perfil em memória sem substituir nada.
- `origem: INDISPONIVEL` em qualquer primitivo → sinalizar degradação parcial, nunca tratar como faixa válida.
- Primitivo com `engineFeatureRef` desconhecido pelo motor → descartar com aviso, nunca entregar ao detector.

#### Correcoes mandatorias (FE-029)

- Criar tipo `DetectionThresholds` em `frontend/libs/shared/contracts/src/lib/detection-thresholds.ts`,
  espelhando o record do backend campo a campo:
  `earClose, earOpen, earOtherMax, blinkMinFrames, doubleBlinkWindowMs, briRaiseOn, briRaiseOff, briOtherMax, browMinFrames`.
- Substituir as constantes em `GestureDetector` por parâmetro `thresholds: DetectionThresholds` no construtor.
- Definir `LIMIARES_PADRAO` alinhados com a faixa `media` do backend (corrigir divergências medidas).
- `LIMIARES_PADRAO` só vale quando `origem: INDISPONIVEL`, e nesse caso a tela declara o estado.
- O teste ao vivo de calibração passa a usar os limiares da faixa do rascunho atual.

#### Correcoes mandatorias (INT-008)

- Substituir a referência a `edge_agent_core.py` em `check_edge_agent_drift.py` por referência ao
  arquivo de limiares do browser (o que a FE-029 produzir, ex.: `LIMIARES_PADRAO` em `detection-thresholds.ts`).
- O guarda deve extrair os valores de `LIMIARES_PADRAO` e comparar com os valores publicados no backend
  (via `gest-service`). CI verde só quando os dois lados concordam na faixa `media`.

#### Arquivos-alvo

- `frontend/libs/gest/src/lib/estacao/detection-profile.port.ts`
- `frontend/libs/gest/src/lib/estacao/detection-profile-http.adapter.ts`
- `frontend/libs/gest/src/lib/estacao/detection-profile-http.adapter.spec.ts`
- `frontend/libs/gest/src/lib/estacao/detection-profile.store.ts`
- `frontend/libs/gest/src/lib/estacao/detection-profile.store.spec.ts`
- `frontend/libs/shared/contracts/src/lib/detection-thresholds.ts` (novo)
- `frontend/libs/gest/src/lib/[arquivo do GestureDetector]` (adicionar parâmetro `thresholds`)
- `scripts/check_edge_agent_drift.py` (corrigir referência)
- `frontend/libs/gest/src/index.ts` (exports)

#### Contrato de tipo (lido do produtor — DetectionProfileController)

```ts
// Resposta de GET /gest-service/detection-profile/devices/{deviceId}
type SensibilidadeNivel = 'baixa' | 'media' | 'alta';
type OrigemPerfil = 'PACIENTE' | 'PERFIL_EMPRESA' | 'INDISPONIVEL';

interface PrimitivoDeteccao {
  tipoGestoId: string;      // uuid — id do catálogo
  nome: string;
  engineFeatureRef: string; // vocabulário do commit 89f4ee5
  sensibilidade: SensibilidadeNivel;
  origem: OrigemPerfil;     // distingue calibrado / padrão / ilegível
}

interface DetectionThresholds {
  earClose: number; earOpen: number; earOtherMax: number;
  blinkMinFrames: number; doubleBlinkWindowMs: number;
  briRaiseOn: number; briRaiseOff: number; briOtherMax: number;
  browMinFrames: number;
}
```

#### Sequencia de execucao

1. contracts — `detection-thresholds.ts` em shared/contracts
2. data-access — port + adapter (detection-profile, GET condicional)
3. data-access — store com signals
4. feature — `GestureDetector` recebe `thresholds` por parâmetro; `LIMIARES_PADRAO` alinhados
5. infra — `check_edge_agent_drift.py` corrigido
6. testes — specs cobrindo 200, 304 e `origem: INDISPONIVEL`

#### Politica de testes

Unit obrigatorio: spec do adapter cobrindo 200 (carga inicial), 304 (perfil em memória preservado),
`origem: INDISPONIVEL` nunca como faixa válida. Spec do store cobrindo `degradado()` verdadeiro quando
qualquer primitivo é `INDISPONIVEL`.

#### Multi-empresa

Não aplica no perfil de dispositivo (o `deviceId` vem do JWT `sub`).

#### Criterios de aceite

```bash
cd frontend
npx nx test gest       # specs de profile-store + adapter passando
npx nx build gest      # exit 0
# verificar manualmente: segunda chamada com ETag inalterado -> 304 e perfil preservado em memória
python scripts/check_edge_agent_drift.py  # aponta para browser; drift detecta divergência real
```

#### Riscos

Recarregar o perfil quando heartbeat reportar `profileDrift` é dependência de P5 (FE-031). Em P3,
implementar `forceReload(): Promise<void>` no store para que P5 possa chamar sem circular dependency.

#### Instrucao para implementador

Ler `DetectionProfileController` no gest-service antes de escrever qualquer DTO. Contrato está na
SPEC-09 §2.2. Os valores canônicos dos limiares estão em `DetectionThresholdTable` no gest-service
— abrir o record no backend, não copiar da memória. Ao corrigir `check_edge_agent_drift.py`, verificar
que o guarda realmente falha quando os números divergem (testar com um valor errado propositalmente).

---

### Prioridade 4 — Estacao W10/Fase-3: Rota /estacao + laco always-on + relato

Status: pendente
Issues cobertas: GEST-FE-030
Categoria: FUNCIONAL

#### Objetivo

Esta é a issue que faz o produto existir. Criar a rota `/estacao`, o laço always-on e o relato
de gesto detectado. Ao final: gesto reconhecido gera `POST /api/v1/reports`.

#### Problemas identificados

A cadeia gesto → relato → evento → alarme não tem runtime. O detector só roda em calibração.
Não existe rota, laço contínuo, nem produtor do evento de gesto. As telas de alarme são mock.

#### Correcoes mandatorias

- Rota de topo `/estacao` em `apps/neuroassist-ai-web/src/app/app.routes.ts`, fora do `AppShellComponent`
  (como `/login` e `/entrada`).
- Criar `EstacaoComponent`: standalone, OnPush, tela cheia, sem menu/sidebar/breadcrumb.
- Criar `estacaoDeviceGuard`: exige sessão de **dispositivo** (token com `edge.device`). Sessão de
  usuário na rota `/estacao` é recusada.
- Laço: obter sessão (P2) → buscar perfil (P3) → aplicar limiares (P3) → `detector.start()`.
- `onGesture(engineFeatureRef, confidence)` → casar com primitivo do perfil → montar relato.
- `reportId` **estável e idempotente**: derivar de `deviceId + engineFeatureRef + janela de cooldown`.
  Reenvio da fila não pode gerar segundo alarme.
- `commandRef` e `pacienteId` vêm do pareamento/perfil (EDGE), nunca de configuração local.
- **Corpo do relato nunca inclui** `rawFramePayload`, landmark ou `confidence` cru além do mapeado
  em `primitives[]`. `@JsonAnySetter` no backend rejeita campos não declarados.
- `deviceTenant` não declarar no corpo — o servidor sobrescreve com o tenant do JWT.

#### Arquivos-alvo

- `frontend/libs/gest/src/lib/estacao/estacao.component.ts`
- `frontend/libs/gest/src/lib/estacao/estacao.component.spec.ts`
- `frontend/libs/gest/src/lib/estacao/report-ingress.port.ts`
- `frontend/libs/gest/src/lib/estacao/report-ingress-http.adapter.ts`
- `frontend/libs/gest/src/lib/estacao/report-ingress-http.adapter.spec.ts`
- `frontend/libs/gest/src/lib/estacao/estacao.guard.ts`
- `frontend/libs/gest/src/lib/estacao/estacao.guard.spec.ts`
- `apps/neuroassist-ai-web/src/app/app.routes.ts` (adicionar rota de topo)
- `frontend/libs/gest/src/index.ts` (export)

#### Contrato de tipo (lido do produtor — ReportIngressController)

```ts
// POST /api/v1/reports — corpo (nunca incluir rawFramePayload)
interface ReportRequest {
  reportId: string;   // uuid idempotente — derivar de deviceId + engineFeatureRef + cooldown
  pacienteId: string; // vem do pareamento
  commandRef: string; // vem do perfil
  primitives: Array<{ tipoGestoId: string; confidence: number }>;
  confidence: number;
  profileVersion: number;
  recognizedAt: string; // ISO 8601
  deviceId: string;     // do JWT sub — redundante mas obrigatório
  // deviceTenant: NUNCA declarar — sobrescrito pelo servidor
  // rawFramePayload: NUNCA incluir — EVNT-023 rejeita
}
// 201 = evento criado; 200 = idempotente (mesmo reportId)
```

#### Sequencia de execucao

1. data-access — port + adapter de report-ingress
2. feature — `EstacaoComponent` (laço + câmara + overlay)
3. wiring — `estacaoDeviceGuard` + rota em `app.routes.ts`
4. testes — guard spec + component spec + adapter spec

#### Politica de testes

Unit obrigatorio: spec do guard verificando que sessão de usuário é recusada. Spec do adapter verificando
que o corpo não contém `rawFramePayload` nem `deviceTenant`. Spec do component cobrindo: gesto dentro do
cooldown não gera segundo relato; `reportId` é o mesmo para o mesmo gesto na mesma janela.

#### Multi-empresa

Não aplica em requisições autenticadas com token de dispositivo. O `tenant_id` vem do JWT.

#### Criterios de aceite

```bash
cd frontend
npx nx test gest                          # specs de estacao + adapter + guard passando
npx nx build gest                         # exit 0
npx nx build neuroassist-ai-web           # exit 0 — rota registrada sem erro de compilação
# Verificar: gesto reconhecido -> POST /api/v1/reports com tipoGestoId certo
# Verificar: segundo gesto no cooldown -> mesmo reportId, um único POST
# Verificar: sessão de usuário em /estacao -> redirect pelo guard
```

#### Riscos

`commandRef` e `pacienteId` precisam vir do pareamento EDGE (`GET /api/v1/edge/patients/{pacienteId}/device`).
Verificar que esse endpoint existe e está acessível antes de implementar o laço. Se não existir, declarar
flag `isMock: true` no store e um sinal de aviso na tela.

---

### Prioridade 5 — Estacao W10/Fase-4: Heartbeat, Wake Lock, fila offline

Status: pendente
Issues cobertas: GEST-FE-031, GEST-FE-032
Categoria: FUNCIONAL

#### Objetivo

A estação sobrevive a interrupções. `parada` e `degradada` são visíveis à distância. Heartbeat
continua em qualquer estado. Relatos sem rede são enfileirados e reenviados.

#### Problemas identificados

- 16 dispositivos registrados, zero heartbeats no banco: nenhum código emite heartbeat.
- Nenhum código implementa Wake Lock API.
- Nenhuma fila offline existe; relatos que falham são descartados silenciosamente.
- `parada` (aba oculta) e `degradada` (falha de câmera/perfil) não têm representação visual de primeiro
  classe — seriam banners discretos que não cumprem a função (SPEC-09 I2).

#### Correcoes mandatorias (FE-031)

- `HeartbeatService`: emitir `POST /api/v1/edge/heartbeat` a cada intervalo fixo (ex.: 30s).
- Corpo: apenas `{ agentVersion, runningProfileVersion, deviceId }`. Nenhum campo adicional
  (`@JsonAnySetter` no backend lança em campo não declarado — invariante de privacidade nº 6).
- Heartbeat **continua** em `parada` e `degradada` — é como o servidor distingue parada de morta.
- Resposta com `profileDrift: true` → chamar `detectionProfileStore.forceReload()`.
- `WakeLockService`: solicitar `screen` wake lock quando em `vigiando`; liberar quando em `parada`.
  Aba oculta (`visibilitychange`) → estado `parada`; aba visível de novo → tentar retornar a `vigiando`.
- Estados `parada` e `degradada` ocupam a tela inteira — não banners.

#### Correcoes mandatorias (FE-032)

- `ReportQueueService`: fila em IndexedDB com `idb` ou API nativa.
- Relato que falha em rede → enfileirar com `status: 'pendente'`.
- Background sync (Service Worker ou interval) reenvia quando rede volta.
- `reportId` idempotente garante que reenvio não duplica alarme.
- Estado `enfileirado` visível na tela de estação (não em silêncio).

#### Arquivos-alvo

- `frontend/libs/gest/src/lib/estacao/heartbeat.service.ts`
- `frontend/libs/gest/src/lib/estacao/heartbeat.service.spec.ts`
- `frontend/libs/gest/src/lib/estacao/wake-lock.service.ts`
- `frontend/libs/gest/src/lib/estacao/wake-lock.service.spec.ts`
- `frontend/libs/gest/src/lib/estacao/report-queue.service.ts`
- `frontend/libs/gest/src/lib/estacao/report-queue.service.spec.ts`
- `frontend/libs/gest/src/lib/estacao/estacao.component.ts` (integrar P5 services)
- `frontend/libs/gest/src/index.ts` (exports)

#### Contrato de tipo (lido do produtor — HeartbeatController)

```ts
// POST /api/v1/edge/heartbeat
interface HeartbeatRequest {
  agentVersion: string;              // @NotBlank
  runningProfileVersion: number | null;
  deviceId: string | null;           // se presente, DEVE bater com JWT sub — senão 403
  // Nenhum outro campo — @JsonAnySetter lança
}
interface HeartbeatResponse {
  status: string;
  serverTime: string;
  currentProfileVersion: number;
  profileDrift: boolean;
}
```

#### Sequencia de execucao

1. data-access — `HeartbeatService` (POST /api/v1/edge/heartbeat + timer + profileDrift handler)
2. data-access — `WakeLockService` (visibilitychange + wake lock API)
3. data-access — `ReportQueueService` (IndexedDB + retry)
4. feature — integrar os três services em `EstacaoComponent`
5. testes — specs dos três services

#### Politica de testes

Unit obrigatorio: spec do heartbeat verificando que corpo nunca tem campos extras; spec do wake-lock
verificando transição `vigiando → parada` ao ocultar aba; spec da fila verificando reenvio ao
restaurar rede e que `reportId` duplicado não gera segundo POST.

#### Multi-empresa

Não aplica.

#### Criterios de aceite

```bash
cd frontend
npx nx test gest        # specs de P5 passando
npx nx build gest       # exit 0
# Verificar: aba oculta -> estado parada visível (tela inteira) + heartbeat continua
# Verificar: profileDrift no heartbeat response -> forceReload() chamado
# Verificar: rede caída -> relato enfileirado; rede volta -> reenvio com mesmo reportId
```

---

### Prioridade 6 — Foundation restante + integration cleanup

Status: pendente
Issues cobertas: GEST-INT-002, GEST-INT-006, GEST-INT-007
Categoria: ESTRUTURAL + WIRING

#### Objetivo

Remover gaps de foundation que bloqueiam automação de CI e completam a cadeia de composição.

#### Problemas identificados

1. **GEST-INT-007**: `project-context.json` tem campos incorretos `frontendLib`, `hasUi`, `isInfrastructure`.
   Bloqueia automação de scaffold e registry.
2. **GEST-INT-002**: Sem regras ESLint `depConstraints` para `scope:gest`. `scope:pac` e `scope:acs`
   podem importar `scope:gest` diretamente — viola a boundary (somente `scope:web` pode).
3. **GEST-INT-006**: Slot de composição no onboarding (`/onboarding/gesto`) não existe. `INT-005`
   (PAC) está feito; `INT-006` (onboarding) está faltando.

#### Correcoes mandatorias

- **INT-007**: Corrigir `project-context.json` do módulo GEST com `frontendLib: "libs/gest"`,
  `hasUi: true`, `isInfrastructure: false`.
- **INT-002**: Adicionar em `frontend/.eslintrc.json` regra `depConstraints` proibindo que
  `scope:pac` e `scope:acs` importem `scope:gest`. Somente `scope:web` pode importar `scope:gest`.
- **INT-006**: Criar page component de onboarding para `/onboarding/gesto` que hospeda
  `<gest-live-test>` via `GestShellIntegrationService.resolveLiveTestSlot()`.

#### Arquivos-alvo

- `project-context.json` (raiz ou `docs/` — localizar antes de editar)
- `frontend/.eslintrc.json` (adicionar depConstraints)
- `frontend/apps/neuroassist-ai-web/src/app/[onboarding-gesto-page].component.ts` (criar)
- `frontend/apps/neuroassist-ai-web/src/app/app.routes.ts` (rota de onboarding/gesto)

#### Sequencia de execucao

1. registry — corrigir `project-context.json`
2. estrutural — ESLint depConstraints para `scope:gest`
3. wiring — page component de onboarding + rota
4. testes — `npx nx lint` para verificar boundary; smoke test da rota

#### Politica de testes

`npx nx lint neuroassist-ai-web` deve falhar se alguém importar `scope:gest` de dentro de `scope:pac`.
Criar um teste de lint que confirma isso.

#### Criterios de aceite

```bash
cd frontend
npx nx lint gest                  # zero erros de boundary
npx nx lint neuroassist-ai-web    # zero erros
npx nx build neuroassist-ai-web   # rota /onboarding/gesto compilando
```

---

### Prioridade 7 — UX polish + observability + navigation

Status: pendente
Issues cobertas: GEST-FE-018, GEST-FE-019, GEST-FE-020, GEST-FE-021
Categoria: FUNCIONAL

#### Objetivo

Completar estados de loading/vazio/erro, roteamento de erros de câmera, breadcrumb do host thin,
e telemetria de eventos do teste ao vivo.

#### Correcoes mandatorias

- **FE-018**: Breadcrumb `Calibração › Teste ao vivo` apenas no thin host `apps/gest`. Sem breadcrumb
  quando composto no PAC ou onboarding.
- **FE-019**: Spinner de loading, placeholder em idle, ação de retry expostos como estados do componente.
- **FE-020**: Painel de erro de câmera como primeiro elemento de classe. Roteamento: erro de câmera →
  painel dedicado (não toast). Erro de backend → toast. Erro de eligibility → banner.
- **FE-021**: Eventos de telemetria: `live-test:start`, `live-test:stop`, `live-test:verdict`
  (recognized/not-recognized), `live-test:degraded`. Emitir via `console.info` com payload estruturado
  até que o sink de observabilidade esteja disponível.

#### Arquivos-alvo

- `frontend/libs/gest/src/lib/gest.routes.ts` (breadcrumb FE-018)
- `frontend/libs/gest/src/lib/feature-live-test/gesture-live-test.component.ts` (FE-019, 020)
- `frontend/libs/gest/src/lib/api/telemetry.service.ts` (FE-021)

#### Criterios de aceite

```bash
cd frontend
npx nx test gest        # >= todos os testes anteriores + novos de telemetria
npx nx build gest       # exit 0
```

---

### Prioridade 8 — E2E calibracao — 5 cenarios Playwright

Status: pendente
Issues cobertas: GEST-FE-022, GEST-FE-023, GEST-FE-024, GEST-FE-025, GEST-FE-026
Categoria: VALIDACAO

#### Objetivo

Implementar os 5 spec files de Playwright para o fluxo de calibração (teste ao vivo).
`GEST-FE-026` (stream-no-cache-between-runs) é gate obrigatório — sem ela o módulo não fecha.

#### Arquivos-alvo

- `frontend/apps/gest-e2e/src/live-test-simple-recognized.spec.ts`
- `frontend/apps/gest-e2e/src/live-test-composite.spec.ts`
- `frontend/apps/gest-e2e/src/live-test-degraded-offline.spec.ts`
- `frontend/apps/gest-e2e/src/live-test-camera-error.spec.ts`
- `frontend/apps/gest-e2e/src/stream-no-cache-between-runs.spec.ts` (obrigatorio)

#### Criterios de aceite

```bash
cd frontend
npx nx e2e gest-e2e     # 5 spec files passando; GEST-FE-026 verde
```

---

### Prioridade 9 — E2E estacao — 8 cenarios SPEC-09 §5 + fechamento do modulo

Status: pendente
Issues cobertas: GEST-FE-033
Categoria: VALIDACAO

#### Objetivo

Implementar os 8 cenários e2e da estação do paciente (SPEC-09 §5) e encerrar o módulo com gate verde.

#### Cenarios (SPEC-09 §5)

| # | Cenário |
|---|---|
| E1 | Dispositivo pareado → estação chega a `vigiando` |
| E2 | Gesto reconhecido → `POST /api/v1/reports` com `engineFeatureRef` certo |
| E3 | Mesmo gesto duas vezes no cooldown → um relato (mesmo `reportId`) |
| E4 | Aba oculta → estado `parada` visível + heartbeat continua |
| E5 | Perfil indisponível → `degradada` com motivo, sem detectar |
| E6 | Rede caída → relato enfileirado; volta → reenviado |
| E7 | Sessão de usuário em `/estacao` → recusada pelo guard |
| E8 | Nenhuma requisição carrega quadro, landmark ou campo não declarado |

#### Arquivos-alvo

- `frontend/apps/gest-e2e/src/estacao-*.spec.ts` (8 specs)

#### Criterios de aceite

```bash
cd frontend
npx nx e2e gest-e2e     # todos os cenários E1..E8 passando
npx nx lint gest        # exit 0
npx nx test gest        # todas as suítes passando
npx nx build gest       # exit 0
```

Score alvo após P9: >= 85/100.

---

## 4. Dependencias externas

| Endpoint | Produtor | Authority | P |
|---|---|---|---|
| `POST /oauth2/token/device` | acs-service | público | P2 |
| `GET /gest-service/detection-profile/devices/{deviceId}` | gest-service | `gest.detection-profile.read` | P3 |
| `POST /api/v1/reports` | evnt-service | `edge.device` | P4 |
| `POST /api/v1/edge/heartbeat` | edge-service | `edge.device` | P5 |
| `GET /api/v1/edge/patients/{pacienteId}/device` | edge-service | `edge.device` | P4 |

Todos os contratos foram lidos do `@RestController` do produtor e documentados na SPEC-09 §2. Qualquer
divergência entre esta tabela e o código do backend é defeito da tabela, não do backend.

---

## 5. Restricoes arquiteturais

| Regra | Descrição |
|---|---|
| RULE-NX-01 | `scope:gest` importa somente `scope:shared` e `scope:web` (apenas para registro). |
| RULE-NX-02 | `scope:pac` e `scope:acs` não importam `scope:gest` diretamente. |
| RULE-NX-03 | Composição de `GestureLiveTestComponent` em host pages de `scope:web` via `GestShellIntegrationService.resolveLiveTestSlot()` apenas. |
| RULE-NX-04 | `type:data-access` não importa `type:feature`. |
| RULE-NX-05 | Estação (`/estacao`) fora do `AppShellComponent` — sem menu, sidebar, breadcrumb. |
| RULE-NX-06 | Nenhum quadro, landmark ou `confidence` cru sai do dispositivo (SPEC-09 I1). |
| RULE-NX-07 | `clientSecret` ausente de log, DOM, error message (ADR-008 P1). |

---

## 6. Referencias

- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-09-estacao-paciente.md` — contratos, máquina de estados, invariantes, cenários e2e
- `docs/architecture/frontend/modules/gest/frontend/adr/ADR-008-estacao-do-paciente-como-dispositivo.md` — decisão de identidade, segurança, pendência clientSecret
- `docs/architecture/frontend/modules/gest/frontend/adr/ADR-007-browser-as-sole-detection-engine.md` — motor no browser; agente Python aposentado
- `docs/architecture/frontend/modules/gest/frontend/adr/ADR-005-live-test-via-edge-agent-stream.md` — invariante honest-degraded
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/generator-config.yml` — 7 sublibs, tags, shared_deps
- `docs/architecture/frontend/modules/gest/frontend/issues/issues-map.md` — 41 issues, dependências, estimativas
- `docs/architecture/shared/multi-empresa.md` — interceptor X-Empresa-Id, access-http-base
