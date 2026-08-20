# Plano de Validação E2E — Integração Cross-Module (Backend)

> **Scope:** todos os 7 microserviços backend + GWY + REG  
> **Status:** PLANEJADO — todos os módulos PRODUCTION-READY, nenhum broker ativo (MVP)  
> **Criado em:** 2026-08-20  
> **Autores:** be-autopilot / equipe TechLab IA  

---

## 1. Objetivo

Validar que os 7 microserviços funcionam corretamente como **sistema integrado**, não apenas isolados.
Cada serviço já passou pelo seu próprio gate de testes unitários e BDD. Este plano cobre os
**pontos de corte entre serviços**: chamadas Feign, respostas JWT, propagação de audit facts,
fail-closed behaviors e fluxos de negócio completos de ponta a ponta.

---

## 2. Mapa de Integrações

```
                ┌──────────────────────────────────────────────────────────┐
                │                  GWY  :8080  (Spring Cloud Gateway)      │
                │           REG  :8761  (Eureka Service Registry)          │
                └──────────────────────────────────────────────────────────┘

  ACS :8088 ──────────────────────────────────────────────────────────────────┐
  (OAuth2 AS + Directory)                                                      │
   │  JWK Set (/oauth2/jwks)  ──────────── consumed by ALL (JWT validation)  │
   │  Directory (/directory/*)  ──── PAC (AcsDirectoryPort)                  │
   │                           ──── EVNT (CaregiverRoutingClient)            │
   │                           ──── HIST (ActorDirectoryPort, patient-scope) │
   │  Audit facts ──► HIST (/api/v1/hist/audit-facts)                        │
   └──────────────────────────────────────────────────────────────────────────┘

  PAC :8082 ────────────────────────────────────────────────────────────────┐
  (Pacientes + Catálogo de Comandos)                                         │
   │  → ACS: AcsDirectoryPort (GET /directory/caregivers/{id}/patient-scope)│
   │  → GEST: GestDetectabilityPort (eligibility check)                     │
   │  → EDGE: EdgeDevicePort (GET /edge/patients/{id}/device)               │
   │  → HIST: domain facts (ComandoPublicado/Recalibrado/Aposentado)        │
   └────────────────────────────────────────────────────────────────────────┘

  GEST :8084 ────────────────────────────────────────────────────────────────┐
  (Detecção de Gesto — perfis + primitivas)                                   │
   │  ← EDGE: profile pointer (GET /detection-profiles/active, por heartbeat)│
   │  ← PAC: eligibility guard (GestDetectabilityPort)                       │
   │  ← EVNT: edge report de gesto reconhecido (POST /reports)               │
   └────────────────────────────────────────────────────────────────────────┘

  EVNT :8085 ────────────────────────────────────────────────────────────────┐
  (Eventos e Alarmes)                                                          │
   │  → ACS: CaregiverRoutingClient (directory)                              │
   │  → HIST: audit facts (EventoCriado, EventoConfirmado)                  │
   │  → DASH: dispatch (POST /internal/notifications)                        │
   └────────────────────────────────────────────────────────────────────────┘

  EDGE :8087 ────────────────────────────────────────────────────────────────┐
  (Integração de Dispositivo Edge)                                             │
   │  → GEST: profile pointer por heartbeat                                  │
   │  ← PAC: device validation (GET /edge/patients/{id}/device)              │
   │  ← DASH: liveness poll (GET /edge/devices/{id}/liveness)                │
   └────────────────────────────────────────────────────────────────────────┘

  HIST :8089 ────────────────────────────────────────────────────────────────┐
  (Histórico e Auditoria)                                                      │
   │  ← ACS/PAC/EVNT/EDGE: audit facts (POST /api/v1/hist/audit-facts)      │
   │  → ACS: ActorDirectoryPort (patient-scope em leituras de cuidador)     │
   │  ← DASH: timeline reads (GET /api/v1/hist/history*)                    │
   └────────────────────────────────────────────────────────────────────────┘

  DASH :8090 ────────────────────────────────────────────────────────────────┐
  (Dashboard e Notificações)                                                   │
   │  ← EVNT: dispatch de notificação (POST /internal/notifications)         │
   │  → EVNT: delivery callbacks                                             │
   │  → HIST: timeline reads                                                 │
   │  → EDGE: liveness poll                                                  │
   └────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Pré-requisitos de Infraestrutura

```bash
# 1. Subir infra local (PostgreSQL, se necessário)
docker compose -f docker/docker-compose.dev.yml up -d

# 2. Rodar migrations PRIMEIRO (sempre)
./gradlew :services:neuroassist-ai-migrations:bootRun

# 3. Subir REG (Eureka)
./gradlew :services:neuroassist-ai-service-registry:bootRun

# 4. Subir ACS primeiro (todos os outros precisam do JWK Set)
./gradlew :services:acs-service:bootRun

# 5. Subir os demais em qualquer ordem
./gradlew :services:gest-service:bootRun &
./gradlew :services:evnt-service:bootRun &
./gradlew :services:pac-service:bootRun &
./gradlew :services:edge-service:bootRun &
./gradlew :services:hist-service:bootRun &
./gradlew :services:dash-service:bootRun &

# 6. Subir GWY por último
./gradlew :services:neuroassist-ai-api-gateway:bootRun
```

**Health check esperado:** todos os serviços registrados no Eureka em < 30s.

---

## 4. Fases de Validação

A ordem reflete a criticidade: um serviço upstream com falha torna os testes downstream inválidos.

---

### Fase 1 — Infra & Discovery

**Objetivo:** confirmar que GWY roteia e REG resolve todos os serviços.

| ID | Cenário | Endpoint | Critério |
|---|---|---|---|
| INF-01 | REG lista todos os 7 serviços como UP | `GET :8761/eureka/apps` | 7 instâncias `status=UP` |
| INF-02 | GWY roteia para cada serviço | `GET :8080/api/v1/<svc>/actuator/health` | `{"status":"UP"}` em todos |
| INF-03 | JWK Set público acessível | `GET :8080/oauth2/jwks` | JSON com `keys[]` não vazio |

---

### Fase 2 — Auth Backbone (ACS)

**Objetivo:** JWT emitido por ACS é validado por todos os outros serviços (resource servers).

| ID | Cenário | Endpoint | Critério |
|---|---|---|---|
| AUTH-01 | Login com credenciais válidas | `POST /oauth2/token` | `200` + `accessToken` RS256 assinado |
| AUTH-02 | Login com credenciais inválidas | `POST /oauth2/token` | `401` + sem `accessToken` |
| AUTH-03 | Refresh token rotation | `POST /oauth2/token/refresh` | `200` + novo par (novo `jti`) |
| AUTH-04 | JWT aceito em PAC | `GET :8080/api/v1/pac/patients` com token ACS | `200` ou `403` por papel (nunca `401`) |
| AUTH-05 | JWT aceito em HIST | `GET :8080/api/v1/hist/history` com token ACS | `200` ou `403` (nunca `401`) |
| AUTH-06 | JWT aceito em EDGE | `GET :8080/api/v1/edge/devices` com token ACS | `200` ou `403` (nunca `401`) |
| AUTH-07 | JWT aceito em EVNT | `GET :8080/api/v1/evnt/events/active` com token ACS | `200` ou `403` |
| AUTH-08 | JWT aceito em GEST | `GET :8080/api/v1/gest/detection-profiles/active` com token ACS | `200` ou `403` |
| AUTH-09 | JWT aceito em DASH | `GET :8080/api/v1/dash/panel/...` com token ACS | `200` ou `403` |
| AUTH-10 | Token expirado rejeitado em todos | qualquer endpoint com JWT exp=passado | `401` em todos os 7 |

---

### Fase 3 — Directory ACS → Downstream

**Objetivo:** PAC, EVNT e HIST consultam o directory ACS corretamente.

| ID | Cenário | Fluxo | Critério |
|---|---|---|---|
| DIR-01 | PAC resolve patient-scope de cuidador | `AcsDirectoryPort.getPatientScope(caregiverId)` | lista de `patientId` do tenant |
| DIR-02 | EVNT roteia notificação via directory | `CaregiverRoutingClient` → ACS directory | cuidadores responsáveis retornados |
| DIR-03 | HIST fail-closed se ACS indisponível | desligar ACS → `GET /api/v1/hist/history` como cuidador | `503` (scope-unavailable) |
| DIR-04 | HIST serve `hist:read:all` sem chamar ACS | coordenador/DPO token → HIST read | `200` sem chamada ao ACS |
| DIR-05 | Circuit breaker HIST→ACS abre após falhas | simular timeout ACS (5x) → HIST | CB OPEN; `503` retornado |

---

### Fase 4 — Audit Trail (todos → HIST)

**Objetivo:** todos os produtores de fatos de auditoria chegam ao HIST corretamente.

| ID | Cenário | Produtor | Fact esperado em HIST |
|---|---|---|---|
| AUD-01 | Login bem-sucedido registrado | ACS | `AutenticacaoRealizada` |
| AUD-02 | Login falho registrado | ACS | `AutenticacaoFalhou` |
| AUD-03 | Comando publicado registrado | PAC | `ComandoPublicado` |
| AUD-04 | Comando recalibrado registrado | PAC | `ComandoRecalibrado` |
| AUD-05 | Comando aposentado registrado | PAC | `ComandoAposentado` |
| AUD-06 | Evento criado registrado | EVNT | `EventoCriado` |
| AUD-07 | Evento confirmado registrado | EVNT | `EventoConfirmado` |
| AUD-08 | Dispositivo pareado registrado | EDGE | `DispositivoPareado` |
| AUD-09 | Idempotência — fact duplicado ignorado | qualquer | `200` + fact ignorado (mesmo `factId`) |
| AUD-10 | HIST indisponível não bloqueia produtor | desligar HIST → ACS login | `200` em ACS; fact na fila de retry |

---

### Fase 5 — EDGE ↔ GEST (Heartbeat + Profile)

**Objetivo:** heartbeat do dispositivo retorna pointer do perfil ativo de detecção.

| ID | Cenário | Endpoint | Critério |
|---|---|---|---|
| HB-01 | Heartbeat retorna `currentProfileVersion` | `POST /api/v1/edge/devices/{id}/heartbeat` | campo `currentProfileVersion` = versão ativa em GEST |
| HB-02 | `profileDrift: true` quando device roda versão antiga | heartbeat com `runningProfileVersion` < ativo | `profileDrift: true` |
| HB-03 | GEST indisponível → heartbeat aceito degradado | desligar GEST → heartbeat | `200` + `profilePointerDegraded: true` |
| HB-04 | GEST retorna `404` no-active-profile → heartbeat OK | sem perfil ativo em GEST → heartbeat | `currentProfileVersion: null`, `profileDrift: false` |
| HB-05 | Circuit breaker EDGE→GEST abre | GEST timeout 10x → EDGE heartbeat | CB OPEN; degraded aceito |

---

### Fase 6 — PAC ↔ EDGE (Validação de Dispositivo)

**Objetivo:** PAC valida `dispositivo_edge_id` ao registrar/atualizar paciente.

| ID | Cenário | Fluxo | Critério |
|---|---|---|---|
| DEV-01 | Paciente com dispositivo pareado válido | PAC PUT paciente → EDGE `GET /devices/{id}` | `200` em PAC |
| DEV-02 | Dispositivo não registrado no EDGE | PAC PUT com `dispositivo_edge_id` fantasma | `422` em PAC (referência inválida) |
| DEV-03 | Dispositivo registrado mas não pareado | EDGE retorna `404 patient-not-paired` | `422` em PAC |
| DEV-04 | EDGE indisponível ao registrar paciente | desligar EDGE → PAC PUT | comportamento definido pelo plano PAC (fail-open ou reject) |

---

### Fase 7 — EVNT → DASH (Dispatch de Notificação)

**Objetivo:** evento de gesto gera notificação entregue ao cuidador via DASH.

| ID | Cenário | Fluxo | Critério |
|---|---|---|---|
| NTF-01 | Evento criado → dispatch para cuidador com sessão aberta | EVNT → DASH `POST /internal/notifications` | `delivered` callback para EVNT |
| NTF-02 | Cuidador sem sessão aberta | EVNT → DASH | `no_open_session`; EVNT agenda retry |
| NTF-03 | `dispatchId` duplicado → idempotência | EVNT reenvia mesmo `dispatchId` | DASH ignora; sem duplo `delivered` |
| NTF-04 | DASH indisponível | desligar DASH → EVNT dispatch | EVNT retry-budget; não bloqueia fluxo de evento |
| NTF-05 | Cuidador confirma evento | `POST /panel/notifications/{id}/acknowledge` → callback EVNT | `EventoConfirmado` persistido em EVNT + fact em HIST |

---

### Fase 8 — DASH ↔ HIST (Timeline) e DASH ↔ EDGE (Liveness)

| ID | Cenário | Endpoint | Critério |
|---|---|---|---|
| DSH-01 | Cuidador lê timeline dos seus pacientes | `GET /api/v1/hist/history?patientId=...` via DASH | apenas fatos dos pacientes vinculados |
| DSH-02 | Coordenador lê timeline completa | `GET /api/v1/hist/history` com `hist:read:all` | fatos de todos os pacientes do tenant |
| DSH-03 | Liveness de dispositivo online | `GET /api/v1/edge/devices/{id}/liveness` | `status=online` dentro do staleness window |
| DSH-04 | Liveness de dispositivo offline | heartbeat parado > threshold → liveness poll | `status=offline` |
| DSH-05 | ETag / 304 em liveness sem mudança | 2 polls consecutivos sem mudança de status | `304 Not Modified` no segundo poll |

---

### Fase 9 — Fluxos de Negócio Ponta-a-Ponta

Cenários que cruzam 3+ serviços. São os mais representativos da operação real.

#### Flow-01: Onboarding de Tenant + Cuidador + Paciente

```
1. POST /api/v1/acs/tenants           → ACS provisiona tenant + schema (MIG)
2. POST /api/v1/acs/caregivers        → ACS cria cuidadora Jamilly
3. POST /oauth2/token (Jamilly)        → ACS emite JWT
4. POST /api/v1/pac/patients          → PAC cria paciente (valida device no EDGE)
5. POST /api/v1/pac/commands          → PAC cria comando "Chamar cuidadora"
6. :publish comando                   → PAC → GEST (eligibility) + HIST (ComandoPublicado)
7. GET  /api/v1/hist/history          → HIST retorna ComandoPublicado do paciente
```

**Gate:** todos os 7 passos retornam 2xx; HIST contém o fact; GEST aceita o comando como elegível.

#### Flow-02: Enrollment de Dispositivo + Heartbeat

```
1. POST /api/v1/edge/devices/:register → EDGE registra dispositivo (device token ACS)
2. POST /api/v1/edge/devices/:claim    → EDGE vincula device ao tenant
3. POST /api/v1/edge/devices/:enroll   → EDGE pareia ao paciente
4. POST /api/v1/edge/devices/{id}/heartbeat → EDGE consulta GEST; retorna profile version
5. HIST recebe DispositivoPareado      → fact auditado
6. DASH polls liveness                 → status=online
```

**Gate:** heartbeat retorna `currentProfileVersion` não-nulo; HIST tem o fact; DASH mostra `online`.

#### Flow-03: Gesto Reconhecido → Alarme → Confirmação

```
1. POST /api/v1/gest/reports           → GEST recebe resultado de detecção do edge
2. POST /api/v1/evnt/reports           → EVNT recebe report; cria Evento
3. EVNT → ACS directory               → resolve cuidadores responsáveis
4. EVNT → DASH (dispatch)             → notificação ao cuidador
5. EVNT → HIST                        → EventoCriado auditado
6. DASH SSE push                       → cuidador recebe alarme
7. POST /panel/notifications/{id}/acknowledge → cuidador confirma
8. DASH → EVNT callback                → EventoConfirmado
9. EVNT → HIST                        → EventoConfirmado auditado
10. GET /api/v1/hist/history           → timeline mostra 2 fatos do evento
```

**Gate:** cadeia completa sem falha; 2 fatos em HIST; Evento com `feedback = real`.

#### Flow-04: LGPD — Erasure + Verificação de Audit Trail

```
1. DELETE /api/v1/pac/patients/{id}    → PAC apaga paciente + LGPD erasure
2. GET /api/v1/hist/history?patientId  → HIST: dados anonimizados (keys erasure)
3. GET /api/v1/hist/history/:verifyChain → chain integrity mantida pós-erasure
```

**Gate:** chain de hash verificável; campos PII não retornados; metadados de fato preservados.

---

## 5. Estratégia de Execução

### 5.1 Camadas de Teste

| Camada | Ferramenta | Quando rodar |
|---|---|---|
| **Contrato** (par-a-par) | Spring Cloud Contract / Pact | CI a cada PR |
| **Integração cross-service** (Testcontainers) | JUnit 5 + Testcontainers (PostgreSQL + todos os serviços) | CI nightly ou pré-release |
| **E2E fluxo real** (docker-compose) | REST Assured ou Newman (Postman collection) | pré-release manual ou pipeline staging |

### 5.2 Abordagem Recomendada — Fase de Validação Manual Inicial

Dado que todos os 7 serviços estão prontos e não há ainda uma suíte E2E automatizada:

1. **Subir o stack completo** via docker-compose.dev.yml
2. **Executar os flows 01–04** com REST Assured ou Postman collection
3. **Documentar os resultados** nos issues E2E abaixo
4. **Criar a suíte automatizada** (E2E service `e2e-service` ou módulo de testes separado)

### 5.3 Testcontainers Cross-Service (futura suíte automatizada)

```java
// Exemplo: IntegrationTestBase.java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(properties = {
    "spring.datasource.url=${postgres.url}",
    "acs.service.url=http://localhost:${acs.port}",
    "hist.service.url=http://localhost:${hist.port}"
})
class CrossServiceIntegrationTest {
    // Container singleton para PostgreSQL + serviços em Testcontainers GenericContainer
}
```

---

## 6. Issues a Criar

| Issue ID | Título | Fase | Prioridade |
|---|---|---|---|
| E2E-001 | Infra: docker-compose.dev.yml com todos os 7 serviços | F1 | P1 |
| E2E-002 | Auth backbone: JWT ACS validado por todos os serviços | F2 | P1 |
| E2E-003 | Directory: PAC/EVNT/HIST → ACS fail-closed | F3 | P1 |
| E2E-004 | Audit trail: todos os produtores → HIST idempotente | F4 | P2 |
| E2E-005 | Heartbeat EDGE ↔ GEST + degraded mode | F5 | P2 |
| E2E-006 | PAC ↔ EDGE device validation | F6 | P2 |
| E2E-007 | EVNT ↔ DASH dispatch + ack callback | F7 | P2 |
| E2E-008 | DASH ↔ HIST timeline + DASH ↔ EDGE liveness | F8 | P3 |
| E2E-009 | Flow-01: Onboarding completo (tenant→cuidador→paciente→comando) | F9 | P1 |
| E2E-010 | Flow-02: Device enrollment + heartbeat + HIST + DASH | F9 | P2 |
| E2E-011 | Flow-03: Gesto → Alarme → Confirmação → Audit | F9 | P1 |
| E2E-012 | Flow-04: LGPD erasure + chain integrity | F9 | P3 |
| E2E-013 | Suíte automatizada REST Assured / Newman collection | — | P2 |

---

## 7. Gate Criteria (Definição de Pronto)

O módulo de integração E2E está DONE quando:

- [ ] Fases 1–3 (INF, AUTH, DIR): **100% passando** — são bloqueantes
- [ ] Fases 4–8 (AUD, HB, DEV, NTF, DSH): **≥ 90% passando** (com issues abertos para os 10%)
- [ ] Flows 01, 03, 11 (críticos): **100% passando**
- [ ] Flows 02, 04: **passando** ou issue rastreado com workaround documentado
- [ ] Todos os fail-closed behaviors verificados (DIR-03, HB-03, AUD-10)
- [ ] Nenhuma regressão nos gates unitários dos 7 módulos (score mantido)

---

## 8. Ordem de Execução Recomendada

```
P1 (bloqueantes — fazer primeiro):
  E2E-001 → E2E-002 → E2E-003 → E2E-009 → E2E-011

P2 (podem rodar em paralelo após P1):
  E2E-004 + E2E-005 + E2E-006 + E2E-007 + E2E-010 + E2E-013

P3 (pós-validação principal):
  E2E-008 + E2E-012
```
