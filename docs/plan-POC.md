# NeuroAssist AI — Plano de Implementação PoC

> **Objetivo:** provar o fluxo completo `gesto calibrado → edge detecta → EVNT persiste Evento
> com `tier: full_screen_alarm` → cuidador vê o alarme` com o mínimo de código necessário.
> O desenvolvimento completo ocorre após validação do conceito.

**Criado em:** 2026-07-30  
**Contexto:** scaffold BE gerado (gest-service, evnt-service, acs-service) — compileJava OK, ArchUnit OK.  
**Serviços ativos no PoC:** `gest-service` + `evnt-service` + PostgreSQL.  
**Serviços excluídos do PoC:** GWY, REG, ACS real, EDGE enrollment, HIST, DASH, MIG service, broker de mensagens.

---

## Escopo do PoC — o que se prova

```
[Python script]  POST /api/v1/reports  →  evnt-service
                                              ↓ valida report
                                         gest-service  POST /recognized-commands
                                              ↓ ok
                                         Evento persiste (urgencia=EMERGENCIA, tier=FULL_SCREEN_ALARM)

[curl/Postman]   GET /api/v1/events/active  →  evnt-service
                                              ↓
                                         activeEvent.tier = "full_screen_alarm" ✓
```

---

## Simplificações fixas para o PoC

| Componente | Substituição |
|---|---|
| Multi-tenancy dinâmica | `TenantContextFilter` retorna tenant fixo `"poc"` — ignora JWT |
| JWT / OAuth2 | `SecurityConfig` → `permitAll()` nos dois serviços |
| Artemis / broker | Remover `spring-boot-starter-artemis` dos dois `build.gradle.kts` |
| PAC (`CommandLookupPort`) | `CommandLookupHardcodedAdapter` → retorna `urgencia=EMERGENCIA, label="Preciso de ajuda"` |
| ACS (`CaregiverRoutingPort`) | `CaregiverRoutingHardcodedAdapter` → retorna `"cuidadora-jamilly-fixo"` |
| Notificação (`NotificationDispatchPort`) | `NotificationDispatchLogAdapter` → `log.info` |
| HIST (`AuditTrailPort`) | `AuditTrailNoOpAdapter` → no-op |
| EDGE (`EdgePairingPort`) em gest-service | `EdgePairingPortStub` → retorna tenant `"poc"` |
| GWY / REG | Chamadas diretas nas portas locais |
| Edge agent real | Script Python com payload hardcoded |
| SSE / notificação push | `GET /events/active` (polling) |

---

## Fase P0 — Infraestrutura e simplificação transversal

**Estimativa: ½ dia**

### P0.1 — Docker Compose mínimo

Arquivo: `backend/docker-compose.poc.yml`

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB:       neuroassist_poc
      POSTGRES_USER:     neuroassist
      POSTGRES_PASSWORD: poc123
    ports:
      - "5432:5432"
```

### P0.2 — Desligar multi-tenancy (ambos os serviços)

Arquivo: `infrastructure/security/TenantContextFilter.java` (gest-service e evnt-service)

```java
// substitui a leitura do JWT tenant claim
TenantContextHolder.setTenant("poc");
```

O Hibernate usa o schema `neuroassist_poc` para ambos os serviços.

### P0.3 — Desligar validação JWT (ambos os serviços)

Arquivo: `infrastructure/config/SecurityConfig.java`

```java
http.authorizeHttpRequests(r -> r.anyRequest().permitAll())
    .csrf(AbstractHttpConfigurer::disable);
// remover .oauth2ResourceServer(...)
```

### P0.4 — Desligar Artemis (ambos os serviços)

Nos `build.gradle.kts`: remover (ou comentar) `implementation("org.springframework.boot:spring-boot-starter-artemis")`.  
Os producers já existem no scaffold — ficam inertes sem broker.

---

## Fase P1 — gest-service: catálogo + perfil + validação

**Estimativa: 2 dias**

### P1.1 — Flyway migration `V1__gest_poc_schema.sql`

Local: `src/main/resources/db/migration/V1__gest_poc_schema.sql`

4 tabelas (mapeiam as JPA entities existentes):
- `tipo_gesto` (id, nome, detectability_state, engine_feature_ref, created_at, updated_at)
- `tipo_gesto_transition` (id, tipo_gesto_id, from_state, to_state, actor, reason, at)
- `detection_profile` (id, version, active, contract_version, classification_params jsonb, published_at)
- `detection_profile_primitive` (id, profile_id, tipo_gesto_id, nome, engine_feature_ref)

### P1.2 — `TipoGestoService` (corpo dos métodos)

Classe existente: `application/service/TipoGestoService.java`

Implementar:
- `registrar(nome)` → persiste com `detectabilityState = REGISTERED`
- `transitar(id, toState, engineFeatureRef, reason)`:
  - Valida transição no `TipoGestoDomainService` (máquina de estados)
  - Persiste nova linha em `tipo_gesto_transition`
  - Atualiza `tipo_gesto.detectability_state`
- `listar(stateFilter)` → query por tenant + estado opcional

### P1.3 — `TipoGestoRepositoryAdapter` (corpo dos métodos)

Classe existente: `infrastructure/persistence/adapter/TipoGestoRepositoryAdapter.java`  
Delegar para `TipoGestoJpaRepository` (já gerado).

### P1.4 — `DetectionProfileService` (corpo dos métodos)

Classe existente: `application/service/DetectionProfileService.java`

Implementar:
- `publicarPerfil(params)`:
  - Query `TipoGesto` com `state = SUPPORTED` para o tenant
  - Aloca versão monotônica (MAX + 1)
  - Persiste `DetectionProfile` + `DetectionProfilePrimitive` por gesto
  - Seta anteriores `active = false`
- `buscarAtivo()` → query `WHERE active = true`

### P1.5 — `DetectionProfileRepositoryAdapter` (corpo dos métodos)

Classe existente: `infrastructure/persistence/adapter/DetectionProfileRepositoryAdapter.java`

### P1.6 — `POST /recognized-commands` — validação stateless

Controller existente: chamado por EVNT para validar o report antes de persistir o Evento.

Lógica:
1. Buscar `DetectionProfile` pela `profileVersion` do report
2. Verificar que cada `tipoGestoId` do report pertence ao perfil
3. Retornar `{ "valid": true, "contractVersion": "1.0.0" }` ou `422`
4. **Não persistir nada** — endpoint stateless

### P1.7 — `EdgePairingPortStub`

Classe nova (PoC): `infrastructure/persistence/adapter/EdgePairingPortStub.java`

```java
@Primary @Component
public class EdgePairingPortStub implements EdgePairingPort {
    public String getTenantForDevice(String deviceId) { return "poc"; }
}
```

---

## Fase P2 — evnt-service: ingestão + consulta + confirmação

**Estimativa: 2 dias**

### P2.1 — Flyway migration `V1__evnt_poc_schema.sql`

Local: `src/main/resources/db/migration/V1__evnt_poc_schema.sql`

3 tabelas:
- `evento` (id, paciente_id, comando_ref, command_label_snapshot, urgencia_snapshot, timing_window jsonb, feedback, confirmado_por, confirmado_at, recognized_at, created_at)
- `notification_dispatch` (id, evento_id, tier, target_caregiver_id, status, attempts, last_attempt_at)
- `report_idempotency` (report_id PK, evento_id, processed_at)

### P2.2 — Adapters hardcoded para dependências externas

4 classes novas em `infrastructure/persistence/adapter/` (ou `infrastructure/stub/`):

```java
// CommandLookupHardcodedAdapter.java
@Primary @Component
public class CommandLookupHardcodedAdapter implements CommandLookupPort {
    public CommandSnapshot lookup(String commandRef) {
        return new CommandSnapshot(commandRef, "Preciso de ajuda", Urgencia.EMERGENCIA);
    }
}

// CaregiverRoutingHardcodedAdapter.java
@Primary @Component
public class CaregiverRoutingHardcodedAdapter implements CaregiverRoutingPort {
    public String resolveCaregiver(String pacienteId) { return "cuidadora-jamilly-fixo"; }
}

// NotificationDispatchLogAdapter.java
@Primary @Component
public class NotificationDispatchLogAdapter implements NotificationDispatchPort {
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchLogAdapter.class);
    public void dispatch(NotificationDispatch d) {
        log.info("[ALERTA PoC] tier={} caregiver={} evento={}", d.tier(), d.caregiverId(), d.eventoId());
    }
}

// AuditTrailNoOpAdapter.java
@Primary @Component
public class AuditTrailNoOpAdapter implements AuditTrailPort {
    public void append(AuditFact fact) { /* PoC: no-op */ }
}
```

### P2.3 — `GestValidationClient` (Feign — chamada real para gest-service)

Classe existente: `infrastructure/clients/GestValidationClient.java`  
Configurar URL no `application.yml`:

```yaml
spring:
  application:
    name: evnt-service

gest-service:
  url: http://localhost:8082

feign:
  client:
    config:
      gest-service:
        url: http://localhost:8082
```

### P2.4 — `ReportIngressService` (corpo do método principal)

Classe existente: `application/service/ReportIngressService.java`

Fluxo do método `ingest(ReportIngressRequest)`:

```
1. reportIdempotencyPort.exists(reportId)?
   → sim: buscar Evento original, retornar 200 com status="duplicate"

2. gestValidationPort.validate(report)
   → inválido: lançar exceção → 422

3. commandLookupPort.lookup(commandRef)
   → CommandSnapshot (urgencia, label)

4. caregiverRoutingPort.resolveCaregiver(pacienteId)
   → caregiverId

5. Criar Evento (feedback=NAO_AVALIADO, urgenciaSnapshot=EMERGENCIA)
6. Criar NotificationDispatch (tier=FULL_SCREEN_ALARM, status=QUEUED)
7. eventoRepositoryPort.save(evento)
8. notificationDispatchRepositoryPort.save(dispatch)
9. reportIdempotencyPort.save(reportId, eventoId)
10. notificationDispatchPort.dispatch(dispatch)   ← log
11. auditTrailPort.append(...)                    ← no-op
12. Retornar 201 com eventoId + tier
```

### P2.5 — `EventoRepositoryAdapter` + `NotificationDispatchRepositoryAdapter` + `ReportIdempotencyRepositoryAdapter`

Classes existentes: implementar delegação para os JpaRepositories gerados.

### P2.6 — `EventQueryService.getActive()`

Classe existente: `application/service/EventQueryService.java`

Query: `WHERE feedback = 'NAO_AVALIADO' ORDER BY urgencia_snapshot DESC, recognized_at DESC`  
Montar `ActiveEventsResponse` com `activeEvent` = primeiro da lista.

### P2.7 — `EventConfirmationService`

Classe existente: `application/service/EventConfirmationService.java`

```
1. Buscar Evento por id
2. Se feedback != NAO_AVALIADO → 409
3. Setar feedback = REAL | FALSO_POSITIVO
4. Registrar confirmadoPor (hardcoded "cuidadora-jamilly-fixo" no PoC)
5. auditTrailPort.append(...) → no-op
6. Retornar 200
```

---

## Fase P3 — Seed + Script + Teste

**Estimativa: ½ dia**

### P3.1 — Seed SQL

Arquivo: `backend/poc-seed.sql` (executar manualmente após o Flyway)

```sql
-- Schema neuroassist_poc deve existir (criado pelo Flyway)
SET search_path = neuroassist_poc;

-- Primitivo já em supported (bypass do lifecycle para o PoC)
INSERT INTO tipo_gesto (id, nome, detectability_state, engine_feature_ref, created_at, updated_at)
VALUES ('tg-piscar-duplo-001', 'piscar duplo', 'SUPPORTED', 'mp.blink.double.v1', now(), now())
ON CONFLICT DO NOTHING;

-- Perfil ativo version=1
INSERT INTO detection_profile (id, version, active, contract_version, published_at)
VALUES ('prof-v1-001', 1, true, '1.0.0', now())
ON CONFLICT DO NOTHING;

INSERT INTO detection_profile_primitive (id, profile_id, tipo_gesto_id, nome, engine_feature_ref)
VALUES ('pp-001', 'prof-v1-001', 'tg-piscar-duplo-001', 'piscar duplo', 'mp.blink.double.v1')
ON CONFLICT DO NOTHING;
```

### P3.2 — Script edge agent

Arquivo: `backend/poc-edge-agent.py`

```python
#!/usr/bin/env python3
"""
PoC Edge Agent — simula o agente local detectando um gesto e enviando para EVNT.
Uso: python poc-edge-agent.py
"""
import uuid, requests, json
from datetime import datetime, timezone

EVNT_BASE = "http://localhost:8083/api/v1"
PACIENTE_ID  = "paciente-marcio-fixo"
COMMAND_REF  = "cmd-preciso-ajuda-fixo"
TIPO_GESTO_ID = "tg-piscar-duplo-001"
PROFILE_VERSION = 1
DEVICE_ID    = "device-quarto-marcio-fixo"

report = {
    "reportId":      str(uuid.uuid4()),
    "pacienteId":    PACIENTE_ID,
    "commandRef":    COMMAND_REF,
    "primitives":    [{"tipoGestoId": TIPO_GESTO_ID, "confidence": 0.91}],
    "confidence":    0.91,
    "profileVersion": PROFILE_VERSION,
    "timingWindow":  {"startMs": 1000, "endMs": 1850, "durationMs": 850},
    "recognizedAt":  datetime.now(timezone.utc).isoformat(),
    "deviceId":      DEVICE_ID
}

headers = {"X-Correlation-Id": str(uuid.uuid4()), "Content-Type": "application/json"}

print("Enviando report para EVNT...")
print(json.dumps(report, indent=2))

r = requests.post(f"{EVNT_BASE}/reports", json=report, headers=headers)
print(f"\nResposta: {r.status_code}")
print(json.dumps(r.json(), indent=2, ensure_ascii=False))

if r.status_code == 201:
    evento_id = r.json().get("eventoId")
    print(f"\n✓ Evento criado: {evento_id}")
    print(f"  tier: {r.json().get('notification', {}).get('tier')}")
    print(f"\nVerificar alarme:")
    print(f"  curl {EVNT_BASE}/events/active")
```

### P3.3 — Sequência de execução do teste

```bash
# 1. Subir PostgreSQL
docker compose -f backend/docker-compose.poc.yml up -d

# 2. Aguardar PostgreSQL pronto
docker compose -f backend/docker-compose.poc.yml logs -f postgres

# 3. Subir gest-service (porta 8082)
cd backend && gradle :gest-service:bootRun

# 4. Subir evnt-service (porta 8083) — em outro terminal
cd backend && gradle :evnt-service:bootRun

# 5. Aplicar seed (após Flyway rodar no startup)
psql -h localhost -U neuroassist -d neuroassist_poc -f poc-seed.sql

# 6. Disparar o "gesto"
python backend/poc-edge-agent.py

# 7. Verificar alarme (simula tela da cuidadora)
curl -s http://localhost:8083/api/v1/events/active | python -m json.tool

# Esperado:
# activeEvent.urgencia = "EMERGENCIA"
# activeEvent.tier     = "FULL_SCREEN_ALARM"
# feedback             = "NAO_AVALIADO"

# 8. Confirmar (simula cuidadora respondendo)
EVENT_ID="<eventoId do passo 6>"
curl -s -X POST http://localhost:8083/api/v1/events/$EVENT_ID/confirmation \
     -H "Content-Type: application/json" \
     -d '{"verdict":"real"}' | python -m json.tool

# Esperado: feedback = "REAL"
```

---

## Critérios de sucesso do PoC

| # | Critério | Como verificar |
|---|---|---|
| 1 | gest-service sobe sem erros | `gradle :gest-service:bootRun` → `Started GestApplication` |
| 2 | evnt-service sobe sem erros | `gradle :evnt-service:bootRun` → `Started EvntApplication` |
| 3 | Report aceito (201) | `poc-edge-agent.py` retorna 201 + eventoId |
| 4 | Alarme visível | `GET /events/active` → `tier: FULL_SCREEN_ALARM` |
| 5 | Confirmação funciona | `POST .../confirmation` → `feedback: REAL` |
| 6 | Idempotência | Reenviar mesmo `reportId` → retorna 200, sem novo Evento |

---

## O que NÃO está no PoC (para a implementação completa)

- JWT real emitido pelo ACS + validação de assinatura
- Multi-tenancy dinâmica por JWT claim
- Enrollment do dispositivo edge via EDGE service
- Lookup real do Comando via PAC
- Resolução real de cuidador via ACS
- SSE live stream (`GET /events/stream`)
- Notificação real (push/e-mail)
- HIST — trilha de auditoria imutável
- DASH — painel Angular completo
- GWY — roteamento centralizado
- REG — service discovery
