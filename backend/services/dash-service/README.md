<!-- OUTPUT-SCHEMA
- 0. Service Overview
- 1. Arquitetura e Estrutura
- 2. Funcionalidades e Fluxos Principais
- 3. Estado Atual da Implementacao
- 4. Qualidade e Evidencias
- 5. Gaps Atuais
- 6. Issues Relacionadas ao Servico
- 7. Maturidade do Servico
- 8. Historico de Evolucao (append-only)
- 9. Estado Atual Resumido (para agentes)
- 10. Proxima Atencao Recomendada
-->

# dash-service -- Operational README
> Package: `com.neuroassist.dash` | Gradle: `:dash-service` | Módulo: `DASH`

# 0. Service Overview

## Papel do serviço
`dash-service` é o Backend-for-Frontend (BFF) do painel do cuidador na plataforma NeuroAssist AI.
É o único ponto de acesso do Angular panel a dados e ações: compõe informações de 5 serviços donos
(ACS, PAC, EVNT, HIST, EDGE), entrega notificações em tempo real via SSE (`/panel/stream`), e faz
proxy de escrita das ações do cuidador (calibração → PAC, confirmação de evento → EVNT, ack de
notificação → EVNT). Porta `8090`.

## Responsabilidades principais
- Composição cross-module: ACS (identidade/scope), PAC (comandos/calibração), EVNT (eventos/acks), HIST (histórico), EDGE (liveness do dispositivo)
- SSE real-time: `GET /panel/stream` — entrega de notificações, heartbeat, replay/catchup
- Write-throughs: calibração (PAC), confirmação (EVNT), ack (EVNT) — proxy de escrita sem estado próprio
- Duas superfícies de auth: `/panel/**` (JWT do cuidador) e `/internal/**` (authority `SVC_EVNT`)

## Fora de escopo (MVP)
- Nenhuma tabela própria (ADR-004: zero tabelas DASH — toda persistência vive nos serviços donos)
- Nenhum broker de mensageria (ADR-005: EVNT notifica DASH via REST `POST /internal/notifications`)
- Nenhuma regra de negócio de domínio — DASH apenas compõe e roteia

## Schemas Postgres consumidos
Nenhum (ADR-004). DASH não tem datasource próprio no MVP.

---

# 1. Arquitetura e Estrutura

## Stack
- Java 21 / Spring Boot 3.5.x
- Spring Cloud OpenFeign (5 clientes upstream)
- Spring Security OAuth2 Resource Server (JWT)
- Resilience4j (circuit breakers por upstream — configuração pendente)
- Micrometer + Prometheus
- Gradle 8.x (Kotlin DSL), Spotless (google-java-format AOSP)

## Organização de pacotes
```
com.neuroassist.dash/
├── api/
│   ├── controller/    # PanelStreamController, PanelCompositionController,
│   │                  # CalibrationProxyController, ConfirmationProxyController,
│   │                  # NotificationDeliveryController
│   ├── dto/           # records request/response para cada fluxo
│   └── exception/     # GlobalExceptionHandler (@RestControllerAdvice)
├── application/
│   ├── port/in/       # 5 use cases: PanelStreamUseCase, PanelCompositionUseCase,
│   │                  # CalibrationProxyUseCase, ConfirmationProxyUseCase,
│   │                  # NotificationDeliveryUseCase
│   ├── port/out/      # 6 outbound ports: IdentityDirectoryPort, CommandCatalogPort,
│   │                  # EventStatePort, HistoryQueryPort, DeviceLivenessPort,
│   │                  # NotificationDispatchBackPort
│   └── service/       # 5 services (stubs — UnsupportedOperationException)
├── domain/            # VAZIO — value objects MISSING
├── infrastructure/
│   ├── clients/       # 5 Feign clients: AcsClient, PacClient, EvntClient,
│   │                  # HistClient, EdgeClient (todos interfaces vazias)
│   ├── config/        # DomainServiceConfig, SecurityConfig (parcial),
│   │                  # FeignClientConfig, HibernateMultiTenancyConfig (DEVE SER REMOVIDO)
│   └── security/      # TenantContextHolder, TenantContextFilter,
│                      # CorrelationIdInterceptor, MultiTenantConnectionProvider (DEVE SER REMOVIDO)
└── shared/
    ├── audit/         # AuditAction, AuditAspect
    ├── error/         # CodedException, ProblemDetailFactory
    └── security/      # AccessContextHolder, DataScope, DataScopeFilterAspect
```

## Regras arquiteturais aplicadas (RULE-01..09)
- RULE-01 ✅ `domain.*` não importa `org.springframework` (ArchUnit enforced)
- RULE-02 ✅ `domain.*` não importa `jakarta.persistence` (ArchUnit enforced)
- RULE-03 ✅ `@Transactional` apenas em `application.service` (ArchUnit enforced)
- RULE-04 ✅ `api.*` não depende de `infrastructure.*` (ArchUnit enforced)
- RULE-05 ✅ `infrastructure.*` não depende de `api.*` (ArchUnit enforced)
- RULE-06 ✅ `@FeignClient` apenas em `infrastructure.clients` (ArchUnit enforced)
- RULE-07 ❌ `TenantContextHolder.require()` AUSENTE em todos os serviços (todos são stubs)
- RULE-08 ⚠️ `open-in-view=false` no `application.yml` ✅, mas datasource não deveria existir (ADR-004)
- RULE-09 ✅ Pacote base `com.neuroassist.dash`

## Multi-empresa: propagação de EmpresaContext
`TenantContextHolder` e `TenantContextFilter` existem mas **não são chamados em nenhum serviço** (todos lançam `UnsupportedOperationException`). RULE-07 não enforçado efetivamente.

---

# 2. Funcionalidades e Fluxos Principais

## Fluxos críticos (conforme SPEC)

| Fluxo | Endpoint | Status |
|---|---|---|
| SSE open session | `POST /dash-service/panel-stream/open-session` | MISSING |
| SSE heartbeat | `POST /dash-service/panel-stream/heartbeat` | MISSING |
| SSE close session | `POST /dash-service/panel-stream/close-session` | MISSING |
| SSE replay catchup | `PUT /dash-service/panel-stream/{id}/replayCatchup` | MISSING |
| Composição summary (home) | `GET /panel/summary` | MISSING |
| Composição patient overview | `GET /panel/patients/{id}/overview` | MISSING |
| Histórico | `GET /panel/patients/{id}/history` | MISSING |
| Device status | `GET /panel/patients/{id}/device` | MISSING |
| Calibração (write-through → PAC) | `POST /panel/calibration-proxy` | MISSING |
| Confirmação (write-through → EVNT) | `POST /panel/confirmation-proxy` | MISSING |
| Notificação interna (EVNT → DASH) | `POST /internal/notifications` | MISSING |

## Eventos emitidos
Nenhum (ADR-005 — sem broker). Comunicação SSE para o browser é o único canal de "push".

## Dependências inter-serviços (Feign clients)

| Client | Upstream | Métodos definidos |
|---|---|---|
| `AcsClient` | acs-service | Vazio (stub) |
| `PacClient` | pac-service | Vazio (stub) |
| `EvntClient` | evnt-service | Vazio (stub) |
| `HistClient` | hist-service | Vazio (stub) |
| `EdgeClient` | edge-service | Vazio (stub) |

---

# 3. Estado Atual da Implementacao

## Implementado (DONE)
- Estrutura hexagonal de pacotes (`api`, `application`, `domain`, `infrastructure`)
- Definições de interfaces in-ports (5 use cases) e out-ports (6 interfaces)
- Stubs de serviços (`@Service`, `@Transactional`, `@RequiredArgsConstructor`, `@Slf4j`)
- Stubs de controllers com `@PreAuthorize` e `@AuditAction`
- DTOs records para todos os fluxos (request + response)
- `GlobalExceptionHandler` scaffoldado
- `TenantContextHolder` + `TenantContextFilter` presentes
- `CorrelationIdInterceptor` presente (X-Correlation-Id outbound)
- `SecurityConfig` básica (JWT + permitAll para actuator)
- `DomainServiceConfig` presente
- ArchUnit tests (RULE-01..06)
- BDD feature files (3 features: notification-delivery, panel-composition, write-through-actions)
- BDD step skeletons (stubs)

## Parcial (PARTIAL / STUB)
- **P1 Scaffold**: estrutura OK mas `build.gradle.kts` inclui `spring-boot-starter-data-jpa` e `spring-boot-starter-artemis` indevidamente (violação ADR-004/ADR-005). `application.yml` tem configuração de datasource. `HibernateMultiTenancyConfig` presente (deve ser removido).
- **P6 Segurança**: SecurityConfig básica presente; falta surface `/internal/**` com `SVC_EVNT` e circuit breakers.
- **P7 Observabilidade**: MDC keys e logging pattern presentes; `CorrelationIdInterceptor` scaffoldado mas não wired.

## Não implementado (MISSING)
- **Domain model**: pacote `domain/` completamente vazio (nenhum value object: `PanelSession`, `NotificationPayload`, `PanelSummary`, `PatientOverview`, `DeviceStatus`, `HistoryPage`)
- **P2 SSE**: Nenhuma implementação de SSE (`SseEmitter`), sem `PanelSessionRegistry`, sem dispatch
- **P3 Composição**: Todos os métodos de `PanelCompositionService` lançam `UnsupportedOperationException`
- **P4 Write-throughs**: `CalibrationProxyService`, `ConfirmationProxyService`, `NotificationDeliveryService` — stubs
- **P5 Adapters**: Todos os 5 Feign clients têm interfaces vazias (sem métodos)
- **P6**: Degradação matrix (hard-503 vs soft-degrade por upstream), circuit breakers Resilience4j
- **P7**: Métricas SSE sessions (`ActiveSseSessions`), logging JSON
- **P8 BDD**: Todos os step implementations lançam exceção; suite não passa

---

# 4. Qualidade e Evidencias

## Testes
- `ArchitectureTest.java`: RULE-01..07 enforçados via ArchUnit — **provável PASS** (sem código nas classes de domínio que viole)
- `CrossAspectArchTest.java`: WebClient só em infra
- `ApplicationContextLoadsIT.java`: contexto Spring carrega (mas datasource em application.yml pode causar falha sem DB)
- `CucumberIT.java` + step stubs: **FAIL** — todos os steps são stubs

## Cobertura funcional
Zero: nenhum comportamento implementado.

## Observabilidade (logs, métricas, MDC)
- MDC keys configuradas: `correlationId`, `tenantId`, `userId` no logging pattern
- `TenantContextFilter` existe mas não integrado em serviços reais
- `CorrelationIdInterceptor` existe mas não registrado no Feign
- Actuator endpoints: `/actuator/health`, `/actuator/prometheus`

---

# 5. Gaps Atuais

1. **ADR-004 violado**: `build.gradle.kts` tem `spring-boot-starter-data-jpa` + PostgreSQL driver + `spring-boot-starter-artemis`. `application.yml` tem datasource. `HibernateMultiTenancyConfig`, `MultiTenantConnectionProvider`, `TenantIdentifierResolver` devem ser removidos.
2. **Domain model vazio**: nenhum value object. Bloqueia P2, P3, P4.
3. **SSE ausente**: nenhuma implementação de `SseEmitter` ou `PanelSessionRegistry` (in-memory).
4. **Feign clients sem métodos**: 5 interfaces vazias — nenhum upstream pode ser chamado.
5. **RULE-07 não enforçado**: `TenantContextHolder.require()` ausente em todos os serviços.
6. **SecurityConfig incompleta**: falta superfície `/internal/**` com `hasAuthority('SVC_EVNT')`.
7. **Circuit breakers não configurados**: Resilience4j no classpath mas zero configuração.
8. **BDD suite não passa**: step implementations são stubs.

---

# 6. Issues Relacionadas ao Servico

| Issue | Título | P | Status |
|---|---|---|---|
| [DASH-001](../../../docs/architecture/backend/issues/DASH-001.md) | Gradle subproject, Spring Boot, no-DB | P1 | PARTIAL |
| [DASH-002](../../../docs/architecture/backend/issues/DASH-002.md) | Hexagonal outbound ports | P1 | DONE |
| [DASH-003](../../../docs/architecture/backend/issues/DASH-003.md) | Transient value objects | P1 | MISSING |
| [DASH-004](../../../docs/architecture/backend/issues/DASH-004.md) | PanelSession + SSE registry | P2 | MISSING |
| [DASH-005](../../../docs/architecture/backend/issues/DASH-005.md) | NotificationDelivery dispatch | P2 | MISSING |
| [DASH-006](../../../docs/architecture/backend/issues/DASH-006.md) | GET /panel/summary | P3 | MISSING |
| [DASH-007](../../../docs/architecture/backend/issues/DASH-007.md) | GET /panel/patients/{id}/history | P3 | MISSING |
| [DASH-008](../../../docs/architecture/backend/issues/DASH-008.md) | GET /panel/patients/{id}/overview | P3 | MISSING |
| [DASH-009](../../../docs/architecture/backend/issues/DASH-009.md) | GET /panel/patients/{id}/device | P3 | MISSING |
| [DASH-010](../../../docs/architecture/backend/issues/DASH-010.md) | POST /panel/calibration-proxy | P4 | MISSING |
| [DASH-011](../../../docs/architecture/backend/issues/DASH-011.md) | POST /panel/confirmation-proxy | P4 | MISSING |
| [DASH-012](../../../docs/architecture/backend/issues/DASH-012.md) | ACS adapter | P5 | MISSING |
| [DASH-013](../../../docs/architecture/backend/issues/DASH-013.md) | PAC adapter | P5 | MISSING |
| [DASH-014](../../../docs/architecture/backend/issues/DASH-014.md) | EVNT adapter | P5 | MISSING |
| [DASH-015](../../../docs/architecture/backend/issues/DASH-015.md) | HIST adapter | P5 | MISSING |
| [DASH-016](../../../docs/architecture/backend/issues/DASH-016.md) | EDGE adapter | P5 | MISSING |
| [DASH-017](../../../docs/architecture/backend/issues/DASH-017.md) | SecurityConfig dual surface | P6 | PARTIAL |
| [DASH-018](../../../docs/architecture/backend/issues/DASH-018.md) | Circuit breakers + degradação matrix | P6 | MISSING |
| [DASH-019](../../../docs/architecture/backend/issues/DASH-019.md) | MDC + CorrelationId propagation | P7 | PARTIAL |
| [DASH-020](../../../docs/architecture/backend/issues/DASH-020.md) | Métricas SSE sessions | P7 | MISSING |
| [DASH-021](../../../docs/architecture/backend/issues/DASH-021.md) | BDD notification-delivery | P8 | MISSING |
| [DASH-022](../../../docs/architecture/backend/issues/DASH-022.md) | BDD panel-composition | P8 | MISSING |
| [DASH-023](../../../docs/architecture/backend/issues/DASH-023.md) | BDD write-through-actions | P8 | MISSING |
| [DASH-024](../../../docs/architecture/backend/issues/DASH-024.md) | Integration tests | P8 | MISSING |

---

# 7. Maturidade do Servico

**Score: 12/100 — Inicial**

Scaffold hexagonal completo em estrutura de pacotes e interfaces. Zero lógica implementada.
ADR-004 e ADR-005 violados no build.gradle.kts. Domain model vazio. RULE-07 não enforçado.
O serviço não pode iniciar sem um PostgreSQL ativo (dependência JPA indevida no classpath).

---

# 8. Historico de Evolucao (append-only)

- 2026-08-18: Scaffold gerado pelo `be-module-generator.py` (D-540). Estrutura hexagonal + ArchUnit + BDD. `compileJava` OK, ArchUnit RULE-01..06 OK, `AppContextLoadsIT` OK (com DB). Score: 12/100 Inicial.
- 2026-08-20: BASELINE gerado pelo be-validator. Estado real consolidado: P1 parcial (ADR-004/005 violados), P2-P8 MISSING. Prioridade imediata: P1 (remover JPA/Artemis, criar value objects) → P2 (SSE).

---

# 9. Estado Atual Resumido (para agentes)

```
STRUCTURAL:    PARTIAL   (hexagonal structure OK; JPA/Artemis indevidos; domain/ vazio)
FUNCTIONAL:    LOW       (todos os services/controllers lançam UnsupportedOperationException)
INTEGRATION:   LOW       (5 Feign clients vazios; nenhum upstream chamável)
OBSERVABILITY: PARTIAL   (MDC + CorrelationIdInterceptor scaffoldados; não wired)
RESILIENCE:    LOW       (Resilience4j no classpath; zero circuit breakers configurados)
MULTI-EMPRESA: LOW       (TenantContextHolder.require() ausente em todos os services — RULE-07 violado)
```

---

# 10. Proxima Atencao Recomendada

**P1 (bloqueante):**
1. Remover `spring-boot-starter-data-jpa`, PostgreSQL driver, `spring-boot-starter-artemis` do `build.gradle.kts`
2. Remover datasource do `application.yml`, `HibernateMultiTenancyConfig`, `MultiTenantConnectionProvider`, `TenantIdentifierResolver` (violações ADR-004/ADR-005)
3. Criar value objects em `domain/model/`: `PanelSession`, `NotificationPayload`, `PanelSummary`, `PatientOverview`, `DeviceStatus`, `HistoryPage`

**P2 (caminho crítico — golden path):**
4. Implementar `PanelSessionRegistry` (in-memory `ConcurrentHashMap`) + `SseEmitter` em `PanelStreamService`
5. Implementar `NotificationDeliveryService` (dispatch → SSE push → ack idempotente)

END DOCUMENT
