# ADR-007 — O browser é o único motor de detecção (calibração e always-on)

- **Status:** accepted (2026-08-22) — **supersedes `ADR-005`**, torna `ADR-006` sem objeto
- **Scope:** global — module GEST, superfície de calibração (PAC), EDGE (enrollment/heartbeat)
  e todo o runtime `docs/architecture/edge-agent/python-edge-agent/`
- **Decisor:** dono do produto (decisão registrada em 2026-08-22)

## Context

`ADR-005` decidiu que o live-test faz ponte com o Edge Agent local em Python e **rejeitou ML
no browser**, pelo argumento de *engine drift*: um comando calibrado por um algoritmo e
detectado por outro pode passar na calibração e falhar em produção — inaceitável num canal em
que o falso negativo significa Márcio não conseguir pedir ajuda.

Três fatos mudaram a base dessa decisão.

### 1. O drift virou fato, não risco

`ADR-006` registra que `edge-agent-ws.py` (ponte do live-test) usa EAR/BRI com histerese
enquanto o PEA formal (`ADR-003`) usa classificador ONNX — motores diferentes. A paridade que
a `ADR-005` protegia nunca foi implementada. Somando o `PocGestureDetector` do protótipo, o
projeto tem **três** implementações do mesmo algoritmo, com thresholds divergentes
(`EAR_CLOSE` 0.20 no browser × 0.22 no Python; blendshapes × BRI).

### 2. O que roda quando a ponte cai é um veredito falso

`GestureLiveTestComponent.degradedCopy()` → *"Agente de detecção local não respondeu — teste
em modo simulado."* Esse é o estado observado no ambiente de desenvolvimento em 2026-08-22.
A comparação honesta não era browser × agente; era browser × mock.

### 3. Container não abre webcam em Mac nem Windows

| host | passthrough de câmera para container Linux |
|---|---|
| macOS (Docker Desktop / Virtualization.framework) | não existe — sem `/dev/video*` no guest |
| Windows (Docker Desktop / WSL2) | não existe — sem passthrough USB/vídeo |
| Linux | funciona (`--device=/dev/video0`) |

Limite de plataforma, não de esforço. O agente Python exige instalação nativa por máquina; o
browser já tem a câmera nos três sistemas, dentro ou fora de container.

## Decision

**O browser é o único motor de detecção do produto — na calibração e no fluxo always-on.**

O detector roda no cliente com MediaPipe Tasks Vision (`FaceLandmarker` + blendshapes), WASM
servido dos assets locais (sem CDN). A implementação de referência é
`design-reference/angular-prototype/.../core/poc-gesture-detector.ts`, validada em hardware
real, a ser portada para `libs/gest`.

O agente Python **sai do caminho do produto**. `edge-agent-ws.py`,
`poc-edge-agent-webcam.py` e `poc-edge-agent.py` permanecem no repositório como PoC e
referência de algoritmo, não como runtime suportado.

### O que esta decisão compra

- **O drift acaba.** Um motor só: calibração e produção passam a ser literalmente o mesmo
  código. É o que `ADR-005` e `ADR-006` queriam e não conseguiram entregar.
- **Zero instalação na máquina do cuidador** — sem Python, sem MediaPipe, sem OpenCV.
- **Paridade de sistema operacional**: macOS, Windows e Linux se comportam igual.
- **A stack fica containerizável de ponta a ponta** — o browser é do usuário, fora do container.
- **Privacidade preservada**: os frames não saem do dispositivo, como já era na `ADR-005`.

### O que esta decisão custa — e que precisa ser mitigado

O argumento de drift some e é substituído por um de **disponibilidade**. "Always-on" passa a
significar *"enquanto a aba estiver viva e renderizando"*.

1. **Aba em segundo plano é estrangulada pelo browser.** `requestAnimationFrame` para quando
   a página não é renderizada e timers de aba oculta são limitados. `requestVideoFrameCallback`
   também não dispara sem renderização. Captura confiável em background exige
   `MediaStreamTrackProcessor` (WebCodecs) num Worker — **hoje só em Chromium**; Safari e
   Firefox não suportam.
   → **Configuração suportada: Chromium com a aba visível (kiosk).** Qualquer outra combinação
   degrada silenciosamente e por isso precisa ser detectada e avisada, nunca assumida.
2. **A máquina precisa ficar acordada e destravada.** Screen Wake Lock API evita o sleep de
   tela (Chrome/Edge, Safari 16.4+); não evita bloqueio de sessão nem suspensão do sistema.
3. **Sem supervisor de processo.** `systemd` reinicia um processo Python morto; uma aba que
   fecha exige um humano. O controle compensatório é servidor-side e **já existe**: o
   `edge-service` deriva conectividade do frescor do heartbeat (`ADR-005` do EDGE). Um detector
   morto vira "dispositivo offline" no painel — desde que o browser mande heartbeat.
4. **O outbox migra para o browser.** `ADR-004` do PEA (SQLite, 50 eventos FIFO) vira
   IndexedDB + fila de retry, com Service Worker para o flush. E a definição de "offline"
   muda: hoje significa "serviços Java parados"; no browser passa a incluir **aba fechada** —
   e aba fechada não é detecção enfileirada, é **detecção que não aconteceu**.
5. **Enrollment e heartbeat mudam de dono.** O `edge-service` ganhou enrollment de dispositivo
   e heartbeat autenticado (Sessão 3 E2E). Se o dispositivo é o browser, a credencial do
   dispositivo passa a viver no browser — e um `client_secret` de client-credentials **não pode**
   viver ali. Exige esquema próprio (token por dispositivo emitido no pareamento, com a
   ressalva de que armazenamento de browser é limpável pelo usuário).
6. **O classificador ONNX** (`ADR-003` do PEA) precisa de decisão: `onnxruntime-web` no
   browser, ou ficar nos blendshapes/EAR do MediaPipe.

### Condições para esta decisão se sustentar

A decisão é sólida **se** o deployment for uma máquina dedicada, sempre ligada, com a aba em
primeiro plano — que é compatível com a topologia já documentada em
`01-architectural-vision.md §2` ("host machine — same server/PC where Java services run", com
webcam integrada ou USB).

Ela **não** se sustenta se o always-on tiver de rodar no notebook de uso geral da cuidadora,
que fecha a tampa e troca de aba. Nesse cenário o canal de pedido de ajuda fica indisponível
sem ninguém perceber — o pior modo de falha possível para este produto.

**Mitigação obrigatória, independente do cenário:** watchdog visível. A UI precisa de um
indicador inequívoco de "detecção ativa" e o servidor precisa alarmar na ausência de
heartbeat. Detecção parada em silêncio é inaceitável.

## Alternatives considered

1. **Manter a ponte com o agente Python (`ADR-005`)** — rejeitado: exige instalação nativa por
   máquina, não containeriza em Mac/Windows, e na prática entrega veredito **simulado** sempre
   que o agente não está presente. Mantinha três motores divergentes.
2. **Escada de fallback (agente → browser → simulado)** — foi a proposta intermediária desta
   ADR antes da decisão do produto. Rejeitada por preservar o drift: dois motores reais com
   thresholds diferentes, distinguidos só por rótulo na UI. Um motor só é mais simples e mais
   honesto.
3. **Containerizar o agente** — impossível em Mac/Windows (§3). Continuaria válido apenas para
   um dispositivo edge Linux dedicado, cenário que esta decisão descarta.
4. **Container do agente com fonte sintética** — não usa a câmera do paciente; não responde à
   pergunta que a calibração faz.

## Consequences

- **Positivo:** ver "o que esta decisão compra".
- **Negativo:** a garantia de disponibilidade do canal passa a depender do browser permanecer
  aberto, visível e em Chromium. É uma promessa de produto diferente e mais fraca que a de um
  serviço de sistema — e precisa ser dita ao cuidador na UI, não escondida.
- **Blast radius na arquitetura do agente:** `docs/architecture/edge-agent/python-edge-agent/`
  passa a ser referência histórica. Cinco ADRs ficam sem objeto ou precisam de equivalente no
  browser: `ADR-001` (runtime asyncio), `ADR-002` (extração MediaPipe), `ADR-003` (ONNX),
  `ADR-004` (SQLite/outbox), `ADR-005` (HTTP localhost colocado). Devem ser marcadas
  `superseded by GEST ADR-007` em vez de silenciosamente abandonadas.
- **`ADR-006` fica sem objeto:** ela existia para ratificar o detector interino da ponte.
- **Peso de bundle:** `@mediapipe/tasks-vision` + WASM entram na aplicação. Lazy-load na rota
  de detecção é obrigatório — a home não pode pagar esse custo.
- **A ponte localhost (`EDGE_BRIDGE_WS_URL`, `EdgeDetectionClient`) some** do caminho de
  produção, junto com o `degradedReason: 'bridge-unreachable'`.

## Configuração de câmera

Com um motor só, some também a ambiguidade de identificador que existiria com dois runtimes:
a câmera é escolhida no browser via `navigator.mediaDevices.enumerateDevices()`, guardando o
`deviceId` como **preferência local** (localStorage, em try/catch).

O `deviceId` é escopado por origem e permissão e rotaciona quando as permissões são limpas —
então é preferência de máquina, **não estado de domínio**, e não deve ser modelado no EDGE.
O que o EDGE modela é o *dispositivo*, não a câmera dele.

## Follow-ups

1. Portar `poc-gesture-detector.ts` para `libs/gest` com lazy-load e WASM em assets locais.
2. Seletor de câmera na calibração (`enumerateDevices` + persistência local).
3. Watchdog: indicador de "detecção ativa" na UI + alarme de heartbeat ausente no servidor.
4. Outbox no browser (IndexedDB + retry) substituindo `ADR-004` do PEA.
5. Esquema de credencial de dispositivo para o browser (enrollment/heartbeat do EDGE).
6. Decidir ONNX no browser (`onnxruntime-web`) × blendshapes do MediaPipe.
7. Marcar as ADRs do PEA como superseded e mover o diretório para referência histórica.
8. Screen Wake Lock + verificação de browser suportado, com aviso explícito quando a
   configuração não sustentar always-on.

## References

- `ADR-005-live-test-via-edge-agent-stream.md` (superseded por esta)
- `ADR-006-ear-bri-interim-detector.md` (sem objeto)
- `docs/architecture/edge-agent/python-edge-agent/01-architectural-vision.md` §2 (topologia)
- `docs/architecture/edge-agent/python-edge-agent/adr/ADR-003-onnx-classifier-format.md`
- `docs/architecture/edge-agent/python-edge-agent/adr/ADR-004-sqlite-local-persistence.md`
- `docs/architecture/frontend/modules/edge/frontend/adr/ADR-005-connectivity-derived-from-heartbeat-freshness.md`
- `frontend/design-reference/angular-prototype/.../core/poc-gesture-detector.ts`
