# NeuroAssist AI — PoC: O que foi implementado e como ajustar

> Este documento descreve tudo que foi criado ou modificado para o PoC,  
> o que cada peça faz, e como ajustar sem usar Claude Code.

---

## Visão geral do que o PoC prova

```
[Webcam local]
     │  piscar duplo detectado (MediaPipe)
     ▼
poc-edge-agent-webcam.py
     │  GET  /gest-service/detection-profile/active  → valida perfil ativo
     │  POST /api/v1/reports                         → envia report ao evnt-service
     ▼
evnt-service (porta 8083)
     │  chama gest-service para validar o gesto
     │  persiste Evento (urgencia=EMERGENCIA, tier=FULL_SCREEN_ALARM)
     ▼
poc-alarm-screen.html (browser, polling a cada 2s)
     │  GET  /api/v1/events/active                   → detecta o alarme
     │  POST /api/v1/events/{id}/confirmation        → cuidadora confirma
     ▼
Evento confirmado (feedback=REAL)
```

O PoC **não usa**: JWT, Artemis/JMS, cadastro real de paciente, enrollment de dispositivo, SSE, notificação push, HIST, DASH, GWY, REG.

---

## Fases implementadas

### P0 — Simplificações transversais (gest-service + evnt-service)

Alterações feitas nos dois serviços para remover dependências que o PoC não precisa:

| O que foi feito | Onde | Como reverter / ajustar |
|---|---|---|
| Tenant fixo `"poc"` | `infrastructure/security/TenantContextFilter.java` | Restaurar a lógica de JWT para ler o tenant claim real |
| JWT desabilitado (`permitAll()`) | `infrastructure/config/SecurityConfig.java` | Descomentar `.oauth2ResourceServer(...)` e remover `permitAll()` |
| Artemis desabilitado no profile `poc` | `infrastructure/config/ArtemisConfig.java` | Remover `@Profile("!poc")` para reativar os beans JMS |
| Multi-tenancy Hibernate desabilitada | `infrastructure/config/HibernateMultiTenancyConfig.java` | Remover `@Profile("!poc")` |
| CORS habilitado para o browser | `evnt-service/infrastructure/config/SecurityConfig.java` | Remover o bean `corsConfigurationSource()` e o `.cors(...)` |

---

### P1 — gest-service: domínio + persistência + endpoint de validação

#### Arquivos criados

| Arquivo | O que faz |
|---|---|
| `src/main/resources/db/migration/V1__gest_poc_schema.sql` | Cria o schema `neuroassist_poc` com as 4 tabelas do gest-service |
| `src/main/resources/application-poc.yml` | Configura porta 8082, banco `neuroassist_poc`, Flyway local habilitado |
| `infrastructure/persistence/adapter/EdgePairingPortStub.java` | Stub que retorna tenant fixo `"poc"` para qualquer dispositivo |

#### O que foi completado no scaffold

- **Domain models** (records sem Spring/JPA):  
  `TipoGesto`, `TipoGestoTransition`, `DetectionProfile`, `DetectionProfilePrimitive`
- **JPA Entities**: campos completados e alinhados ao schema SQL
- **Services**: `TipoGestoService`, `DetectionProfileService`, `DetectionContractService`
- **Adapters**: todos os 4 adapters de repositório implementados
- **Endpoint de validação**: `POST /api/v1/recognized-commands`  
  — recebido pelo evnt-service via Feign para confirmar que o gesto pertence ao perfil ativo  
  — **stateless**: não persiste nada, apenas valida

#### Endpoint de perfil ativo (usado pelo edge agent)

```
GET http://localhost:8082/gest-service/detection-profile/active
```

Resposta:
```json
{
  "id": "c5a3b2d1-...",
  "version": 1,
  "contractVersion": "1.0.0",
  "primitives": [
    {
      "id": "...",
      "tipoGestoId": "tg-piscar-duplo-001",
      "nome": "piscar duplo",
      "engineFeatureRef": "mp.blink.double.v1"
    }
  ]
}
```

#### Como ajustar o gest-service

**Trocar o tipo de gesto do seed:**  
Editar `poc-seed.sql` — campo `engine_feature_ref` define qual detector o edge agent usa.  
Atualmente: `mp.blink.double.v1` (piscar duplo com MediaPipe).

**Adicionar um segundo gesto ao perfil:**  
Inserir novo registro em `tipo_gesto` e `detection_profile_primitive` no seed.  
O edge agent precisaria suportar o novo `engineFeatureRef`.

**Mudar a porta do gest-service:**  
Editar `application-poc.yml`: `server.port`.  
Atualizar `evnt-service/application-poc.yml`: `evnt.gest.baseurl`.  
Atualizar `poc-edge-agent-webcam.py`: constante `GEST_BASE`.

---

### P2 — evnt-service: ingestão + consulta + confirmação

#### Arquivos criados

| Arquivo | O que faz |
|---|---|
| `src/main/resources/db/migration/poc/V1__evnt_poc_schema.sql` | Tabelas `evento`, `notification_dispatch`, `report_idempotency` |
| `src/main/resources/application-poc.yml` | Porta 8083, banco `neuroassist_poc`, Flyway local, URL do gest-service |
| `infrastructure/persistence/adapter/CommandLookupHardcodedAdapter.java` | Hardcode: retorna label `"Preciso de ajuda"` e urgência `EMERGENCIA` |
| `infrastructure/persistence/adapter/CaregiverRoutingHardcodedAdapter.java` | Hardcode: retorna `"cuidadora-jamilly-fixo"` para qualquer paciente |
| `infrastructure/persistence/adapter/NotificationDispatchLogAdapter.java` | Loga o alerta no console em vez de enviar push real |
| `infrastructure/persistence/adapter/AuditTrailNoOpAdapter.java` | No-op: audit trail não implementado no PoC |
| `infrastructure/persistence/adapter/SseEventBroadcastNoOpAdapter.java` | No-op: SSE não implementado no PoC |
| `infrastructure/persistence/adapter/GestValidationAdapter.java` | Wrapper do Feign client → `GestValidationPort` |

#### Fluxo do `ReportIngressService` (12 passos)

1. Verificar idempotência — `reportId` já processado? → retornar `status=duplicate`
2. Chamar gest-service (`POST /api/v1/recognized-commands`) → validar gesto
3. Lookup do comando → `CommandLookupHardcodedAdapter` → `"Preciso de ajuda" / EMERGENCIA`
4. Resolver cuidador → `CaregiverRoutingHardcodedAdapter` → `"cuidadora-jamilly-fixo"`
5. Criar `Evento` (feedback=`NAO_AVALIADO`)
6. Criar `NotificationDispatch` (tier=`FULL_SCREEN_ALARM`, status=`QUEUED`)
7. Salvar Evento
8. Salvar NotificationDispatch
9. Salvar idempotência (`reportId` → `eventoId`)
10. Despachar notificação → log no console
11. Registrar auditoria → no-op
12. Retornar `201` com `eventoId` e `tier`

#### Endpoints expostos pelo evnt-service

| Método | Path | O que faz |
|---|---|---|
| `POST` | `/api/v1/reports` | Recebe report do edge agent |
| `GET` | `/api/v1/events/active` | Retorna evento não confirmado mais recente |
| `POST` | `/api/v1/events/{id}/confirmation` | Confirma ou marca falso positivo |

Body do `POST /confirmation`:
```json
{ "verdict": "real" }
// ou
{ "verdict": "falso_positivo" }
```

#### Como ajustar o evnt-service

**Trocar o nome/urgência do comando:**  
Editar `CommandLookupHardcodedAdapter.java` → método `lookup()`.

**Trocar a cuidadora:**  
Editar `CaregiverRoutingHardcodedAdapter.java` → método `resolveCaregiver()`.

**Mudar a porta do evnt-service:**  
Editar `application-poc.yml`: `server.port`.  
Atualizar `poc-edge-agent-webcam.py`: constante `EVNT_BASE`.  
Atualizar `poc-alarm-screen.html`: constante `EVNT_BASE` no `<script>`.

---

### P3 — Infraestrutura e scripts de teste

| Arquivo | O que faz |
|---|---|
| `docker-compose.poc.yml` | Sobe PostgreSQL 16 no banco `neuroassist_poc` |
| `poc-seed.sql` | Insere gesto, perfil e primitivo para o PoC (idempotente: `ON CONFLICT DO NOTHING`) |
| `poc-edge-agent.py` | Simula o edge agent com payload hardcoded (sem webcam) |
| `poc-run.md` | Passo a passo completo de execução do PoC |

---

### Webcam — `poc-edge-agent-webcam.py`

#### Dependências

```bash
pip install opencv-python mediapipe numpy requests
```

#### O que o script faz

1. **Pull do perfil** — chama `GET /gest-service/detection-profile/active`  
   Lê `version` (usado como `profileVersion` no report) e `primitives[0].tipoGestoId`.  
   Se `engineFeatureRef` não começa com `mp.blink` → avisa e sai.  
   Use `--no-pull` para pular essa etapa e usar os defaults do seed.

2. **Loop de detecção** — MediaPipe Face Mesh com câmera padrão (`index 0`)  
   Calcula EAR (Eye Aspect Ratio) para os dois olhos:
   ```
   EAR = (||P2-P6|| + ||P3-P5||) / (2 * ||P1-P4||)
   ```
   - EAR < `EAR_THRESHOLD` (default: `0.22`) = olho fechado = blink
   - 2 blinks em menos de `DOUBLE_BLINK_WINDOW` (default: `2.0s`) = disparo
   - Cooldown de `COOLDOWN_SECONDS` (default: `3.0s`) após disparar

3. **Disparo** — `POST /api/v1/reports` no evnt-service  
   Inclui `timingWindow` com `startMs`/`endMs`/`durationMs` calculados dos blinks reais.

#### Como ajustar

**Ajustar a câmera:**
```bash
python poc-edge-agent-webcam.py --camera 1   # câmera USB alternativa
```
Ou editar a constante `DEFAULT_CAMERA = 0` no topo do script.

**Ajustar sensibilidade de detecção:**  
Editar as constantes no topo do arquivo:
```python
EAR_THRESHOLD      = 0.22   # menor = mais difícil piscar "de verdade"
BLINK_CONSEC_FRAMES = 2     # frames mínimos com olho fechado
DOUBLE_BLINK_WINDOW = 2.0   # janela em segundos entre os dois blinks
COOLDOWN_SECONDS    = 3.0   # espera após disparar antes de detectar de novo
```
Se a detecção estiver disparando por engano (luz ruim, óculos), **aumente** `EAR_THRESHOLD` para `0.25`.  
Se o paciente tem dificuldade de piscar completamente, **reduza** para `0.19`.

**Trocar o paciente ou comando:**
```python
PACIENTE_ID = "paciente-marcio-fixo"
COMMAND_REF = "cmd-preciso-ajuda-fixo"
DEVICE_ID   = "webcam-local-poc"
```

**Debug de EAR por frame:**
```bash
python poc-edge-agent-webcam.py --debug
```
Imprime o valor de EAR a cada frame no terminal — útil para calibrar o threshold para um paciente específico.

---

### Tela de alarme — `poc-alarm-screen.html`

Página HTML auto-contida (zero dependências externas) com polling automático no evnt-service.

#### Como abrir

```bash
# Na pasta backend/
python -m http.server 8090
# Abrir no browser: http://localhost:8090/poc-alarm-screen.html
```

> **Importante**: abrir via `http://localhost:8090` e não como `file://`. O browser bloqueia  
> chamadas CORS de `file://` mesmo com o servidor configurado para aceitar `*`.

#### Estados da tela

| Estado | O que aparece |
|---|---|
| **Conectando** | Spinner, aguardando primeira resposta da API |
| **Monitorando** | Fundo verde-escuro, pulso animado, "Nenhum alerta ativo" |
| **ALARME** | Fundo vermelho pulsante, "PRECISO DE AJUDA" em letras grandes, timer, botão "Confirmar" |
| **Confirmado** | Tela teal com check, retorna ao monitoramento em ~3s |

#### Como ajustar

**Mudar o nome da cuidadora** (canto superior esquerdo):  
Editar a div `<div id="greeting">Olá, Jamilly</div>`.

**Mudar o nome do paciente** (aparece durante o alarme):  
Editar a div `<div class="alarm-name" id="alarm-patient">Márcio</div>`.

**Mudar a frequência de polling:**  
Editar a constante `const POLL_MS = 2000;` (em milissegundos).

**Mudar a URL do evnt-service:**  
Editar a constante `const EVNT_BASE = 'http://localhost:8083/api/v1';`.

**Desativar o som de alarme:**  
Remover ou comentar a função `beep()` e sua chamada em `poll()`.

**Tema claro/escuro:**  
Botão "Tema" no canto superior direito. A preferência é salva no `localStorage`.

---

## Banco de dados

Ambos os serviços usam o mesmo banco PostgreSQL:

```
Host:     localhost:5432
Banco:    neuroassist_poc
Usuário:  neuroassist
Senha:    poc123
Schema:   neuroassist_poc
```

O Flyway cria o schema e as tabelas automaticamente no startup de cada serviço.

**Para resetar o banco do zero:**
```bash
docker compose -f backend/docker-compose.poc.yml down -v
docker compose -f backend/docker-compose.poc.yml up -d
# Reiniciar os dois serviços — Flyway recriará as tabelas
# Reaplicar o seed:
psql -h localhost -U neuroassist -d neuroassist_poc -f backend/poc-seed.sql
```

**Para inspecionar os dados:**
```bash
psql -h localhost -U neuroassist -d neuroassist_poc

-- Ver eventos criados:
SELECT id, paciente_id, urgencia_snapshot, feedback, recognized_at
FROM neuroassist_poc.evento
ORDER BY recognized_at DESC;

-- Ver perfil ativo:
SELECT dp.version, dp.active, dpp.tipo_gesto_id, dpp.engine_feature_ref
FROM neuroassist_poc.detection_profile dp
JOIN neuroassist_poc.detection_profile_primitive dpp ON dpp.profile_id = dp.id
WHERE dp.active = true;

-- Ver idempotência (reports processados):
SELECT report_id, evento_id, processed_at
FROM neuroassist_poc.report_idempotency
ORDER BY processed_at DESC;
```

---

## Subir os serviços

```bash
# gest-service — porta 8082
cd backend
./gradlew :gest-service:bootRun --args='--spring.profiles.active=poc'

# evnt-service — porta 8083 (outro terminal)
./gradlew :evnt-service:bootRun --args='--spring.profiles.active=poc'
```

O profile `poc` ativa:
- Flyway local (migration nas pastas `db/migration/` de cada serviço)
- Banco `neuroassist_poc` em vez do banco de desenvolvimento padrão
- Artemis em modo embedded (sem broker externo)
- Eureka desabilitado
- Multi-tenancy Hibernate substituída por schema fixo `neuroassist_poc`
- `ArtemisConfig` e producers JMS desativados (`@Profile("!poc")`)

---

## Problemas comuns

### Serviço não sobe — `Schema-validation: missing table`

O Flyway não rodou ou rodou no schema errado.  
**Causa mais comum**: subir sem `--spring.profiles.active=poc`.  
**Solução**: garantir o profile e reiniciar.

Se o banco tiver tabelas com schema errado:
```bash
# Resetar (destrói todos os dados)
docker compose -f backend/docker-compose.poc.yml down -v
docker compose -f backend/docker-compose.poc.yml up -d
```

### `Connection refused` ao chamar gest-service do evnt-service

evnt-service tenta chamar `http://localhost:8082` — gest-service precisa estar rodando primeiro.  
Verifique que o gest-service subiu com `Started GestApplication` no log.

### Tela de alarme não atualiza / CORS error no browser

1. Confirmar que o evnt-service está rodando na porta 8083
2. Confirmar que a página foi aberta via `http://localhost:8090` (não `file://`)
3. Verificar o console do browser (F12) — erro de CORS aparece aí

### Webcam não detecta piscar

1. Rodar com `--debug` para ver o EAR por frame
2. Em boa iluminação, EAR ao piscar costuma cair abaixo de `0.15`
3. Em iluminação ruim ou com óculos, pode não descer o suficiente — reduzir `EAR_THRESHOLD`
4. Se estiver disparando sem piscar — aumentar `EAR_THRESHOLD` ou `BLINK_CONSEC_FRAMES`

### `mediapipe` não instala no Python 3.12+

MediaPipe tem suporte limitado ao Python 3.12. Usar Python 3.10 ou 3.11:
```bash
python3.11 -m pip install opencv-python mediapipe numpy requests
python3.11 poc-edge-agent-webcam.py
```

---

### Prototype Angular — calibração e alarme integrados

O prototype Angular em `frontend/design-reference/angular-prototype/` foi conectado às APIs reais.  
Usa o prototype existente (Angular 18 standalone + Tailwind v4 + signals) sem recriar nenhum componente.

#### Arquivos criados/modificados no prototype

| Arquivo | O que mudou |
|---|---|
| `src/app/core/poc-http-adapters.ts` | **Novo.** Adapters HTTP + serviços PoC (TipoGesto, Eventos, publicar perfil, confirmar evento) |
| `src/app/app.config.ts` | Adicionado `provideHttpClient()` + `POC_HTTP_PROVIDERS` (sobrescreve mocks de TipoGesto e Eventos) |
| `src/app/screens/calibracao/calibracao.component.ts` | Adicionado card "Perfil de detecção" com botão "Publicar" + feedback visual |
| `src/app/screens/alarme/alarme.component.ts` | Adicionado polling de 5s + confirmação real via API |

#### Arquivo modificado no backend

| Arquivo | O que mudou |
|---|---|
| `gest-service/.../config/SecurityConfig.java` | Adicionado `CorsConfigurationSource` bean (igual ao evnt-service) para permitir chamadas do browser em `localhost:4200` |

#### O que o prototype faz agora com o backend real

**Tela `/calibracao`:**
- Carrega tipos de gesto reais de `GET http://localhost:8082/gest-service/tipo-gesto`  
  (substituiu mock via `GestTiposGestoHttpAdapter`)
- Botão **"Publicar perfil de detecção"** → `POST /gest-service/detection-profile/publish`  
  Exibe feedback: "Perfil vN publicado" ou mensagem de erro se gest-service estiver offline
- Comandos continuam locais (mock) — o PoC não tem API de CRUD de comandos

**Tela `/alarme`:**
- Polling a cada 5s → `GET http://localhost:8083/api/v1/events/active`  
  (substituiu mock via `EvntEventosHttpAdapter`)
- Se houver evento ativo (`feedback: NAO_AVALIADO`): fundo vermelho + botão confirmar
- Clique "Confirmar" → atualiza a UI imediatamente (optimista) + `POST /api/v1/events/{id}/confirmation`
- Em 5s o polling limpa o estado local e volta a "Nenhum alarme"

#### Como iniciar o prototype

```bash
cd frontend/design-reference/angular-prototype
npm install          # só na primeira vez
npx nx serve neuro-assist-ai-web
# Acesse: http://localhost:4200
```

Rotas relevantes para o PoC:
- `/#/calibracao` — catálogo de comandos + publicar perfil
- `/#/alarme` — tela de alarme em tempo real (polling)

#### Como ajustar

**Mudar as URLs dos serviços:**  
Editar `src/app/core/poc-http-adapters.ts`:
```typescript
const GEST = 'http://localhost:8082';
const EVNT = 'http://localhost:8083';
```

**Mudar o intervalo de polling do alarme:**  
Editar `alarme.component.ts`, linha `setInterval(() => void this.facade.load(), 5000)`.  
Valor em milissegundos (5000 = 5s).

**Usar tela de alarme HTML puro (sem Angular):**  
`poc-alarm-screen.html` continua funcionando como alternativa mais leve.

---

### Sessão 2 — Startup real + fixes de boot (2026-07-30)

Esta sessão subiu toda a PoC para teste e corrigiu os problemas encontrados no boot real.

#### Problema 1: Flyway — tabela de histórico compartilhada

**Causa:** gest-service e evnt-service compartilham o mesmo schema `neuroassist_poc`. Ambos tentavam usar `flyway_schema_history` → conflito de versões (gest tem V1+V2, evnt tem seu próprio V1).

**Fix:** Cada serviço usa sua própria tabela de histórico:
- `application-poc.yml` do gest: `spring.flyway.table: flyway_gest_history`
- `application-poc.yml` do evnt: `spring.flyway.table: flyway_evnt_history` + `baseline-on-migrate: true` + `baseline-version: 0`

O `baseline-on-migrate: true` é necessário porque quando o evnt sobe o schema já tem tabelas (criadas pelo gest) — sem essa flag o Flyway recusa iniciar em schema não-vazio sem histórico próprio.

#### Problema 2: Producers JMS do evnt-service

**Causa:** 5 producers injetam `JmsTemplate` mas Artemis está excluído do auto-configure no profile `poc`.

**Fix:** Adicionado `@Profile("!poc")` nos 5 producers:
- `EventoconfirmadoProducer`
- `EventocriadoProducer`
- `FalsopositivomarcadoProducer`
- `NotificacaodespachadaProducer`
- `NotificacaofalhouProducer`

Nenhum deles é injetado por outros beans — safe para desativar no PoC.

#### Problema 3: StaleObjectStateException no DetectionProfile.publish()

**Causa:** `DetectionProfileJpaEntity` usava `@GeneratedValue(strategy = GenerationType.UUID)` mas a aplicação sempre definia o UUID manualmente via builder. Spring Data chamava `merge()` (porque ID ≠ null) mas o Hibernate 6 lançava `StaleObjectStateException` para entidades novas com UUID pré-definido sem histórico no BD.

**Fix:** `DetectionProfileJpaEntity` implementa `Persistable<UUID>`:
```java
@Transient @Builder.Default
private boolean isNew = true;

@PostLoad @PostPersist
void markNotNew() { this.isNew = false; }

@Override public boolean isNew() { return isNew; }
```
Com `isNew() = true`, Spring Data chama `persist()` em vez de `merge()` → INSERT correto.

#### Problema 4: node_modules ausentes no prototype Angular

**Causa:** `frontend/design-reference/angular-prototype/node_modules/` não existia — prototype nunca tinha sido instalado nesta máquina.

**Fix:** `npm install --legacy-peer-deps` na pasta do prototype (só necessário uma vez).

#### Estado final validado

| Serviço | Porta | Status |
|---|---|---|
| PostgreSQL (TechLab) | 5432 | ✅ banco `neuroassist_poc` criado |
| gest-service | 8082 | ✅ Flyway V1+V2, JPA validate OK |
| evnt-service | 8083 | ✅ Flyway V1, JPA validate OK |
| Angular prototype | 4200 | ✅ compilado, HTTP adapters ativos |

**Fluxo end-to-end validado via curl:**
1. `GET /gest-service/tipo-gesto` → retorna `piscar duplo` do BD ✅
2. `POST /gest-service/detection-profile/publish` → `{"version": 2, "contractVersion": "1.0.0"}` ✅
3. `POST /api/v1/reports` → `{"eventoId": "...", "tier": "FULL_SCREEN_ALARM"}` ✅
4. `GET /api/v1/events/active` → evento ativo com `tier: FULL_SCREEN_ALARM` ✅
5. `POST /api/v1/events/{id}/confirmation` → 200 / 204 ✅
6. `GET /api/v1/events/active` → `{"activeEvent": null}` ✅

---

### Sessão 3 — Detecção real de gesto no browser + fix Python MediaPipe (2026-08-01)

#### Mudança 1: MediaPipe Face Mesh no browser (Angular)

O botão "Testar agora" na calibração agora roda detecção de gesto **de verdade** no browser, sem precisar do script Python.

**Novo arquivo:** `src/app/core/poc-gesture-detector.ts`

Classe `PocGestureDetector` que:
- Carrega `@mediapipe/tasks-vision` via CDN (WASM + modelo `face_landmarker.task`, ~30 MB no primeiro uso)
- Roda `FaceLandmarker.detectForVideo()` via `requestAnimationFrame` (~30 fps)
- Calcula EAR (Eye Aspect Ratio) para cada olho usando os mesmos índices do script Python
- Detecta piscar duplo em ≤2s (mesmo threshold: `EAR < 0.22`, mínimo 2 frames)
- Ao detectar: chama `onDoubleBlink()` → dispara automaticamente `POST /api/v1/reports`
- Desenha no canvas:
  - Grade sutil (tesselação completa, 468 landmarks, ciano claro)
  - Contorno facial (face oval, ciano médio)
  - Olhos destacados (verde-menta, strokeWidth=2)
  - Pontos nos 6 vértices de cada olho

**Mudanças em `calibracao.component.ts`:**
- Preview expandido de `80×112px` → `aspect-ratio: 4/3` (full-width do card)
- Canvas overlay sobre o vídeo com grade de landmarks em tempo real
- Barra de EAR (vermelho quando olho fechado <0.22, verde quando aberto)
- Badge de piscadas no canto (● ○ → ● ●)
- Estado `'inicializando'` para feedback do download do modelo
- Disparo automático ao completar 2 piscadas; botão "Disparar manualmente" como fallback
- `PocGestureDetector` reutilizado entre testes (sem reinicializar o WASM)
- `NgZone.run()` para garantir que os callbacks do detector atualizam os signals Angular

**Como ajustar a sensibilidade de blink no browser:**
Editar `poc-gesture-detector.ts`:
```typescript
const EAR_THRESHOLD = 0.22;        // menor = mais fácil piscar
const BLINK_CONSEC_FRAMES = 2;     // frames mínimos com olho fechado
const DOUBLE_BLINK_WINDOW_MS = 2000; // janela entre os dois blinks
```

#### Mudança 2: fix mediapipe Python (downgrade 1.0.0 → 0.10.21)

**Causa:** `pip install mediapipe` instalou a versão 1.0.0, que removeu a API legada `mp.solutions`. O script `poc-edge-agent-webcam.py` usa `mp.solutions.face_mesh`.

**Fix:**
```bash
py -3.11 -m pip install "mediapipe==0.10.21"
```
A versão 0.10.21 mantém tanto `mp.solutions` (legacy) quanto o Tasks API. Verificar: `py -3.11 -c "import mediapipe as mp; print(mp.__version__)"` deve retornar `0.10.21`.

#### Dependências atualizadas

```bash
# Python (usar py -3.11 ou python3.11 — Python 3.14 não é suportado pelo MediaPipe)
py -3.11 -m pip install "mediapipe==0.10.21" opencv-python numpy requests

# Angular prototype (na pasta frontend/design-reference/angular-prototype/)
npm install   # inclui @mediapipe/tasks-vision agora no package.json
```

#### Fluxo de teste no browser (sem script Python)

1. `/#/calibracao` → selecionar ou criar um comando
2. Clicar em **"Testar agora"** → aguardar "Carregando detector…" (~10-30s no primeiro uso)
3. Quando câmera abrir com a grade de landmarks, piscar duas vezes em ≤2s
4. Alarme disparado automaticamente → `POST /api/v1/reports` → `tier: FULL_SCREEN_ALARM`
5. Ir para `/#/alarme` para confirmar

---

### Sessão 4 — Correção de detecção no browser + gesto deliberado (2026-08-01)

#### Problema 1: WASM incompatível entre npm package e CDN

**Causa:** `poc-gesture-detector.ts` carregava o runtime WASM via CDN na versão `@0.10.22`:
```
https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.22/wasm
```
Porém o pacote instalado era `@mediapipe/tasks-vision@1.0.0`. O JavaScript do pacote npm e o WASM do CDN são de versões diferentes e incompatíveis — o `FaceLandmarker` nunca inicializava corretamente, resultando em nenhuma detecção de face (sintoma: "Posicione o rosto..." permanente).

**Fix:**
1. Adicionado às assets do Angular (`project.json`) os arquivos WASM do node_modules:
   ```json
   { "glob": "**", "input": "node_modules/@mediapipe/tasks-vision/wasm", "output": "assets/mediapipe-wasm" }
   ```
2. `FilesetResolver.forVisionTasks('/assets/mediapipe-wasm')` — sem CDN, sem dependência de rede para o WASM.
3. Modelo do face landmarker ainda baixado do Google Storage (`~30 MB`, apenas no primeiro uso, cacheado pelo browser).

#### Problema 2: `delegate: 'GPU'` falhava silenciosamente

**Causa:** Em alguns ambientes Windows (Chrome + certos drivers GPU), o delegate WebGL do MediaPipe falha sem lançar erro — simplesmente não detecta rostos.

**Fix:** Trocado `delegate: 'GPU'` por `delegate: 'CPU'`. Ligeiramente mais lento por frame mas 100% confiável em qualquer hardware/browser.

#### Problema 3: Canvas dimensionado antes do primeiro frame

**Causa:** O código aguardava `readyState >= 1` (HAVE_METADATA) para ler `videoWidth`. Alguns drivers de câmera retornam `videoWidth = 0` em `readyState 1`, fazendo o canvas ter tamanho zero — todas as coordenadas dos landmarks eram mapeadas para `(0, 0)`.

**Fix:** Aguarda `readyState >= 2` (HAVE_CURRENT_DATA via evento `loadeddata`) — garante que o primeiro frame real foi recebido e as dimensões são corretas.

#### Melhoria: gesto de piscar duplo deliberado (anti-falso-positivo)

**Problema anterior:** O detector antigo contava qualquer dois fechamentos de olho dentro de 2s. Piscadas involuntárias durante uma conversa podiam acionar o alarme.

**Novo estado da máquina em `poc-gesture-detector.ts`:**

```
WAITING_BLINK1 → IN_BLINK1 → WAITING_OPEN1 → WAITING_BLINK2 → IN_BLINK2 → DONE
```

Critérios para cada transição:
- Cada piscada deve durar ≥ 3 frames (~100ms a 30fps) — exclui micro-piscadas involuntárias
- O olho deve reabrir completamente (EAR > 0.27) por ≥ 3 frames entre as duas piscadas — garante dois movimentos distintos
- Janela total: 1.8s (reduzida de 2s)
- Cooldown: 4s (aumentado de 3s)
- Threshold EAR fechado: 0.20 (ligeiramente mais restritivo que 0.22)

**Como ajustar para um paciente com controle muscular reduzido:**
```typescript
// poc-gesture-detector.ts — constantes no topo
const EAR_CLOSE           = 0.20;  // aumentar para 0.25 se precisa de fechamento mais forte
const EAR_OPEN            = 0.27;  // reduzir para 0.24 se não consegue abrir totalmente
const BLINK_MIN_FRAMES    = 3;     // reduzir para 2 se piscadas são muito rápidas
const OPEN_MIN_FRAMES     = 3;     // reduzir para 2 se abertura é curta
const DOUBLE_BLINK_WINDOW_MS = 1800; // aumentar para 3000 se precisa de mais tempo
```

#### Melhoria: feedback visual de progresso

A tela de calibração agora mostra o estado da máquina:
- `Detectado ✓ — pisque deliberadamente` — face encontrada, aguardando primeira piscada
- `1ª piscada ✓ — aguardando 2ª` — primeira piscada confirmada, aguardando a segunda
- Badge de piscadas no canto: `● ○` → `● ●`

---

## O que NÃO está no PoC (para a implementação completa)

| Funcionalidade | Módulo | O que substituiu no PoC |
|---|---|---|
| JWT real + validação de assinatura | ACS | `permitAll()` em SecurityConfig |
| Multi-tenancy dinâmica por JWT | ACS | Tenant fixo `"poc"` em TenantContextFilter |
| Enrollment do dispositivo edge | EDGE | `EdgePairingPortStub` retorna `"poc"` |
| Lookup real de comando via PAC | PAC | `CommandLookupHardcodedAdapter` |
| Resolução real de cuidador via ACS | ACS | `CaregiverRoutingHardcodedAdapter` |
| Notificação real (push/e-mail) | DASH | `NotificationDispatchLogAdapter` (log) |
| Trilha de auditoria imutável | HIST | `AuditTrailNoOpAdapter` (no-op) |
| SSE live stream | DASH | `SseEventBroadcastNoOpAdapter` (no-op) |
| Broker de mensagens Artemis | infra | Embedded/inerte com `@Profile("!poc")` |
| CRUD real de comandos no prototype | PAC | Catálogo local (mock signals no Angular) |
| API Gateway + Service Registry | GWY/REG | Chamadas diretas entre serviços |
