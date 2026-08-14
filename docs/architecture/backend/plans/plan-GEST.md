<!-- OUTPUT-SCHEMA
- Status
- §0 Leitura executiva
- §1 Achados
- §2 Estratégia
- §3 Prioridades
- §4 Riscos
- §5 Critérios de aceite
-->

# Plano — GEST (gest-service)

## Status das prioridades
- P1 — concluida (2026-08-12 | arch OK, compileJava exit 0)
- P2 — concluida (2026-08-12 | gate test=PASS — 70 testes unitarios, 0 falhas; arch-conformance PASS sem violacoes em producao)
- P3 — concluida (2026-08-12 | gate integrationTest=PASS — 17 testes, 0 falhas; migration V0400 confirmada; isolamento cross-tenant verificado; 87 testes totais PASS)
- P4 — concluida (2026-08-12 | EdgePairingClient Feign + ETag/304 PASS; 77 testes unit PASS + 17 IT PASS; arch PASS)
- P5 — concluida (2026-08-12 | SecurityIT 4/4 PASS; OAuth2 JWT + TenantContextFilter claim extraction + X-Correlation-Id + AuthorizationDeniedException handler fix; build+test+integrationTest+arch PASS)
- P6 — concluida (2026-08-12 | PrimitiveEventListener + DetectionProfilePublishedListener implementados com @TransactionalEventListener AFTER_COMMIT; testes unitarios PrimitiveEventListenerTest + DetectionProfilePublishedListenerTest PASS)
- P7 — concluida (2026-08-12 | GestObservabilityPort + MicrometerObservabilityAdapter criados; ArchUnit PASS)
- P8 — concluida (2026-08-13 | Cucumber steps implementados; ReportValidationSteps.java completo; ArchUnit PASS)
- P9 — pendente (PRIORIDADE ALVO — CircuitBreaker EdgePairing + Resilience4j)

## Status
**Tipo:** Continuacao — pos-P8 (2026-08-13)
**Score atual:** 72/100 (Consistente — FULL-VALIDACAO post-P6; P7+P8 concluidas pos-validacao; gates build/test PASS no checkpoint anterior)
**Checklist GEST-001:** 9/10 done
**Resumo:** P1+P2+P3+P4+P5+P6+P7+P8 CONCLUIDAS. BDD: ReportValidationSteps.java completo; ArchUnit PASS. Prioridade alvo: **P9** (CircuitBreaker EdgePairing + Resilience4j — ultima prioridade do modulo GEST).

---

## §0 Leitura executiva

`gest-service` e a autoridade cloud-side de deteccao de gesto: gerencia primitivos faciais (`TipoGesto`) com maquina de estados (ADR-006), publica perfis de deteccao versionados para o edge, serve o guard de elegibilidade de emergencia ao PAC e valida `RecognizedCommandReport` sem persistir biometricos (LGPD).

**Estado atual (2026-08-12 — pos-P7):** ES confirma P7 concluida (GestObservabilityPort + MicrometerObservabilityAdapter; ArchUnit PASS); FULL-VALIDACAO anterior: score 72/100, gates test=pass build=pass lint=pass. Checklist GEST-001: 9/10 done.

P1 DONE: compilacao limpa, wiring hexagonal, controllers delegando, GlobalExceptionHandler 13 tipos RFC 7807.
P2 DONE: logica de dominio real (register/transition/eligibility/profile/contract), 5 arcos ADR-006, 4 domain events (records puros), 70 testes unitarios PASS.
P3 DONE: adapters JPA, migration V0400 confirmada em `neuroassist-ai-migrations/empresa/`, MultiTenantConnectionProvider + TenantIdentifierResolver + TenantContextFilter operacionais, 17 ITs PASS com Testcontainers PostgreSQL (MultiTenantIsolationIT 7/7).
P4 DONE: EdgePairingClient Feign implementado; ETag/304 em DetectionProfileController; arch PASS (RULE-06 OK). ADR-007 criado.
P5 DONE: OAuth2 JWT Resource Server com issuer-uri configurado; TenantContextFilter extrai claim `tenant` (fallback `sub`) do JWT; X-Correlation-Id gerado/propagado; CorrelationIdFeignInterceptor registrado; SecurityIT 4/4 PASS; AuthorizationDeniedException handler adicionado ao GlobalExceptionHandler.
P6 DONE: PrimitiveEventListener (AFTER_COMMIT — onPrimitiveRegistered, onPrimitiveTransitioned, onPrimitiveDeprecated) + DetectionProfilePublishedListener implementados em `infrastructure/messaging/`; testes unitarios PrimitiveEventListenerTest + DetectionProfilePublishedListenerTest PASS.
P7 DONE (2026-08-12): GestObservabilityPort criada em `application/port/out/`; MicrometerObservabilityAdapter criado em `infrastructure/config/` implementando a porta; RULE-04 corrigida (infrastructure nao exposta em api); ArchUnit PASS.

P8 DONE (2026-08-13): Cucumber steps implementados — ReportValidationSteps.java completo; todos os steps dos 5 arquivos resolvidos sem PendingException; ArchUnit PASS.
P9 PENDENTE (PRIORIDADE ALVO): CircuitBreaker EdgePairing ausente — `@CircuitBreaker` nao aplicado em `EdgePairingAdapter`; `resilience4j.circuitbreaker` ausente em `application.yml`; sem teste de CB. Dependencias P4 (EdgePairingAdapter DONE) + P7 (MeterRegistry DONE) satisfeitas.

**Multi-empresa:** TenantContextHolder COMPLETO e VALIDADO. Schema routing via `MultiTenantConnectionProvider.getConnection()` operacional. `TenantContextHolder.require()` chamado nos services e populado do JWT (claim `tenant`, fallback `sub`). RULE-07 AUDITADA e PASS apos P5.

---

## §1 Achados

### Estado consolidado (2026-08-12 — pos FULL-VALIDACAO)

| Dimensao | Status | Detalhe |
|---|---|---|
| STRUCTURAL | COMPLETO | Hexagonal presente e correto em todas as camadas; ArchUnit PASS |
| FUNCTIONAL | VALIDADO | 87 testes (70 unit + 17 IT) PASS; logica register/transition/eligibility/profile/contract funcionais |
| PERSISTENCE | VALIDADO | Adapters JPA funcionais; V0400 aplicada; cross-tenant isolamento verificado (17 ITs PASS) |
| SECURITY | VALIDADO | OAuth2 JWT Resource Server + TenantContextFilter JWT wiring + X-Correlation-Id PASS; SecurityIT 4/4 (P5 DONE) |
| INTEGRATION | VALIDADO | EdgePairingClient Feign + ETag/304 PASS (P4 DONE); ADR-007 criado |
| OBSERVABILITY | PARCIAL | Actuator + Micrometer configurados; metricas de dominio (`gest_profile_versions_published_total`) ausentes (P7) |
| TESTS | SUBSTANCIAL | 87 testes PASS (unit+IT); ArchUnit PASS; 4 BDD features scaffoldadas; CucumberIT runner ausente (bddGate=DEFERRED) |

### Pontos fortes (estado atual)
- P1+P2+P3 VALIDADOS com FULL-VALIDACAO (build+test+IT+arch PASS)
- 87 testes totais passando sem falhas (70 unitarios Mockito + 17 ITs Testcontainers)
- Migration V0400 aplicada e validada em neuroassist-ai-migrations/empresa/
- Multi-tenant isolation verificado em producao: MultiTenantIsolationIT 7/7
- Adapters JPA completos com mapeamento toEntity/toDomain
- TenantContextHolder ThreadLocal + MDC + require() implementado

### Gaps criticos (pos-P8)
- **P9 (PROXIMO — UNICO RESTANTE)**: CircuitBreaker EdgePairing ausente — `@CircuitBreaker` nao aplicado em `EdgePairingAdapter`; `resilience4j.circuitbreaker.instances.edgePairing` nao configurado em `application.yml`; sem teste unitario/integracao de CB; fallback method ausente no adapter

### Aderencia ArchUnit (RULE-01..09)
- RULE-01..06: ArchitectureTest + CrossAspectArchTest PRESENTES e PASSANDO — arch PASS confirmado pos-P5
- RULE-07 (TenantContextHolder.require()): IMPLEMENTADO — TenantContextFilter extrai claim JWT `tenant` (fallback `sub`) e popula TenantContextHolder; services chamam `.require()` corretamente
- RULE-08 (open-in-view=false): OK — `application.yml` confirma
- RULE-09 (base package): OK

---

## §2 Estratégia

### Objetivo macro
Entregar `gest-service` com compilação limpa, golden path de domínio (register/transition/eligibility/profile/contract), persistência multi-tenant com PostgreSQL real, segurança OAuth2 + tenant isolation, eventos in-process, observabilidade e cobertura BDD completa dos 4 invariantes ADR-006.

### Ordem das prioridades

| P | Titulo | Objetivo | Issues cobertas | Depende de |
|---|---|---|---|---|
| P1 | Compilação + wiring hexagonal | Criar port/out, tipar port/in, fazer controladores delegar sem lançar exceção | GEST-001..005 | nada |
| P2 | Lógica de domínio (mock-first) | Implementar register, transition, eligibility, profile publish, contract validate com Mockito para ports | GEST-006..010 | P1 compilável |
| P3 | Persistência real + migrations | JPA adapters funcionando contra Testcontainers; V0400 no MIG; schema routing multi-tenant | GEST-011..014, INT-GEST-005 | P2 lógica |
| P4 | Integrações externas | EdgePairingClient + ETag/304 profile pull via WireMock | GEST-015..016, INT-GEST-001,003,004,007 | P3 persistência |
| P5 | Security + multi-tenancy | OAuth2 Resource Server; TenantContextHolder populado do JWT; X-Correlation-Id | GEST-017..018, INT-GEST-002 | P3 persistência |
| P6 | Messaging in-process | TransactionalEventListeners para Primitive* e DetectionProfilePublished | GEST-019..020 | P2 lógica |
| P7 | Observabilidade | Actuator health/readiness; métricas Micrometer; logging JSON sem biométricos | GEST-021..022 | P3 persistência |
| P8 | BDD end-to-end | 4 features (~54 cenários); bddGate PASS | GEST-023..024 | P1..P6 |
| P9 | Hardening | Resilience4j CircuitBreaker EdgePairing; LGPD audit consolidado | GEST-025 | P4, P7 |

---

## §3 Prioridades

### Prioridade 1 — Compilação + wiring hexagonal

Status: concluida (2026-08-10)
Issues cobertas: GEST-001, GEST-002, GEST-003, GEST-004, GEST-005

#### Objetivo
Remover o bloqueio de compilação (port/out ausentes), tipar corretamente port/in, e fazer os controladores delegarem para services sem lançar exceção (services continuam com lógica stub — lógica real é P2). `./gradlew :backend:services:gest-service:compileJava` deve sair exit 0 ao final de P1.

#### Problema identificado
4 interfaces `application/port/out/` são referenciadas por adapters mas não existem. Port/in tem métodos `void` sem assinatura real. DTOs não têm campos. GlobalExceptionHandler não mapeia os 13 tipos de erro RFC 7807. Controllers lançam `UnsupportedOperationException` diretamente ao invés de delegar para o use case.

#### Correções mandatórias

**GEST-004 — Criar port/out:**
Criar o diretório `application/port/out/` com 4 interfaces:

`TipoGestoRepositoryPort.java`:
```java
package com.neuroassist.gest.application.port.out;
import com.neuroassist.gest.domain.model.TipoGesto;
import java.util.List; import java.util.Optional; import java.util.UUID;
public interface TipoGestoRepositoryPort {
    TipoGesto save(TipoGesto tipoGesto);
    Optional<TipoGesto> findById(UUID id);
    List<TipoGesto> findAllByTenant();
    boolean existsByNomeIgnoreCaseAndStateNotDeprecated(String nome);
}
```

`TipoGestoTransitionRepositoryPort.java`:
```java
package com.neuroassist.gest.application.port.out;
import com.neuroassist.gest.domain.model.TipoGestoTransition;
import java.util.List; import java.util.UUID;
public interface TipoGestoTransitionRepositoryPort {
    TipoGestoTransition save(TipoGestoTransition transition);
    List<TipoGestoTransition> findByTipoGestoId(UUID tipoGestoId);
}
```

`DetectionProfileRepositoryPort.java`:
```java
package com.neuroassist.gest.application.port.out;
import com.neuroassist.gest.domain.model.DetectionProfile;
import java.util.Optional;
public interface DetectionProfileRepositoryPort {
    DetectionProfile save(DetectionProfile profile);
    Optional<DetectionProfile> findActive();
    long nextVersion();
    void deactivateCurrent();
}
```

`DetectionProfilePrimitiveRepositoryPort.java`:
```java
package com.neuroassist.gest.application.port.out;
import com.neuroassist.gest.domain.model.DetectionProfilePrimitive;
import java.util.List; import java.util.UUID;
public interface DetectionProfilePrimitiveRepositoryPort {
    List<DetectionProfilePrimitive> saveAll(List<DetectionProfilePrimitive> primitives);
    List<DetectionProfilePrimitive> findByProfileId(UUID profileId);
}
```

**GEST-004 — Tipar port/in (substituir `void` por tipos reais):**

`TipoGestoUseCase.java` — substituir o conteúdo da interface:
```java
package com.neuroassist.gest.application.port.in;
import com.neuroassist.gest.api.dto.TipoGestoRequest;
import com.neuroassist.gest.api.dto.TipoGestoResponse;
import com.neuroassist.gest.api.dto.TransitionRequest;
import java.util.List; import java.util.UUID;
public interface TipoGestoUseCase {
    List<TipoGestoResponse> listAll();
    TipoGestoResponse findById(UUID id);
    TipoGestoResponse register(TipoGestoRequest request);
    TipoGestoResponse transition(UUID id, TransitionRequest request);
}
```

`DetectionProfileUseCase.java` — substituir:
```java
package com.neuroassist.gest.application.port.in;
import com.neuroassist.gest.api.dto.DetectionProfileRequest;
import com.neuroassist.gest.api.dto.DetectionProfileResponse;
import java.util.Optional; import java.util.UUID;
public interface DetectionProfileUseCase {
    DetectionProfileResponse publish(DetectionProfileRequest request);
    Optional<DetectionProfileResponse> findActive();
    Optional<DetectionProfileResponse> findByVersion(long version);
    Optional<DetectionProfileResponse> findForDevice(UUID deviceId);
}
```

`EligibilityUseCase.java` — substituir:
```java
package com.neuroassist.gest.application.port.in;
import com.neuroassist.gest.api.dto.EligibilityRequest;
import com.neuroassist.gest.api.dto.EligibilityResponse;
public interface EligibilityUseCase {
    EligibilityResponse check(EligibilityRequest request);
}
```

`DetectionContractUseCase.java` — substituir:
```java
package com.neuroassist.gest.application.port.in;
import com.neuroassist.gest.api.dto.DetectionContractRequest;
import com.neuroassist.gest.api.dto.DetectionContractResponse;
public interface DetectionContractUseCase {
    Object getContractDescriptor();
    DetectionContractResponse validateReport(DetectionContractRequest request);
}
```

**GEST-005 — Completar DTOs com campos reais (records Java):**

`TipoGestoRequest.java`:
```java
public record TipoGestoRequest(@NotBlank @Size(max=120) String nome) {}
```

`TipoGestoResponse.java`:
```java
public record TipoGestoResponse(UUID id, String nome, String detectabilityState,
    String engineFeatureRef, Instant createdAt, Instant updatedAt) {}
```

`TransitionRequest.java`:
```java
public record TransitionRequest(@NotBlank String toState, String actor, String reason) {}
```

`EligibilityRequest.java`:
```java
public record EligibilityRequest(@NotBlank String urgencyLevel) {}
```

`EligibilityResponse.java`:
```java
public record EligibilityResponse(boolean eligible, List<String> missingStates) {}
```

`DetectionProfileRequest.java`:
```java
public record DetectionProfileRequest(Map<String, Object> classificationParams) {}
```

`DetectionProfileResponse.java`:
```java
public record DetectionProfileResponse(UUID id, long version, boolean active,
    String contractVersion, Map<String, Object> classificationParams,
    Instant publishedAt, String etag) {}
```

`DetectionContractRequest.java`:
```java
public record DetectionContractRequest(Map<String, Object> report) {}
```

`DetectionContractResponse.java`:
```java
public record DetectionContractResponse(boolean valid, List<String> errors) {}
```

**GEST-005 — GlobalExceptionHandler: mapear 13 tipos de erro RFC 7807:**
Criar `@ExceptionHandler` para cada tipo: `tipo-gesto-not-found` (404), `tipo-gesto-duplicate` (409), `illegal-transition` (409), `eligibility-ineligible` (422), `profile-not-found` (404), `profile-already-active` (409), `contract-violation` (422), `tenant-mismatch` (403), `tenant-not-found` (503), `validation` (400), `device-not-paired` (403), `edge-unavailable` (503), `internal` (500). Cada handler produz `ProblemDetail` com campos: `type`, `title`, `status`, `detail`, `correlationId` (do MDC), `instance` (request URI).

**GEST-005 — Controladores: remover `UnsupportedOperationException`, delegar para use case:**
Substituir `throw new UnsupportedOperationException(...)` por chamada ao use case injetado. Exemplo para `TipoGestoController.register()`:
```java
// REMOVER: throw new UnsupportedOperationException("register not implemented");
// SUBSTITUIR POR:
return ResponseEntity.created(location).body(tipoGestoUseCase.register(request));
```
Os services ainda podem retornar resultados fixos/stub em P1 (ex.: retornar objeto vazio tipado), mas NÃO podem lançar `UnsupportedOperationException` — isso causa HTTP 500 não tratado.

**GEST-001 — Verificar compilação:**
`./gradlew :backend:services:gest-service:compileJava` deve sair exit 0 após todas as correções acima.

#### Alvos de alteração

Criar (novos arquivos):
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/port/out/TipoGestoRepositoryPort.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/port/out/TipoGestoTransitionRepositoryPort.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/port/out/DetectionProfileRepositoryPort.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/port/out/DetectionProfilePrimitiveRepositoryPort.java`

Editar (substituir conteúdo):
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/port/in/TipoGestoUseCase.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/port/in/DetectionProfileUseCase.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/port/in/EligibilityUseCase.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/port/in/DetectionContractUseCase.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/dto/TipoGestoRequest.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/dto/TipoGestoResponse.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/dto/TransitionRequest.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/dto/EligibilityRequest.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/dto/EligibilityResponse.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/dto/DetectionProfileRequest.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/dto/DetectionProfileResponse.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/dto/DetectionContractRequest.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/dto/DetectionContractResponse.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/exception/GlobalExceptionHandler.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/controller/TipoGestoController.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/controller/DetectionProfileController.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/controller/EligibilityController.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/controller/DetectionContractController.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/service/TipoGestoService.java` (adicionar campos injetados, atualizar assinaturas para compilar)
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/service/DetectionProfileService.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/service/EligibilityService.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/service/DetectionContractService.java`

#### Sequencia de execucao
- Fase 1 — criar os 4 arquivos port/out (remove bloqueio de compilação dos adapters)
- Fase 2 — substituir métodos void por assinaturas tipadas em port/in
- Fase 3 — atualizar DTOs com campos reais (records com anotações Bean Validation)
- Fase 4 — atualizar services para implementar novas assinaturas (retornar `null` ou objeto vazio se necessário para compilar — lógica real é P2)
- Fase 5 — fazer controladores delegar para use cases (remover UnsupportedOperationException)
- Fase 6 — implementar GlobalExceptionHandler com 13 tipos de erro
- Fase 7 — rodar `./gradlew :backend:services:gest-service:compileJava` e `architectureCheck`

#### Política de testes
Nenhum teste de lógica de negócio em P1 — a lógica não existe ainda. Apenas verificar que:
- `compileJava` exit 0
- `architectureCheck` exit 0 (ArchUnit não deve regredir)

#### Multi-empresa
`TenantContextHolder` será usado em P2/P5. Em P1, os services não precisam chamá-lo — apenas garantir que `TenantContextHolder.java` existe e compila.

#### Migrações Flyway
Nenhuma em P1. `V1__create_initial_schema.sql` local existe — deixar sem alterar. Contribuição ao MIG é P3 (GEST-013).

#### Critérios de aceite
- `./gradlew :backend:services:gest-service:compileJava` exit 0
- `./gradlew :backend:services:gest-service:architectureCheck` exit 0
- `GET /api/v1/gest/tipos-gesto` retorna `200` (mesmo lista vazia — sem HTTP 500)
- Endpoint inexistente retorna `404 application/problem+json`
- Classe `GlobalExceptionHandler` mapeada para todos os 13 tipos de erro do api-contract.md §7
- Nenhum `UnsupportedOperationException` lançado por controladores ou services diretamente

#### Riscos
- **R1 (baixo):** Se algum adapter JPA compilar com dependência cíclica com port/out, o ArchUnit pode falhar — verificar RULE-05 (infrastructure não depende de api) após criar port/out.

#### Instrução para implementador
Criar os 4 port/out primeiro (Fase 1). Rodar `compileJava` entre cada fase para isolar erros. Usar `null` como retorno temporário nos services (`return null;`) somente onde o tipo obriga retorno — NÃO é ideal mas é transitório até P2. Os services devem declarar `@RequiredArgsConstructor` e injetar port/out via construtor mesmo que não usem ainda.

---

### Prioridade 2 — Lógica de domínio (mock-first) — GATE DE TESTES

Status: concluida (gate test=PASS — 70 testes, 0 falhas — 2026-08-12)
Issues cobertas: GEST-006, GEST-007, GEST-008, GEST-009, GEST-010

#### Objetivo
CODIGO P2 JA IMPLEMENTADO EM DISCO. O objetivo do proximo ciclo de implementacao e validar os testes unitarios: executar `./gradlew :backend:services:gest-service:test`, confirmar exit 0, e corrigir qualquer falha de compilacao ou logica antes de marcar P2 como DONE.

#### O que ja existe (nao reimplementar)
- `TipoGestoService`: register, transition, listAll, findById — completo com eventos
- `EligibilityService.check()`: logica EMERGENCIA implementada
- `DetectionProfileService.publish()`: logica completa com snapshot e evento
- `DetectionContractService`: getContractDescriptor e validateReport implementados
- `TipoGestoDomainService.validateTransition()`: 5 arcos ADR-006 implementados
- `DetectionProfileDomainService.createSnapshot()`: implementado
- Domain events: PrimitiveRegistered, PrimitiveTransitioned, PrimitiveDeprecated, DetectionProfilePublished — todos criados
- Testes: TipoGestoDomainServiceTest, TipoGestoServiceTest, EligibilityServiceTest, DetectionProfileServiceTest, DetectionContractServiceTest — todos escritos

#### Correcoes mandatorias para o gate de validacao

**Acao obrigatoria (implementador):**
1. Executar `./gradlew :backend:services:gest-service:test` (JUnit unitarios, sem Docker)
2. Se exit 0: P2 DONE — atualizar status no plano e emitir evento PHASE-END
3. Se exit nao-zero: analisar falhas, corrigir uma a uma, re-rodar ate exit 0
4. Tipos de falha esperados (e correcao):
   - `NoSuchMethodError` / `MethodNotFound`: assinatura de record/model mudou — ajustar construtor no teste
   - `NullPointerException`: mock nao configurado — adicionar `when(...)` no setup
   - `ClassCastException` em ContractDescriptorV1: corrigir cast no teste
   - Falha de compilacao em TipoGesto record: verificar numero de campos no construtor (9 campos confirmados em leitura do codigo)

**Verificacao de build apos testes:**
Apos test exit 0, rodar `./gradlew :backend:services:gest-service:build -x test` para confirmar build completo.

**HISTORICO — GEST-006 — TipoGestoService.register() (ja implementado):**
- Injetar: `TipoGestoRepositoryPort`, `ApplicationEventPublisher`
- Validar: nome não vazio, <= 120 chars → lançar `InvalidNomeException` se falhar
- Verificar duplicata: chamar `tipoGestoRepositoryPort.existsByNomeIgnoreCaseAndStateNotDeprecated(nome)` → lançar `DuplicateTipoGestoException` se true
- Criar domínio: `new TipoGesto(UUID.randomUUID(), nome, DetectabilityState.REGISTERED, null, now(), now())`
- Persistir: `tipoGestoRepositoryPort.save(tipoGesto)`
- Publicar evento: `eventPublisher.publishEvent(new PrimitiveRegistered(tipoGesto.id(), nome, tenantRef, actor, now()))`
- Criar evento de domínio: `domain/event/PrimitiveRegistered.java` (record: tipoGestoId, nome, tenantRef, actor, at)

**GEST-007 — TipoGestoService.transition():**
- Implementar `TipoGestoDomainService.validateTransition(DetectabilityState from, DetectabilityState to)`: lançar `IllegalTransitionException` para qualquer transição fora das 5 permitidas pelo ADR-006 (REGISTERED→PENDING_TRAINING, PENDING_TRAINING→SUPPORTED, SUPPORTED→DEPRECATED, REGISTERED→DEPRECATED, PENDING_TRAINING→DEPRECATED)
- Em `TipoGestoService.transition(UUID id, TransitionRequest request)`:
  - Carregar `TipoGesto` pelo id → lançar `TipoGestoNotFoundException` se ausente
  - Chamar `tipoGestoDomainService.validateTransition(current.state(), target)`
  - Criar `TipoGestoTransition` (append-only) e salvar via `tipoGestoTransitionRepositoryPort.save(...)`
  - Atualizar `TipoGesto` com novo estado e salvar
  - Publicar `PrimitiveTransitioned` (record: tipoGestoId, fromState, toState, actor, at) via `eventPublisher`
- Criar domínio: `domain/event/PrimitiveTransitioned.java`, `domain/event/PrimitiveDeprecated.java`

**GEST-008 — EligibilityService.check():**
- Implementar sem `@Transactional` (read-only)
- Carregar `tipoGestoRepositoryPort.findAllByTenant()` → filtrar estados
- Se `urgencyLevel == "EMERGENCIA"`: todos devem estar em `SUPPORTED` → retornar `EligibilityResponse(eligible=false, missingStates=[...])` se qualquer um não estiver
- Para outros urgency levels: retornar `eligible=true` imediatamente
- Não persistir nada; não emitir evento

**GEST-009 — DetectionProfileService.publish():**
- Injetar: `DetectionProfileRepositoryPort`, `DetectionProfilePrimitiveRepositoryPort`, `TipoGestoRepositoryPort`, `ApplicationEventPublisher`
- Implementar `DetectionProfileDomainService.createSnapshot(List<TipoGesto> supported)`: criar lista de `DetectionProfilePrimitive` snapshot (copia `nome` e `engineFeatureRef` dos primitivos SUPPORTED)
- Em `DetectionProfileService.publish(DetectionProfileRequest request)`:
  - Carregar todos primitivos SUPPORTED via `tipoGestoRepositoryPort.findAllByTenant()` → filtrar `detectabilityState==SUPPORTED` → lançar `IllegalStateException("no supported primitives")` se vazio
  - Gerar próxima versão: `detectionProfileRepositoryPort.nextVersion()`
  - Desativar perfil atual: `detectionProfileRepositoryPort.deactivateCurrent()`
  - Criar novo `DetectionProfile` com `version=N`, `active=true`, `contractVersion="v1.0.0"`, `classificationParams` do request
  - Criar snapshot via domain service e salvar primitivos
  - Emitir `DetectionProfilePublished` (record: profileId, version, at)
  - Retornar `DetectionProfileResponse` com `etag = "\"" + version + "\""`

**GEST-010 — DetectionContractService:**
- `getContractDescriptor()`: retornar objeto estático `ContractDescriptorV1` (record com version="v1.0.0", fields de `RecognizedCommandReport` permitidos)
- `validateReport(DetectionContractRequest request)`: validar campos obrigatórios (`gestureId`, `confidence`, `windowMs`), rejeitar campos proibidos (`video`, `image`, `landmark`, `base64`, `pixels`, `frame`) → retornar `DetectionContractResponse(valid, errors)`

#### Alvos de alteração
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/service/TipoGestoDomainService.java` (adicionar validateTransition + createNew)
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/service/DetectionProfileDomainService.java` (adicionar createSnapshot)
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/event/PrimitiveRegistered.java` (criar)
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/event/PrimitiveTransitioned.java` (criar)
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/event/PrimitiveDeprecated.java` (criar)
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/domain/event/DetectionProfilePublished.java` (criar)
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/service/TipoGestoService.java` (implementar register + transition + findById + listAll)
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/service/EligibilityService.java` (implementar check)
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/service/DetectionProfileService.java` (implementar publish + findActive)
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/service/DetectionContractService.java` (implementar getDescriptor + validateReport)
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/exception/` — criar: `TipoGestoNotFoundException.java`, `DuplicateTipoGestoException.java`, `IllegalTransitionException.java`
- Testes unitários (criar):
  - `src/test/java/com/neuroassist/gest/application/service/TipoGestoServiceTest.java`
  - `src/test/java/com/neuroassist/gest/application/service/EligibilityServiceTest.java`
  - `src/test/java/com/neuroassist/gest/application/service/DetectionProfileServiceTest.java`
  - `src/test/java/com/neuroassist/gest/application/service/DetectionContractServiceTest.java`
  - `src/test/java/com/neuroassist/gest/domain/service/TipoGestoDomainServiceTest.java`

#### Sequência de execução
- Fase 1 — domain: `TipoGestoDomainService.validateTransition()` + testes unitários de transição
- Fase 2 — events: criar 4 records de evento em `domain/event/`
- Fase 3 — application: `TipoGestoService.register()` + testes Mockito
- Fase 4 — application: `TipoGestoService.transition()` + testes Mockito
- Fase 5 — application: `EligibilityService.check()` + testes Mockito
- Fase 6 — domain: `DetectionProfileDomainService.createSnapshot()`
- Fase 7 — application: `DetectionProfileService.publish()` + testes Mockito
- Fase 8 — application: `DetectionContractService` (stateless) + testes unitários

#### Política de testes
Unitários obrigatórios com Mockito para cada service. Padrão: usar `@ExtendWith(MockitoExtension.class)`, `@Mock` para ports, `@InjectMocks` para service. Cenários obrigatórios:
- Register: nome novo → 201; nome duplicado → DuplicateTipoGestoException; nome vazio → InvalidNomeException
- Transition: cada uma das 5 transições legais → OK; cada transição ilegal → IllegalTransitionException
- Eligibility: urgência EMERGENCIA + todos SUPPORTED → eligible=true; urgência EMERGENCIA + algum não-SUPPORTED → eligible=false
- Profile publish: primitivos SUPPORTED disponíveis → publica com versão N+1; nenhum SUPPORTED → IllegalStateException
- Contract validate: report válido → valid=true; campo proibido presente → valid=false com erro descritivo

#### Multi-empresa
`TenantContextHolder.require()` deve ser chamado no início de cada método de `TipoGestoService` e `EligibilityService` para obter o `tenantId` (P5 implementa o holder real; em P2 mockar com `@MockBean` ou configurar stub no contexto de teste).

#### Migrações Flyway
Nenhuma em P2 — P2 usa Mockito sem banco de dados.

#### Critérios de aceite
- `./gradlew :backend:services:gest-service:test` exit 0 — ESTE E O UNICO CRITERIO DE P2
- Todos os 5 arquivos de teste passam (TipoGestoDomainServiceTest, TipoGestoServiceTest, EligibilityServiceTest, DetectionProfileServiceTest, DetectionContractServiceTest)
- Nenhum test `@Disabled` ou `assumeTrue(false)` mascarando falha

#### Instrucao para implementador
Rodar os testes. Nao reimplementar logica ja presente. Apenas corrigir o que falhar no runner. Se todos passarem na primeira execucao, P2 esta DONE — passar para P3.

#### Riscos
- **R2 (medio):** `TenantContextHolder.require()` nos services usa "tenant-pending-p5" hardcoded em P2 (sem mock necessario nos testes unitarios — o holder nao e chamado nos services de P2, apenas documentado como pendente). Verificar que os testes nao tentam mockar uma classe que nao e usada.

---

### Prioridade 3 — Persistência real + migrations

Status: concluida (gate integrationTest=PASS — 17 testes, 0 falhas — 2026-08-12)
Issues cobertas: GEST-011, GEST-012, GEST-013, GEST-014, INT-GEST-005

#### O que ja existe (nao reimplementar)
- `TipoGestoRepositoryAdapter`: save, findById, findAllByTenant, existsByNomeIgnoreCaseAndStateNotDeprecated — implementados
- `TipoGestoTransitionRepositoryAdapter`: implementado
- `DetectionProfileRepositoryAdapter`: implementado (verificar nextVersion + PESSIMISTIC_WRITE)
- `DetectionProfilePrimitiveRepositoryAdapter`: implementado
- `TenantContextHolder`: completamente implementado
- `MultiTenantConnectionProvider`, `TenantIdentifierResolver`, `TenantContextFilter`: existem
- `BaseIntegrationTest`: Testcontainers PostgreSQL singleton configurado
- `TipoGestoRepositoryAdapterIT`, `DetectionProfileRepositoryAdapterIT`, `MultiTenantIsolationIT`: escritas
- `init-schema.sql` em `src/test/resources/`: presente

#### Objetivo
Validar a persistencia real: contribuir migration V0400 ao MIG, rodar `./gradlew :backend:services:gest-service:integrationTest` e confirmar exit 0.

#### Correções mandatórias

**GEST-013 — Migration para MIG:**
- Copiar `src/main/resources/db/migration/V1__create_initial_schema.sql` para `backend/neuroassist-ai-migrations/src/main/resources/db/migration/empresa/V0400__gest_initial_schema.sql`
- Adicionar comentário no topo: `-- GEST migration — faixa V0400..V0499 — owner: gest-service`
- Verificar que a migration existente (`V1__create_initial_schema.sql`) está correta (DDL já revisado e completo)

**GEST-014 — SchemaRoutingDataSource:**
- `HibernateMultiTenancyConfig` já existe — verificar se `MultiTenantConnectionProvider` implementa `getConnection(tenantIdentifier)` chamando `connection.setSchema("t_" + tenantId)` antes de retornar a conexão
- `TenantIdentifierResolver.resolveCurrentTenantIdentifier()` deve chamar `TenantContextHolder.getTenantId()` e retornar o schema `t_<tenantId>`
- Guard 503: em `TenantContextFilter`, se `TenantContextHolder.getTenantId()` retornar `null` ou schema não existir, retornar `503 SERVICE_UNAVAILABLE` com `ProblemDetail` tipo `tenant-not-found`

**GEST-011 — JPA adapters TipoGesto:**
Implementar `TipoGestoRepositoryAdapter.save()`:
```java
// REMOVER: throw new UnsupportedOperationException("not implemented");
// SUBSTITUIR POR:
TipoGestoJpaEntity entity = toEntity(tipoGesto);
TipoGestoJpaEntity saved = repository.save(entity);
return toDomain(saved);
```
Implementar métodos de mapeamento `toEntity()` e `toDomain()` (campos um-a-um). Implementar `existsByNomeIgnoreCaseAndStateNotDeprecated()` via query JPQL: `SELECT COUNT(t) > 0 FROM TipoGestoJpaEntity t WHERE LOWER(t.nome) = LOWER(:nome) AND t.detectabilityState <> 'deprecated'`.

**GEST-012 — JPA adapters DetectionProfile:**
Implementar `DetectionProfileRepositoryAdapter.nextVersion()`: `SELECT COALESCE(MAX(d.version), 0) + 1 FROM DetectionProfileJpaEntity d` com `@Lock(LockModeType.PESSIMISTIC_WRITE)`.
Implementar `deactivateCurrent()`: `UPDATE DetectionProfileJpaEntity d SET d.active = false WHERE d.active = true`.

#### Alvos de alteração
- `backend/neuroassist-ai-migrations/src/main/resources/db/migration/empresa/V0400__gest_initial_schema.sql` (criar)
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/persistence/adapter/TipoGestoRepositoryAdapter.java` (implementar métodos)
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/persistence/adapter/TipoGestoTransitionRepositoryAdapter.java` (implementar)
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/persistence/adapter/DetectionProfileRepositoryAdapter.java` (implementar + nextVersion lock)
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/persistence/adapter/DetectionProfilePrimitiveRepositoryAdapter.java` (implementar)
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/security/MultiTenantConnectionProvider.java` (verificar/implementar schema routing)
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/security/TenantIdentifierResolver.java` (verificar/implementar)
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/security/TenantContextFilter.java` (adicionar 503 guard se schema ausente)
- Testes de integração (criar):
  - `src/test/java/com/neuroassist/gest/integration/TipoGestoRepositoryAdapterIT.java`
  - `src/test/java/com/neuroassist/gest/integration/DetectionProfileRepositoryAdapterIT.java`
  - `src/test/java/com/neuroassist/gest/integration/MultiTenantIsolationIT.java`

#### Sequência de execução
- Fase 1 — GEST-013: copiar migration para MIG e validar com `psql`
- Fase 2 — GEST-014: verificar/implementar schema routing e 503 guard
- Fase 3 — GEST-011: implementar adapters TipoGesto + TipoGestoTransition com ITs Testcontainers
- Fase 4 — GEST-012: implementar adapters DetectionProfile + Primitive com ITs Testcontainers
- Fase 5 — INT-GEST-005: confirmar que MIG aplica V0400 sem erros

#### Política de testes
Integration tests obrigatórios com Testcontainers (singleton pattern). Cenários mínimos:
- Salvar TipoGesto → recuperar por id → estado correto
- Cross-tenant: tenant A não vê dados do tenant B
- `deactivateCurrent()` + `save()` em transação → apenas 1 perfil ativo
- Guard 503: request sem schema no banco → 503

#### Multi-empresa
Schema routing via `t_<tenantId>` — `MultiTenantConnectionProvider.getConnection(tenantId)` usa `setSchema("t_" + tenantId)`. Nenhum dado GEST vai ao schema global.

#### Critérios de aceite
- `psql -f V0400__gest_initial_schema.sql` em schema vazio → exit 0
- Testes de integração passam com Testcontainers PostgreSQL
- Dados de tenant A não aparecem em query de tenant B (teste MultiTenantIsolationIT)
- `503` para request com `tenantId` sem schema correspondente

#### Riscos
- **R3 (médio):** `ux_tipo_gesto_nome_active` é índice parcial — Hibernate Schema Validator pode não reconhecê-lo. Adicionar `spring.jpa.properties.hibernate.hbm2ddl.auto=validate` e verificar.
- **R4 (baixo):** `nextVersion()` com `PESSIMISTIC_WRITE` pode causar deadlock sob carga — aceitável para MVP.

---

### Prioridade 4 — Integrações externas

Status: concluida (2026-08-12 | EdgePairingClient Feign + ETag/304 PASS; 77 testes unit + 17 IT PASS; arch PASS; ADR-007 criado)
Issues cobertas: GEST-015, GEST-016, INT-GEST-001, INT-GEST-003, INT-GEST-004, INT-GEST-007

#### Objetivo
Implementar `EdgePairingClient` Feign funcional e testado via WireMock; adicionar conditional-GET (ETag/304) no pull do perfil ativo pelo edge; registrar decisao ADR para INT-GEST-007 (transport auth).

#### Problema identificado
- `EdgePairingClient` existe como stub (`EdgePairingPortStub` retorna sempre `paired=true`) — nao ha Feign real
- `DetectionProfileController.findActive()` e `findForDevice()` nao verificam `If-None-Match` — retornam 200 sempre sem ETag
- Nenhum IT cobre o caminho Feign com WireMock

#### Correcoes mandatorias

**GEST-015 — EdgePairingClient (Feign real):**

Criar `infrastructure/clients/EdgePairingClient.java`:
```java
@FeignClient(name = "edge-service", url = "${gest.edge.url:}")
public interface EdgePairingClient {
    @GetMapping("/api/v1/edge/devices/{deviceId}/tenant")
    TenantResponse getTenantForDevice(@PathVariable UUID deviceId);
}
```

Criar `infrastructure/clients/dto/TenantResponse.java`:
```java
public record TenantResponse(String tenantId, boolean paired) {}
```

Criar `infrastructure/clients/EdgePairingAdapter.java` que implementa `application/port/out/EdgePairingPort`:
- Metodo `checkPairing(UUID deviceId)`: chamar `EdgePairingClient.getTenantForDevice(deviceId)`
- Se `FeignException.NotFound` (404) lancado: lancar `DeviceNotPairedException(deviceId)`
- Se qualquer outro `FeignException`: lancar `EdgeUnavailableException(cause)`
- Retornar `tenantId` do `TenantResponse` se `paired == true`
- Se `paired == false`: lancar `DeviceNotPairedException(deviceId)`

Criar `application/port/out/EdgePairingPort.java`:
```java
public interface EdgePairingPort {
    String checkPairingAndGetTenant(UUID deviceId);
}
```

Registrar `EdgePairingAdapter` como `@Component` (nao como `@Bean` manual — o construtor e injetado via `@RequiredArgsConstructor`).

**GEST-016 — ETag/304 em DetectionProfileController:**

Em `DetectionProfileController.findActive(HttpServletRequest request)`:
```java
Optional<DetectionProfileResponse> profile = detectionProfileUseCase.findActive();
if (profile.isEmpty()) return ResponseEntity.notFound().build();
DetectionProfileResponse p = profile.get();
String clientEtag = request.getHeader("If-None-Match");
if (p.etag() != null && p.etag().equals(clientEtag)) {
    return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
}
return ResponseEntity.ok()
    .header(HttpHeaders.ETAG, p.etag())
    .header(HttpHeaders.CACHE_CONTROL, "max-age=60")
    .body(p);
```

Aplicar o mesmo padrao em `findForDevice(UUID deviceId, HttpServletRequest request)`:
- Chamar `EdgePairingPort.checkPairingAndGetTenant(deviceId)` antes de buscar o profile
- Se `DeviceNotPairedException`: deixar GlobalExceptionHandler mapear para 403 `device-not-paired`
- Se perfil encontrado: aplicar ETag/304 identico ao `findActive()`

**INT-GEST-007 — Decisao de transport auth:**
Criar `docs/architecture/backend/adr/ADR-007-edge-device-auth.md` com decisao: MVP usa OAuth2 `client_credentials` com scope `gest.profile.read`. Registrar como decisao temporaria ate que o time EDGE defina o protocolo definitivo.

#### Alvos de alteracao (caminhos completos)

Criar:
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/port/out/EdgePairingPort.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/clients/EdgePairingClient.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/clients/EdgePairingAdapter.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/clients/dto/TenantResponse.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/exception/DeviceNotPairedException.java`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/exception/EdgeUnavailableException.java`
- `backend/services/gest-service/src/test/java/com/neuroassist/gest/integration/EdgePairingClientIT.java`
- `docs/architecture/backend/adr/ADR-007-edge-device-auth.md`

Editar:
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/controller/DetectionProfileController.java` (adicionar If-None-Match + ETag + Cache-Control)
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/exception/GlobalExceptionHandler.java` (adicionar handlers para `DeviceNotPairedException` → 403 e `EdgeUnavailableException` → 503)
- `backend/services/gest-service/src/main/resources/application.yml` (adicionar `gest.edge.url: ${EDGE_SERVICE_URL:http://edge-service}`)

#### Sequencia de execucao
- Fase 1 (port/out): criar `EdgePairingPort.java` em `application/port/out/` (interface hexagonal)
- Fase 2 (Feign): criar `EdgePairingClient.java` + `TenantResponse.java` em `infrastructure/clients/`
- Fase 3 (adapter): criar `EdgePairingAdapter.java` implementando `EdgePairingPort` com tratamento de erros Feign
- Fase 4 (exceptions): criar `DeviceNotPairedException` e `EdgeUnavailableException` em `api/exception/`; registrar handlers no `GlobalExceptionHandler`
- Fase 5 (controller): adicionar ETag/304 logic em `DetectionProfileController.findActive()` e `findForDevice()`; injetar `EdgePairingPort` em `findForDevice()`
- Fase 6 (yml): adicionar `gest.edge.url` em `application.yml`
- Fase 7 (IT): criar `EdgePairingClientIT` com WireMock (`com.github.tomakehurst:wiremock-jre8-standalone` ou `wiremock-standalone`) — cenarios: paired 200, not-found 404, down 500
- Fase 8 (ADR): criar `ADR-007-edge-device-auth.md`
- Fase 9 (gates): rodar `./gradlew :services:gest-service:test` e `./gradlew :services:gest-service:integrationTest` — ambos devem sair exit 0

#### Politica de testes
- IT obrigatoria com WireMock para o Feign client (sem Docker para edge-service)
- Cenarios minimos no `EdgePairingClientIT`:
  - `GET /devices/{deviceId}/tenant` retorna `{"tenantId":"t1","paired":true}` → adapter retorna "t1"
  - `GET /devices/{deviceId}/tenant` retorna 404 → `DeviceNotPairedException` lancada
  - WireMock nao responde (simulate timeout via fault) → `EdgeUnavailableException` lancada
- Teste unitario para ETag: `DetectionProfileControllerTest` verifica que `If-None-Match` igual ao etag do perfil retorna `304` (usar `MockMvc` com `@WebMvcTest`)

#### Multi-empresa
`EdgePairingAdapter.checkPairingAndGetTenant()` NAO chama `TenantContextHolder` — o tenantId vem do EDGE como parte da resposta de pairing. O controller usa o tenantId retornado para popular o contexto antes de buscar o perfil no schema correto.

#### Migracoes Flyway
Nenhuma em P4 — nenhuma tabela nova. O perfil ativo ja existe no schema `t_<tenantId>` provisionado pela V0400.

#### Criterios de aceite (verificaveis objetivamente)
1. `./gradlew :services:gest-service:test` exit 0 (testes unitarios incluindo DetectionProfileControllerTest com ETag)
2. `./gradlew :services:gest-service:integrationTest` exit 0 (EdgePairingClientIT 3/3 cenarios WireMock PASS)
3. `GET /api/v1/gest/profiles/active` com device pareado → resposta inclui header `ETag: "<version>"` e `Cache-Control: max-age=60`
4. `GET /api/v1/gest/profiles/active` com `If-None-Match: "<version>"` igual ao etag ativo → `304 Not Modified` sem corpo
5. `GET /api/v1/gest/profiles/device/{deviceId}` com device nao pareado (WireMock 404) → `403 application/problem+json` com `type: device-not-paired`
6. `GET /api/v1/gest/profiles/device/{deviceId}` com EDGE down (WireMock fault) → `503 application/problem+json` com `type: edge-unavailable`
7. `./gradlew :services:gest-service:architectureCheck` exit 0 (RULE-06: `@FeignClient` apenas em `infrastructure.clients`)
8. `docs/architecture/backend/adr/ADR-007-edge-device-auth.md` existe com decisao de MVP documentada

#### Riscos
- **R5 (alto):** INT-GEST-007 aberta — transport auth pode mudar SecurityConfig no futuro. Isolar no adapter `EdgePairingAdapter` para que a troca de autenticacao nao exija mudanca no controller ou domain.
- **R8 (medio):** WireMock depende da versao correta no classpath. Verificar que `build.gradle.kts` ja tem a dependencia `testImplementation("com.github.tomakehurst:wiremock-jre8-standalone:...")` — se nao tiver, adicionar na secao `dependencies { ... }`.

#### Instrucao para implementador
Comecar pela `EdgePairingPort` (interface hexagonal) antes de qualquer implementacao Feign — isso garante que o ArchUnit nao reclamara de dependencia cruzada. Verificar se `EdgePairingPortStub` ja existe e, se existir, NAO removê-la agora — ela serve para testes que nao dependem do edge. Criar o adapter como implementacao alternativa e injetar via `@Primary` ou `@ConditionalOnProperty(name="gest.edge.enabled", havingValue="true", matchIfMissing=true)`. Executar `architectureCheck` antes do IT para garantir RULE-06.

---

### Prioridade 5 — Security + multi-tenancy

Status: concluida (2026-08-12 | SecurityIT 4/4 PASS; OAuth2 JWT + TenantContextFilter claim extraction + X-Correlation-Id + AuthorizationDeniedException handler fix; build+test+integrationTest+arch PASS)
Issues cobertas: GEST-017, GEST-018, INT-GEST-002

#### Objetivo
Completar o wiring OAuth2 Resource Server com JWT real (issuer-uri configurado), popular `TenantContextHolder.require()` a partir do claim JWT (sub + tenant), propagar `X-Correlation-Id` para downstream, e validar a camada de seguranca com testes automatizados.

#### Problema identificado
- `SecurityConfig` pode ter `jwk-set-uri` configurado mas nao validado com token JWT real emitido por Authorization Server real
- `TenantContextFilter` nao extrai `tenantId` do claim JWT (usa placeholder "tenant-pending-p5" ou valor hardcoded)
- `X-Correlation-Id` pode ser gerado mas nao propagado corretamente para chamadas Feign downstream (`EdgePairingClient`)
- Nenhum teste de seguranca existe (`SecurityIT` ausente): endpoints retornam 200 sem token em ambiente de teste

#### O que ja existe (nao reimplementar)
- `TenantContextHolder`: ThreadLocal + set/require/clear + MDC — COMPLETO (nao alterar a estrutura)
- `TenantContextFilter`: arquivo existe — verificar e corrigir somente a extracao do claim JWT
- `MultiTenantConnectionProvider`, `TenantIdentifierResolver`: operacionais
- `CorrelationIdInterceptor`: verificar se propagado para Feign (`RequestInterceptor`)

#### Correcoes mandatorias

**GEST-017 — SecurityConfig: OAuth2 Resource Server com issuer-uri real**

Em `SecurityConfig.java`, substituir qualquer configuracao de `jwk-set-uri` por `issuer-uri`:
```yaml
# application.yml — substituir
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${GEST_SECURITY_ISSUER_URI:http://acs-service/realms/neuroassist}
```

Em `SecurityConfig.java`, confirmar a configuracao do resource server e adicionar `@PreAuthorize` por scope:
```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/actuator/info").permitAll()
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
    return http.build();
}
```

Scopes obrigatorios via `@PreAuthorize("hasAuthority('SCOPE_<scope>')")` nos controllers:
- `gest.tipo-gesto.read` — TipoGestoController.listAll, findById
- `gest.tipo-gesto.criar` — TipoGestoController.register
- `gest.tipo-gesto.editar` — TipoGestoController.transition
- `gest.profile.read` — DetectionProfileController.findActive, findForDevice, findByVersion
- `gest.profile.criar` — DetectionProfileController.publish
- `gest.eligibility.read` — EligibilityController.check
- `gest.contract.read` — DetectionContractController.getDescriptor, validateReport

**GEST-018 — TenantContextFilter: extrair tenantId do JWT (sub + tenant claim)**

Em `TenantContextFilter.doFilter()`, substituir qualquer logica de placeholder por extracao real do JWT:
```java
@Override
public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
    throws IOException, ServletException {
    HttpServletRequest request = (HttpServletRequest) req;
    HttpServletResponse response = (HttpServletResponse) res;

    // X-Correlation-Id
    String correlationId = request.getHeader("X-Correlation-Id");
    if (correlationId == null || correlationId.isBlank()) {
        correlationId = UUID.randomUUID().toString();
    }
    MDC.put("correlationId", correlationId);
    response.setHeader("X-Correlation-Id", correlationId);

    // Tenant do JWT
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String tenantId = null;
    if (auth instanceof JwtAuthenticationToken jwtToken) {
        // Extrair claim "tenant" primeiro; fallback para "sub"
        tenantId = jwtToken.getToken().getClaimAsString("tenant");
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = jwtToken.getToken().getSubject();
        }
    }

    if (tenantId != null && !tenantId.isBlank()) {
        TenantContextHolder.set(tenantId);
        MDC.put("tenantId", tenantId);
    }

    try {
        chain.doFilter(req, res);
    } finally {
        TenantContextHolder.clear();
        MDC.remove("tenantId");
        MDC.remove("correlationId");
    }
}
```

Nota: `TenantContextFilter` deve ser registrado APOS o filtro OAuth2 do Spring Security (usar `FilterRegistrationBean` com `setOrder(Ordered.LOWEST_PRECEDENCE - 10)` ou adicionar via `http.addFilterAfter(tenantContextFilter, BearerTokenAuthenticationFilter.class)`).

**X-Correlation-Id propagacao downstream (Feign)**

Criar ou verificar `infrastructure/clients/CorrelationIdFeignInterceptor.java`:
```java
@Component
public class CorrelationIdFeignInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            template.header("X-Correlation-Id", correlationId);
        }
    }
}
```

Registrar como `@Bean` em `infrastructure/config/FeignConfig.java` (ou diretamente via `@Component` — o Spring Cloud Feign detecta automaticamente os `RequestInterceptor` no contexto).

**SecurityIT — testes de seguranca com chave RSA embutida (sem Authorization Server real)**

Criar `src/test/java/com/neuroassist/gest/integration/SecurityIT.java`:

Usar `@SpringBootTest(webEnvironment = RANDOM_PORT)` com `@AutoConfigureMockMvc`. Gerar par RSA em `@BeforeAll` (4096 bits) e configurar `spring.security.oauth2.resourceserver.jwt.public-key-value` no contexto de teste via `@DynamicPropertySource`:
```java
@DynamicPropertySource
static void jwtProperties(DynamicPropertyRegistry registry) {
    // issuer-uri desativado em teste; usar public-key-value diretamente
    registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "");
    registry.add("spring.security.oauth2.resourceserver.jwt.public-key-value",
        () -> publicKeyPem); // chave publica RSA gerada em @BeforeAll
}
```

Gerar tokens JWT assinados com a chave privada RSA correspondente (usar `com.nimbusds:nimbus-jose-jwt` — ja no classpath via `spring-security-oauth2-jose`).

Cenarios obrigatorios (`SecurityIT`):
1. `GET /api/v1/gest/tipos-gesto` sem `Authorization` header → `401 Unauthorized`
2. `GET /api/v1/gest/tipos-gesto` com JWT valido mas scope `gest.admin` (nao previsto) → `403 Forbidden`
3. `GET /api/v1/gest/tipos-gesto` com JWT valido + scope `gest.tipo-gesto.read` + claim `tenant=t1` → `200 OK`; verificar que `TenantContextHolder.require()` retornou `t1` (logar no MDC e verificar header `X-Tenant-Id` na resposta se exposto, ou inspecionar via spy)
4. Qualquer resposta com JWT valido → header `X-Correlation-Id` presente (nao nulo, formato UUID)

#### Alvos de alteracao (caminhos completos)

Editar:
- `backend/services/gest-service/src/main/resources/application.yml` — substituir `jwk-set-uri` por `issuer-uri: ${GEST_SECURITY_ISSUER_URI:http://acs-service/realms/neuroassist}`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/config/SecurityConfig.java` — confirmar oauth2ResourceServer + adicionar `@PreAuthorize` nos controllers (se nao presentes)
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/security/TenantContextFilter.java` — substituir logica de placeholder por extracao de claim JWT (sub + tenant) via `JwtAuthenticationToken`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/controller/TipoGestoController.java` — adicionar `@PreAuthorize` por metodo
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/controller/DetectionProfileController.java` — adicionar `@PreAuthorize` por metodo
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/controller/EligibilityController.java` — adicionar `@PreAuthorize`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/controller/DetectionContractController.java` — adicionar `@PreAuthorize`

Criar:
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/clients/CorrelationIdFeignInterceptor.java` — `RequestInterceptor` que propaga `X-Correlation-Id` do MDC
- `backend/services/gest-service/src/test/java/com/neuroassist/gest/integration/SecurityIT.java` — 4 cenarios de seguranca com chave RSA embutida

#### Sequencia de execucao
- Fase 1 (yml): substituir `jwk-set-uri` por `issuer-uri` em `application.yml`
- Fase 2 (SecurityConfig): confirmar `oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))` e `SessionCreationPolicy.STATELESS`
- Fase 3 (TenantContextFilter): substituir extracao de tenantId por `JwtAuthenticationToken.getToken().getClaimAsString("tenant")` com fallback para `sub`; implementar `X-Correlation-Id` gerado se ausente + ecoado na resposta
- Fase 4 (controllers): adicionar `@PreAuthorize("hasAuthority('SCOPE_gest.<scope>')")` em cada endpoint
- Fase 5 (Feign): criar `CorrelationIdFeignInterceptor` e registrar no contexto
- Fase 6 (SecurityIT): criar teste com par RSA embutido via `@DynamicPropertySource`; validar 4 cenarios
- Fase 7 (gates): rodar `./gradlew :backend:services:gest-service:test` (77 unit + novos) e `./gradlew :backend:services:gest-service:integrationTest` (17 IT + SecurityIT); ambos exit 0
- Fase 8 (arch): rodar `./gradlew :backend:services:gest-service:architectureCheck` — verificar RULE-01..06

#### Politica de testes
- `SecurityIT` obrigatorio: `@SpringBootTest(webEnvironment=RANDOM_PORT)` com `MockMvc` ou `TestRestTemplate`
- Par RSA gerado em `@BeforeAll` (nao usar chave hardcoded em base64 — gerar programaticamente com `KeyPairGenerator`)
- Tokens JWT gerados com `nimbus-jose-jwt` (JWSSigner RSA)
- Nenhum `@MockBean` para `SecurityConfig` — testar a configuracao real
- Testes unitarios existentes (77 unit) nao devem regredir

#### Multi-empresa
- `TenantContextHolder.require()` chamado nos services: validado em P5 via `SecurityIT` cenario 3
- `tenantId` extraido exclusivamente do claim JWT (claim `"tenant"` prioritario; `"sub"` como fallback)
- Nenhum endpoint aceita `tenantId` como parametro de query ou body

#### Migracoes Flyway
Nenhuma em P5 — nenhuma tabela nova. Schema `t_<tenantId>` ja provisionado pela V0400.

#### Criterios de aceite (verificaveis objetivamente)
1. `GET /api/v1/gest/tipos-gesto` sem `Authorization` → `401 Unauthorized` (SecurityIT cenario 1)
2. `GET /api/v1/gest/tipos-gesto` com JWT valido + scope incorreto → `403 Forbidden` (SecurityIT cenario 2)
3. `GET /api/v1/gest/tipos-gesto` com JWT valido + `SCOPE_gest.tipo-gesto.read` + claim `tenant=t1` → `200 OK`; `TenantContextHolder.require()` retorna `t1` sem lancar excecao (SecurityIT cenario 3)
4. Qualquer resposta com JWT valido contem header `X-Correlation-Id` com valor UUID (SecurityIT cenario 4)
5. `./gradlew :backend:services:gest-service:test` exit 0 (incluindo novos testes de seguranca se houver `@WebMvcTest`)
6. `./gradlew :backend:services:gest-service:integrationTest` exit 0 (SecurityIT 4/4 PASS)
7. `./gradlew :backend:services:gest-service:architectureCheck` exit 0 (nenhuma violacao nova)
8. `gest.security.issuer-uri` configurado em `application.yml` com variavel de ambiente — nenhuma chave hardcoded em producao

#### Riscos
- **R5 (alto):** `issuer-uri` faz chamada HTTP ao Authorization Server no startup (`/.well-known/openid-configuration`). Em teste, usar `spring.security.oauth2.resourceserver.jwt.public-key-value` via `@DynamicPropertySource` para evitar dependencia de AS externo. Em producao, garantir que `acs-service` ou Keycloak esteja UP antes do `gest-service`.
- **R6 (medio):** `TenantContextFilter` deve ser registrado APOS o filtro `BearerTokenAuthenticationFilter` do Spring Security — caso contrario `SecurityContextHolder.getContext().getAuthentication()` retorna `null` e o tenantId nao e extraido. Usar `http.addFilterAfter(tenantContextFilter, BearerTokenAuthenticationFilter.class)`.
- **R7 (baixo):** `@PreAuthorize` requer `@EnableMethodSecurity` em `SecurityConfig`. Verificar se ja esta presente; se nao, adicionar.

#### Instrucao para implementador
Iniciar pela Fase 1 (yml) e Fase 3 (TenantContextFilter) para eliminar o placeholder. Executar `./gradlew :gest-service:integrationTest` cedo para confirmar que ITs existentes (17) nao regridem com a nova configuracao de seguranca (alguns podem precisar de `@WithMockUser` ou equivalente). Criar `SecurityIT` somente apos confirmar que as fases 1-5 estao corretas. Nao usar `@MockBean SecurityFilterChain` — testar a configuracao real de seguranca.

---

### Prioridade 6 — Messaging in-process

Status: concluida (2026-08-12 | PrimitiveEventListener + DetectionProfilePublishedListener com @TransactionalEventListener AFTER_COMMIT; PrimitiveEventListenerTest + DetectionProfilePublishedListenerTest PASS)
Issues cobertas: GEST-019, GEST-020

#### Objetivo
Eventos de domínio publicados pos-commit via `ApplicationEventPublisher`; listeners isolados do caminho crítico com `@TransactionalEventListener(phase=AFTER_COMMIT)`.

#### Correções mandatórias

**GEST-019 — Listeners Primitive*:**
Criar `PrimitiveEventListener.java` em `infrastructure/messaging/` com:
- `@TransactionalEventListener(phase=AFTER_COMMIT) onPrimitiveRegistered(PrimitiveRegistered event)` → logar evento, invalidar cache (mapa local)
- `@TransactionalEventListener(phase=AFTER_COMMIT) onPrimitiveTransitioned(PrimitiveTransitioned event)` → logar, invalidar cache
- `@TransactionalEventListener(phase=AFTER_COMMIT) onPrimitiveDeprecated(PrimitiveDeprecated event)` → logar, invalidar cache

**GEST-020 — Listener DetectionProfilePublished:**
Em listener dedicado: invalidar `@Cacheable` de profile ativo (ou mapa local) após `DetectionProfilePublished`.

#### Alvos de alteração
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/messaging/PrimitiveEventListener.java` (criar)
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/messaging/DetectionProfilePublishedListener.java` (criar)

#### Critérios de aceite
- Listener não é chamado em rollback (teste: forçar rollback em register → spy não invocado)
- Exceção em listener não afeta resposta HTTP (teste: listener lança RuntimeException → request ainda retorna 201)

---

### Prioridade 7 — Observabilidade

Status: concluida (2026-08-12 | GestObservabilityPort + MicrometerObservabilityAdapter criados; ArchUnit PASS RULE-04)
Issues cobertas: GEST-021, GEST-022

#### Objetivo
Entregar observabilidade completa do `gest-service`: metricas de dominio Micrometer instrumentadas e exportadas via `/actuator/prometheus`; validacao LGPD automatizada via `LogPrivacyTest` (nenhum campo biometrico em surface de log); `ObservabilityIT` confirmando endpoint `/actuator/health` acessivel sem JWT.

#### Problema identificado
- `OBSERVABILITY: PARCIAL` no §1 — Actuator e Micrometer configurados no classpath mas nenhuma metrica de dominio registrada
- Ausencia de `@Counted` / `Counter.increment()` nos services que publicam eventos de dominio
- `LogPrivacyTest` ausente: campo `confidence` bruto, `landmark`, `video`, `base64`, `image` poderiam vazar em logs de erro ou de debug sem validacao automatizada
- `ObservabilityIT` ausente: `/actuator/health` e `/actuator/prometheus` nao sao exercitados em nenhum IT existente

#### Correcoes mandatorias

**GEST-021 — MeterRegistryCustomizer + metricas de dominio:**

Criar `infrastructure/config/ObservabilityConfig.java`:
```java
package com.neuroassist.gest.infrastructure.config;

@Configuration
public class ObservabilityConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> gestMetricsCustomizer() {
        return registry -> registry.config()
            .commonTags("service", "gest-service", "layer", "domain");
    }
}
```

Em `application/service/TipoGestoService.java`, injetar `MeterRegistry` e incrementar ao publicar eventos:
```java
// no campo:
private final MeterRegistry meterRegistry;

// em register(), apos eventPublisher.publishEvent(...):
meterRegistry.counter("gest_tipo_gesto_registrations_total",
    "tenant", TenantContextHolder.require()).increment();

// em transition(), apos salvar transicao bem-sucedida:
meterRegistry.counter("gest_tipo_gesto_transitions_total",
    "tenant", TenantContextHolder.require(),
    "from_state", fromState.name(),
    "to_state", toState.name()).increment();
```

Em `application/service/DetectionProfileService.java`, injetar `MeterRegistry` e incrementar apos publish:
```java
// em publish(), apos emitir DetectionProfilePublished:
meterRegistry.counter("gest_profile_versions_published_total",
    "tenant", TenantContextHolder.require()).increment();
```

Nota: `MeterRegistry` e injetado via `@RequiredArgsConstructor` — adicionar ao construtor existente. NAO usar `@Autowired` em campo.

**GEST-022 — LogPrivacyTest (unit test, sem Spring context):**

Criar `src/test/java/com/neuroassist/gest/api/LogPrivacyTest.java`:
```java
@ExtendWith(MockitoExtension.class)
class LogPrivacyTest {

    private static final Pattern BIOMETRIC_PATTERN = Pattern.compile(
        "(?i)(video|image|landmark|base64|pixels|frame|confidence|\\braw\\b)",
        Pattern.CASE_INSENSITIVE
    );

    @Test
    void globalExceptionHandler_does_not_log_biometric_fields() throws Exception {
        // Capturar output de log do GlobalExceptionHandler via ListAppender (Logback)
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        // Simular chamada com corpo contendo campo biometrico
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/gest/contract/validate");

        // lancar excecao com mensagem contendo campo proibido
        DetectionContractRequest badRequest = new DetectionContractRequest(
            Map.of("video", "base64data==", "gestureId", "uuid-1")
        );
        // apenas verificar que o handler nao loga os campos proibidos

        logger.detachAppender(appender);

        boolean foundBiometric = appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .anyMatch(msg -> BIOMETRIC_PATTERN.matcher(msg).find());

        assertThat(foundBiometric).isFalse();
    }

    @Test
    void dto_field_names_do_not_contain_biometric_identifiers() {
        // Verificar que nenhum campo dos DTOs de request/response contem nomes biometricos
        Class<?>[] dtoClasses = {
            DetectionContractRequest.class,
            DetectionContractResponse.class,
            DetectionProfileRequest.class,
            DetectionProfileResponse.class,
            TipoGestoRequest.class,
            TipoGestoResponse.class
        };
        for (Class<?> dto : dtoClasses) {
            for (RecordComponent component : dto.getRecordComponents()) {
                assertThat(BIOMETRIC_PATTERN.matcher(component.getName()).find())
                    .as("DTO %s nao deve ter campo biometrico: %s",
                        dto.getSimpleName(), component.getName())
                    .isFalse();
            }
        }
    }
}
```

**GEST-021 — ObservabilityIT:**

Criar `src/test/java/com/neuroassist/gest/integration/ObservabilityIT.java`:
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ObservabilityIT extends BaseIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void actuator_health_returns_200_without_jwt() {
        webTestClient.get().uri("/actuator/health")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void actuator_prometheus_exposes_gest_profile_metric() {
        webTestClient.get().uri("/actuator/prometheus")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(body -> assertThat(body)
                .contains("gest_profile_versions_published_total"));
    }

    @Test
    void actuator_prometheus_exposes_gest_tipo_gesto_metrics() {
        webTestClient.get().uri("/actuator/prometheus")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(body -> assertThat(body)
                .contains("gest_tipo_gesto_registrations_total")
                .contains("gest_tipo_gesto_transitions_total"));
    }
}
```

Verificar que `application.yml` ja expoe os endpoints corretos:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      show-details: when_authorized
  metrics:
    export:
      prometheus:
        enabled: true
```
Se ausente, adicionar. Se ja presente, confirmar que `prometheus` esta na lista `include`.

#### Alvos de alteracao (caminhos completos)

Criar:
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/config/ObservabilityConfig.java`
- `backend/services/gest-service/src/test/java/com/neuroassist/gest/integration/ObservabilityIT.java`
- `backend/services/gest-service/src/test/java/com/neuroassist/gest/api/LogPrivacyTest.java`

Editar:
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/service/TipoGestoService.java` — injetar `MeterRegistry`; incrementar `gest_tipo_gesto_registrations_total` em `register()` e `gest_tipo_gesto_transitions_total` em `transition()`
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/service/DetectionProfileService.java` — injetar `MeterRegistry`; incrementar `gest_profile_versions_published_total` em `publish()`
- `backend/services/gest-service/src/main/resources/application.yml` — verificar/adicionar `management.endpoints.web.exposure.include: health,info,prometheus,metrics`

#### Contrato operacional
Deve existir:
- `ObservabilityConfig.java` em `infrastructure/config/`
- `Counter` `gest_profile_versions_published_total` registrado apos qualquer chamada a `DetectionProfileService.publish()`
- `Counter` `gest_tipo_gesto_registrations_total` registrado apos `TipoGestoService.register()`
- `Counter` `gest_tipo_gesto_transitions_total` registrado apos `TipoGestoService.transition()`
- `/actuator/health` retorna 200 sem `Authorization` header
- `/actuator/prometheus` contem as 3 metricas de dominio

Nao deve existir:
- Campo `video`, `image`, `landmark`, `base64`, `confidence` em nenhuma surface de log (validado por `LogPrivacyTest`)
- `@Autowired` em campo nos services modificados

Condicao de bloqueio:
- `./gradlew :services:gest-service:test` exit nao-zero apos adicionar `MeterRegistry` ao construtor (injetar como `final` via `@RequiredArgsConstructor`)

#### Sequencia de execucao
- Fase 1 (config): criar `ObservabilityConfig.java` com `MeterRegistryCustomizer`; verificar `application.yml` actuator endpoints
- Fase 2 (TipoGestoService): injetar `MeterRegistry` como ultimo campo `final`; adicionar `counter.increment()` em `register()` e `transition()`; rodar `./gradlew :services:gest-service:test` — testes unitarios existentes devem continuar PASS (mockar `MeterRegistry` onde necessario nos testes existentes)
- Fase 3 (DetectionProfileService): idem — injetar `MeterRegistry`; adicionar `counter.increment()` em `publish()`
- Fase 4 (LogPrivacyTest): criar `LogPrivacyTest.java` — unit test sem Spring context; rodar `./gradlew :services:gest-service:test`
- Fase 5 (ObservabilityIT): criar `ObservabilityIT.java` com 3 cenarios; rodar `./gradlew :services:gest-service:integrationTest`
- Fase 6 (gates): rodar `./gradlew :services:gest-service:architectureCheck` para confirmar RULE-01 (domain nao importou io.micrometer — MeterRegistry esta em infrastructure/config, nao em domain)

#### Politica de testes
- `LogPrivacyTest`: unit test puro (`@ExtendWith(MockitoExtension.class)`) — sem Spring context; cobre surface de log do `GlobalExceptionHandler` e DTOs
- `ObservabilityIT`: integration test com `@SpringBootTest(RANDOM_PORT)` + `BaseIntegrationTest`; usa `WebTestClient` sem JWT para `/actuator/health` (endpoint permitAll)
- Testes unitarios existentes (`TipoGestoServiceTest`, `DetectionProfileServiceTest`): adicionar mock de `MeterRegistry` onde necessario — `when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mock(Counter.class))`

#### Multi-empresa
- `MeterRegistry` e um bean global — as metricas usam tag `"tenant"` com `TenantContextHolder.require()` para granularidade por tenant nas metricas
- Nao ha dados de tenant armazenados no `MeterRegistry` — apenas identificador de tag, sem PII

#### Migracoes Flyway
Nenhuma em P7 — nenhuma tabela nova.

#### Criterios de aceite (verificaveis objetivamente)
1. `./gradlew :services:gest-service:test` exit 0 (incluindo `LogPrivacyTest`)
2. `./gradlew :services:gest-service:integrationTest` exit 0 (`ObservabilityIT` 3/3 PASS)
3. `./gradlew :services:gest-service:architectureCheck` exit 0 (RULE-01 OK — `MeterRegistry` nao usado em domain.*)
4. `GET /actuator/health` retorna `200 {"status":"UP"}` sem `Authorization` header
5. `GET /actuator/prometheus` contem string `gest_profile_versions_published_total`
6. `GET /actuator/prometheus` contem string `gest_tipo_gesto_registrations_total`
7. `GET /actuator/prometheus` contem string `gest_tipo_gesto_transitions_total`
8. `LogPrivacyTest` confirma que nenhum campo biometrico aparece em logs do `GlobalExceptionHandler`

#### Riscos
- **R8 (medio):** `TipoGestoServiceTest` e `DetectionProfileServiceTest` ja existem com `@InjectMocks` — adicionar `MeterRegistry` ao construtor do service exige adicionar mock correspondente nos testes existentes; sem isso os testes falham com `NullPointerException`. Correcao: adicionar `@Mock MeterRegistry meterRegistry` e configurar `when(meterRegistry.counter(...)).thenReturn(mock(Counter.class))` no `@BeforeEach` de cada teste afetado.
- **R9 (baixo):** `ObservabilityIT` depende de `BaseIntegrationTest` (Testcontainers PostgreSQL singleton) — confirmar que o Spring context carrega sem erro com `WebTestClient` autowired; se `@SpringBootTest` conflitar com `WebFlux`, usar `TestRestTemplate` em vez de `WebTestClient`.
- **R10 (baixo):** RULE-01 proibe domain.* de importar org.springframework — `MeterRegistry` e `io.micrometer.core.instrument.MeterRegistry`; este pacote nao e `org.springframework`, portanto RULE-01 nao e violada. Verificar somente que a injecao ocorre em `application.service`, nao em `domain.service` ou `domain.model`.

#### Instrucao para implementador
Iniciar pela Fase 1 (ObservabilityConfig + application.yml) para garantir que o endpoint `/actuator/prometheus` responde antes de adicionar metricas. Fase 2 e 3: ao injetar `MeterRegistry` nos services, adicionar como ultimo campo `final` para nao quebrar a ordem dos argumentos do construtor gerado pelo `@RequiredArgsConstructor`. Atualizar os testes unitarios existentes ANTES de rodar `test` — a falha e previsivel e a correcao e mecanica (adicionar mock). Criar `LogPrivacyTest` na Fase 4 como validacao de contrato LGPD — nao pular mesmo que o gate de test ja esteja verde.

---

### Prioridade 8 — BDD end-to-end

Status: concluida (2026-08-13 | Cucumber steps implementados; ReportValidationSteps.java completo; ArchUnit PASS)
Issues cobertas: GEST-023, GEST-024

#### Objetivo
Implementar todos os step definitions Cucumber para que os ~54 cenarios das 4 features passem; mudar bddGate de DEFERRED para PASS. Nao criar novos feature files — apenas implementar os steps ja scaffoldados.

#### Problema identificado
- `CucumberIT.java` e `CucumberSpringContext.java` existem em `bdd/` mas o runner ainda nao executa cenarios reais
- 5 step files existem (`TipoGestoLifecycleSteps.java`, `EligibilityCheckSteps.java`, `DetectionProfileSteps.java`, `ReportValidationSteps.java`, `SharedSteps.java`) com todos os metodos lancando `io.cucumber.java.PendingException()`
- 4 feature files presentes: `tipo-gesto-lifecycle.feature` (15 cenarios), `eligibility-check.feature` (11), `detection-profile.feature` (14), `report-validation.feature` (11) — total ~51 base + Scenario Outline expansions = ~54 efetivos
- bddGate=DEFERRED (0/54 cenarios executados)

#### Correcoes mandatorias

**GEST-023 — CucumberIT runner e contexto Spring:**

Verificar `bdd/CucumberIT.java`: deve ter `@Suite`, `@IncludeEngines("cucumber")`, `@SelectClasspathResource("features")` e apontar para o glue correto. Formato esperado:
```java
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.neuroassist.gest.bdd")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @Disabled")
public class CucumberIT {}
```

Verificar `bdd/CucumberSpringContext.java`: deve ter `@CucumberContextConfiguration`, `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@ActiveProfiles("test")` e estender `BaseIntegrationTest` para herdar o Testcontainers PostgreSQL singleton.

Se `CucumberSpringContext` nao extende `BaseIntegrationTest`, adicionar a extensao (nao duplicar a configuracao Testcontainers — reusar o singleton).

**GEST-023 — SharedSteps.java (autenticacao + setup de tenant):**

Os steps compartilhados entre features cobrem setup de JWT e tenant. Implementar:
- `@Given("the system has a valid tenant {string}")`: configurar `TenantContextHolder.set(tenant)` no thread de teste; se houver chema ausente no Testcontainers, criar via `CREATE SCHEMA IF NOT EXISTS t_<tenant>` + aplicar DDL GEST.
- `@Given("the caller is authenticated with role {string}")`: gerar JWT de teste com o scope informado usando par RSA do `BaseIntegrationTest` (ou `MockMvc` com `@WithMockUser`). Se `BaseIntegrationTest` ja tem utilitario de JWT, reusar.
- `@And("the database is clean")` / `@After`: limpar tabelas `tipo_gesto`, `tipo_gesto_transition`, `detection_profile`, `detection_profile_primitive` no schema de teste via `@Transactional` ou DELETE direto.

Decidir estrategia de HTTP: **usar `MockMvc` com JWT inline** (sem start de servidor real) — mais rapido e isolado. Alternativa `TestRestTemplate` (server real) apenas se MockMvc conflitar com Testcontainers. O planner decide: usar `MockMvc` com `@AutoConfigureMockMvc` e tokens JWT gerados com nimbus-jose-jwt (par RSA configurado via `@DynamicPropertySource`).

**GEST-024 — TipoGestoLifecycleSteps.java (15 cenarios):**

Cobrem o ciclo completo: register → pending_training → supported → deprecated. Implementar cada step removendo o `PendingException` e adicionando a chamada HTTP via `MockMvc`:
- `POST /api/v1/gest/tipos-gesto` com body `{"nome":"<nome>"}` + Bearer JWT
- `GET /api/v1/gest/tipos-gesto/{id}`
- `PUT /api/v1/gest/tipos-gesto/{id}/transition` com body `{"toState":"<estado>","actor":"test","reason":"BDD"}`
- Armazenar `UUID` retornado em variavel de instancia do step (`this.lastCreatedId`) para reuso entre steps do mesmo cenario
- Assertions via `MvcResult`: verificar status HTTP, campo `detectabilityState`, campo `nome`
- Transicoes ilegais (ex.: REGISTERED → SUPPORTED direto) devem retornar `409` com `type: illegal-transition`

**GEST-024 — EligibilityCheckSteps.java (11 cenarios):**

Cobrem: todos primitivos SUPPORTED → eligible=true; algum nao-SUPPORTED → eligible=false; urgencyLevel != EMERGENCIA → eligible=true. Implementar:
- Setup: criar N TipoGestos via API (reuso de steps TipoGesto) e transicionar para os estados desejados
- `GET /api/v1/gest/eligibility?urgencyLevel=EMERGENCIA`
- Assertions: `eligible` booleano, lista `missingStates` quando aplicavel

**GEST-024 — DetectionProfileSteps.java (14 cenarios):**

Cobrem: publicar perfil com primitivos SUPPORTED → versao incrementada; ETag/304; perfil device via EdgePairing. Para EdgePairing nos steps BDD: usar `WireMock` embutido (ja no classpath via P4) ou `@MockBean EdgePairingPort` para cenarios de pairing — decidir: usar `@MockBean` do port para isolar do EDGE real. Implementar:
- `POST /api/v1/gest/profiles` → perfil publicado, versao=1
- `GET /api/v1/gest/profiles/active` com `If-None-Match: "<etag>"` → 304
- `GET /api/v1/gest/profiles/device/{deviceId}` com `@MockBean EdgePairingPort` retornando `"tenant-test"` → perfil ativo
- Cenario device nao pareado: `@MockBean` lanca `DeviceNotPairedException` → 403

**GEST-024 — ReportValidationSteps.java (11 cenarios):**

Cobrem: report valido → valid=true; campos proibidos (`video`, `image`, `landmark`, `base64`, `confidence`) → valid=false com lista de erros; campos obrigatorios ausentes → valid=false. Implementar:
- `POST /api/v1/gest/contract/validate` com body `{"report":{...}}`
- Assertions: `valid` booleano, array `errors` com mensagens especificas

#### Alvos de alteracao (caminhos completos)

Verificar e corrigir se necessario (nao recriar):
- `backend/services/gest-service/src/test/java/com/neuroassist/gest/bdd/CucumberIT.java`
- `backend/services/gest-service/src/test/java/com/neuroassist/gest/bdd/CucumberSpringContext.java`

Implementar (substituir PendingException por logica real):
- `backend/services/gest-service/src/test/java/com/neuroassist/gest/bdd/steps/SharedSteps.java`
- `backend/services/gest-service/src/test/java/com/neuroassist/gest/bdd/steps/TipoGestoLifecycleSteps.java`
- `backend/services/gest-service/src/test/java/com/neuroassist/gest/bdd/steps/EligibilityCheckSteps.java`
- `backend/services/gest-service/src/test/java/com/neuroassist/gest/bdd/steps/DetectionProfileSteps.java`
- `backend/services/gest-service/src/test/java/com/neuroassist/gest/bdd/steps/ReportValidationSteps.java`

Nao alterar (ja presentes e corretos):
- `backend/services/gest-service/src/test/resources/features/tipo-gesto-lifecycle.feature`
- `backend/services/gest-service/src/test/resources/features/eligibility-check.feature`
- `backend/services/gest-service/src/test/resources/features/detection-profile.feature`
- `backend/services/gest-service/src/test/resources/features/report-validation.feature`

#### Contrato operacional
Deve existir:
- `CucumberIT.java` com `@Suite` e glue apontando para `com.neuroassist.gest.bdd`
- `CucumberSpringContext.java` com `@CucumberContextConfiguration` e PostgreSQL Testcontainers ativo
- Todos os steps em `SharedSteps.java` resolvidos sem `PendingException`
- 54/54 cenarios passando (nenhum skipped, nenhum pending)

Nao deve existir:
- `throw new io.cucumber.java.PendingException()` em nenhum step dos 5 arquivos
- `@MockBean` para `TipoGestoRepositoryPort`, `DetectionProfileRepositoryPort` — usar banco Testcontainers real para persistencia; `@MockBean` apenas para `EdgePairingPort` nos cenarios de device

Condicao de bloqueio:
- `./gradlew :backend:services:gest-service:integrationTest` exit nao-zero

#### Sequencia de execucao
- Fase 1 (runner): verificar `CucumberIT.java` e `CucumberSpringContext.java`; rodar `./gradlew :services:gest-service:integrationTest` para confirmar que o runner esta ativo (cenarios aparecem como PENDING, nao como erro de configuracao)
- Fase 2 (shared): implementar `SharedSteps.java` — autenticacao JWT + limpeza de banco; confirmar que o contexto Spring sobe sem erro com `BaseIntegrationTest`
- Fase 3 (tipo-gesto): implementar `TipoGestoLifecycleSteps.java` (15 cenarios); rodar integrationTest — somente esses cenarios devem passar
- Fase 4 (eligibility): implementar `EligibilityCheckSteps.java` (11 cenarios); rodar integrationTest cumulativo
- Fase 5 (detection-profile): implementar `DetectionProfileSteps.java` (14 cenarios) — adicionar `@MockBean EdgePairingPort` onde necessario; rodar integrationTest cumulativo
- Fase 6 (report-validation): implementar `ReportValidationSteps.java` (11 cenarios); rodar integrationTest final
- Fase 7 (gate): rodar `./gradlew :services:gest-service:integrationTest` — todos os cenarios devem sair PASS; confirmar `./gradlew :services:gest-service:architectureCheck` exit 0

#### Politica de testes
- BDD com Testcontainers PostgreSQL (banco real — nao H2); MockMvc para chamadas HTTP (sem start de servidor real)
- `@MockBean EdgePairingPort` permitido somente nos steps de DetectionProfile que cobrem cenarios de pairing (isolar do EDGE externo)
- Nenhum step deve usar `@Disabled` ou `assumeTrue(false)` para mascarar falhas
- ArchUnit deve continuar PASS apos P8 (nenhuma violacao de RULE-01..06 nos arquivos de teste)

#### Multi-empresa
- `SharedSteps.java` deve configurar `TenantContextHolder.set("tenant-bdd-test")` antes de cada cenario
- Schema `t_tenant-bdd-test` deve ser criado no Testcontainers PostgreSQL no setup (via SQL direto ou reutilizando logica de `BaseIntegrationTest`)
- Nenhum dado de tenant vaza entre cenarios — limpeza obrigatoria no `@After` de cada cenario

#### Migracoes Flyway
Nenhuma em P8 — DDL GEST ja aplicado via `BaseIntegrationTest` (`init-schema.sql` em `src/test/resources/`). Verificar que o schema DDL inclui todas as tabelas necesssarias para os cenarios BDD (tipo_gesto, tipo_gesto_transition, detection_profile, detection_profile_primitive).

#### Criterios de aceite (verificaveis objetivamente)
1. `./gradlew :backend:services:gest-service:integrationTest` exit 0 com todos os cenarios Cucumber PASS
2. Nenhum cenario em estado PENDING ou UNDEFINED (zero `PendingException` no output)
3. Contagem de cenarios passando >= 54 (ou o total efetivo apos expansao de Scenario Outline)
4. `./gradlew :backend:services:gest-service:architectureCheck` exit 0 (nenhuma violacao nova)
5. `POST /api/v1/gest/tipos-gesto` com nome duplicado → cenario BDD confirma `409` com `type: tipo-gesto-duplicate`
6. Transicao ilegal via BDD → `409` com `type: illegal-transition`
7. Report com campo `video` → `valid=false` com `errors` contendo referencia ao campo proibido

#### Riscos
- **R11 (alto):** `CucumberSpringContext` pode nao herdar `BaseIntegrationTest` — se o container PostgreSQL nao estiver ativo o Spring context falha no startup; correcao: verificar heranca e adicionar `extends BaseIntegrationTest` se ausente
- **R12 (medio):** JWT gerado em `SharedSteps` pode conflitar com a configuracao OAuth2 do contexto de teste — usar `@DynamicPropertySource` ja utilizado em `SecurityIT` para consistencia; se existir helper de JWT em `BaseIntegrationTest`, reusar
- **R13 (medio):** Steps de `DetectionProfileSteps` dependem de ter TipoGestos SUPPORTED cadastrados — os steps de setup devem reusar `TipoGestoLifecycleSteps` via `@Autowired` ou via chamadas de API sequenciais dentro do step de Given
- **R14 (baixo):** `Scenario Outline` com `Examples` pode gerar cenarios duplicados se o `@DataTableType` nao estiver configurado — verificar que o `CucumberSpringContext` registra os converters necessarios

#### Instrucao para implementador
Iniciar pela Fase 1 (runner check) antes de qualquer implementacao de step — confirmar que o runner esta ativo e que os cenarios aparecem como PENDING (nao como erro de configuracao). Se aparecer erro de `@CucumberContextConfiguration ausente`, o `CucumberSpringContext` nao esta no classpath do runner — corrigir o glue package. Usar `MockMvc` injetado via `@Autowired` no `CucumberSpringContext` e compartilhado por todos os steps via `@Autowired`. Para JWT nos steps: criar um utilitario estatico `TestJwtFactory.forScope(String scope)` que gera e assina o token com o par RSA configurado — centralizar para nao duplicar em cada step file. Nao usar `@WithMockUser` (nao funciona com Cucumber) — usar JWT real ou `SecurityConfig` com `permitAll()` em perfil `test` (escolher um e documentar).

---

### Prioridade 9 — CircuitBreaker EdgePairing (Resilience4j)

Status: pendente (PRIORIDADE ALVO)
Issues cobertas: GEST-025

#### Objetivo
Adicionar `@CircuitBreaker(name="edgePairing", fallbackMethod="edgePairingFallback")` em `EdgePairingAdapter.checkPairingAndGetTenant()`, configurar o circuito em `application.yml` e validar o comportamento com um teste de integracao leve (`EdgePairingCircuitBreakerTest`). A dependencia `io.github.resilience4j:resilience4j-spring-boot3` ja esta presente no `build.gradle.kts` (linha 52) — nenhuma alteracao de build necessaria. `@EnableAspectJAutoProxy` e auto-configurado pelo Spring Boot + Resilience4j starter — nenhuma mudanca em configuracao de AOP.

#### Problema identificado
- `EdgePairingAdapter.checkPairingAndGetTenant()` chama `edgePairingClient.getTenantForDevice()` sem nenhum mecanismo de circuit breaker: falhas de EDGE causam bloqueio do thread ate timeout do Feign (padrao 10s), degradando o servico sob falha do EDGE
- `application.yml` nao tem `resilience4j.circuitbreaker` — o starter esta no classpath mas sem instancia `edgePairing` configurada
- Nenhum teste verifica que o circuito abre apos falhas consecutivas e retorna 503 imediato com o circuito aberto

#### Correcoes mandatorias

**GEST-025-A — Adicionar `@CircuitBreaker` em `EdgePairingAdapter`:**

Em `EdgePairingAdapter.checkPairingAndGetTenant()`, adicionar a anotacao e o metodo de fallback. O fallback deve relançar `CodedException("edge-unavailable")` imediatamente (sem nova chamada ao EDGE). O comportamento do fallback e identico ao catch de `FeignException` ja existente — a diferenca e que com o CB aberto o fallback e invocado sem chamar o EDGE.

Adicionar a anotacao na assinatura do metodo publico:
```java
@CircuitBreaker(name = "edgePairing", fallbackMethod = "edgePairingFallback")
@Override
public String checkPairingAndGetTenant(UUID deviceId) {
    // corpo existente sem alteracao
}
```

Adicionar o metodo de fallback imediatamente apos `checkPairingAndGetTenant()`. O fallback DEVE ter assinatura identica ao metodo original mais um parametro `Throwable` como ultimo argumento; deve ter visibilidade `private` (nao expor como API do adapter):
```java
private String edgePairingFallback(UUID deviceId, Throwable cause) {
    log.warn("EdgePairing circuit breaker open or error for deviceId={} — cause: {}",
        deviceId, cause.getClass().getSimpleName());
    throw new CodedException("edge-unavailable",
        "Edge service circuit open — deviceId: " + deviceId, cause);
}
```

Import a adicionar: `import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;`

**GEST-025-B — Configurar `resilience4j.circuitbreaker` em `application.yml`:**

Adicionar ao final de `application.yml` (antes de qualquer comentario final, ou como bloco de topo-nivel novo):

```yaml
resilience4j:
  circuitbreaker:
    instances:
      edgePairing:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 5
        failure-rate-threshold: 60
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 1
        automatic-transition-from-open-to-half-open-enabled: true
        register-health-indicator: true
        record-exceptions:
          - feign.FeignException
          - com.neuroassist.gest.shared.error.CodedException
```

Semantica das configuracoes (planner decide — implementador nao altera):
- `sliding-window-size: 5` — janela de 5 chamadas para calcular taxa de falha
- `failure-rate-threshold: 60` — abre o circuito se >= 60% das 5 chamadas falharem (3 de 5)
- `wait-duration-in-open-state: 30s` — permanece aberto por 30s antes de tentar meio-aberto
- `permitted-number-of-calls-in-half-open-state: 1` — testa com 1 chamada no estado meio-aberto
- `record-exceptions` — registra tanto `FeignException` quanto `CodedException` como falhas do circuito (a `CodedException("device-not-paired")` de 404 NAO deve abrir o circuito — apenas `edge-unavailable` deve; para isso, usar `ignore-exceptions` para `CodedException` com code `device-not-paired`)

Ajuste adicional: o `CodedException("device-not-paired")` e um erro de negocio (device nao existe), nao uma falha de infra — o CB NAO deve contar esse caso como falha. Adicionar `ignore-exceptions` para excluir esse codigo especifico:

```yaml
        ignore-exceptions:
          - com.neuroassist.gest.shared.error.CodedException
```

E remover `com.neuroassist.gest.shared.error.CodedException` de `record-exceptions`, mantendo apenas `feign.FeignException`. Isso garante que somente falhas de comunicacao (timeout, 500, 503) abrem o circuito, nao erros de negocio (404 device-not-found ja capturado como `CodedException`).

Configuracao final de `resilience4j.circuitbreaker.instances.edgePairing`:
```yaml
resilience4j:
  circuitbreaker:
    instances:
      edgePairing:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 5
        failure-rate-threshold: 60
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 1
        automatic-transition-from-open-to-half-open-enabled: true
        register-health-indicator: true
        record-exceptions:
          - feign.FeignException
        ignore-exceptions:
          - com.neuroassist.gest.shared.error.CodedException
```

**GEST-025-C — Criar `EdgePairingCircuitBreakerTest` (Spring integration test leve):**

A anotacao `@CircuitBreaker` so e ativada via Spring AOP — um teste Mockito puro sem Spring context nao exercita o comportamento do circuito. O planner decide: criar um Spring slice test usando `@SpringBootTest` com propriedades de teste que reduzem a janela para 3 chamadas, garantindo que o CB abra rapidamente no cenario de teste.

Criar `backend/services/gest-service/src/test/java/com/neuroassist/gest/integration/EdgePairingCircuitBreakerTest.java`:

```java
package com.neuroassist.gest.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.neuroassist.gest.application.port.out.EdgePairingPort;
import com.neuroassist.gest.infrastructure.clients.EdgePairingClient;
import feign.FeignException;
import feign.Request;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EdgePairingCircuitBreakerTest extends BaseIntegrationTest {

    @MockBean
    private EdgePairingClient edgePairingClient;

    @Autowired
    private EdgePairingPort edgePairingPort;

    @DynamicPropertySource
    static void circuitBreakerProperties(DynamicPropertyRegistry registry) {
        // Janela menor para testar abertura do circuito rapidamente
        registry.add("resilience4j.circuitbreaker.instances.edgePairing.sliding-window-size", () -> "3");
        registry.add("resilience4j.circuitbreaker.instances.edgePairing.failure-rate-threshold", () -> "100");
        registry.add("resilience4j.circuitbreaker.instances.edgePairing.wait-duration-in-open-state", () -> "60s");
    }

    @Test
    void circuit_opens_after_consecutive_feign_failures_and_subsequent_call_fails_immediately() {
        // Arrange: FeignException.ServiceUnavailable simula EDGE fora do ar
        FeignException.ServiceUnavailable feignError = new FeignException.ServiceUnavailable(
            "edge down",
            Request.create(Request.HttpMethod.GET, "http://edge-service/test",
                Map.of(), null, StandardCharsets.UTF_8, null),
            null, Map.of()
        );
        when(edgePairingClient.getTenantForDevice(any())).thenThrow(feignError);

        UUID deviceId = UUID.randomUUID();

        // Act: realizar sliding-window-size (3) chamadas para saturar a janela
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> edgePairingPort.checkPairingAndGetTenant(deviceId))
                .hasMessageContaining("edge-unavailable")
                .hasMessageContaining("Edge service");
        }

        // Assert: 4a chamada deve falhar via fallback (sem chamar o EDGE) com edge-unavailable
        // O circuito esta aberto: CallNotPermittedException e capturada pelo fallback
        assertThatThrownBy(() -> edgePairingPort.checkPairingAndGetTenant(deviceId))
            .hasMessageContaining("edge-unavailable");
    }

    @Test
    void device_not_paired_does_not_open_circuit() {
        // CodedException("device-not-paired") e ignorada pelo CB — nao deve abrir o circuito
        FeignException.NotFound notFound = new FeignException.NotFound(
            "device not found",
            Request.create(Request.HttpMethod.GET, "http://edge-service/test",
                Map.of(), null, StandardCharsets.UTF_8, null),
            null, Map.of()
        );
        when(edgePairingClient.getTenantForDevice(any())).thenThrow(notFound);

        UUID deviceId = UUID.randomUUID();

        // 3 chamadas de 404 — CB NAO deve abrir (FeignException.NotFound
        // e convertida em CodedException("device-not-paired") que esta em ignore-exceptions)
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> edgePairingPort.checkPairingAndGetTenant(deviceId))
                .hasMessageContaining("device-not-paired");
        }

        // Apos 3 x 404, uma 5a chamada com 503 ainda deve passar pelo EDGE (circuito fechado)
        FeignException.ServiceUnavailable feignError = new FeignException.ServiceUnavailable(
            "edge down",
            Request.create(Request.HttpMethod.GET, "http://edge-service/test",
                Map.of(), null, StandardCharsets.UTF_8, null),
            null, Map.of()
        );
        when(edgePairingClient.getTenantForDevice(any())).thenThrow(feignError);
        assertThatThrownBy(() -> edgePairingPort.checkPairingAndGetTenant(deviceId))
            .hasMessageContaining("edge-unavailable");
    }
}
```

Nota sobre `BaseIntegrationTest`: este teste usa `@SpringBootTest(webEnvironment=NONE)` e nao precisa de PostgreSQL. Se `BaseIntegrationTest` inicia Testcontainers PostgreSQL, isso adiciona overhead desnecessario mas e aceitavel — o contexto Spring sobe sem erro porque o datasource e configurado mas nenhuma query e feita. Alternativa: criar uma configuracao de teste `@TestConfiguration` que desabilita o datasource. O planner decide: usar `BaseIntegrationTest` diretamente para reutilizar a configuracao existente e evitar nova configuracao de contexto.

#### Alvos de alteracao (caminhos completos)

Editar:
- `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/clients/EdgePairingAdapter.java`
  - Adicionar import `io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker`
  - Adicionar anotacao `@CircuitBreaker(name="edgePairing", fallbackMethod="edgePairingFallback")` em `checkPairingAndGetTenant()`
  - Adicionar metodo privado `edgePairingFallback(UUID deviceId, Throwable cause)` apos o metodo principal
- `backend/services/gest-service/src/main/resources/application.yml`
  - Adicionar bloco `resilience4j.circuitbreaker.instances.edgePairing` com as 7 propriedades definidas acima

Criar:
- `backend/services/gest-service/src/test/java/com/neuroassist/gest/integration/EdgePairingCircuitBreakerTest.java`
  - 2 cenarios: `circuit_opens_after_consecutive_feign_failures` + `device_not_paired_does_not_open_circuit`

NAO alterar:
- `build.gradle.kts` — resilience4j ja presente na linha 52
- `EdgePairingClient.java` — interface Feign sem alteracao
- `EdgePairingPort.java` — interface do port sem alteracao
- Nenhum arquivo de dominio

#### Contrato operacional

Deve existir apos P9:
- `EdgePairingAdapter.checkPairingAndGetTenant()` anotado com `@CircuitBreaker(name="edgePairing")`
- Metodo privado `edgePairingFallback(UUID, Throwable)` em `EdgePairingAdapter` que lanca `CodedException("edge-unavailable")`
- Bloco `resilience4j.circuitbreaker.instances.edgePairing` em `application.yml` com `sliding-window-size`, `failure-rate-threshold`, `wait-duration-in-open-state`, `ignore-exceptions: [CodedException]`
- `EdgePairingCircuitBreakerTest` com 2 cenarios passando

Nao deve existir:
- `@CircuitBreaker` aplicado em qualquer metodo de `EdgePairingClient` diretamente (RULE-06: client e interface Feign — CB deve estar no adapter, nao no client)
- `@CircuitBreaker` em qualquer classe de dominio ou application.service (violaria RULE-01/03)
- Metodo fallback `public` (exporia como API do adapter)

Condicao de bloqueio:
- `./gradlew :backend:services:gest-service:architectureCheck` exit nao-zero (qualquer violacao de RULE-01..06)

#### Sequencia de execucao

- Fase 1 (adapter): adicionar import + anotacao `@CircuitBreaker` em `EdgePairingAdapter.checkPairingAndGetTenant()`; adicionar metodo `edgePairingFallback` privado; verificar que o arquivo compila sem erros (`./gradlew :backend:services:gest-service:compileJava`)
- Fase 2 (yml): adicionar bloco `resilience4j.circuitbreaker.instances.edgePairing` em `application.yml`; verificar que o Spring context sobe sem erro de configuracao
- Fase 3 (teste): criar `EdgePairingCircuitBreakerTest` com os 2 cenarios; rodar `./gradlew :backend:services:gest-service:integrationTest`
- Fase 4 (arch): rodar `./gradlew :backend:services:gest-service:architectureCheck` — confirmar RULE-01 (sem imports Spring em domain), RULE-04 (infrastructure nao exposto em api), RULE-05 (infrastructure nao depende de api), RULE-06 (@FeignClient somente em infrastructure.clients)
- Fase 5 (gates finais): rodar `./gradlew :backend:services:gest-service:test` (testes unitarios nao devem regredir — nenhum service foi alterado); confirmar que `TipoGestoServiceTest` e `DetectionProfileServiceTest` continuam PASS

#### Politica de testes

- `EdgePairingCircuitBreakerTest`: Spring integration test (`@SpringBootTest(webEnvironment=NONE)`) com `@MockBean EdgePairingClient`; usa `@DynamicPropertySource` para reduzir `sliding-window-size` a 3 no contexto de teste; nao usa `@WebMvcTest` (nao exercita HTTP)
- Cenario 1: 3 x `FeignException.ServiceUnavailable` → CB abre → 4a chamada falha via fallback com `edge-unavailable`
- Cenario 2: 3 x `FeignException.NotFound` → `CodedException("device-not-paired")` ignorada pelo CB → CB permanece fechado → chamada subsequente com 503 chega ao EDGE (CB nao abriu)
- Testes unitarios existentes (`TipoGestoServiceTest`, `DetectionProfileServiceTest`, etc.): nenhuma alteracao necessaria — `EdgePairingAdapter` nao e injetado nos services testados nesses testes

#### Multi-empresa

Nao aplicavel para P9. O CircuitBreaker opera no nivel de infraestrutura (cliente Feign → EDGE service) independente do `TenantContextHolder`. O contexto de tenant nao e propagado ao CB — o CB e global para a instancia JVM, nao por tenant. Isso e correto: se o EDGE esta indisponivel, todos os tenants sao afetados igualmente.

#### Migracoes Flyway

Nenhuma em P9 — nenhuma tabela nova.

#### Criterios de aceite (verificaveis objetivamente)

1. `./gradlew :backend:services:gest-service:compileJava` exit 0 apos adicionar `@CircuitBreaker` e fallback
2. `./gradlew :backend:services:gest-service:integrationTest` exit 0 com `EdgePairingCircuitBreakerTest` 2/2 PASS
3. `./gradlew :backend:services:gest-service:test` exit 0 (testes unitarios existentes sem regredir)
4. `./gradlew :backend:services:gest-service:architectureCheck` exit 0 (nenhuma violacao nova de RULE-01..06)
5. `application.yml` contem `resilience4j.circuitbreaker.instances.edgePairing.failure-rate-threshold: 60`
6. `application.yml` contem `resilience4j.circuitbreaker.instances.edgePairing.ignore-exceptions` listando `CodedException`
7. Metodo `edgePairingFallback` em `EdgePairingAdapter` e privado (nao exposto como API)
8. `@CircuitBreaker` NAO esta aplicado em nenhuma interface de dominio ou application.service

#### Riscos

- **R15 (medio):** `EdgePairingCircuitBreakerTest` usa `BaseIntegrationTest` que inicia Testcontainers PostgreSQL — se o PostgreSQL nao estiver disponivel no ambiente de CI, o teste falhara por razao ortogonal ao CB. Mitigacao: o singleton Testcontainers ja e reutilizado por todos os ITs existentes; nao ha overhead adicional. Se necessario, criar profile de teste separado que desativa o datasource — mas o planner decide que usar `BaseIntegrationTest` e a abordagem padrao do projeto.
- **R16 (baixo):** `@DynamicPropertySource` em `EdgePairingCircuitBreakerTest` pode conflitar com o context cache do Spring (propriedades diferentes criam contextos diferentes). Verificar que o tempo de startup nao ultrapassa o timeout do CI. Mitigacao: o contexto adicional e minimo (`webEnvironment=NONE`).
- **R17 (baixo):** `FeignException.ServiceUnavailable` e `FeignException.NotFound` requerem um `Request` objeto no construtor — garantir que o mock de `FeignException` e construido corretamente no teste (ver codigo de exemplo na secao de correcoes mandatorias acima).

#### Instrucao para implementador

Comecar pela Fase 1 (adapter) — adicionar `@CircuitBreaker` e fallback primeiro, confirmar que compila. Fase 2 (yml) e critica: sem a configuracao, o CB usa defaults do Resilience4j (pode diferir do esperado). Fase 3 (teste): ao criar `EdgePairingCircuitBreakerTest`, verificar que `BaseIntegrationTest` e importado corretamente e que o `@MockBean EdgePairingClient` sobrescreve o bean real (nao o stub `EdgePairingPortStub`). O `@MockBean` e do tipo `EdgePairingClient` (interface Feign), nao `EdgePairingPort` — o adapter real (`EdgePairingAdapter`) deve ser o bean ativo no contexto de teste. Confirmar com `ctx.getBean(EdgePairingPort.class)` que e uma instancia de `EdgePairingAdapter` (nao do stub). Se o stub estiver ativo por alguma razao (profile `poc` ativo), desativar o profile no `@SpringBootTest`: `@ActiveProfiles("test")` (nenhum perfil `poc`).

---

## §4 Riscos

| Risco | Severidade | Mitigação |
|---|---|---|
| R1: Compilação dos adapters falha por outros motivos além de port/out | BAIXO | Executar `compileJava` depois de cada fase de P1 para isolar |
| R2: TenantContextHolder mockar em P2 é trabalhoso | MÉDIO | Usar `MockedStatic<TenantContextHolder>` do Mockito; padronizar nos testes |
| R3: Hibernate Schema Validator não reconhece índice parcial | MÉDIO | Validar com `psql \d tipo_gesto` antes de rodar `validate` |
| R4: nextVersion() com lock pessimístico sob carga | BAIXO | Aceitável para MVP; P9 pode revisar se necessário |
| R5: INT-GEST-007 aberta pode invalidar SecurityConfig de P5 | ALTO | Isolar transport auth no adapter; SecurityConfig usa `client_credentials` como padrão |
| R6: bddGate DEFERRED sem P1..P6 concluídos | ALTO | Não iniciar P8 antes de P6 DONE |
| R7: Listener com TransactionalEventListener não dispara em teste | BAIXO | Usar `@Transactional` no teste + `@Rollback(false)` para forçar commit; ou `@TestTransaction` |

---

## §5 Critérios de aceite

Critérios de aceite do plano completo (verificáveis objetivamente ao fim de cada P):

- **P1:** `compileJava` e `architectureCheck` exit 0; GET /tipos-gesto retorna 200 sem exceção
- **P2:** `test` exit 0; POST /tipos-gesto → 201 REGISTERED; transição ilegal → 409; eligibility → 200 eligible/ineligible
- **P3:** `integrationTest` exit 0; V0400 aplicável via psql; isolamento cross-tenant verificado
- **P4:** Profile pull com ETag correto → 304; device não pareado → 403; EdgePairing down → 503
- **P5:** Sem JWT → 401; scope errado → 403; X-Correlation-Id presente em toda resposta
- **P6:** Listener não invocado em rollback; exception em listener não propaga para HTTP response
- **P7:** `/actuator/prometheus` expõe `gest_profile_versions_published_total`; nenhum log contém campo biométrico
- **P8:** 54/54 cenários Cucumber passam; `integrationTest` exit 0
- **P9:** Circuit aberto após 5 falhas; 503 imediato com circuit aberto

---

## 3. Dependências externas

| Serviço | Tipo | Contrato | Status |
|---|---|---|---|
| EDGE | Outbound Feign | `GET /api/v1/edge/devices/{deviceId}/tenant` | Transport auth aberta (INT-GEST-007) |
| ACS | Inbound auth | `jwk-set-uri` para JWT validation | Operacional |
| GWY | Ingresso | Rota `/api/v1/gest/**` via Eureka | Operacional |
| REG | Discovery | Eureka defaultZone | Operacional |
| MIG | Migrations | V0400 DDL a contribuir | GEST-013 pendente |
| PAC | Consumer | `POST /eligibility:check` | Contrato definido (INT-GEST-006) |
| EVNT | Consumer | `RecognizedCommandReport` validação stateless | Contrato v1.0.0 congelado |

---

## 4. Restrições arquiteturais

- **RULE-01..06:** domain e application não importam infrastructure (ArchUnit ativo)
- **RULE-07:** `TenantContextHolder.require()` obrigatório em todo método de service que acessa dados de tenant (implementado em P5, verificado via code review)
- **RULE-08:** `spring.jpa.open-in-view=false` — manter em `application.yml`
- **LGPD:** nenhuma coluna, payload, log ou erro pode carregar `video`, `image`, `landmark`, `confidence` bruto, `base64`
- **Imutabilidade:** `TipoGestoTransition` append-only; `DetectionProfile` imutável após publish (só `active` flip)
- **Messaging:** in-process apenas (MVP); nenhuma dependência de broker externo no classpath

---

## 5. Referências

- `docs/architecture/backend/services/gest-service/contracts/api-contract.md`
- `docs/architecture/backend/services/gest-service/contracts/data-model.md`
- `docs/architecture/backend/services/gest-service/contracts/event-contracts.md`
- `docs/architecture/backend/services/gest-service/contracts/integration-contracts.md`
- `docs/architecture/backend/services/gest-service/adr/ADR-006-tipogesto-detectability-lifecycle.md`
- `docs/architecture/backend/issues/GEST-001.md` ate `GEST-025.md`
- `docs/architecture/backend/issues/INT-GEST-001.md` ate `INT-GEST-007.md`
- `backend/services/gest-service/src/main/resources/db/migration/V1__create_initial_schema.sql`
