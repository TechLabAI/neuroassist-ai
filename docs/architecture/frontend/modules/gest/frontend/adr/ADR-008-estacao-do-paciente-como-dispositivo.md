# ADR-008 — A estação do paciente é um DISPOSITIVO, não um usuário

- **Status:** proposed (2026-08-25) — **complementa `ADR-007`**, que decidiu o motor mas não o runtime
- **Scope:** módulo GEST (superfície always-on), com dependências em EDGE (identidade e heartbeat)
  e EVNT (ingestão de relato)
- **Decisor:** dono do produto

---

## Context

A `ADR-007` decidiu **onde** a detecção roda: no browser, com MediaPipe. Não decidiu **quem** a
executa, nem **onde ela vive** quando não há cuidadora olhando para a tela.

O levantamento de 2026-08-25 mostrou que essa lacuna não é teórica — ela é o produto inteiro:

### 1. A cadeia gesto → evento → alarme não tem runtime

O detector só roda dentro do teste ao vivo, quando alguém clica em "Testar gesto" durante a
calibração. Não existe rota, laço contínuo, nem produtor do evento de gesto:

```
grep -rn "always-on|alwaysOn|kiosk|WakeLock|visibilitychange" frontend/libs frontend/apps
  → só comentários dentro do próprio detector

grep -rn "@JmsListener" backend/services/*/src/main/java
  → nenhum consumidor; nenhum produtor de "gesto detectado"
```

As telas de alarme que o sistema exibe hoje são alimentadas por mock. **A promessa central —
transformar um movimento residual em pedido de ajuda — não tem execução.**

Sintoma coerente e verificado no banco: **16 dispositivos registrados, zero heartbeats.** Não falta
só o heartbeat; falta quem o emitiria.

### 2. O backend já resolveu a identidade — e resolveu bem

Nada disto precisa ser inventado. Já existe, implementado e guardado:

| Contrato | Guarda | O que faz |
|---|---|---|
| `POST /oauth2/token/device` (acs) | público; valida `clientId`+`clientSecret` do enroll | emite token de dispositivo |
| `POST /api/v1/reports` (evnt) | `hasAuthority('edge.device')` | recebe o relato de detecção |
| `POST /api/v1/edge/heartbeat` (edge) | `hasAuthority('edge.device')` | frescor → estado de conectividade |
| `GET /api/v1/edge/profile` (edge) | `edge.device` | o que detectar + de quem — **a criar, EDGE-022** |

> **Correção de 2026-08-25.** A primeira versão desta tabela listava
> `GET /gest-service/detection-profile/devices/{deviceId}` como se a estação pudesse chamá-lo. Não
> pode: ele exige `gest.detection-profile.read`, e o token de dispositivo carrega **apenas**
> `edge.device`. Varrendo os controllers dos 7 serviços, esse token alcança exatamente os dois
> `POST` acima — nada mais.
>
> Eu havia conferido corpos, regras de tenant e invariantes de privacidade nos `@RestController`, e
> **não cruzei a autoridade exigida com a que o token carrega**. O backend já registrara o problema
> como **defeito C-19**, escrito no Javadoc do `DetectionProfileClient`: *"GEST requires the
> authority `gest.detection-profile.read`, which a device token never has"*. Foi por isso que aquele
> cliente usa identidade de serviço.
>
> **Correção arquitetural:** o edge-service serve o perfil ao dispositivo, reusando o
> `DetectionProfileClient` que já existe — o dispositivo nunca fala com o gest-service. Isso resolve
> junto a identidade do paciente, porque o perfil já traz `pacienteId`. Rastreado em **EDGE-022**,
> que **bloqueia** `GEST-FE-028`, `030`, `031` e `032`.

E as invariantes certas já estão escritas no código do backend:

- **O tenant vem do token, nunca do corpo** (`ReportIngressController`), com rejeição de relato
  entre empresas.
- **O `deviceId` vem do `sub` do JWT, nunca do corpo** (`HeartbeatController`); corpo que declara
  outro dispositivo leva 403 `edge.identity-mismatch`.
- **Nenhum campo não declarado** no heartbeat (`@JsonAnySetter` que lança) — invariante de
  privacidade nº 6.
- **`rawFramePayload` presente ⇒ 400** (EVNT-023): quadro de vídeo não entra no backend.

### 3. Um papel de usuário `PACIENTE` seria a solução errada

Os papéis existentes são `COORDENADOR_CLINICA`, `CUIDADORA` e `PACIENTE_APOIO`. Não há `PACIENTE`,
e a tentação de criar um esbarra na premissa do produto: **o Márcio não digita senha.** Um papel
autenticado por login é inutilizável exatamente pela pessoa que lhe dá nome.

Some-se a isso: a estação é desatendida e permanente. Sessão de usuário expira e exige um humano
para renovar — o oposto do que "always-on" significa.

### 4. A sensibilidade que a cuidadora escolhe não faz nada

`DetectionThresholdTable` no gest-service define três faixas coerentes entre si (`alta` detecta
mais que `media`, que detecta mais que `baixa` — validado na construção). O detector do browser
tem os limiares como **constantes de módulo** e nunca recebe `sensibilidade`:

| | `earClose` | `blinkMinFrames` | `doubleBlinkWindowMs` | `briRaiseOn` |
|---|---|---|---|---|
| backend `media` (canônico) | 0.22 | 2 | 2000 | 0.30 |
| browser (fixo, qualquer faixa) | **0.20** | **3** | **1800** | **0.40** |

Divergem. E o guarda de drift do CI (`check_edge_agent_drift.py`) vigia `edge_agent_core.py` — o
agente **Python que a ADR-007 tirou do produto**. É um guarda verde apoiado no lado errado da
divergência: exatamente a armadilha "um guarda que não guarda nada" catalogada no `CLAUDE.md`.

---

## Decision

### D1 — A estação autentica-se como DISPOSITIVO

A superfície always-on usa **token de dispositivo** (`POST /oauth2/token/device`), não sessão de
usuário. O paciente que ela observa vem do **pareamento** já existente no EDGE
(`GET /api/v1/edge/patients/{pacienteId}/device`), não de um papel.

Quem faz o enroll é uma pessoa autenticada — a coordenadora pareia o dispositivo uma vez. Depois
disso a estação vive da própria credencial.

**Consequência de segurança que precisa ser encarada, não contornada:** um `clientSecret` de
client-credentials **não pode** morar no browser. A `ADR-007` já registrou isto (custo nº 5) e
continua em aberto. Enquanto não houver esquema próprio (token por dispositivo emitido no
pareamento, com armazenamento limpável pelo usuário como ressalva declarada), a estação **não vai
para uso real** — só para piloto fechado em máquina dedicada. Ver "Pendências" abaixo.

### D2 — Rota dedicada, fora do shell

`/estacao` é rota de topo, irmã de `/login` e `/entrada`: sem menu, sem navegação, sem barra
lateral. Não é uma tela onde a cuidadora "também" pode trabalhar — é o que a máquina exibe.

Tela cheia, alto contraste, e um único conteúdo: o preview com a malha e o estado da detecção.

### D3 — Os limiares vêm do perfil publicado, não do código

O `GestureDetector` passa a receber `DetectionThresholds` por parâmetro. As constantes de módulo
viram **apenas o default** usado quando o perfil não pôde ser lido — e esse caso é declarado na
tela, não silencioso.

Isto liga a sensibilidade que a cuidadora escolhe ao que o motor de fato faz, e é pré-requisito
para o `origem` (`PACIENTE` / `PERFIL_EMPRESA` / `INDISPONIVEL`) que o backend já devolve por
primitivo significar alguma coisa.

### D4 — Cada reconhecimento vira um relato

Gesto reconhecido → `POST /api/v1/reports` com `commandRef`, `primitives[].tipoGestoId`,
`confidence`, `recognizedAt`, `deviceId`. **Nunca** `rawFramePayload`: o quadro não sai do
dispositivo, e o backend rejeita quem tentar.

O casamento gesto → comando usa `engineFeatureRef`, o vocabulário unificado no commit `89f4ee5`.
O perfil por dispositivo já devolve os dois lados (`tipoGestoId` **e** `engineFeatureRef`), que é
precisamente a tradução que a estação precisa.

### D5 — Aba em segundo plano PARA a detecção, e isso precisa gritar

`requestAnimationFrame` não dispara em aba oculta. A detecção não degrada: **para**.

A configuração suportada é **Chromium com a aba visível, em máquina dedicada** — o que a `ADR-007`
já havia declarado. Esta ADR acrescenta o que faltava: **os controles que tornam a parada
visível**, e que são inegociáveis.

1. **Wake Lock** (`navigator.wakeLock`) enquanto a estação roda — evita o sleep de tela.
2. **`visibilitychange`** → estado explícito `detecção parada` na tela, e no servidor pela
   ausência de heartbeat.
3. **Heartbeat na cadência que o SERVIDOR manda**, com `agentVersion` e `runningProfileVersion`.

   > **Correção de 2026-08-25.** Esta linha dizia "a cada 15 s … limiar de 45 s". Os dois números
   > eram meus, não do sistema. O edge-service publica ambos: `neuroassist.edge.heartbeat.
   > interval-seconds: 30` e `staleness-factor: 3` — cadência **30 s**, limiar **90 s**.
   >
   > E não é preciso copiá-los: o enroll devolve `recommendedHeartbeatSeconds` e toda resposta de
   > heartbeat/liveness devolve `stalenessThresholdSeconds`. **A estação lê do servidor em vez de
   > fixar constante** — assim a cadência muda por configuração, num lugar só, e o cliente não
   > precisa ser reimplantado para acompanhar.
4. **Watchdog visível**: um indicador que prova que o motor processou um quadro recentemente.
   Detecção parada em silêncio é o pior modo de falha deste produto.

O heartbeat é o que fecha o buraco: hoje uma estação morta é indistinguível de uma estação
funcionando sem gestos.

### D6 — Detecção com a rede caída é enfileirada; com a aba fechada, não existe

Relato que falha vai para uma fila em IndexedDB com retry. Mas a distinção precisa estar na cara de
quem opera: **aba fechada não é detecção enfileirada — é detecção que não aconteceu.** Nenhuma
fila recupera um pedido de ajuda que ninguém observou.

---

## Alternatives considered

1. **Criar o papel `PACIENTE`** — rejeitado. O paciente não autentica; é a premissa do produto.
   Um papel cuja pessoa não consegue fazer login é uma abstração que só existe no diagrama.
2. **A estação como sessão de cuidadora deixada aberta** — rejeitado. A sessão expira, o token
   pertence a uma pessoa, e a auditoria passaria a atribuir a ela detecções que ela não presenciou.
   Também dá à estação todas as permissões da cuidadora, quando ela precisa de duas.
3. **Voltar ao agente Python** — rejeitado pela `ADR-007` e nada mudou: container não abre webcam
   em Windows nem em Mac, e o agente exige instalação nativa por máquina.
4. **Service Worker detectando em background** — não é possível: Service Worker não tem acesso a
   `getUserMedia`. A alternativa real seria `MediaStreamTrackProcessor` (WebCodecs) num Worker, que
   é **só Chromium** e não elimina a necessidade do watchdog. Fica registrado como caminho futuro,
   não como mitigação disponível.

---

## Consequences

**Positivas**

- A cadeia ponta a ponta passa a existir: gesto → relato → evento → alarme na tela da cuidadora.
- Menor privilégio: a estação tem `edge.device` e nada mais — não lê lista de pacientes, não
  administra nada.
- Uma estação morta vira "dispositivo offline" no painel, em até 45 s, usando controle que o
  backend já implementa.
- A sensibilidade escolhida na calibração finalmente muda o comportamento do motor.

**Negativas, e que exigem decisão antes do uso real**

- **Credencial de dispositivo no browser** (D1) — bloqueia uso fora de piloto fechado.
- **Disponibilidade depende de uma aba aberta.** É o custo que a `ADR-007` aceitou; esta ADR o
  torna observável, não o elimina.
- **A estação vira um segundo consumidor dos limiares**, então a divergência com o backend passa a
  ter consequência clínica. Exige guarda automatizado (ver Pendências).

---

## Pendências que esta ADR abre — e que não devem ser esquecidas

| # | Pendência | Bloqueia |
|---|---|---|
| P1 | Esquema de credencial de dispositivo no browser (`ADR-007` custo nº 5, ainda aberto) | uso fora de piloto fechado |
| P2 | Guarda de drift dos limiares apontando para o **browser**, não para `edge_agent_core.py` | confiança no CI |
| P3 | `check_edge_agent_drift.py` continua no CI guardando runtime aposentado — decidir: reapontar ou aposentar | ruído de gate |
| P4 | Classificador ONNX (`ADR-003` do PEA): `onnxruntime-web` ou ficar nos blendshapes | precisão futura |
| P5 | **EDGE-022** — `GET /api/v1/edge/profile` guardado por `edge.device` (defeito C-19 para a estação) | **toda a onda W10** |
| P6 | Faixa de limiares calibrada para BLENDSHAPES. O backend publica valores de BRI — razão geométrica —, e este motor lê blendshapes do MediaPipe. Escalas diferentes; hoje a divergência é afirmada pelo guarda de drift (`CAMPOS_ESCALA_BLENDSHAPE`) em vez de silenciada | sensibilidade de sobrancelha significar o que promete |

---

## References

- `ADR-007` — O browser é o único motor de detecção (decide o motor; esta ADR decide o runtime)
- `EDGE ADR-005` — conectividade derivada de frescor de heartbeat
- `ReportIngressController`, `HeartbeatController`, `DetectionProfileController` — contratos lidos
  no código, não presumidos
- `DetectionThresholdTable` — as três faixas e a coerência entre elas
- commit `89f4ee5` — vocabulário `engine_feature_ref` unificado (pré-requisito de D4)
