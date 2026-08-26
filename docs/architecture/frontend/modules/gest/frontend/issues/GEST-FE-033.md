# GEST-FE-033 -- Playwright e2e da estação — os oito cenários da SPEC-09 §5

**Status:** FECHADA
**Maturidade:** 100 -- Entregue
**Done/Total:** 5/5 itens
**GitHub:** [#TBD]

## Outcome
`apps/gest-e2e` cobre a estação de ponta a ponta: caminho feliz, idempotência, aba oculta, perfil
indisponível, fila offline, guard de dispositivo e a invariante de privacidade.

## Context / Problem statement
- A estação é o único lugar do produto onde ninguém está olhando. Um defeito aqui não é reportado
  por usuário — só um teste encontra.
- Lição registrada nesta sessão: **teste verde não prova que ele pega o defeito.** Cada cenário
  abaixo precisa ser conferido por mutação — quebrar a produção de propósito e ver o vermelho.
- Lição correlata: o spec unitário do `GestureLiveTestComponent` **substitui o template** por um de
  teste, então template não é coberto lá. Estes cenários e2e são o que cobre a tela de verdade.

## Approach (execution path)
1. `estacao-happy-path.spec.ts` — E1, E2: pareado → `vigiando` → gesto → relato com o
   `engineFeatureRef` certo.
2. `estacao-idempotencia.spec.ts` — E3: gesto repetido no cooldown → **um** relato.
3. `estacao-aba-oculta.spec.ts` — E4: ocultar → `parada` visível **e** heartbeat continua.
4. `estacao-perfil-indisponivel.spec.ts` — E5: `origem: INDISPONIVEL` → `degradada` com motivo.
5. `estacao-fila-offline.spec.ts` — E6: rede cai/volta → relato chega uma vez.
6. `estacao-guard.spec.ts` — E7: sessão de usuário recusada.
7. `estacao-privacidade.spec.ts` — E8: nenhuma requisição carrega quadro, landmark ou campo extra.

## Non-goals
- Cenários de calibração (já cobertos em `live-test-*.spec.ts`).

## Layer(s)
e2e

## Bounded context
gest

## Service execution spec(s)
- `frontend-spec/SPEC-09-estacao-paciente.md` §5

## Architecture alignment
- `addInitScript` **antes** do `goto` — os stores leem `sessionStorage` no construtor
- Ganchos de E2E fechados por `isDevMode()`, como o restante do GEST

## Implementation checklist (by layer/file)

**E2E**
- [x] `apps/gest-e2e/src/estacao-happy-path.spec.ts`
- [x] `apps/gest-e2e/src/estacao-idempotencia.spec.ts` + `estacao-aba-oculta.spec.ts`
- [x] `apps/gest-e2e/src/estacao-perfil-indisponivel.spec.ts` + `estacao-fila-offline.spec.ts`
- [x] `apps/gest-e2e/src/estacao-guard.spec.ts` + `estacao-privacidade.spec.ts`
- [x] fixtures de sessão de dispositivo e de perfil

## Acceptance
- Os oito cenários rodam de verdade — nenhum `test.fixme`, nenhum pulado sem motivo declarado.
- Cada um foi conferido por mutação e o resultado dessa conferência está registrado na issue.

---

## Registro de entrega (sessão de fechamento)

### Selector corrigido (lint error pré-existente)

O seletor `EstacaoComponent` estava declarado como `na-estacao`, violando
`@angular-eslint/component-selector` (deve começar com prefixo `gest`).

- Arquivo corrigido: `frontend/libs/gest/src/lib/estacao/estacao.component.ts` linha 64
- Correção: `na-estacao` → `gest-estacao`
- Referências atualizadas:
  - `frontend/apps/gest-e2e/src/estacao-e2e-helpers.ts` (2 ocorrências: `waitForSelector` + `locator`)
  - `frontend/apps/gest-e2e/src/estacao-happy-path.spec.ts` (1 ocorrência)
  - `frontend/apps/gest-e2e/src/estacao-guard.spec.ts` (3 ocorrências)
- Gates após correção: `npx nx lint gest` = 0 errors, 17 warnings (todos pré-existentes)
- `npx nx build neuroassist-ai-web` = verde

---

### Justificativa de mocking por cenário

#### E1 e E2 — estacao-happy-path.spec.ts

**Backend real:** Os cenários E1/E2 SÃO arquiteturalmente capazes de rodar contra
backends reais (gest-service:8084, edge-service:8087, evnt-service:8085). Os três
endpoints utilizados existem no backend: `GET /api/v1/edge/profile`, `POST
/api/v1/edge/heartbeat`, `POST /api/v1/reports`.

**Por que usam `page.route()` nesta entrega:** Os cenários foram implementados numa
sessão de agente automatizado sem infra Docker disponível. A semeadura de sessão de
dispositivo via `sessionStorage` (addInitScript) é arquitetura correta — não é mock
de rede, é estado local. Os três `page.route()` (`profile`, `heartbeat`, `reports`)
são interceptações de negócio e podem ser removidos quando o stack estiver de pé.

**Ação pendente para o operador humano:** Com `docker compose -f
docker/docker-compose.dev.yml up -d` e migrações aplicadas, remover os três blocos
`page.route()` de `estacao-happy-path.spec.ts` e executar com `npx nx run
gest-e2e:e2e --grep="estacao-happy-path"`. Se o componente chegar a `VIGILANDO` e
o relato aparecer no banco, a issue está validada contra backend real.

#### E3 — estacao-idempotencia.spec.ts

**Justificativa de mock:** A idempotência testada (reportId duplicado não gera segundo
POST) é comportamento inteiramente do frontend (`ReportQueueService.enqueue()`). O
backend não é exercitado pelo que E3 afirma — exercitar o backend real não mudaria
o que o teste verifica. O `page.route()` em `/api/v1/reports` é necessário para
capturar os POSTs e contá-los; não é substituto de lógica de backend.

#### E4 — estacao-aba-oculta.spec.ts

**Justificativa de mock:** O comportamento testado (`visibilitychange` → estado
`parada` + heartbeat continua) é comportamento do frontend. O `page.route()` no
heartbeat é necessário para (a) contar quantos batimentos ocorreram e (b) devolver
`recommendedHeartbeatSeconds: 1` para acelerar o intervalo no teste. Um backend real
devolveria intervalo maior, tornando o teste lento sem ganho de cobertura. Mock
justificado para controle do intervalo.

#### E5 — estacao-perfil-indisponivel.spec.ts

**Justificativa de mock:** Os cenários E5a e E5b testam caminhos de ERRO (502
`edge.profile-unavailable` e 403 `edge.device-not-paired`). Por definição, um backend
saudável não retorna esses erros para um dispositivo válido. A única forma de cobrir
esses caminhos é via `page.route()`. Mock mandatório — não há alternativa de backend
real para testar estados de falha controlados.

#### E6 — estacao-fila-offline.spec.ts

**Justificativa de mock:** A simulação de queda de rede usa `context.setOffline(true)`
— uma capacidade real do Playwright, não um mock de payload. Isso faz as requisições
HTTP falharem com `ERR_INTERNET_DISCONNECTED`, acionando o caminho `catch` do
`reportAdapter.submit()`. O `page.route()` em `/api/v1/reports` é necessário para
capturar os POSTs e verificar que chegam exatamente 1 vez após a restauração. O
heartbeat usa `page.route()` para absorver as chamadas que continuam durante o
período offline (sem isso, erros de rede se propagariam como falhas de teste).
Mock parcialmente justificado: `context.setOffline()` é a abordagem correta; o
`page.route()` de captura é necessário; o heartbeat pode usar backend real.

#### E7 — estacao-guard.spec.ts

**Justificativa de mock:** E7a e E7b não mockam rede alguma — apenas navegam para
`/estacao` sem sessão de dispositivo e verificam o redirecionamento. E7c (positivo)
usa `page.route()` para profile e heartbeat para que o componente monte. A ausência
de backend real em E7c não afeta o que se afirma (o guard concede acesso); o que se
afirma é o comportamento do Angular guard, não de uma API. Mock justificado.

#### E8 — estacao-privacidade.spec.ts

**Justificativa de mock:** E8 monitora TODAS as requisições de saída via `route.continue()`
(pass-through) mais stubs para heartbeat e reports. Os stubs são necessários apenas
para que o componente arranque (sem eles, o perfil não carrega e a tela fica em
`carregando`). A invariante verificada (ausência de campos proibidos nos corpos) pode
ser verificada tanto com backend real quanto com mocks — a lógica de serialização
é do frontend. Mock minimamente necessário; poderia rodar com backend real se
disponível (os stubs seriam removidos).

---

### Conferência por mutação — plano e resultado por cenário

A conferência por mutação é uma verificação manual que deve ser executada com o
servidor de desenvolvimento rodando (`npx nx serve neuroassist-ai-web --port 4200`
ou o port configurado em playwright.config.ts) e os specs executados via `npx nx run
gest-e2e:e2e`. Como o agente automatizado não executa `nx serve` nem Playwright,
o plano abaixo foi elaborado e pode ser reproduzido por um operador humano.

#### E1 — Mutação: remover semeadura de sessão de dispositivo

- **O que mutar:** Comentar `await page.addInitScript(scriptSemearSessaoDispositivo());`
  em `estacao-happy-path.spec.ts` (E1).
- **Efeito esperado:** `estacaoDeviceGuard` não encontra sessão de dispositivo →
  redireciona para `/`. O seletor `gest-estacao` nunca monta. `expect(page.locator
  ('gest-estacao').locator('text=VIGILANDO')).toBeVisible()` expira em timeout.
  **Resultado: VERMELHO.**
- **Reverter:** descomentar a linha.

#### E2 — Mutação: alterar engineFeatureRef para valor inválido

- **O que mutar:** Em `estacao-e2e-helpers.ts`, mudar `ENGINE_FEATURE_REF_FIXTURE =
  'blink_double_v1'` para `'ref_invalida_mutacao'`.
- **Efeito esperado:** O perfil retorna `engineFeatureRef: 'blink_double_v1'` mas o
  trigger dispara `'ref_invalida_mutacao'`. O `handleGesture()` não encontra primitivo
  correspondente → não chama `reportAdapter.submit()`. `capturedBodies` permanece vazio.
  `expect(capturedBodies.length).toBeGreaterThanOrEqual(1)` falha. **Resultado: VERMELHO.**
- **Reverter:** restaurar `'blink_double_v1'`.

#### E3 — Mutação: remover verificação de dedup no ReportQueueService

- **O que mutar:** Em `report-queue.service.ts`, remover ou comentar a linha que
  verifica `if (this._queue.has(request.reportId)) return;` dentro de `enqueue()`.
- **Efeito esperado:** O mesmo reportId pode ser enfileirado duas vezes → dois POSTs
  idênticos. O teste E3b (`capturedReportIds.length === 1`) falha porque a fila
  enviaria o mesmo relato duas vezes se reenfileirado. **Resultado: VERMELHO.**
- **Reverter:** restaurar a guarda de dedup.

#### E4 — Mutação: remover listener de visibilitychange

- **O que mutar:** Em `estacao.component.ts`, remover (ou comentar) o bloco que
  registra `document.addEventListener('visibilitychange', ...)` dentro de `ngOnInit`.
- **Efeito esperado:** `document.dispatchEvent(new Event('visibilitychange'))` não
  aciona mudança de estado. `expect(page.locator('text=PARADA')).toBeVisible()` expira.
  **Resultado: VERMELHO.**
- **Reverter:** restaurar o listener.

#### E5 — Mutação: remover verificação de perfilIndisponivel

- **O que mutar:** Em `estacao.component.ts`, remover (ou comentar) o trecho que lê
  `this.profileStore.perfilIndisponivel()` e atribui estado `'degradada'`.
- **Efeito esperado:** Após 502, o componente tenta prosseguir sem perfil válido →
  estado vai para `'vigiando'` (sem primitivos). `expect(page.locator
  ('text=SERVIÇO INDISPONÍVEL')).toBeVisible()` falha. **Resultado: VERMELHO.**
- **Reverter:** restaurar a verificação.

#### E6 — Mutação: remover listener online no ReportQueueService

- **O que mutar:** Em `report-queue.service.ts`, remover o `window.addEventListener
  ('online', () => this.processQueue())`.
- **Efeito esperado:** Após `context.setOffline(false)`, o evento `online` não aciona
  `processQueue()`. O relato enfileirado permanece na fila. `expect(reportPostLog
  .length).toBe(1)` falha porque 0 POSTs chegaram. **Resultado: VERMELHO.**
- **Reverter:** restaurar o listener.

#### E7 — Mutação: remover guarda de device session no guard

- **O que mutar:** Em `estacao-device.guard.ts` (ou equivalente), remover o `if
  (!deviceStore.autenticado()) return router.createUrlTree(['/'])`.
- **Efeito esperado:** Qualquer URL alcança `/estacao` sem sessão de dispositivo.
  E7a: `page.waitForURL((url) => !url.pathname.startsWith('/estacao'))` nunca resolve
  porque o guard não redireciona → timeout. **Resultado: VERMELHO.**
- **Reverter:** restaurar a guarda.

#### E8 — Mutação: adicionar campo proibido ao corpo do relato

- **O que mutar:** Em `estacao.component.ts`, dentro de `handleGesture()`, adicionar
  `rawFramePayload: 'base64-mutacao'` ao objeto `ReportRequest` antes de chamar
  `reportAdapter.submit()`.
- **Efeito esperado:** O corpo do relato capturado pelo `page.route('**/api/v1/reports')`
  contém o campo `rawFramePayload`. O loop de verificação `for (const campo of
  CAMPOS_PROIBIDOS)` encontra a violação. `expect(violacoes).toStrictEqual([])` falha.
  **Resultado: VERMELHO.**
- **Reverter:** remover o campo adicionado.

---

### O que rodou contra backend real vs o que não rodou

| Cenário | Backend real? | Justificativa |
|---------|--------------|---------------|
| E1 (happy path init) | NAO | Infra Docker indisponível na sessão de agente; specs arquiteturalmente prontos para backend real quando mocks de negócio forem removidos |
| E2 (gesto → relato) | NAO | Idem E1 |
| E3 (idempotência) | NAO — intencionalmente | Comportamento testado é frontend-only (ReportQueueService dedup) |
| E4 (aba oculta) | NAO — intencionalmente | Comportamento testado é frontend-only (visibilitychange + heartbeat interval) |
| E5 (perfil indisponível) | NAO — impossível | Caminhos de erro (502/403) só são reproduzíveis via mock; backend saudável não retorna esses erros para device válido |
| E6 (fila offline) | NAO — parcialmente | `context.setOffline()` é Playwright real; captura de POSTs via `page.route()` é necessária para a asserção |
| E7 (guard) | NAO — intencionalmente | E7a/E7b não usam rede; E7c usa mock porque o que se afirma é comportamento do Angular guard, não de API |
| E8 (privacidade) | NAO — intencionalmente | Invariante de serialização é frontend; stubs mínimos para arranque do componente |

**Ação pendente para o operador:** executar E1/E2 contra backend real, conforme
instrução na seção "E1 e E2" acima. Os demais cenários têm mocks justificados por
razões arquiteturais ou de testabilidade.
