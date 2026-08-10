# PoC Run — NeuroAssist AI

## Pré-requisitos
- Docker Desktop rodando (com container `techlab-framework-pgvector` ativo)
- Java 21 + Gradle instalados
- Python 3.11 (MediaPipe não suporta 3.12+) — instalar dependências:
  ```bash
  py -3.11 -m pip install "mediapipe==0.10.21" opencv-python numpy requests
  ```
- Node.js 18+ + npm (para o prototype Angular)

## Passo a passo

### 1. Verificar PostgreSQL
O PoC usa o container PostgreSQL do TechLab (`techlab-framework-pgvector` em localhost:5432).  
O banco `neuroassist_poc` e o usuário `neuroassist` devem existir.

Verificar que o container está rodando:
```bash
docker ps | grep pgvector
```

Se o banco ainda não existir (primeira vez):
```bash
docker exec -it techlab-framework-pgvector psql -U postgres -c "CREATE USER neuroassist WITH PASSWORD 'poc123';"
docker exec -it techlab-framework-pgvector psql -U postgres -c "CREATE DATABASE neuroassist_poc OWNER neuroassist;"
docker exec -it techlab-framework-pgvector psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE neuroassist_poc TO neuroassist;"
docker exec -it techlab-framework-pgvector psql -U postgres -d neuroassist_poc -c "CREATE SCHEMA IF NOT EXISTS neuroassist_poc AUTHORIZATION neuroassist;"
```

### 2. Subir gest-service (porta 8082)
```bash
cd backend
./gradlew :services:gest-service:bootRun --args='--spring.profiles.active=poc' --no-daemon
```
Aguardar: `Started GestApplication` — Flyway aplica V1+V2 automaticamente.

### 3. Subir evnt-service (porta 8083) — novo terminal
```bash
cd backend
./gradlew :services:evnt-service:bootRun --args='--spring.profiles.active=poc' --no-daemon
```
Aguardar: `Started EvntApplication` — Flyway aplica V1 no `flyway_evnt_history`.

### 4. Aplicar seed (após os dois serviços subirem)
```bash
docker exec -i techlab-framework-pgvector psql -U neuroassist -d neuroassist_poc < backend/poc-seed.sql
```
Saída esperada: `INSERT 0 1` três vezes (idempotente com `ON CONFLICT DO NOTHING`).

### 5. Subir o prototype Angular — novo terminal
```bash
cd frontend/design-reference/angular-prototype
npm install          # só na primeira vez (instala node_modules)
npx nx serve neuro-assist-ai-web
```
Aguardar: `Application bundle generation complete` → abrir http://localhost:4200

### 6. Calibrar o perfil de detecção (tela Calibração)

Acesse: **http://localhost:4200/#/calibracao**

A tela vai:
- Carregar os tipos de gesto do gest-service (`GET /gest-service/tipo-gesto`)
- Exibir os comandos de Márcio (dados locais do prototype)
- Mostrar o card **"Perfil de detecção"** no canto inferior direito

Clique em **"Publicar perfil de detecção"** → confirma que o gest-service está ativo  
e gera um novo `DetectionProfile` com os gestos suportados.

### 7. Disparar o gesto

**Opção A — Direto no browser** (sem script Python):

Na tela `/#/calibracao`, abrir qualquer comando para editar e clicar em **"Testar agora"**.
- Primeiro uso: aguardar ~10-30s (baixa modelo MediaPipe ~30 MB do Google Storage)
- O WASM MediaPipe é servido localmente (em `/assets/mediapipe-wasm/`) — sem CDN
- Grade de landmarks aparece sobre o rosto na câmera quando detectado
- **Gesto:** piscar duas vezes de forma **deliberada** (cada piscada ≥ 3 frames / ~100ms)  
  com pausa entre elas (olho deve reabrir completamente), dentro de 1.8s
- O status muda de "pisque deliberadamente" → "1ª piscada ✓ — aguardando 2ª" → alarme disparado

**Opção B — Script Python** (com overlay OpenCV):
```bash
cd backend
py -3.11 poc-edge-agent-webcam.py
```
Pisque duas vezes em ≤2s em frente à câmera.  
Esperado: `ALARM TRIGGERED — eventoId=<uuid>` no terminal.

**Alternativa sem webcam** (para testar o fluxo):
```bash
curl -X POST http://localhost:8083/api/v1/reports \
  -H "Content-Type: application/json" \
  -d '{
    "reportId": "test-manual-001",
    "pacienteId": "paciente-marcio-fixo",
    "commandRef": "cmd-preciso-ajuda-fixo",
    "primitives": [{"tipoGestoId": "tg-piscar-duplo-001", "confidence": 0.95}],
    "confidence": 0.95,
    "profileVersion": 1,
    "timingWindow": {"startMs": 0, "endMs": 800, "durationMs": 800},
    "recognizedAt": "2026-07-30T07:00:00Z",
    "deviceId": "test-manual"
  }'
```

### 8. Ver o alarme na tela (tela Alarme do prototype)

Acesse: **http://localhost:4200/#/alarme**

A tela faz polling a cada 5s no evnt-service. Ao detectar um evento ativo:
- Fundo vermelho com "Márcio Amaral precisa de ajuda"
- Botão "Confirmar que atendi Márcio"

Clique no botão → mostra "Atendimento confirmado" + chama `POST /api/v1/events/{id}/confirmation`.

### 9. Verificar/confirmar via CLI (alternativa à tela)
```bash
# Ver evento ativo
curl http://localhost:8083/api/v1/events/active

# Confirmar (substituir <eventoId> pelo id retornado)
curl -X POST http://localhost:8083/api/v1/events/<eventoId>/confirmation \
  -H "Content-Type: application/json" \
  -d '{"verdict": "real"}'
```

---

## Fluxo completo

```
[poc-edge-agent-webcam.py]   piscar duplo → POST /api/v1/reports
         ↓
[evnt-service:8083]          Evento criado (FULL_SCREEN_ALARM)
         ↓
[Angular prototype :4200]    polling /api/v1/events/active → alarme em tela
         ↓
[cuidadora clica confirmar]  POST /api/v1/events/{id}/confirmation
         ↓
Evento confirmado (REAL)   → activeEvent = null
```

## Critérios de sucesso

| # | Critério | Como verificar |
|---|---|---|
| 1 | gest-service sobe | `Started GestApplication` nos logs |
| 2 | evnt-service sobe | `Started EvntApplication` nos logs |
| 3 | Flyway aplicou migrações | `Successfully applied 2 migrations` (gest) e `1 migration` (evnt) |
| 4 | Prototype Angular abre | http://localhost:4200 carrega sem erro |
| 5 | TipoGestos reais na calibração | Tela `/calibracao` mostra "piscar duplo" do BD (não mock) |
| 6 | Publicar perfil funciona | Botão "Publicar" exibe "Perfil vN publicado" (HTTP 200) |
| 7 | Report aceito (201) | `poc-edge-agent-webcam.py` dispara e imprime `ALARM TRIGGERED` |
| 8 | Alarme visível no Angular | Tela `/alarme` fica vermelha em ≤5s |
| 9 | Confirmação funciona | Clique no botão → "Atendimento confirmado" → `activeEvent: null` |

---

## Troubleshooting rápido

**`Schema-validation: missing table`** → subiu sem `--spring.profiles.active=poc`. Reiniciar com o profile.

**`Found non-empty schema but no schema history table`** → schema existe mas sem a tabela de histórico do Flyway.  
Solução: ambos os serviços usam tabelas separadas (`flyway_gest_history` / `flyway_evnt_history`) + `baseline-on-migrate: true` no evnt.

**`Connection refused` do evnt ao gest** → gest-service precisa subir primeiro. Checar `Started GestApplication` antes de subir o evnt.

**Webcam não detecta (Opção A — browser)** → abrir F12 → Console. Verificar:
  - `[PocGestureDetector] FilesetResolver failed` → dev server não está servindo os WASM (reiniciar `npx nx serve`)
  - `[PocGestureDetector] FaceLandmarker ready` aparece → modelo OK, problema é na detecção
  - Ajustar `EAR_CLOSE` em `poc-gesture-detector.ts` se o paciente não fecha os olhos completamente

**Webcam não detecta (Opção B — Python)** → rodar com `--debug` para ver o EAR por frame. Ajustar `EAR_THRESHOLD` conforme iluminação.

**CORS error no browser** → gest-service e evnt-service têm `CorsConfigurationSource` configurado para `localhost:4200`. Verificar que os serviços estão rodando.
