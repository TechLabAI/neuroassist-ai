# Plano -- ACS (acs-service)

> **Módulo:** ACS (Cuidadores e Acesso) | **Serviço:** acs-service (port 8088)
> **Data de Criação:** 2026-08-19 | **Baseline Score:** 18/100 (Inicial)
> **Validação Final (Modo 4):** 2026-08-19 20:30 | **Score Atual:** 72/100 (Consistente)
> **Plano P7 Refinado:** 2026-08-20 | **Tipo de Planejamento:** INCREMENTAL ESPECIFICO (P7 ACS-026 — 13 itens)

## Status das prioridades

- P1 -- DONE (ACS-001, ACS-002, ACS-003, ACS-004) ✅ Foundation: Estrutura + Contratos
- P2 -- DONE (ACS-005, ACS-006, ACS-007, ACS-012, ACS-013, ACS-019, ACS-020, ACS-021) ✅ Auth + Core
- P3 -- DONE (ACS-008, ACS-009, ACS-010, ACS-011, ACS-014, ACS-015, ACS-016, ACS-017) ✅ Admin + Links
- P4 -- DONE (ACS-018, ACS-022, ACS-023) ✅ Security + Config
- P5 -- DONE (ACS-024) ✅ BDD + Cucumber
- P6 -- DONE (ACS-025) ✅ Observabilidade
- P7 -- OPEN (ACS-026) 🔄 Consolidação Final (signing key persistida + refresh token real + OTel/RULE-07)

**Modo 4 FULL-VALIDACAO (2026-08-19 20:30):**
- Gates: compileJava PASS ✅ | architectureCheck PASS ✅ | build PASS ✅
- Score: 72/100 (Consistente)
- Testes: 67/113 PASS (unitários OK; 46 falhas em integração/BDD requerem Docker)

---

## 0. Leitura Executiva do Estado Atual

### Papel do serviço

acs-service é o serviço de **identidade e autorização centralizado** da plataforma NeuroAssist AI. Atua como OAuth2 Authorization Server + Resource Server dual-mode: emite JWT RS256, valida tokens, gerencia tenants, cuidadores e links cuidador-paciente, e serve como diretório de contexto de acesso para outros serviços.

### Estado atual observado

Score 18/100 (Inicial). Scaffold ~85% completo estruturalmente: todos os stubs de controllers, services, ports, domain models, entities, adapters, repositories e Feign clients existem. Funcionalidade: **0% implementada** — todos os application services lançam `UnsupportedOperationException`.

Violação arquitetural ativa: `@Autowired private ApplicationContext context;` em `ApplicationContextLoadsIT.java` (campo — substituir por injeção via construtor).

### Continuidade da última rodada

Não houve implementação anterior. Este é o primeiro ciclo de execução após o scaffold.

### Estado multi-empresa

ACS usa esquema próprio de multi-tenancy: `neuroassist_global` (Tenant + SigningKey) + `t_<tenantId>` (Cuidador + CaregiverPatientLink). A infraestrutura já tem scaffolds de `TenantContextHolder`, `TenantContextFilter`, `MultiTenantConnectionProvider` e `TenantIdentifierResolver`. O `EmpresaContextHolder.require()` global da plataforma mapeia para `TenantContextHolder.require()` neste serviço — enforcement completo em P4 (ACS-022).

---

## 1. Principais Achados

### Pontos fortes

- Estrutura hexagonal completa (api/application/domain/infrastructure)
- ArchUnit rules RULE-01..06 implementadas em `ArchitectureTest.java`
- Domain models, enums, JPA entities, Feign clients e adapters scaffolded
- BDD stubs (Cucumber) e ArchitectureTest já estruturados

### Gaps críticos

1. Todos os port interfaces estão vazios (MigProvisioningPort, HistAuditPort, PacPatientResolvePort, SigningKeyPort têm corpo vazio com TODO)
2. Domain models incompletos: `Cuidador` tem só `id, tenantId, createdAt`; falta `nome, login, credentialHash, papel, status`
3. JPA entities incompletos: `CuidadorJpaEntity` falta `nome, login, credentialHash, papel`
4. DTOs são stubs mínimos (campos insuficientes para os contratos de API)
5. Todos os application services lançam `UnsupportedOperationException`
6. `ApplicationContextLoadsIT`: `@Autowired` em campo (violação — substituir por construtor)

### Riscos

- Último cuidador revogado → alarm sem destino (SAFETY-CRITICAL — enforçar em P3/ACS-016)
- `credentialHash` e `privateKeyEnc` nunca devem aparecer em respostas de API ou logs

### Aderência às regras (RULE-01..09)

- RULE-01, RULE-02: domain models não importam Spring/JPA — PASS (stubs limpos)
- RULE-03: `@Transactional` apenas em application.service — PASS nos stubs
- RULE-04, RULE-05: api não depende de infra e vice-versa — PASS nos stubs
- RULE-06: @FeignClient apenas em infrastructure.clients — PASS
- RULE-07: TenantContextHolder.require() — não implementado (P4)
- RULE-08: `open-in-view=false` — necessita verificação em application.yml
- RULE-09: base package `com.neuroassist.acs` — PASS

---

## 2. Estratégia Geral

### Objetivo macro

Implementar o acs-service de forma incremental em 6 prioridades, iniciando pelos fundamentos (build + contratos), depois autenticação JWT, depois CRUD admin, depois segurança e observabilidade.

### Ordem das prioridades

| P | Título | Motivo | Issues |
|---|---|---|---|
| P1 | Foundation | Build passa + contratos (domain, ports, entities, DTOs) | ACS-001, ACS-002, ACS-003, ACS-004 |
| P2 | Auth + Core | JWT emitter, autenticação, provisioning, JWK, exception handler, MDC | ACS-005..007, ACS-012, ACS-013, ACS-019..021 |
| P3 | Admin + Links | CRUD cuidadores + links com invariante de cobertura | ACS-008..011, ACS-014..017 |
| P4 | Config | SecurityConfig OAuth2 dual-mode + TenantContextFilter multi-tenant + DirectoryService | ACS-018, ACS-022, ACS-023 |
| P5 | BDD | 40 Cucumber scenarios implementados | ACS-024 |
| P6 | Observabilidade | Prometheus metrics + distributed traces | ACS-025 |
| P7 | Consolidação Final | Signing key persistida + refresh token real + OTel + RULE-07 TenantService | ACS-026 |

---

## 3. Plano por Prioridade

### Prioridade 1 -- Foundation

Status: DONE ✅
Issues cobertas: ACS-001, ACS-002, ACS-003, ACS-004

#### Objetivo

Garantir que `./gradlew :services:acs-service:compileJava architectureCheck` passa sem erros e que os contratos da camada de domínio (models, enums, ports, entities, DTOs) estão completos e compiláveis.

#### Problemas identificados

1. `ApplicationContextLoadsIT.java:11` — `@Autowired private ApplicationContext context;` (campo) — substituir por injeção via construtor
2. `Cuidador.java` — campos incompletos (falta nome, login, papel, status, credentialHash)
3. `CuidadorJpaEntity.java` — campos incompletos (falta nome, login, credentialHash, papel)
4. Ports em `application/port/out/` com corpo vazio: `MigProvisioningPort`, `HistAuditPort`, `PacPatientResolvePort`, `SigningKeyPort`
5. DTOs em `api/dto/` são stubs mínimos sem os campos corretos para os contratos de API

#### Correções mandatórias

1. **Substituir `@Autowired` campo → construtor** em `ApplicationContextLoadsIT`:
   ```java
   // REMOVER:
   @Autowired private ApplicationContext context;
   // ADICIONAR no construtor:
   private final ApplicationContext context;
   ApplicationContextLoadsIT(ApplicationContext context) { this.context = context; }
   ```

2. **Completar `Cuidador` domain model** (record ou classe POJO — zero Spring/JPA):
   campos: `UUID id, UUID tenantId, String nome, String login, String credentialHash, CuidadorPapel papel, CuidadorStatus status, Instant criadoEm, Instant atualizadoEm`

3. **Completar `Tenant` domain model** (record POJO):
   campos: `UUID id, String nome, TenantTipo tipo, TenantStatus status, String schemaNome, Instant criadoEm`

4. **Completar `SigningKey` domain model** (record POJO — sem `privateKeyEnc` no modelo de domínio):
   campos: `UUID id, String kid, String algorithm, String publicJwk, SigningKeyStatus status, Instant notBefore, Instant retiredAt`

5. **Completar `MigProvisioningPort`** em `application/port/out/`:
   ```java
   interface MigProvisioningPort {
       MigSchemaResponse requestTenantSchema(UUID tenantId, String schemaName);
       MigSchemaStatusResponse getTenantSchemaStatus(UUID tenantId);
   }
   ```
   Criar inner records `MigSchemaResponse` e `MigSchemaStatusResponse` no mesmo arquivo ou em `application/port/out/dto/`.

6. **Completar `PacPatientResolvePort`** em `application/port/out/`:
   ```java
   interface PacPatientResolvePort {
       Optional<PatientResolveResponse> resolvePatient(UUID pacienteId, UUID tenantId);
   }
   ```
   Criar record `PatientResolveResponse(UUID pacienteId, UUID tenantId, String nome, String status)` no mesmo arquivo.

7. **Completar `HistAuditPort`** em `application/port/out/`:
   ```java
   interface HistAuditPort {
       void recordFact(AuditFact fact);
   }
   ```
   Criar record `AuditFact(String fact, UUID empresaId, UUID actorId, String correlationId, Instant occurredAt, Map<String,Object> payload)` no mesmo arquivo.

8. **Completar `SigningKeyPort`** em `application/port/out/`:
   ```java
   interface SigningKeyPort {
       SigningKey getActiveSigningKey();
       List<SigningKey> getPublicKeys();
   }
   ```

9. **Completar DTOs** em `api/dto/` — substituir stubs pelos records completos conforme seção ACS-002 abaixo.

10. **Completar JPA entities** em `infrastructure/persistence/entity/` — adicionar todos os campos conforme seção ACS-004 abaixo.

11. **Verificar `application.yml`** — confirmar `spring.jpa.open-in-view: false` presente.

#### Alvos de alteração (caminhos completos)

```
backend/services/acs-service/src/test/java/com/neuroassist/acs/integration/ApplicationContextLoadsIT.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/domain/model/Cuidador.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/domain/model/Tenant.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/domain/model/SigningKey.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/domain/model/CaregiverPatientLink.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/application/port/out/MigProvisioningPort.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/application/port/out/PacPatientResolvePort.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/application/port/out/HistAuditPort.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/application/port/out/SigningKeyPort.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/api/dto/AutenticacaoRequest.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/api/dto/AutenticacaoResponse.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/api/dto/CaregiverRequest.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/api/dto/CaregiverResponse.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/api/dto/CaregiverPatientLinkRequest.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/api/dto/CaregiverPatientLinkResponse.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/api/dto/TenantRequest.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/api/dto/TenantResponse.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/api/dto/DirectoryRequest.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/api/dto/DirectoryResponse.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/api/dto/JwkSetRequest.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/api/dto/JwkSetResponse.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/infrastructure/persistence/entity/CuidadorJpaEntity.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/infrastructure/persistence/entity/TenantJpaEntity.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/infrastructure/persistence/entity/SigningKeyJpaEntity.java
backend/services/acs-service/src/main/java/com/neuroassist/acs/infrastructure/persistence/entity/CaregiverPatientLinkJpaEntity.java
backend/services/acs-service/src/main/resources/application.yml
```

#### Contrato operacional

Deve existir:
- `./gradlew :services:acs-service:compileJava` com exit 0
- `./gradlew :services:acs-service:architectureCheck` com exit 0 (zero RULE violations)
- Todos os domain models são Java records ou POJOs sem Spring/JPA imports
- Todos os port interfaces têm assinaturas de métodos completas (sem TODO orphans)
- Todos os DTOs são Java records com Bean Validation annotations adequadas
- Nenhum DTO contém campo `password`, `credentialHash`, `privateKey`, ou dado biométrico
- `CuidadorJpaEntity`: getter `credentialHash` marcado com `@JsonIgnore`
- `SigningKeyJpaEntity`: campo `privateKeyEnc` marcado com `@JsonIgnore`
- `application.yml` contém `spring.jpa.open-in-view: false`

Não deve existir:
- `@Autowired` em campo em nenhuma classe (incluindo testes)
- Imports `org.springframework.*` em `domain.*`
- Imports `jakarta.persistence.*` em `domain.*`

Verificação objetiva: `./gradlew :services:acs-service:compileJava architectureCheck` passa sem warnings.

Condição de bloqueio: falha no compileJava ou architectureCheck bloqueia P2.

#### Sequência de execução

- **Fase 1 — domain:** Completar `Cuidador`, `Tenant`, `SigningKey`, `CaregiverPatientLink` (records POJO, zero Spring/JPA)
- **Fase 2 — port:** Completar `MigProvisioningPort`, `PacPatientResolvePort`, `HistAuditPort`, `SigningKeyPort` com assinaturas e DTOs de port inline
- **Fase 3 — infra/entity:** Completar `CuidadorJpaEntity`, `TenantJpaEntity`, `SigningKeyJpaEntity`, `CaregiverPatientLinkJpaEntity` com todos os campos + @JsonIgnore em credentialHash/privateKeyEnc
- **Fase 4 — api/dto:** Substituir stubs de DTO pelos records completos com Bean Validation; verificar LGPD
- **Fase 5 — fix arquitetural:** Substituir `@Autowired` campo por construtor em `ApplicationContextLoadsIT`; verificar `application.yml` (open-in-view: false)

#### Política de testes

- Unit tests NÃO são necessários em P1 (não há lógica de negócio)
- Verificação: `./gradlew :services:acs-service:compileJava architectureCheck` é o gate de P1

#### Multi-empresa

P1 é foundation pura (domain + ports + entities). O TenantContextHolder.require() NÃO é implementado ainda — isso vai em P4 (ACS-022). As entities JPA devem ter o mapeamento de schema correto: `@Table(schema="neuroassist_global")` para `TenantJpaEntity` e `SigningKeyJpaEntity`; sem schema fixo para `CuidadorJpaEntity` e `CaregiverPatientLinkJpaEntity` (schema resolvido via Hibernate multi-tenancy em runtime).

#### Migrações Flyway necessárias

NÃO em P1. Migrações estão em P3 (ACS-008). Os serviços usam `ddl-auto: validate` — o schema deve já existir via `neuroassist-ai-migrations`. Verificar se já existe o SQL correspondente antes de tentar iniciar o serviço.

#### Critérios de aceite

- [ ] `./gradlew :services:acs-service:compileJava` passa (exit 0)
- [ ] `./gradlew :services:acs-service:architectureCheck` passa (exit 0, zero violations)
- [ ] Todos os domain models (`Cuidador`, `Tenant`, `SigningKey`, `CaregiverPatientLink`) têm campos completos sem Spring/JPA
- [ ] Todos os 4 port interfaces (`MigProvisioningPort`, `PacPatientResolvePort`, `HistAuditPort`, `SigningKeyPort`) têm assinaturas de métodos completas
- [ ] Todos os DTOs são Java records; nenhum contém `password`, `credentialHash`, `privateKey`
- [ ] `CuidadorJpaEntity.credentialHash` e `SigningKeyJpaEntity.privateKeyEnc` anotados com `@JsonIgnore`
- [ ] `ApplicationContextLoadsIT` usa injeção via construtor (sem `@Autowired` em campo)
- [ ] `application.yml` contém `spring.jpa.open-in-view: false`

#### Riscos

- Se DTOs ou entities forem alterados incompativelmente em P3, código de P2 precisará ser revisado. Mitigação: definir todos os campos em P1 para minimizar mudanças.

#### Instrução para implementador

Execute as Fases 1 a 5 em sequência. Não implemente lógica de negócio em P1 — apenas contratos. Após cada fase, compilar para validar. Gate final: `./gradlew :services:acs-service:compileJava architectureCheck` com exit 0. Não avançar para P2 sem este gate passar.

---

## 0. Visão Geral do Plano

O módulo ACS é o serviço de **identidade e autorização** centralizado da plataforma NeuroAssist AI. Funciona como OAuth2 Authorization Server + Resource Server dual-mode para:

- Autenticação (JWT RS256)
- Administração de cuidadores e pacientes
- Gerenciamento de chaves de assinatura
- Diretório de acesso (consultas de contexto para decisões de autorização)

### Contexto de Maturidade

**Estado atual:** Scaffolding 90% completo (estrutura Hexagonal, controllers, DTOs, ports, entities). Funcionalidade: **0% implementada** (services são stubs, testes ausentes).

**Objetivo deste plano:** Orquestrar implementação incremental de P1 → P6, com gates de conformidade e validação após cada priority.

### Agrupamento de Prioridades

```
TIER A (Foundation)     — P1 (4 issues) — bootstrap + contratos
TIER B (Authentication) — P2 (8 issues) — token issuer + auth flow + core infra
TIER C (Admin)          — P3 (8 issues) — CRUD cuidadores + links + safety
TIER D (Config + Tests) — P4-P6 (5 issues) — segurança + observabilidade + BDD
```

---

## 1. TIER A — Foundation (P1)

**Objetivo:** Validar e completar base estrutural; garantir compilação + ArchUnit pass.

**Issues:** ACS-001, ACS-002, ACS-003, ACS-004

### ACS-001 — Criar subprojeto Gradle acs-service com dependências Spring Boot 3 + Cloud + Security + OAuth2 AS

**Status:** INTACT (scaffolding complete)
**Estimativa:** M | **Complexity:** Medium | **Dependency:** None (bootstrap)

**Checklist:**

- [ ] `./gradlew :services:acs-service:compileJava` passa sem erros (zero compiler warnings)
- [ ] `./gradlew :services:acs-service:test` passa (unit test stubs OK)
- [ ] `./gradlew :services:acs-service:architectureCheck` passa (RULE-01..09)
- [ ] `./gradlew :services:acs-service:build` passa (full build pipeline)
- [ ] Dependências declaradas em `build.gradle.kts`:
  - spring-boot-starter-web, spring-boot-starter-security
  - spring-boot-starter-oauth2-authorization-server, spring-boot-starter-oauth2-resource-server
  - spring-boot-starter-data-jpa, spring-cloud-starter-netflix-eureka-client
  - spring-cloud-starter-openfeign, spring-cloud-starter-circuitbreaker-resilience4j
  - postgresql, spring-boot-starter-actuator, micrometer-registry-prometheus
- [ ] `application.yml` contém config básica: server.port=8088, spring.application.name=acs-service, Eureka client, logging MDC
- [ ] `AcsApplication.java` executa sem erros; `GET /actuator/health` retorna 200 UP

**Gate:** ArchUnit pass (RULE-01..09)

**Próximo:** ACS-002 (DTOs)

---

### ACS-002 — Criar DTOs das três superfícies de API (auth, admin, diretório)

**Status:** INTACT (scaffolding 50%)
**Estimativa:** M | **Complexity:** Medium | **Dependency:** ACS-001

**Checklist:**

- [ ] DTOs de **auth surface** (`/oauth2/token`):
  - `LoginRequest(grantType, login, password)` com Bean Validation
  - `TokenResponse(accessToken, tokenType, expiresIn, tenant, sub, papel, authorities)` — sem campo password
  
- [ ] DTOs de **tenant admin** (`/api/v1/acs/tenants`):
  - `CreateTenantRequest(nome, tipo)` com validação
  - `TenantResponse(id, nome, tipo, status, schemaNome, criadoEm)` — sem data de atualização interna
  - `ProvisioningStatusResponse(tenantId, status, percentual, estimatedSeconds)`

- [ ] DTOs de **caregiver admin** (`/api/v1/acs/caregivers`):
  - `CreateCaregiverRequest(nome, login, papel, senha)` — senha only in request
  - `CaregiverResponse(id, tenant, nome, login, papel, status, criadoEm)` — **sem password ou credentialHash**
  - `PagedCaregiversResponse(items[], page, size, total)` com paginação
  - `ListCaregiversRequest(page, size, papel, status)` para queries

- [ ] DTOs de **link admin** (`/api/v1/acs/caregivers/{id}/patients/{pacId}/link`):
  - `CreateLinkRequest(pacienteId)` com validação
  - `CaregiverPatientLinkResponse(id, tenant, cuidadorId, pacienteId, status, criadoEm, revogadoEm)`

- [ ] DTOs de **directory** (`/api/v1/acs/directory/*`):
  - `TenantValidityResponse(tenantId, status, valid, tipo)`
  - `RoutingTargetsResponse(pacienteId, tenant, responsibleCaregivers[{cuidadorId, papel, authorities}])`
  - `CaregiverRoleResponse(cuidadorId, tenant, papel, status, authorities[])`
  - `PatientScopeResponse(cuidadorId, papel, scope, pacienteIds[])`

- [ ] `ProblemDetailResponse(type, title, status, detail, instance, correlationId, errors[])` — RFC 7807
  - Campo `correlationId` obrigatório e não-nulo
  - Array `errors[]` para validação de campos

- [ ] **Validação LGPD:** Nenhum DTO contém campo `password`, `credentialHash`, `privateKey`, ou dado biométrico/clínico
  - ArchUnit rule: `api.dto.*.java` — grep nenhuma menção a "password", "credentialHash"
  - Code review: verificar getters/setters de `CaregiverResponse` — sem expor hash

**Gate:** Todos os DTOs compilam; Bean Validation não bloqueia; LGPD check pass

**Próximo:** ACS-003 (Ports)

---

### ACS-003 — Criar port interfaces (MigProvisioningPort, PacPatientResolvePort, HistAuditPort, SigningKeyPort)

**Status:** INTACT (scaffolding 30%)
**Estimativa:** S | **Complexity:** Low | **Dependency:** ACS-001

**Checklist:**

- [ ] `MigProvisioningPort` em `application/port/out/`:
  ```java
  interface MigProvisioningPort {
    MigSchemaResponse requestTenantSchema(tenantId: UUID, schemaName: String);
    MigSchemaStatusResponse getTenantSchemaStatus(tenantId: UUID);
  }
  ```
  - Timeout: 5s (fail-closed)
  - Response DTOs: `MigSchemaResponse`, `MigSchemaStatusResponse`

- [ ] `PacPatientResolvePort` em `application/port/out/`:
  ```java
  interface PacPatientResolvePort {
    Optional<PatientResolveResponse> resolvePatient(pacienteId: UUID, tenantId: UUID);
  }
  ```
  - Timeout: 2s (fail-closed)
  - Response DTO: `PatientResolveResponse(pacienteId, tenantId, nome, status)`

- [ ] `HistAuditPort` em `application/port/out/`:
  ```java
  interface HistAuditPort {
    void recordFact(AuditFact fact);  // fire-and-forget
  }
  ```
  - Timeout: 1s (best-effort, nunca bloqueia transação principal)
  - Value object: `AuditFact(fact, empresaId, actorId, correlationId, occurredAt, payload)`

- [ ] `SigningKeyPort` em `application/port/out/`:
  ```java
  interface SigningKeyPort {
    SigningKey getActiveSigningKey();
    List<SigningKey> getPublicKeys();  // active + recently retired
  }
  ```
  - Modelo: `SigningKey(id, kid, algorithm, publicJwk, status, notBefore, retiredAt)`
  - Sem exposição de `privateKeyEnc`

- [ ] **Validação:** Nenhuma port interface importa `org.springframework.*`, `javax.persistence.*`, ou Feign
  - ArchUnit rule check: `application.port.*.java` — zero Spring/JPA imports
  - Code review: verificar que ports são POJO interfaces

**Gate:** Compilação pass; RULE-04 (no infrastructure dependency) pass

**Próximo:** ACS-004 (Entities)

---

### ACS-004 — Criar entidades JPA para ambos os schemas (global: Tenant, SigningKey; empresa: Cuidador, Link)

**Status:** INTACT (scaffolding 40%)
**Estimativa:** M | **Complexity:** Medium | **Dependency:** ACS-001, ACS-003

**Checklist:**

- [ ] **GlobalSchema Entities** em `infrastructure/persistence/entity/global/`:
  
  - `TenantEntity`:
    - Campos: `id (UUID)`, `nome`, `tipo (enum)`, `status (enum)`, `schemaNome`, audit cols
    - Anotações: `@Table(schema = "neuroassist_global", name = "tenant")`
    - Enums: `TenantTipo(CLINICA, PAC_HOME, TELEHEALTH)`, `TenantStatus(PROVISIONING, ACTIVE, SUSPENDED)`
  
  - `SigningKeyEntity`:
    - Campos: `id (UUID)`, `kid`, `algorithm (RS256)`, `publicJwk (String/JSONB)`, `privateKeyEnc (byte[])`, `status (enum)`, `notBefore`, `retiredAt`, audit cols
    - Anotações: `@Table(schema = "neuroassist_global", name = "signing_key")`
    - **Getter `privateKeyEnc`** marcado `@JsonIgnore` — nunca serializar
    - Enums: `SigningKeyStatus(ACTIVE, RETIRED, ROTATED)`

- [ ] **EmpresaSchema Entities** em `infrastructure/persistence/entity/empresa/`:
  
  - `CuidadorEntity`:
    - Campos: `id (UUID)`, `tenantId (UUID — sem @ManyToOne)`, `nome`, `login`, `credentialHash (String)`, `papel (enum)`, `status (enum)`, audit cols
    - Anotações: `@Table(name = "cuidador")`
    - **Getter `credentialHash`** retorna `null` fora de auth context (ou `@JsonIgnore`)
    - Enums: `CuidadorPapel(COORDENADOR_CLINICA, CUIDADORA, PACIENTE_APOIO)`, `CuidadorStatus(ACTIVE, INACTIVE)`
    - Índice case-insensitive: `login` lower + partial index em `status='active'` (DDL em migrations)
  
  - `CaregiverPatientLinkEntity`:
    - Campos: `id (UUID)`, `tenantId (UUID — sem FK)`, `cuidadorId (UUID, @ManyToOne → CuidadorEntity)`, `pacienteId (UUID — lógico, sem FK)`, `status (enum)`, `revogadoEm`, audit cols
    - Anotações: `@Table(name = "caregiver_patient_link")`
    - Enums: `LinkStatus(ACTIVE, REVOKED, SUSPENDED)`
    - **Invariante:** Sem UPDATE/DELETE físico; apenas status transitions

- [ ] **Audit Columns** (PS.002):
  - `criadoEm (LocalDateTime, @Column(updatable=false))`
  - `criadoPorUsuarioId (UUID, @Column(updatable=false))`
  - `atualizadoEm (LocalDateTime)`
  - `atualizadoPorUsuarioId (UUID)`

- [ ] **Validação:**
  - `./gradlew :services:acs-service:compileJava` passa
  - Nenhuma entidade usa `DELETE` físico (invariante #7)
  - Cross-schema FK (`@ManyToOne` global → empresa) explicitamente bloqueada (PS.003)

**Gate:** Compilação pass; JPA entity mapping valid; cross-schema rules enforced

**Próximo:** TIER B (P2 issues — TokenIssuer, AutenticacaoService)

---

## 2. TIER B — Authentication + Core Flows (P2)

**Objetivo:** Implementar autenticação (JWT RS256), token refresh, e provisioning de tenant. Validar OAuth2 AS+RS dual-mode.

**Issues:** ACS-005, ACS-006, ACS-007, ACS-012, ACS-013, ACS-019, ACS-020, ACS-021

### ACS-005 — Implementar TokenIssuer (JWT RS256, claims assembly, role → authorities)

**Status:** INTACT
**Estimativa:** M | **Complexity:** Medium | **Dependency:** ACS-001, ACS-003, ACS-004

**Checklist:**

- [ ] Classe `TokenIssuer` em `application/service/`:
  ```java
  @Service
  class TokenIssuer {
    public TokenResponse issue(Cuidador cuidador) { ... }
  }
  ```
  - Método: `issue(cuidador: Cuidador): TokenResponse`
  - Carrega signing key ativa via `SigningKeyPort.getActiveSigningKey()`
  - Monta claims: `sub=cuidadorId`, `tenant=tenantId`, `papel`, `authorities=roleToAuthorities(papel)`, `exp=now+900s`, `iss=acs-service`, `kid=signingKey.kid`
  - Assina JWT com RS256 (Nimbus JOSE+JWT ou Spring Authorization Server `JwtEncoder`)
  - Retorna `TokenResponse(accessToken, tokenType="Bearer", expiresIn=900, tenant, sub, papel, authorities)`

- [ ] Classe `RoleAuthorityPolicy` em `application/`:
  ```java
  @Component
  class RoleAuthorityPolicy {
    public List<String> toAuthorities(papel: CuidadorPapel): List<String> { ... }
  }
  ```
  - Mapeamento total de papel → authorities:
    - `COORDENADOR_CLINICA` → `[acs.tenant.read, acs.tenant.create, acs.cuidador.read, acs.cuidador.create, acs.cuidador.deactivate, acs.link.read, acs.link.create, acs.link.revoke, acs.directory.read, evnt.read, hist.read, dash.read]` (12 authorities)
    - `CUIDADORA` → `[acs.cuidador.read, acs.directory.read, evnt.read, evnt.confirm, dash.read]` (5 authorities)
    - `PACIENTE_APOIO` → `[acs.directory.read, dash.read]` (2 authorities)
  - Lança `IllegalStateException` se papel desconhecido (nunca silencia)
  - Testado unitariamente: 100% dos papéis

- [ ] **Validação:**
  - JWT produzido tem `kid` correspondente à chave ativa no `SigningKeyPort`
  - `authorities` de `cuidadora` contém exatamente `[acs.cuidador.read, acs.directory.read, evnt.read, evnt.confirm, dash.read]`
  - `authorities` de `coordenador_clinica` contém exatamente 12 authorities
  - Token não expõe `private_key_enc` em exceções
  - Teste unitário: `RoleAuthorityPolicyTest` — 100% cobertura dos 3 papéis

**Gate:** Unit tests pass (RoleAuthorityPolicyTest); JWT signature valid; no credential leakage

**Próximo:** ACS-006 (AutenticacaoController)

---

### ACS-006 — Implementar AutenticacaoController + AutenticacaoService (POST /oauth2/token)

**Status:** INTACT
**Estimativa:** M | **Complexity:** Medium | **Dependency:** ACS-005

**Checklist:**

- [ ] Classe `AutenticacaoService` em `application/service/`:
  ```java
  @Service
  @Transactional
  class AutenticacaoService {
    public TokenResponse authenticate(LoginRequest req) { ... }
  }
  ```
  - Valida grant type (apenas `password` para bootstrap; `refresh_token` → ACS-007)
  - Lookup `Cuidador` por login via `CuidadorRepositoryPort.findByLogin(tenantId, login)`
  - Valida senha contra `credentialHash` (BCrypt)
  - Carrega `Tenant` para validar status (não SUSPENDED)
  - Chama `TokenIssuer.issue(cuidador)` para gerar JWT
  - Chama `HistAuditPort.recordFact(...)` async (fire-and-forget): auditoria de login
  - Retorna `TokenResponse` | lança exceções: `InvalidCredentialsException` (401), `TenantNotFoundException` (404), `TenantSuspendedException` (409)

- [ ] Classe `AutenticacaoController` em `api/controller/`:
  ```java
  @RestController
  @RequestMapping("/oauth2")
  class AutenticacaoController {
    @PostMapping("/token")
    public ResponseEntity<TokenResponse> token(@RequestBody LoginRequest req) { ... }
  }
  ```
  - Endpoint: `POST /oauth2/token`
  - Request: `LoginRequest(grantType, login, password)` com `@Valid`
  - Response: `200 TokenResponse` | `401 invalid-credentials` | `404 tenant-not-found` | `409 tenant-suspended`
  - Sem `@PreAuthorize` (público)

- [ ] **Password Hashing:**
  - Usar BCrypt com strength 10+ configurável
  - Criar `PasswordEncoderConfig` em `infrastructure/config/`
  - Teste: `AutenticacaoServiceTest` — valid password, invalid password, tenant not found, tenant suspended

- [ ] **Validação:**
  - POST /oauth2/token com credenciais válidas → 200 TokenResponse
  - POST /oauth2/token com credenciais inválidas → 401 (sem discriminar se login não existe vs. password errada)
  - POST /oauth2/token com tenant não-existent → 404
  - POST /oauth2/token com tenant suspended → 409
  - Auditoria registrada via HistAuditPort (fire-and-forget)

**Gate:** AutenticacaoServiceTest + AutenticacaoControllerTest pass; no credential leakage in errors

**Próximo:** ACS-007 (RefreshTokenService) — paralelo com ACS-012 (TenantProvisioning)

---

### ACS-007 — Implementar RefreshTokenService (token refresh + rotation)

**Status:** INTACT
**Estimativa:** M | **Complexity:** Medium | **Dependency:** ACS-005, ACS-006

**Checklist:**

- [ ] Estender `AutenticacaoService` ou criar `RefreshTokenService` em `application/service/`:
  ```java
  @Service
  @Transactional
  class RefreshTokenService {
    public TokenResponse refreshToken(String refreshToken) { ... }
  }
  ```
  - Valida refresh token (signature, expiry — TTL 7 dias)
  - Carrega `Cuidador` de `sub` claim
  - Gera novo access token via `TokenIssuer.issue(cuidador)`
  - Opcionalmente emite novo refresh token (token rotation)
  - Retorna `TokenResponse(newAccessToken, refreshToken, ...)`

- [ ] Estender `AutenticacaoController`:
  ```java
  @PostMapping("/token/refresh")
  public ResponseEntity<TokenResponse> refresh(@RequestBody RefreshTokenRequest req) { ... }
  ```
  - Request: `RefreshTokenRequest(refreshToken)`
  - Response: `200 TokenResponse` | `401 invalid-refresh-token` | `404 caregiver-not-found`

- [ ] **Validação:**
  - Refresh token válido → novo access token + novo refresh token (rotation)
  - Refresh token expirado → 401
  - Refresh token inválido → 401 (sem discriminar causa)
  - Teste: `RefreshTokenServiceTest` — valid, expired, invalid, caregiver not found

**Gate:** RefreshTokenServiceTest pass; token rotation tested; no credential leakage

**Próximo:** ACS-012 (TenantProvisioning — SAGA)

---

### ACS-012 — Implementar TenantProvisioningService (saga MIG fail-closed, retry, idempotency)

**Status:** INTACT
**Estimativa:** M | **Complexity:** Medium-High | **Dependency:** ACS-001, ACS-003, ACS-004, ACS-009, ACS-011

**Checklist:**

- [ ] Classe `TenantProvisioningService` em `application/service/`:
  ```java
  @Service
  @Transactional
  class TenantProvisioningService {
    public void provisionTenant(TenantEntity tenant) { ... }
  }
  ```
  - Algoritmo:
    1. Gera `schemaName = "t_" + UUID.randomUUID()`
    2. Persiste `TenantEntity(status=PROVISIONING, schemaNome=null)`
    3. Chama `MigProvisioningPort.requestTenantSchema(tenantId, schemaName)` (timeout 5s, fail-closed)
    4. Polling `MigProvisioningPort.getTenantSchemaStatus(tenantId)` até `status=READY` (max 30s total, exponential backoff 1s→5s)
    5. Atualiza `TenantEntity(status=ACTIVE, schemaNome=t_xxx)` (transação separada)
    6. Emite `TenantCreatedEvent` (Artemis JMS)

- [ ] **Resiliência (Resilience4j):**
  - Retry: `maxAttempts=3`, `waitDuration=1s`, exponential backoff
  - Timeout: 5s via `TimeLimiter`
  - Circuit breaker: `failureThreshold=3`, `successThreshold=2`, half-open state 30s
  - Bulkhead: `maxConcurrentCalls=10` (proteção contra thundering herd)

- [ ] **Idempotência:**
  - `MigProvisioningPort.requestTenantSchema()` é idempotente: mesma (tenantId, schemaName) retorna mesmo resultado
  - Se provisioning já iniciado (status=PROVISIONING), resumir polling (não re-enviar request)
  - Tratamento de race condition: se outro agente atualizar status para ACTIVE durante polling, completar silenciosamente

- [ ] **Tratamento de Erros:**
  - MIG indisponível (timeout/5xx) → `UpstreamUnavailableException` (502) | retry + circuitbreaker
  - Polling timeout (30s total) → `TenantProvisioningTimeoutException` (504) → manter status PROVISIONING para retry manual
  - Schema já existe (MIG 409) → idempotent; continuar polling

- [ ] **Validação:**
  - Teste: `TenantProvisioningServiceTest` com Testcontainers PostgreSQL
  - Mock `MigProvisioningPort` para simular success, timeout, retry scenarios
  - Verifica transação: TenantEntity persisted com status progression
  - Verifica evento: `TenantCreatedEvent` emitido após ACTIVE

**Gate:** TenantProvisioningServiceTest pass; retry logic + timeout tested; Resilience4j config valid

**Próximo:** ACS-013 (TenantService CRUD)

---

### ACS-013 — Implementar TenantService (CRUD tenants, status transitions)

**Status:** INTACT
**Estimativa:** M | **Complexity:** Medium | **Dependency:** ACS-001, ACS-012

**Checklist:**

- [ ] Classe `TenantService` em `application/service/`:
  ```java
  @Service
  @Transactional
  class TenantService {
    public TenantResponse createTenant(CreateTenantRequest req) { ... }
    public TenantResponse getTenant(UUID tenantId) { ... }
    public TenantResponse updateTenantStatus(UUID tenantId, TenantStatus newStatus) { ... }
    public void deleteTenant(UUID tenantId) { ... }  // soft-delete: status=SUSPENDED
  }
  ```
  - `createTenant()` → `TenantProvisioningService.provisionTenant()`
  - `getTenant()` → lookup via `TenantRepositoryPort`
  - `updateTenantStatus()` → ACTIVE ↔ SUSPENDED (transições válidas)
  - `deleteTenant()` → muda para SUSPENDED (nunca delete físico)

- [ ] Classe `TenantController` em `api/controller/`:
  ```java
  @RestController
  @RequestMapping("/api/v1/acs/tenants")
  class TenantController {
    @PostMapping
    @PreAuthorize("hasAuthority('acs.tenant.create')")
    public ResponseEntity<TenantResponse> create(@RequestBody CreateTenantRequest req) { ... }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('acs.tenant.read')")
    public ResponseEntity<TenantResponse> get(@PathVariable UUID id) { ... }
    
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('acs.tenant.update')")
    public ResponseEntity<TenantResponse> updateStatus(...) { ... }
  }
  ```

- [ ] **Validação:**
  - Teste: `TenantServiceTest` + `TenantControllerTest`
  - POST /api/v1/acs/tenants → 201 + TenantProvisioningService call
  - GET /api/v1/acs/tenants/{id} → 200 TenantResponse
  - PATCH /api/v1/acs/tenants/{id}/status → 200 + event emitted

**Gate:** TenantServiceTest + TenantControllerTest pass; event emission verified

**Próximo:** ACS-019 (JwkSetService — public keys endpoint)

---

### ACS-019 — Implementar JwkSetService (GET /.well-known/jwks.json para validação de tokens)

**Status:** INTACT
**Estimativa:** S | **Complexity:** Low | **Dependency:** ACS-001, ACS-003, ACS-004

**Checklist:**

- [ ] Classe `JwkSetService` em `application/service/`:
  ```java
  @Service
  class JwkSetService {
    public JwkSetResponse getJwkSet() { ... }
  }
  ```
  - Carrega chaves via `SigningKeyPort.getPublicKeys()`
  - Converte para JWK format (RFC 7517): `{kty, use, alg, kid, n, e, ...}`
  - Retorna `JwkSetResponse(keys[])`

- [ ] Classe `JwkSetController` em `api/controller/`:
  ```java
  @RestController
  class JwkSetController {
    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<JwkSetResponse> getJwkSet() { ... }
  }
  ```
  - Endpoint público (sem `@PreAuthorize`)
  - Response: `200 JwkSetResponse` com cache headers (`Cache-Control: public, max-age=3600`)
  - Nunca expõe chaves privadas

- [ ] **Validação:**
  - Teste: `JwkSetServiceTest` + `JwkSetControllerTest`
  - GET /.well-known/jwks.json → 200 com keys[] válido
  - Verifica: `kid` presente, `n` e `e` válidos, sem `d` (private exponent)

**Gate:** JwkSetServiceTest pass; no private key exposure

**Próximo:** ACS-020 (GlobalExceptionHandler) — paralelo com ACS-021 (MDC)

---

### ACS-020 — Implementar GlobalExceptionHandler com ProblemDetail (RFC 7807)

**Status:** INTACT (skeleton 30%)
**Estimativa:** S | **Complexity:** Low | **Dependency:** ACS-001

**Checklist:**

- [ ] Classe `GlobalExceptionHandler` em `api/exception/`:
  ```java
  @RestControllerAdvice
  class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(...) { ... }
    // ... mais handlers
  }
  ```
  - Mapear todas as exceções de domínio + infraestrutura:
    - `ValidationException` → `400 bad-request`
    - `InvalidCredentialsException` → `401 unauthorized`
    - `UnauthenticatedException` → `401 unauthenticated`
    - `TenantMismatchException` → `403 forbidden`
    - `InsufficientAuthorityException` → `403 insufficient-authority`
    - `TenantNotFoundException` → `404 tenant-not-found`
    - `CaregiverNotFoundException` → `404 caregiver-not-found`
    - `LoginDuplicateException` → `409 login-duplicate`
    - `TenantNotActiveException` → `409 tenant-not-active`
    - `TenantSuspendedException` → `409 tenant-suspended`
    - `LastCaregiverLinkException` → `409 last-caregiver`
    - `UpstreamUnavailableException` → `502 service-unavailable`
  - Injetar `correlationId` do MDC em cada `ProblemDetail` (campo `instance` ou `correlationId`)
  - Validação: nenhum handler inclui `password`, `credentialHash`, `privateKey`, ou dado clínico

- [ ] DTO `ProblemDetailResponse` (se não herdado de Spring):
  ```java
  record ProblemDetailResponse(
    String type,           // ex: "urn:acs:error:login-duplicate"
    String title,          // ex: "Login Already Exists"
    int status,            // ex: 409
    String detail,         // ex: "The login 'jose' is already active in this tenant"
    String instance,       // ex: "/api/v1/acs/caregivers"
    String correlationId,  // ex: "abc-123-def-456"
    List<Map<String, Object>> errors  // [{ field: "login", message: "..." }]
  ) { }
  ```

- [ ] **Validação:**
  - Teste: `GlobalExceptionHandlerTest` com mock de exceções
  - Verifica: `401 invalid-credentials` sem discriminar login vs. password
  - Verifica: `409 login-duplicate` mapeado de `DataIntegrityViolationException(constraint=ux_cuidador_login_active)`
  - Verifica: `correlationId` não-nulo em todas as respostas

**Gate:** GlobalExceptionHandlerTest pass; no credential leakage; RFC 7807 format valid

**Próximo:** ACS-021 (MDC Filter)

---

### ACS-021 — Implementar MDC filter + correlation ID propagation (observabilidade)

**Status:** INTACT
**Estimativa:** S | **Complexity:** Low | **Dependency:** ACS-001

**Checklist:**

- [ ] Classe `CorrelationIdFilter` em `infrastructure/security/`:
  ```java
  @Component
  class CorrelationIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
      String correlationId = req.getHeader("X-Correlation-Id") ?? UUID.randomUUID();
      MDC.put("correlationId", correlationId);
      MDC.put("tenantId", EmpresaContextHolder.getTenantId() ?? "N/A");
      try {
        res.addHeader("X-Correlation-Id", correlationId);
        chain.doFilter(req, res);
      } finally {
        MDC.clear();
      }
    }
  }
  ```

- [ ] Classe `TenantIdMdcFilter` (se separado):
  ```java
  @Component
  class TenantIdMdcFilter extends OncePerRequestFilter {
    // Extrai tenantId do JWT e popula MDC
  }
  ```

- [ ] Config em `application.yml`:
  ```yaml
  logging:
    pattern:
      console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - correlationId=%X{correlationId} tenantId=%X{tenantId} - %msg%n"
  ```

- [ ] **Validação:**
  - Teste: `CorrelationIdFilterTest` com MockHttpServletRequest
  - Verifica: `X-Correlation-Id` header presente em response
  - Verifica: MDC populated com correlationId + tenantId
  - Verifica: Logs contêm correlationId

**Gate:** CorrelationIdFilterTest pass; MDC filters registered

**Próximo:** TIER C (P3 issues — Admin CRUD, Links)

---

## 3. TIER C — Admin + Links (P3)

**Objetivo:** Implementar CRUD de cuidadores, gerenciamento de links, com invariante crítico de cobertura.

**Issues:** ACS-008, ACS-009, ACS-010, ACS-011, ACS-014, ACS-015, ACS-016, ACS-017

(Similar em estrutura a TIER B; cada issue segue o padrão: Domain Service → Application Service → Controller → Tests + Gate)

### Roadmap Resumido de P3

1. **ACS-008** — Executar migrations (DDL para `t_<tenantId>` schemas)
2. **ACS-009** — TenantRepositoryPort + Adapter (neuroassist_global)
3. **ACS-010** — CuidadorJpaAdapter (t_<tenantId> schema, case-insensitive login)
4. **ACS-011** — SigningKeyRepositoryPort + Adapter (neuroassist_global)
5. **ACS-014** — CaregiverService (CRUD + deactivate with coverage check)
6. **ACS-015** — CaregiverController (endpoints + @PreAuthorize + filtering)
7. **ACS-016** — LinkService (create link, revoke with coverage invariant — **SAFETY-CRITICAL**)
8. **ACS-017** — CaregiverPatientLinkController (endpoints for link management)

**Gate:** 8/8 issues done + TenantServiceTest, CaregiverServiceTest, LinkServiceTest, CaregiverControllerTest, LinkControllerTest all pass

**Próximo:** TIER D (P4-P6 issues — Config, Tests)

---

## 4. TIER D — Config + Tests + Observability (P4-P6)

**Issues:** ACS-018, ACS-022 (P4), ACS-023, ACS-024 (P5), ACS-025 (P6)

### Roadmap Resumido

1. **ACS-018** — SecurityConfig (OAuth2 dual-mode: AS + RS; @PreAuthorize policies per endpoint)
2. **ACS-022** — TenantContextFilter + schema routing (multi-tenancy enforcement)
3. **ACS-023** — DirectoryService (queries de contexto: tenant validity, responsible caregivers, role info, patient scope)
4. **ACS-024** — BDD/Cucumber step definitions (40 scenarios, test app context, Testcontainers)
5. **ACS-025** — Observabilidade (Prometheus metrics, distributed traces, alerting)

**Gate:** 5/5 issues done + all 40 Cucumber scenarios pass + metrics registered

**Final Gate:** ArchUnit pass; all 25 issues DONE; score ≥ 80/100 (Maduro)

---

## 5. Dependências e Caminho Crítico

```
ACS-001 (bootstrap)
  ├─ ACS-002 (DTOs)
  ├─ ACS-003 (Ports)
  │  ├─ ACS-005 (TokenIssuer) ← ACS-004
  │  │  ├─ ACS-006 (AutenticacaoController)
  │  │  ├─ ACS-007 (RefreshToken)
  │  │  └─ ACS-012 (TenantProvisioningService) ← ACS-009, ACS-011
  │  │      ├─ ACS-013 (TenantService)
  │  │      └─ ACS-019 (JwkSetService)
  │  └─ ACS-004 (Entities)
  │      ├─ ACS-009 (TenantRepositoryPort)
  │      ├─ ACS-010 (CuidadorJpaAdapter) ← ACS-008
  │      ├─ ACS-011 (SigningKeyRepositoryPort)
  │      └─ ACS-014 (CaregiverService) ← ACS-016 (coverage check)
  │          ├─ ACS-015 (CaregiverController)
  │          └─ ACS-016 (LinkService) ← ACS-010, ACS-011
  │              └─ ACS-017 (LinkController)
  ├─ ACS-020 (GlobalExceptionHandler)
  ├─ ACS-021 (MDC Filter)
  ├─ ACS-018 (SecurityConfig)
  ├─ ACS-022 (TenantContextFilter)
  ├─ ACS-023 (DirectoryService)
  └─ ACS-024 (BDD Tests)
      └─ ACS-025 (Observability)
```

**Caminho crítico:** ACS-001 → ACS-002, ACS-003, ACS-004 → ACS-005 → ACS-006, ACS-007, ACS-012 → ACS-013 → ACS-024 (BDD)

**Tempo estimado (sem paralelismo):** ~20 sprints (M=2, S=1, L=3 semanas cada)

**Com paralelismo:** ~8-10 sprints (P1 sequencial, P2 parcialmente paralelo, P3-P6 paralelo)

---

## 6. Gates de Validação

Após cada priority (P1, P2, P3, ...), executar:

### Gate A — Compilação + ArchUnit

```bash
./gradlew :services:acs-service:clean compileJava architectureCheck
```

**Critério de sucesso:** Exit code 0; zero RULE violations

### Gate B — Testes Unitários

```bash
./gradlew :services:acs-service:test
```

**Critério de sucesso:** 100% dos testes pass; coverage mínimo 70% para classes de aplicação

### Gate C — Testes de Integração (P3+)

```bash
./gradlew :services:acs-service:integrationTest
```

**Critério de sucesso:** Testcontainers PostgreSQL; schema multi-tenant tested; todas as operações CRUD validated

### Gate D — Conformidade (P2+)

```bash
python scripts/arch-conformance.py check --layer be --target backend/services/acs-service --report
```

**Critério de sucesso:** Zero `@Autowired` field violations; zero credential/private key leakage; RULE-07 EmpresaContextHolder.require() in all empresa-accessing services

### Gate E — BDD (P5 — ACS-024)

```bash
./gradlew :services:acs-service:integrationTest --tests "*.features" -D cucumber.options="--tags @smoke"
```

**Critério de sucesso:** 40/40 scenarios pass; 100% step definitions implemented

---

## 7. Métricas de Progresso

| Milestone | Issues DONE | Score | Gate Status |
|---|---|---|---|
| P1 Complete | 4/25 | 30/100 (Parcial) | ArchUnit ✅ |
| P2 Complete | 12/25 | 55/100 (Parcial) | ArchUnit + Unit tests ✅ |
| P3 Complete | 20/25 | 75/100 (Consistente) | ArchUnit + Unit + Integration tests ✅ |
| P4-P6 Complete | 25/25 | 90-100/100 (Maduro) | All gates ✅ + BDD ✅ |

---

## 8. Decisões Arquiteturais Registradas

- **D-201 (OAuth2 dual-mode):** ACS é tanto Authorization Server quanto Resource Server; dual-role simplifica provisioning + autorização centralizada
- **D-202 (Schema-per-tenant):** Multi-tenancy via schema separation (não row-based); isolamento de dados em nível PostgreSQL
- **D-203 (Saga para provisioning):** MigProvisioningPort com retry + circuit-breaker; fail-closed se MIG indisponível
- **D-204 (Coverage invariant):** Último cuidador não pode ser revogado (safety-critical para alarms); enforçado em LinkService
- **D-205 (JWT RS256):** Tokens assinados com RS256; rotation de chaves suportada via SigningKey entity

---

## 9. Riscos e Mitigações

| Risco | Severidade | Mitigação |
|---|---|---|
| Saga provisioning MIG falha → tenant fica PROVISIONING indefinidamente | HIGH | Retry com timeout; polling max 30s; manual intervention via admin endpoint |
| Último cuidador revogado inadvertidamente → alarm sem destino | CRITICAL | Coverage check em LinkService; testes de invariante; alertas em HIST |
| Credential hash vaza em responses | HIGH | @JsonIgnore em CuidadorEntity.credentialHash; GlobalExceptionHandler valida; code review obrigatória |
| Cross-tenant data access via query injection | HIGH | RULE-07 enforcement: EmpresaContextHolder.require() + AbstractRoutingDataSource; teste em TenantSchemaResolverTest |
| Chave privada expõe em JWT exception | MEDIUM | @JsonIgnore em SigningKeyEntity.privateKeyEnc; TokenIssuer sem catch-all que loga key |

---

## 10. Próximos Passos Imediatos (Semana 1)

1. **Executar Gradle build end-to-end (ACS-001)**
   - Fix `@Autowired` field violation em test context
   - Validar `./gradlew :services:acs-service:build` passa
   - Registrar RULE violations (se houver)

2. **Code review das DTOs (ACS-002)**
   - Validar LGPD: nenhum DTO contém credentialHash, password, privateKey
   - Validar Bean Validation annotations

3. **Implementar TokenIssuer + RoleAuthorityPolicy (ACS-005)**
   - JWT RS256 signing com Nimbus ou Spring Authorization Server
   - Teste unitário: RoleAuthorityPolicyTest com 100% papéis

4. **Implementar AutenticacaoService (ACS-006)**
   - BCrypt password validation
   - HistAuditPort integration (fire-and-forget)
   - Teste: AutenticacaoServiceTest com mock CuidadorRepositoryPort

---

---

## 4. Dependências Externas

| Dependência | Tipo | Port | Timeout | Fail mode |
|---|---|---|---|---|
| mig-service | Feign | `MigProvisioningPort` | 5s | fail-closed (circuit breaker) |
| pac-service | Feign | `PacPatientResolvePort` | 2s | fail-closed |
| hist-service | Feign | `HistAuditPort` | 1s | best-effort (fire-and-forget) |
| JMS Artemis | ActiveMQ | producer: `TenantCreatedEvent` | N/A | durable, persistent |
| PostgreSQL | DB | `neuroassist_global` (Tenant, SigningKey) + `t_<tenantId>` (Cuidador, Link) | N/A | fail on connection |
| Eureka | Service Registry | `neuroassist-ai-service-registry:8761` | N/A | fail-fast sem registro |

Schemas DB alvo: `neuroassist_global` + `t_<tenantId>` (empresa). DDL canonical em `sql/01_schema_novo.sql`. Migrations em `backend/services/neuroassist-ai-migrations/`.

---

## 5. Restrições Arquiteturais

- RULE-01: domain.* não importa org.springframework — ArchUnit enforced
- RULE-02: domain.* não importa jakarta.persistence — ArchUnit enforced
- RULE-03: @Transactional somente em application.service — ArchUnit enforced
- RULE-04: api.* não depende de infrastructure.* — ArchUnit enforced
- RULE-05: infrastructure.* não depende de api.* — ArchUnit enforced
- RULE-06: @FeignClient somente em infrastructure.clients — ArchUnit enforced
- RULE-07: TenantContextHolder.require() em todos os application.service que acessam schema empresa — enforçado em P4
- RULE-08: spring.jpa.open-in-view=false em application.yml — verificar antes de P2
- RULE-09: base package com.neuroassist.acs — PASS (scaffolded)
- `credentialHash` e `privateKeyEnc` nunca serializar em JSON — @JsonIgnore obrigatório
- Sem DELETE físico em entidades de domínio — apenas status transitions
- Sem cross-schema FK (@ManyToOne global → empresa) — tenantId é UUID lógico

---

## 6. Referências

- `docs/architecture/backend/services/acs-service/` (SPECs, contratos, diagramas)
- `docs/architecture/shared/modelo-dados/` (modelo canônico de dados)
- `docs/architecture/backend/standards/hexagonal-patterns.md` (Ports & Adapters, ArchUnit rules)
- `docs/architecture/backend/standards/platform-patterns.md` (Feign+Resilience4j, JMS Artemis, Saga)
- `docs/architecture/backend/standards/testing-conventions.md` (Testcontainers, Cucumber)
- `docs/architecture/backend/standards/java-conventions.md` (Lombok, SLF4J, Bean Validation)
- `sql/01_schema_novo.sql` (DDL canônico — não alterar diretamente)
- ADRs relevantes: `docs/architecture/backend/adr/`

---

### Prioridade 7 -- Consolidação Final

Status: OPEN 🔄
Issues cobertas: ACS-026

#### Objetivo

Fechar os 13 gaps que impedem o score de atingir 100/100 (Maduro): persistência real da signing key RSA (5 itens), refresh token com validação JWT real (5 itens), e completude soft — RULE-07 em TenantService, Micrometer Observation e docs (3 itens).

#### Problemas identificados

1. `JwtEncoderConfig` gera RSA-2048 em memória — restart invalida todos os tokens emitidos anteriormente
2. `AutenticacaoService.refresh()` é stub — não valida assinatura, expiração nem claim `token_type` do refresh JWT
3. `TenantService.createTenant()` e `findById()` não chamam `TenantContextHolder.require()` (violação RULE-07)
4. `AutenticacaoService.authenticate()` e `TenantProvisioningService.provision()` sem Micrometer Observation span distribuído
5. Docs em `docs/architecture/backend/services/acs-service/` possivelmente desatualizados em relação à implementação real

#### Correcoes mandatórias (13 itens ordenados)

**Bloco A — Signing Key persistida (Itens 1-5)**

Item A1 — Criar `SigningKeyService.java` em `application/service/`:
- Método `getOrCreateActiveKey()`: busca chave ACTIVE via `SigningKeyRepositoryPort.findActiveKey()`; se ausente, gera RSA-2048, criptografa `privateKeyEnc` com AES-256 usando segredo `${ACS_SIGNING_KEY_SECRET}`, persiste via `SigningKeyRepositoryPort.save()`, retorna par RSA
- Método `rotateIfExpired()`: verifica se `notBefore` > 7 dias → gera nova chave ACTIVE, marca anterior como RETIRED via `SigningKeyRepositoryPort.markRetired()`
- Método `getPublicKeys()`: delega para `SigningKeyRepositoryPort.findPublicKeys()`
- Não usa `@Autowired` em campo — `@RequiredArgsConstructor`

Item A2 — Criar `SigningKeyInitializer.java` em `infrastructure/config/`:
- Implementa `ApplicationRunner`
- No `run()`: chama `SigningKeyService.getOrCreateActiveKey()`, usa par RSA retornado para registrar `JwtEncoder` e `JwtDecoder` no contexto Spring
- Alternativa: adaptar `JwtEncoderConfig` existente para receber `SigningKeyService` e expor os beans após inicialização

Item A3 — Adicionar propriedade em `application.yml`:
- `acs.signing-key.encryption-secret: ${ACS_SIGNING_KEY_SECRET}` — nunca hardcoded
- Documentar variável de ambiente necessária

Item A4 — Atualizar `SigningKeyPortAdapter` em `infrastructure/persistence/adapter/`:
- Implementar `findActiveKey()`, `findPublicKeys()`, `markRetired()`, `save(SigningKey)` usando `SigningKeyJpaRepository`
- Mapear `SigningKeyJpaEntity` ↔ `SigningKey` domain model (sem expor `privateKeyEnc` no domain model)

Item A5 — Criar `SigningKeyServiceTest.java` em `test/java/com/neuroassist/acs/application/service/`:
- Teste: `getOrCreateActiveKey()` → repository retorna empty → gera e persiste nova chave
- Teste: `getOrCreateActiveKey()` → repository retorna chave existente → reutiliza sem gerar nova
- Teste: `rotateIfExpired()` → chave expirada → gera nova + marca anterior RETIRED
- Teste: `rotateIfExpired()` → chave recente → não gera nova (noop)

**Bloco B — Refresh token real (Itens 6-10)**

Item B6 — Adicionar claim `token_type` em `TokenIssuer.java` (em `application/service/`):
- Access token emitido com `token_type: "access"` e `exp = now + 900s`
- Refresh token emitido com `token_type: "refresh"` e `exp = now + 604800s` (7 dias), `jti = UUID.randomUUID()`
- Criar método `issueRefreshToken(Cuidador cuidador)` separado ou estender `issue()` com parâmetro de tipo

Item B7 — Implementar validação real em `AutenticacaoService.refresh()`:
- Decodificar refresh JWT via `JwtDecoder.decode(refreshToken)` — lanca `JwtException` se inválido/expirado
- Verificar claim `token_type == "refresh"` — lanca `InvalidTokenTypeException` se diferente
- Verificar que JWT não está expirado (já coberto pelo `JwtDecoder`)
- Carregar `Cuidador` via `CuidadorRepositoryPort.findById(sub)` — lanca `CaregiverNotFoundException` se não encontrado

Item B8 — Implementar sliding window em `AutenticacaoService.refresh()`:
- Emitir novo access token via `TokenIssuer.issue(cuidador)` (TTL 900s)
- Emitir novo refresh token via `TokenIssuer.issueRefreshToken(cuidador)` (TTL 7 dias, novo `jti`)
- Retornar `AutenticacaoResponse(accessToken, refreshToken, expiresIn=900, tokenType="Bearer")`
- Sem armazenamento de estado (stateless sliding window)

Item B9 — Atualizar `AutenticacaoResponse` se necessário:
- Verificar se o record já contém campo `refreshToken` — adicionar se ausente
- Garantir que `refreshToken` não é serializado em logs nem em GlobalExceptionHandler

Item B10 — Criar / atualizar `AutenticacaoServiceTest.java` para cobrir:
- Refresh válido → retorna novo access + novo refresh token
- Refresh expirado → 401 `InvalidCredentialsException`
- Refresh com `token_type != "refresh"` → 400 `InvalidTokenTypeException`
- Cuidador não encontrado no `sub` → 404 `CaregiverNotFoundException`

**Bloco C — Soft completeness (Itens 11-13)**

Item C11 — Adicionar `TenantContextHolder.require()` em `TenantService.java`:
- `findById(UUID tenantId)`: chamar `TenantContextHolder.require()` como primeira linha (RULE-07)
- `createTenant()`: NÃO chamar `require()` — este é o path de bootstrap (antes de ter tenant context); documentar exceção no próprio Javadoc
- `updateTenantStatus()`, `deleteTenant()`: chamar `TenantContextHolder.require()` como primeira linha

Item C12 — Instrumentar com Micrometer Observation:
- `AutenticacaoService.authenticate()`: envolver corpo em `Observation.createNotStarted("acs.auth.authenticate", observationRegistry).observe(() -> { ... })`
- `TenantProvisioningService.provision()` (ou `provisionTenant()`): envolver em `Observation.createNotStarted("acs.tenant.provision", observationRegistry).observe(() -> { ... })`
- Injetar `ObservationRegistry` via `@RequiredArgsConstructor` (disponível em `micrometer-core`, sem dependência adicional)

Item C13 — Verificar e atualizar docs em `docs/architecture/backend/services/acs-service/`:
- Comparar `api.md` com endpoints reais implementados (controllers) — atualizar se desatualizado
- Comparar `data-model.md` com entities JPA — atualizar campos `signing_key.privateKeyEnc` + `token_type` claim
- Comparar `events.md` com JMS producers — confirmar `TenantCreatedEvent` publicado
- Atualizar `README.md` do serviço com status P7 completo e gates finais

#### Alvos de alteração (caminhos completos)

```
backend/services/acs-service/src/main/java/com/neuroassist/acs/application/service/SigningKeyService.java  [CRIAR]
backend/services/acs-service/src/main/java/com/neuroassist/acs/infrastructure/config/SigningKeyInitializer.java  [CRIAR ou adaptar JwtEncoderConfig]
backend/services/acs-service/src/main/resources/application.yml  [ATUALIZAR — acs.signing-key.encryption-secret]
backend/services/acs-service/src/main/java/com/neuroassist/acs/infrastructure/persistence/adapter/SigningKeyPortAdapter.java  [ATUALIZAR]
backend/services/acs-service/src/test/java/com/neuroassist/acs/application/service/SigningKeyServiceTest.java  [CRIAR]
backend/services/acs-service/src/main/java/com/neuroassist/acs/application/service/TokenIssuer.java  [ATUALIZAR — claim token_type + issueRefreshToken()]
backend/services/acs-service/src/main/java/com/neuroassist/acs/application/service/AutenticacaoService.java  [ATUALIZAR — refresh() + Observation]
backend/services/acs-service/src/main/java/com/neuroassist/acs/api/dto/AutenticacaoResponse.java  [ATUALIZAR — campo refreshToken se ausente]
backend/services/acs-service/src/test/java/com/neuroassist/acs/application/service/AutenticacaoServiceTest.java  [ATUALIZAR]
backend/services/acs-service/src/main/java/com/neuroassist/acs/application/service/TenantService.java  [ATUALIZAR — TenantContextHolder.require()]
backend/services/acs-service/src/main/java/com/neuroassist/acs/application/service/TenantProvisioningService.java  [ATUALIZAR — Observation]
docs/architecture/backend/services/acs-service/api.md  [VERIFICAR/ATUALIZAR]
docs/architecture/backend/services/acs-service/data-model.md  [VERIFICAR/ATUALIZAR]
docs/architecture/backend/services/acs-service/events.md  [VERIFICAR/ATUALIZAR]
```

#### Contrato operacional

Deve existir:
- `SigningKeyService.getOrCreateActiveKey()` persiste chave RSA na tabela `neuroassist_global.signing_key` ao primeiro boot
- Restart do serviço reutiliza a chave existente (tokens anteriores continuam válidos)
- `POST /oauth2/token/refresh` com refresh token expirado → 401
- `POST /oauth2/token/refresh` com `token_type != "refresh"` → 400
- `TenantService.findById()` chama `TenantContextHolder.require()` como primeira linha
- `AutenticacaoService.authenticate()` e `TenantProvisioningService.provision()` registram span Micrometer
- `./gradlew :services:acs-service:test` passa com 0 failures
- `./gradlew :services:acs-service:architectureCheck` passa com 0 violations

Não deve existir:
- `@Autowired` em campo em nenhuma classe nova
- Segredo de criptografia hardcoded em código ou yml (somente `${ACS_SIGNING_KEY_SECRET}`)
- `refreshToken` ou `privateKeyEnc` em logs ou em stacks de exception

#### Sequência de execução (ordem obrigatória)

- **Fase 1 — domain/application/service:** Item A1 (`SigningKeyService`), Item B6 (`TokenIssuer` — claim `token_type`)
- **Fase 2 — infrastructure:** Item A4 (`SigningKeyPortAdapter`), Item A2 (`SigningKeyInitializer`), Item A3 (`application.yml`)
- **Fase 3 — application/service (refresh):** Item B7, B8 (`AutenticacaoService.refresh()` real), Item B9 (`AutenticacaoResponse`)
- **Fase 4 — application/service (RULE-07 + OTel):** Item C11 (`TenantService.require()`), Item C12 (Observation em `AutenticacaoService` + `TenantProvisioningService`)
- **Fase 5 — testes:** Item A5 (`SigningKeyServiceTest`), Item B10 (`AutenticacaoServiceTest`)
- **Fase 6 — docs:** Item C13 (verificar/atualizar `api.md`, `data-model.md`, `events.md`)

#### Política de testes

- Unit tests obrigatórios: `SigningKeyServiceTest` (Item A5) + `AutenticacaoServiceTest` (Item B10)
- Mocks: `SigningKeyRepositoryPort` mockado em `SigningKeyServiceTest`; `JwtDecoder` mockado em `AutenticacaoServiceTest`
- Integration tests não são requeridos em P7 (já cobertos em P3-P5); manter 0 regressões

#### Multi-empresa

- Item C11: `TenantContextHolder.require()` somente em métodos que acessam dados de empresa (findById, updateStatus, deleteTenant) — NÃO em `createTenant()` (path de bootstrap, não há tenant context ainda)
- `SigningKeyService` acessa schema `neuroassist_global` — não requer `TenantContextHolder`

#### Migrações Flyway necessárias

NÃO em P7. A tabela `neuroassist_global.signing_key` já deve existir (criada em P3/ACS-011). Verificar antes de iniciar Item A1 que a migration correspondente existe em `backend/services/neuroassist-ai-migrations/`.

#### Critérios de aceite

- [ ] (A1) `SigningKeyService` criado em `application/service/` com `getOrCreateActiveKey()`, `rotateIfExpired()`, `getPublicKeys()`
- [ ] (A2) `SigningKeyInitializer` criado em `infrastructure/config/`; `JwtEncoder`/`JwtDecoder` beans construídos a partir da chave persistida
- [ ] (A3) `application.yml` contém `acs.signing-key.encryption-secret: ${ACS_SIGNING_KEY_SECRET}` — sem valor hardcoded
- [ ] (A4) `SigningKeyPortAdapter` implementa `findActiveKey()`, `save()`, `markRetired()`, `findPublicKeys()`
- [ ] (A5) `SigningKeyServiceTest` com 4 cenários: criar nova chave, reutilizar existente, rotacionar expirada, não rotacionar recente
- [ ] (B6) `TokenIssuer` emite claim `token_type: "access"` e `token_type: "refresh"` com TTLs distintos
- [ ] (B7) `AutenticacaoService.refresh()` decodifica JWT via `JwtDecoder`, verifica `token_type == "refresh"`
- [ ] (B8) Sliding window: emite novo access + novo refresh com `jti` único
- [ ] (B9) `AutenticacaoResponse` contém campo `refreshToken`; sem vazamento em logs
- [ ] (B10) `AutenticacaoServiceTest` cobre refresh válido, expirado, tipo errado, cuidador não encontrado
- [ ] (C11) `TenantService.findById()`, `updateTenantStatus()`, `deleteTenant()` chamam `TenantContextHolder.require()` como primeira linha
- [ ] (C12) `AutenticacaoService.authenticate()` e `TenantProvisioningService.provision()` envolvidos em `Observation`
- [ ] (C13) `api.md`, `data-model.md`, `events.md` refletem implementação real
- [ ] `./gradlew :services:acs-service:test` → 0 failures
- [ ] `./gradlew :services:acs-service:architectureCheck` → 0 violations

#### Riscos

- `SigningKeyInitializer` roda antes de `SecurityConfig` ter registrado os beans → usar `@DependsOn("securityFilterChain")` ou ordem via `@Order` para evitar circular dependency
- Método `createTenant()` NÃO deve chamar `require()` — chamar inadvertidamente quebraria o bootstrap de tenants novos
- Criptografia AES-256 do `privateKeyEnc` deve usar IV aleatório por chave para evitar ECB determinístico — escolher GCM mode

#### Instrução para implementador

Ler `docs/architecture/backend/issues/ACS-026.md` antes de iniciar. Executar itens na ordem das Fases 1 a 6. Após Fase 2, compilar para confirmar que `SigningKeyInitializer` resolve sem erros de contexto Spring. Após Fase 5, executar `./gradlew :services:acs-service:test` e confirmar 0 failures antes de avançar para Fase 6. Não usar `@Autowired` em campo em nenhuma classe nova. Encryption secret via `${ACS_SIGNING_KEY_SECRET}` em `application.yml` — nunca hardcoded.

**END PLAN DOCUMENT**
