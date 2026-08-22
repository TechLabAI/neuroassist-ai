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

## 8. Status das Issues

| Issue | Status | Concluído em |
|---|---|---|
| E2E-001 | ✅ DONE | 2026-08-20 |
| E2E-002 | ✅ DONE (parcial — EVNT stub security; GEST corrigido) | 2026-08-21 |
| E2E-014 | ✅ DONE — RWT-01..09; superfície pública normalizada no GWY | 2026-08-21 |
| E2E-003 | 🟡 PARCIAL — DIR-01/DIR-04 DONE; DIR-02 feature ausente (EVNT stub); DIR-03/DIR-05 na Sessão 5 | 2026-08-21 |
| E2E-004 | 🟡 PARCIAL — AUD-03/04/05 DONE; AUD-01/02 feature ausente (ACS não produz facts); AUD-06..10 na Sessão 4 | 2026-08-21 |
| E2E-005 | 🟡 PARCIAL — HB-01 DONE; HB-02..05 bloqueados pelo ProfilePointerController (501) | 2026-08-22 |
| E2E-006 | ✅ DONE — DEV-01..04 | 2026-08-22 |
| E2E-007 | OPEN | — |
| E2E-008 | OPEN | — |
| E2E-009 | ✅ DONE (17/17 checks) | 2026-08-21 |
| E2E-010 | ✅ DONE — Flow-02 11/11 | 2026-08-22 |
| E2E-011 | OPEN | — |
| E2E-012 | OPEN | — |
| E2E-013 | OPEN | — |

---

## 9. Ordem de Execução — Context Engineering Sessions

> **Princípio:** cada sessão acumula estado (JWT token, IDs de entidade) que é reutilizado na sessão seguinte.
> Os Flows (E2E-009, E2E-010, E2E-011) **subsuem** os checks unitários correspondentes —
> não executar as issues unitárias separadas antes dos flows; validar como checklist dentro do flow.

### Sessão 1 — Auth Foundation
**Issue:** E2E-002  
**Cenários:** AUTH-01..10  
**Estado gerado:** `access_token` RS256 + `refresh_token`  
**Pré-condição:** E2E-001 DONE, stack docker-compose UP (`docker compose -f docker/docker-compose.dev.yml up -d`)

### Sessão 2 — Onboarding + Directory (estável) + Audit Baseline
**Issues:** E2E-009 + E2E-003 (DIR-01, DIR-02, DIR-04) + E2E-004 (AUD-01..05 como side effects)  
**Raciocínio:** Flow-01 (E2E-009) executa na mesma sequência que DIR-01, DIR-04 e AUD-01/03 —
são os mesmos HTTP calls; validar os checklists das três issues dentro do mesmo contexto.  
**Estado gerado:** `tenantId`, `caregiverId`, `patientId`, `commandId`, audit facts no HIST  
**Pré-condição:** Sessão 1 concluída (JWT disponível)

### Sessão 3 — Device + Heartbeat
**Issues:** E2E-010 + E2E-005 + E2E-006  
**Raciocínio:** Flow-02 (E2E-010) subsume HB-01..05 (E2E-005) e DEV-01..04 (E2E-006);
circuit breaker scenarios (HB-03, HB-05) ficam aqui.  
**Estado gerado:** `deviceId` pareado, perfil ativo no GEST, liveness=online  
**Pré-condição:** Sessão 2 concluída (`patientId` disponível)

### Sessão 4 — Alarm Flow + Notification + Audit Completo
**Issues:** E2E-011 + E2E-007 + E2E-004 (AUD-06..10)  
**Raciocínio:** Flow-03 (E2E-011) executa todos os cenários de E2E-007 (NTF-01..05) como passos
do flow; AUD-06/07 aparecem naturalmente como side effects dos passos 5 e 9.  
**Estado gerado:** evento criado + confirmado, 2 audit facts no HIST, cadeia de hash verificável  
**Pré-condição:** Sessão 3 concluída (`deviceId` disponível para trigger de gesto)

### Sessão 5 — Dashboard + Circuit Breakers Disruptivos + LGPD
**Issues:** E2E-008 + E2E-003 (DIR-03, DIR-05) + E2E-012  
**Raciocínio:** DSH-01..05 precisam de dados das sessões 2–4; DIR-03/05 param containers
(disruptivos → sempre no final); E2E-012 (LGPD erasure) é destrutivo → absoluto último.  
**Pré-condição:** Sessões 2–4 concluídas (dados populados no HIST e EDGE)

### E2E-013 — Automation Suite
Trabalho separado pós-validação manual; não é uma sessão de validação.

### Resumo

| Sessão | Issues | Cenários principais | Carry-forward |
|---|---|---|---|
| 1 | E2E-002 | AUTH-01..10 | JWT token |
| 2 | E2E-009 + E2E-003(estável) + E2E-004(parcial) | Flow-01 + DIR-01/02/04 + AUD-01..05 | tenantId, caregiverId, patientId, commandId |
| 3 | E2E-010 + E2E-005 + E2E-006 | Flow-02 + HB-01..05 + DEV-01..04 | deviceId, profile version |
| 4 | E2E-011 + E2E-007 + E2E-004(final) | Flow-03 + NTF-01..05 + AUD-06..10 | evento confirmado, audit chain |
| 5 | E2E-008 + E2E-003(disruptivo) + E2E-012 | DSH-01..05 + DIR-03/05 + erasure | — (fim) |

### O que NÃO fazer
- Não executar E2E-004 isolada — todos os cenários AUD-* são side effects de ações em outros serviços
- Não executar E2E-007 antes de E2E-011 — Flow-03 subsume todos os cenários NTF-*
- Não separar DIR-03/DIR-05 das outras issues — parar containers invalida JWT e força relogin

---

## 10. Contexto Técnico Acumulado (E2E-001)

> Informações descobertas durante E2E-001 que as sessões seguintes precisam saber.

### Portas (host → container)
| Serviço | Porta host | Porta container |
|---|---|---|
| PostgreSQL | 5433 | 5432 |
| ActiveMQ Artemis | 61616 | 61616 |
| Eureka Registry | 18761 | 8761 |
| API Gateway | 18080 | 8080 |
| acs-service | 8088 | 8088 |
| gest-service | 8084 | 8084 |
| evnt-service | 18085 | 8085 |
| pac-service | 8082 | 8082 |
| edge-service | 8087 | 8087 |
| hist-service | 8089 | 8089 |
| dash-service | 18090 | 8090 |

### Gateway (host 18080)
- Rotas de API: `GET :18080/api/v1/<svc>/...` → encaminha para `lb://<svc>-service`
- Actuator health via gateway: `GET :18080/api/v1/<svc>/actuator/health` (StripPrefix=3)
- JWKS: `GET :18080/oauth2/jwks` → `lb://acs-service`
- Rotas não precisam de JWT: `/actuator/**`, `/oauth2/**`, `/api/v1/*/actuator/**`

### Paths internos dos serviços (sem gateway)
| Serviço | Paths dos controllers |
|---|---|
| acs-service | `/api/v1/acs/**`, `/oauth2/**` |
| evnt-service | `/api/v1/events/**` (não `/api/v1/evnt/`) |
| pac-service | `/api/pac/v1/pacientes/**` (não `/api/v1/pac/`) |
| gest-service | `/gest-service/**` (não `/api/v1/gest/`) |
| edge-service | `/api/v1/edge/**`, `/edge-service/**` (misto) |
| hist-service | `/api/v1/hist/**` |
| dash-service | `/panel/**`, `/internal/notifications/**` |

> Atenção: os paths via gateway (`/api/v1/<svc>/`) são mapeamentos do gateway, não os paths reais dos serviços.
> Chamar os serviços diretamente exige usar os paths internos acima.

### Startup do stack
```bash
# Garantir que todos os containers estão UP antes de qualquer teste
docker compose -f docker/docker-compose.dev.yml up -d
# Aguardar todos os serviços registrarem no Eureka (~60s)
# Verificar: GET http://localhost:18761/eureka/apps (8 registros: 7 domain + gateway)
```

---

## 11. Contexto Técnico Acumulado (Sessão 2 — E2E-009 / E2E-003 / E2E-004)

> A sessão 2 revelou uma classe de defeito recorrente: **cada par de serviços foi
> desenvolvido e testado contra um mock do próprio consumidor**, nunca contra o contrato
> real do produtor. Nenhum desses defeitos aparece nos gates unitários — só na integração.

### 11.1 Defeitos de contrato corrigidos

| # | Par | Sintoma | Correção |
|---|---|---|---|
| C-1 | ACS → todos | Authorities publicadas no claim `authorities`; resource servers liam o claim `scope` (default do `JwtGrantedAuthoritiesConverter`) → **403 em todo endpoint com `@PreAuthorize`** | Converter em PAC/EDGE/HIST lê a união de `scope` + `authorities` |
| C-2 | ACS → todos | Nomes de authority do `RoleAuthorityPolicy` (`hist.read`, `dash.read`, `evnt.read`) não correspondiam a nenhuma regra real (`SCOPE_hist:read`, `dash.panel-composition.read`, …) | `RoleAuthorityPolicy` reescrito a partir das regras `@PreAuthorize`/SecurityConfig reais dos 7 serviços |
| C-3 | ACS → PAC/GEST | Claim de tenant: ACS emite `tenant_id`; PAC lê `empresaId`; GEST e os controllers do HIST leem `tenant` | ACS emite os três como aliases do mesmo valor (canônico = `tenant_id`) |
| C-4 | PAC → GEST | Path e payload divergentes (`/api/gest/v1/detectability/check` vs `/gest-service/eligibility`) | Cliente Feign alinhado ao contrato do GEST (corrigido na sessão 2, parte 1) |
| C-5 | PAC → HIST | Path (`/hist-service/api/v1/facts` vs `/api/v1/hist/audit-facts`) **e** body completamente diferentes | `HistFactRequest` reescrito espelhando `RecordAuditFactRequest`; `factId` cunhado uma vez e reusado pelo outbox |
| C-6 | PAC → HIST | Vocabulário de fact types: PAC emitia `comando.published/recalibrated/retired`; HIST só aceitava `comando.recalibrado` | Nomes alinhados (`comando.publicado`/`recalibrado`/`aposentado`); vocabulário do HIST estendido |
| C-7 | PAC → GEST | O novo contrato de eligibility avalia o conjunto de primitivas do tenant e **não** consegue responder "esta primitiva é desconhecida" — requisito EARS do `:validate` | Guarda per-primitiva passou a usar `GET /gest-service/tipo-gesto/{id}` (404 → 422 `pac.primitive.unknown-to-gest`) |
| C-8 | PAC → ACS | `AcsDirectoryClient` chamava paths inexistentes (`/api/acs/v1/empresas/{id}`, `/api/acs/v1/cuidadores/{id}`) → 404 → `pac.cuidador.not-found` (422) **em toda criação de paciente com cuidador responsável**. Os DTOs de resposta também eram package-private (violação da regra "Feign response record sempre `public`") | Paths → `/api/v1/acs/directory/tenants/{id}` e `/api/v1/acs/caregivers/{id}`; DTOs reescritos espelhando `TenantInfoResponse` / `CaregiverResponse` e promovidos a `public` |
| C-9 | HIST → ACS | `AcsActorDirectoryClient` chamava `GET /api/v1/acs/caregivers/{id}/patients` (inexistente) esperando um `List<UUID>` cru → toda leitura de timeline com `hist:read` (cuidadora) caía no fallback do circuit breaker e retornava 503 | Path → `/api/v1/acs/directory/caregivers/{id}/patient-scope`; response tipada como `PatientScopeResponse` |
| C-10 | ACS → **todos** | Multi-tenancy: os 7 serviços usam o identificador de tenant **literalmente como nome de schema** no `MultiTenantConnectionProvider`, mas o ACS emite o **UUID** do tenant enquanto o schema provisionado está em `neuroassist_global.tenant.schema_nome` (`t_emp_dev`) → `relation "paciente_cuidador" does not exist` na primeira escrita real | O provider resolve `tenant_id → schema_nome` (com cache) e cai de volta no identificador quando não há linha — preserva o formato usado pelas suítes. Aplicado em PAC/HIST/EDGE |

| C-11 | HIST → ACS | `neuroassist.acs.url` sem default utilizável em container (`http://localhost:8088` = o próprio HIST) e sem env no compose → toda leitura de timeline com `hist:read` respondia 503 mesmo com C-9 corrigido | Default via `${ACS_SERVICE_URL:…}` + `ACS_SERVICE_URL` no `docker-compose.dev.yml` |
| C-12 | PAC (schema) | `hist_outbox.status` é ENUM nativo no Flyway, mas `HistOutboxJpaEntity` mapeia `String` → o Postgres não coage varchar→enum e **toda gravação no outbox falhava** (`:retire` → 500). Invisível nos testes porque a suíte do PAC roda `ddl-auto=create-drop` e o Hibernate cria a coluna como varchar | `V0207__pac_hist_outbox_status_varchar.sql` converte para `varchar(20)` + CHECK, alinhando schema e mapeamento |
| C-13 | GEST (test) | A suíte do GEST **não compilava**: `EdgePairingCircuitBreakerIT` chamava `getTenantForDevice`, método inexistente em `EdgePairingClient` (o real é `checkPairingAndGetTenant`) — o gate "P1-P9 done" do GEST não era reproduzível | Chamada corrigida no teste |

| C-14 | ACS → GEST | O `TenantContextFilter` do GEST lê o tenant **apenas do JWT decodificado**, mas a `SecurityConfig` nunca habilitou `oauth2ResourceServer` — o Bearer token jamais era decodificado, o tenant ficava nulo, o `search_path` caía no schema global e o gate de eligibility respondia 500 (`relation "tipo_gesto" does not exist`), derrubando o `:publish` de emergência com 503 fail-closed | `oauth2ResourceServer(jwt)` habilitado com o mesmo converter de C-1, mantendo `anyRequest().permitAll()` — decodifica sem alterar a postura de autorização |
| C-15 | GEST (multi-tenancy) | **Terceira** convenção de nome de schema no mesmo sistema: o GEST prefixa `t_` em dois pontos — `TenantContextFilter` (guard 503) e `TenantIdentifierResolver` (antes do connection provider) — enquanto os outros 6 serviços usam o identificador cru e o ACS registra `schema_nome`. O prefixo escondia o identificador real da consulta ao catálogo, então com token real o guard rejeitava com 503 `tenant-not-found` e o provider roteava para um schema inexistente | Guard e resolver passam o identificador cru; o `MultiTenantConnectionProvider` centraliza a resolução: `schema_nome` → `t_<tenant>` (se existir) → identificador |

### 11.1b Hardening pós-Sessão 2 (trabalho paralelo, fora do pipeline do autopilot)

Três frentes independentes por serviço, executadas em paralelo após o fechamento da
Sessão 2. São **pré-requisitos da Sessão 3**, não parte do Flow-01.

| Frente | Serviço(s) | Objetivo |
|---|---|---|
| GEST resource server | gest | ✅ **DONE.** `@EnableMethodSecurity` + `anyRequest().authenticated()` (público só `/actuator/**`) + `STATELESS`; httpBasic e oauth2ResourceServer coexistem. Corrigido de passagem: os usuários in-memory tinham authorities inexistentes (`gest.profile.read`, `gest.contract.read`) que virariam 403 silencioso com method security ligada. Gates: 88 unit, 54/54 BDD, architectureCheck, spotless. `SecurityIT` **passou a passar** — os cenários de 401 anônimo e 403 por escopo eram impossíveis sob `permitAll()` |
| C-10 restante | evnt | ✅ Aplicado. **DASH = N/A**: não tem `MultiTenantConnectionProvider`, `TenantIdentifierResolver` nem pacote `persistence` — é agregador puro (ADR-004, "zero tabelas MVP", `flyway.enabled: false`). A premissa "provider idêntico nos 7 serviços" não vale para ele. Gates: EVNT 68 testes, DASH 31 testes, 0 falhas |
| ~~Prefixos do EDGE~~ | edge | ❌ **Premissa falsa — nenhuma mudança necessária.** Não existe nenhuma authority com prefixo `SCOPE_` no `src/main` do edge-service (nem no HEAD do git); `SCOPE_` aparece ali só em javadoc explicando a ausência do prefixo. O único `SCOPE_` real do monorepo é o do HIST, que é **coerente** (ACS emite `hist:read`/`hist:write`, HIST usa prefixo padrão). A divergência EDGE-sem-prefixo × HIST-com-prefixo é deliberada. Gate rodado mesmo assim: 122 testes, 0 falhas |

> Ponto de atenção levantado no briefing do GEST: exigir autenticação no GEST só é seguro se
> o **edge-service propagar o header `Authorization`** nas chamadas Feign (o pac-service já
> propaga via `feignAuthPropagationInterceptor`). Sem isso, o heartbeat da Sessão 3 quebra.

### 11.1c EVNT — integrações de saída são fakes `@Primary`

Descoberto ao investigar o DIR-02. **Não é só o `CaregiverRoutingClient`**: os quatro ports
de saída do EVNT são implementados por adapters falsos em código de produção, e os
`@FeignClient` correspondentes existem vazios (`// TODO: add methods from
techlab-ai-integration.md`) e desligados de qualquer port:

| Port | Adapter `@Primary` ativo | Comportamento real |
|---|---|---|
| `CaregiverRoutingPort` → ACS | `CaregiverRoutingHardcodedAdapter` | `return "cuidadora-jamilly-fixo"` |
| `AuditTrailPort` → HIST | `AuditTrailNoOpAdapter` | no-op — nada chega ao HIST |
| `NotificationDispatchPort` → DASH | `NotificationDispatchLogAdapter` | `log.info("[ALERTA PoC] …")` |
| `CommandLookupPort` → PAC | `CommandLookupHardcodedAdapter` | snapshot fixo `"Preciso de ajuda"/EMERGENCIA` |

Os quatro clients ainda têm porta default errada: HIST→8087 (é EDGE), ACS→8084 (é GEST),
DASH→8086 (inexistente), PAC→8081 (inexistente).

Por isso os gates do EVNT ficam verdes: a lógica de negócio é real (`ReportIngressService`,
`EventConfirmationService`, 4 `.feature`), mas conversa apenas com fakes internos.

**Bloqueia:** DIR-02, AUD-06/07 e NTF-01..05 — ou seja, boa parte da Sessão 4.
**Escopo sugerido:** substituir os 4 adapters por adapters Feign reais + C-1 + C-10 no EVNT.
Delimitar ao *outbound*: os DTOs de API do EVNT também são `record X(String tenantId /* TODO
*/)`, e "completar os TODOs" sem limite vira reescrita do módulo.

### 11.1d Identidade de serviço (decisão do usuário, pós-hardening)

Resolve C-18/C-19 e a dívida `hist:write` de uma vez. Contrato completo em
**[standards/service-identity.md](../standards/service-identity.md)**.

**Decisão:** credenciais config-driven no ACS (`acs.service-clients`, hash BCrypt via env var),
não tabela — rotação = trocar env + restart; migrar para
`neuroassist_global.service_client` depois só muda a consulta dentro do `ServiceTokenService`.

| Peça | Status |
|---|---|
| ACS — `POST /oauth2/token/service` (`ServiceClientProperties`, `ServiceTokenRequest`, `ServiceTokenService`, `TokenIssuer.issueServiceToken`) | ✅ DONE — 121 testes + architectureCheck |
| EDGE → GEST (`DetectionProfileClient`) | ✅ DONE — 138 testes (+16), archunit + integrationTest |
| PAC → HIST (`HistFactClient`) | ✅ DONE — 177 testes (+17), archunit; provado por controle negativo |
| Remover `hist:write` do `RoleAuthorityPolicy` | ✅ DONE — nenhum papel de cuidador escreve audit fact |
| EVNT → HIST, ACS → HIST | **N/A hoje** — ambos os clientes Feign são stubs vazios com adapter no-op; já nascem com identidade de serviço quando forem implementados |

**Critério de quando usar cada mecanismo:** se a autorização depende de *quem pediu*, propaga
o JWT do chamador (PAC→ACS directory, PAC→GEST eligibility, PAC→EDGE device, HIST→ACS
patient-scope); se é o serviço agindo por conta própria, usa identidade de serviço.

> **Nota de método — teste frágil confirmado por experimento pareado.** O cenário
> `Directory endpoint responds within 100ms p99` (ACS) falhou 2× (151ms, 185ms) durante o
> trabalho e passou depois. Matriz: `com SI + carga alta → FAIL`, `sem SI + carga baixa → PASS`,
> `com SI + carga baixa → PASS`. A variável que correlaciona é **carga**, não o código.
> O primeiro experimento foi inconclusivo por mudar código *e* carga ao mesmo tempo.
> O cenário mede wall-clock de **uma** chamada e a chama de p99 — decidir entre medir
> percentil sobre N chamadas ou tirar o SLA do gate unitário.

### 11.4 Sessão 3 — Device + Heartbeat (2026-08-22)

**Flow-02 (E2E-010): 11/11.** Cadeia completa `registrar → enroll → parear → heartbeat`,
com o dispositivo saindo de `offline`/`ultimaSincronizacao: null` para **`online`** —
o `DSH-03` deixou de testar "o endpoint responde" e passou a testar o ciclo real.

| Issue | Estado |
|---|---|
| E2E-010 (Flow-02) | ✅ completo |
| E2E-006 (DEV-01..04) | ✅ completo |
| E2E-005 (HB-01) | ✅ heartbeat autenticado; **HB-02..05 pendentes** (dependem do `ProfilePointerController`, que lança 501) |
| DSH-03 (liveness) | ✅ |

#### Defeitos corrigidos

| # | Serviço | Sintoma | Causa raiz | Por que o gate não pegou |
|---|---|---|---|---|
| **C-18** | ACS↔EDGE | `edge.device` sem emissor → heartbeat inalcançável | O enroll já emitia `clientId`/`clientSecret`, mas o `tokenEndpoint` apontava para um Keycloak inexistente | Nenhum teste exercitava o par ACS↔EDGE |
| **C-20** | EDGE | `POST /devices` → 409 optimistic-lock; **`dispositivo_edge` com 0 linhas** | `@GeneratedValue` + id atribuído pela aplicação + `@Version` primitivo ⇒ `isNew()` id-based ⇒ `save()` vira `merge()` ⇒ UPDATE em linha inexistente | Suíte BDD do EDGE em **dry-run**; unitários mockam o repositório |
| **C-21** | EDGE | `enroll` → `relation "dispositivo_enrollment" does not exist` | Endpoint anônimo por desenho (ADR-005) ⇒ sem JWT ⇒ sem tenant ⇒ `search_path` no schema global. Ovo-e-galinha: a tabela é por tenant | Mesma razão: os cenários de enroll não executam |
| **C-22** | PAC→EDGE | Todo paciente com dispositivo pareado → 422 | Path **e** body divergentes (`/api/edge/v1/dispositivos/{id}` × `/api/v1/edge/devices/{id}`; `{id,empresaId}` × `{deviceId,tenantRef}`) | WireMock do PAC stubava o contrato imaginado pelo próprio PAC — **4ª ocorrência do padrão** |
| **`.gitignore`** | repo | 61 dos 62 `application/port/out/*.java` fora do versionamento | `out/` sem barra inicial casa em qualquer profundidade | O Gradle compila o **diretório de trabalho**, não o índice do git; nenhum gate local detecta, e não há CI |

#### Soluções estruturais introduzidas

- **`Persistable` para id atribuído pela aplicação** (C-20): as 4 entidades do EDGE declaram
  explicitamente INSERT vs UPDATE, com o adapter decidindo por `existsById`. O mapper sempre
  constrói uma instância nova, então só o repositório sabe se a linha existe.
- **Índices globais como quebra de ovo-e-galinha** (C-21, C-18): `enrollment_directory` e
  `device_directory` no schema global, alcançáveis sem tenant vinculado. **Ponteiro apenas** —
  o hash do segredo nunca sai do schema do tenant.
- **Resolução de tenant fora da transação**: o Hibernate fixa o `search_path` ao adquirir a
  conexão, então vincular o tenant dentro de um método `@Transactional` não re-roteia nada.
  `DeviceEnrollmentService` e `DeviceCredentialVerificationService` existem por isso.
- **`edge.internal` ganhou emissor e propósito**: era exigida por endpoints do EDGE e emitida
  por ninguém; agora é a authority que autoriza o ACS a verificar credencial de dispositivo.

#### Três "falhas" que eram do roteiro, não do sistema

Registradas porque tratá-las como defeito teria gerado correção errada:

1. **409 `pairing-conflict`** — pareamento exclusivo por paciente é invariante correto; faltava
   isolamento entre execuções do script.
2. **409 `already-enrolled`** — `isEnrolled()` é `pairingState != registered`, então parear
   antes do enroll o invalida. **A ordem real da API é registrar → enroll → parear**, não a do
   plano original (`register → claim → enroll`).
3. **`HTTP 000000` no token endpoint** — o EDGE anuncia o hostname interno do container
   (`acs-service:8088`), correto para o dispositivo real na malha docker; só o script, rodando
   no host, precisava traduzir para a porta publicada.

### 11.2 Dívidas conhecidas (não corrigidas nesta sessão)

- **C-16 — escopo de cuidadora no HIST é filtrado em memória, depois da paginação.**
  `HistoryQueryService` pagina primeiro no banco e só então aplica
  `items.filter(allowedPatients.contains(...))`. Três consequências, observadas no Flow-01
  (a cuidadora recebeu `items: []` com `total: 6`):
  1. **Vazamento de contagem** — `total` vem da consulta *sem* escopo, revelando quantos
     fatos existem no tenant a quem não pode vê-los.
  2. **Paginação incorreta** — uma cuidadora pode receber página 0 vazia mesmo tendo fatos
     na página 3, porque o corte de escopo acontece depois do `LIMIT/OFFSET`.
  3. **Comparação de subject errada** — o filtro compara `subject.id` com IDs de *paciente*,
     mas os fatos de comando têm `subject.type = comando`; nenhum fato de comando passaria
     no filtro nem para a cuidadora responsável.

  Correção real = empurrar o escopo para o SQL (`WHERE ... AND subject/paciente IN (:scope)`),
  o que altera o contrato de `AuditRecordRepositoryPort`. Pertence ao backlog do HIST e é
  validado em DSH-01/DSH-02 (Sessão 5).

- **`hist:write` no token do cuidador.** Os produtores de audit fact (PAC, EVNT, EDGE)
  propagam o JWT do chamador nas chamadas Feign em vez de usarem identidade de serviço.
  Consequência: `CUIDADORA` precisa de `hist:write` no token. O correto é client-credentials
  por serviço — abrir issue.
- **Claim de tenant triplicado.** Os aliases `tenant`/`empresaId` são um shim de
  compatibilidade. Convergir os resource servers em `tenant_id` e removê-los.
- **C-18 — `edge.device` não tem emissor: o heartbeat é inalcançável.**
  `POST /api/v1/edge/heartbeat` exige `edge.device` no filter-chain **e** no `@PreAuthorize`.
  O `SPEC-06-security.md` (linha 44) diz que o device agent obtém um token com scope
  `edge.device` + claims `deviceId`/`tenant` "do endpoint de token do ACS" — **esse endpoint
  não existe** (zero ocorrências de `device` em `acs-service/src/main/java`), e o
  `RoleAuthorityPolicy` não emite `edge.device` para papel nenhum. Resultado: **403 garantido**
  no heartbeat do sistema integrado. Decisão necessária antes da Sessão 3: ou o ACS ganha um
  endpoint de device-token, ou o heartbeat passa a aceitar outro mecanismo (ex.: token de
  device emitido pelo próprio EDGE no enroll). **Bloqueia HB-01..05 junto com o
  `ProfilePointerController` que lança 501** — são dois bloqueios independentes.
- **`edge.internal` também não tem emissor** — impacto baixo, porque todo endpoint que o
  aceita também aceita `edge.read` (que os cuidadores têm). Consequência real: **a identidade
  de serviço nunca é usada** — o tráfego service-to-service anda com o token do cuidador.
  Mesma raiz da dívida `hist:write` em token de cuidadora.
- **`permission-catalogue.arch.json` divergente do código (EDGE).** O catálogo canônico
  declara códigos granulares (`edge.device-registration.criar`, `edge.pairing.criar`,
  `edge.liveness.read`, …) que **não** existem no código, que usa códigos coarse
  (`edge.manage`, `edge.read`, `edge.device`, `edge.internal`). O ArchUnit não detecta porque
  o `CrossAspectArchTest` do EDGE tem o catálogo **pinado inline contendo as duas famílias** —
  ou seja, o teste que deveria guardar a sincronia aceita ambos os lados da divergência.
- **ACS não produz audit facts.** Não há produtor de `AutenticacaoRealizada` /
  `AutenticacaoFalhou` — **AUD-01 e AUD-02 são features ausentes**, não falhas de integração.
- **C-17 — EVNT ainda em modo PoC: tenant hardcoded e sem resource server.**
  `infrastructure/security/TenantContextFilter` faz `TenantContextHolder.set("poc")` em
  **toda** requisição HTTP (javadoc: *"PoC stub … JWT-based tenant extraction disabled"*), e
  a `SecurityConfig` é `anyRequest().permitAll()` **sem** `oauth2ResourceServer` — o JWT nem
  chega a ser decodificado. Consequência: o `search_path` do EVNT resolve para o schema
  `"poc"` (inexistente), e a correção C-10 aplicada ali fica **inerte** até o filtro extrair
  o tenant do JWT (como o PAC faz via `TenantJwtValidator`). O único caminho que hoje recebe
  tenant real é o JMS (`MdcContextRestorer`), e esse **já** se beneficia do C-10.
  Bloqueia todo o E2E do EVNT (Sessão 4).
- **Gateway não injeta header de tenant.** `grep -i "tenant\|empresa"` no `src/main` do
  `neuroassist-ai-api-gateway` retorna zero: cada serviço precisa extrair o tenant do JWT
  por conta própria. Relevante para qualquer serviço que dependa de `X-Empresa-Id`.
- **`edge.pairing.url` do GEST aponta para a porta 8090** (DASH), não 8087 (EDGE) —
  `@FeignClient(name = "edge-pairing-client", url = "${edge.pairing.url:http://localhost:8090}")`.
  Resolver na Sessão 3, junto com o heartbeat EDGE ↔ GEST.
- **Divergência test/prod de schema.** A causa-raiz de C-12 não é o ENUM em si: as suítes
  rodam `ddl-auto=create-drop` (Hibernate desenha o schema) enquanto produção roda Flyway,
  então **nenhum teste valida o schema real**. Qualquer divergência entre migration e
  mapeamento JPA só aparece em runtime. Avaliar `ddl-auto=validate` + Flyway nos
  Testcontainers das suítes de integração.
- **C-19 — heartbeat EDGE→GEST bloqueado por identidade, e falha em silêncio.**
  O `edge-service` **propaga** o `Authorization` (tem `feignAuthPropagationInterceptor`
  idêntico ao do PAC — esse ponto de atenção está resolvido). O problema é outro: o token do
  dispositivo é repassado tal e qual para `GET /gest-service/detection-profile/devices/{id}`,
  que agora exige `gest.detection-profile.read`. Um token de dispositivo não tem essa
  authority → GEST responde 403 → `FeignException` → `DetectionProfileFeignAdapter.fallback()`
  → `Optional.empty()` → o heartbeat responde **200 com `profilePointerDegraded: true`**.
  **HB-01 falha silenciosamente**: erro de autorização é indistinguível de "GEST fora do ar".
  Somado a C-18 (`edge.device` sem emissor), o heartbeat hoje não é alcançável com token algum.
  Correção proposta (decisão de arquitetura): EDGE→GEST deveria usar **identidade de serviço**
  (client credentials) em vez de propagar o JWT do chamador — mesma família da dívida
  `hist:write` em token de cuidadora.
- **`fallback()` do `DetectionProfileFeignAdapter` engole 4xx.** O `@CircuitBreaker` sem
  `ignoreExceptions` trata 403/404 como falha de infraestrutura, transformando erro de
  autorização em "degradado". É o mecanismo que esconde o C-19 — e o mesmo padrão que fez o
  C-9 aparecer como "ACS indisponível" em vez de "path errado".
- **`POST /gest-service/detection-contract` é guardado por `gest.detection-contract.read`** —
  uma escrita protegida por authority de leitura. Funciona hoje (o ACS emite `.read` para
  ambos os papéis), mas o modelo está errado; corrigir exige mexer no catálogo e no ACS.
- **EVNT → ACS directory não existe.** `CaregiverRoutingClient` é um stub vazio
  (`// TODO: add methods from techlab-ai-integration.md`) com `url` default apontando para a
  porta 8084 (GEST, não ACS). **DIR-02 é feature ausente** — a Sessão 2 valida o endpoint
  do ACS diretamente; o consumo pelo EVNT fica para a Sessão 4 (Flow-03).

### 11.3 Inventário de TODO / stub / fake (auditoria pós-Sessão 2)

`grep -rn "TODO\|FIXME\|XXX\|HACK" backend/services/*/src/main` → **97 ocorrências**:
hist 32 · evnt 27 · pac 10 · dash 9 · edge 9 · acs 8 · gest 2.

O que importa não é a contagem, é a distinção entre **stub vivo** (alcançável em runtime,
mente sobre funcionar) e **scaffolding morto** (gerado, nunca chamado).

#### Stubs vivos — capacidade ausente disfarçada de presente

| Serviço | Local | O que falta | Bloqueia |
|---|---|---|---|
| **EDGE** | `ProfilePointerController.currentPointerForTenant` + `ProfilePointerService` | Ambos lançam `UnsupportedOperationException` | **HB-01..05 (Sessão 3)** — o ponteiro de perfil do heartbeat EDGE↔GEST não existe |
| **EVNT** | `NotificationOrchestrationController.{processar,reprocessar}` + service | Ambos lançam `UnsupportedOperationException` | **NTF-01..05 (Sessão 4)** |
| **EVNT** | 4 adapters `@Primary` (`CaregiverRoutingHardcoded`, `AuditTrailNoOp`, `NotificationDispatchLog`, `CommandLookupHardcoded`) | Fakes em código de produção; os 4 `@FeignClient` vazios e com porta default errada | **DIR-02, AUD-06/07, NTF-01..05** |
| **ACS** | `HistAuditClient` (vazio) + `HistAuditAdapter` (`// TODO: call histAuditClient.recordFact(...)`) | O ACS **nunca** envia audit fact ao HIST | **AUD-01 e AUD-02** — esta é a causa raiz de "ACS não produz audit facts" |
| **ACS** | `MigProvisioningClient` (vazio) + `MigProvisioningAdapter` (`// TODO: call requestSchema/getSchemaStatus`) | Provisionar tenant **não chama as migrations** | **E2E-009 passo 1 real.** Na Sessão 2 usamos o tenant seedado, então esse caminho nunca foi exercitado |

#### Scaffolding morto — não quebra, mas mente sobre a capacidade

| Serviço | Local |
|---|---|
| HIST | `HashChainRepositoryAdapter`, `StreamHeadRepositoryAdapter`, `IdempotencyEntryRepositoryAdapter`, `ActorRefRepositoryAdapter`, `SubjectRefRepositoryAdapter` — todos os métodos lançam `UnsupportedOperationException("deferred to P3")`, são a **única** implementação dos respectivos ports, e **nenhum** port é referenciado por `application.service` |
| PAC | `TipoGestoRefRepositoryAdapter` (idem) + `TipoGestoRef` com `// TODO: add domain fields` |

> A cadeia de hash do HIST **funciona** (o Flow-01 mostrou `sequenceNo`/`prevHash`/`recordHash`)
> — ela é calculada e gravada em `audit_record` pelo `AuditLedgerAdapter`. As tabelas
> `hash_chain` e `stream_head` e seus adapters são um caminho paralelo não usado.
