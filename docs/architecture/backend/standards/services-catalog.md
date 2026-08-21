# Services Catalog (NeuroAssist AI Backend)

> Catálogo de referência rápida de todos os microserviços do backend:
> pacote Java canônico, project key Gradle, responsabilidade em uma linha, ports e eventos chave.

Use este arquivo quando:
- Iniciar serviço novo -- encontrar pacote canônico e project key.
- Escrever `applyTo` em CI ou referência cruzada.
- Entender qual serviço é dono de qual fronteira de domínio.
- Saber **qual é a superfície pública** e o que fica deliberadamente fora dela (Seção 8).

> **Atualização:** este catálogo é atualizado por `/be-architect-vision <MODULE>` quando
> um novo módulo é registrado. Edite manualmente apenas se você adicionar capacidades
> a um serviço existente (eventos, Feign clients).

---

## 1. Service Map

| Componente | Gradle project key | Pacote canônico | Módulo | Porta |
|---|---|---|---|---|
| `gest-service` | `:services:gest-service` | `com.neuroassist.gest` | GEST | 8084 |
| `evnt-service` | `:services:evnt-service` | `com.neuroassist.evnt` | EVNT | 8085 |
| `pac-service` | `:services:pac-service` | `com.neuroassist.pac` | PAC | 8082 |
| `acs-service` | `:services:acs-service` | `com.neuroassist.acs` | ACS | 8088 |
| `hist-service` | `:services:hist-service` | `com.neuroassist.hist` | HIST | 8089 |
| `dash-service` | `:services:dash-service` | `com.neuroassist.dash` | DASH | 8090 |
| `edge-service` | `:services:edge-service` | `com.neuroassist.edge` | EDGE | 8087 |
| `neuroassist-ai-api-gateway` | `:services:neuroassist-ai-api-gateway` | `com.neuroassist.gwy` | — | 8080 |
| `neuroassist-ai-service-registry` | `:services:neuroassist-ai-service-registry` | — | — | 8761 |
| `neuroassist-ai-migrations` | `:services:neuroassist-ai-migrations` | `com.neuroassist.migrations` | -- | — |

> Portas são as **do container**. No `docker-compose.dev.yml` alguns diferem no host:
> GWY `18080`, Eureka `18761`, EVNT `18085`, DASH `18090`, PostgreSQL `5433`.

---

## 2. Resumo de responsabilidades

### `` -- `com.neuroassist.gest` (GEST)
- Captura de webcam, extração de landmarks faciais e classificação multi-classe do comando ativo (gesto simples ou composto por múltiplos primitivos), executado localmente no dispositivo do paciente.

### `` -- `com.neuroassist.evnt` (EVNT)
- Geração do evento estruturado correspondente ao comando reconhecido, orquestração de notificação por nível de urgência (alarme full-screen para emergência; discreta para rotina) e roteamento para o responsável.

### `` -- `com.neuroassist.pac` (PAC)
- Cadastro de paciente e catálogo de comandos (gesto -> ação, rótulo livre definido pela família, sensibilidade, tempo mínimo/máximo de gesto e nível de urgência), calibráveis independentemente por comando.

### `` -- `com.neuroassist.acs` (ACS)
- Gestão de cuidadores/usuários, papéis e vínculo com paciente(s); base do multi-tenant (tenant = família ou clínica).

### `` -- `com.neuroassist.hist` (HIST)
- Registro imutável de eventos, alarmes e ações de confirmação/edição, com trilha de auditoria.

### `` -- `com.neuroassist.dash` (DASH)
- Painel web (Angular) para cuidador consultar histórico, calibrar sensibilidade e receber notificações.

### `` -- `com.neuroassist.edge` (EDGE)
- Registro, pareamento e monitoramento de heartbeat do agente local (edge) que roda a detecção de gesto.

### `neuroassist-ai-migrations` -- `com.neuroassist.migrations`
- Flyway centralizado: roda migrations em todos os schemas (`neuroassist_global` + schemas de empresas prefixados com ``).
- Roda **antes** dos outros serviços (`ddl-auto: validate` nos demais).
- Migrações por path: `db/migration/global/V*.sql` e `db/migration/empresa/V*.sql`.

---

## 3. Estrutura interna canônica

Todos os serviços (exceto `neuroassist-ai-migrations`) usam o layout hexagonal:

```
com.neuroassist.<module>/
├── api/
│   ├── controller/          # @RestController
│   ├── dto/                 # Java records (Request/Response)
│   └── exception/           # @RestControllerAdvice (ProblemDetail)
├── application/
│   ├── service/             # @Service, @Transactional, EmpresaContextHolder.require()
│   └── port/
│       ├── in/              # Use-case interfaces
│       └── out/             # Repository + external service interfaces
├── domain/
│   ├── model/               # Pure POJO -- zero Spring, zero JPA
│   └── service/             # Domain business rules
└── infrastructure/
    ├── clients/             # @FeignClient
    ├── config/              # @Configuration, DomainServiceConfig
    ├── messaging/
    │   ├── producer/        # JmsTemplate publishers
    │   └── consumer/        # @JmsListener consumers
    ├── persistence/
    │   ├── entity/          # @Entity JPA
    │   ├── repository/      # Spring Data JPA @Repository
    │   └── adapter/         # Port.out implementations
    └── security/            # Filters, EmpresaContextHolder, CorrelationIdInterceptor
```

Detalhes em `hexagonal-patterns.md`.

---

## 4. Referência base ArchUnit

Cada `ArchitectureTest.java` usa seu pacote canônico:

```java

// :
@AnalyzeClasses(packages = "com.neuroassist.gest")

// :
@AnalyzeClasses(packages = "com.neuroassist.evnt")

// :
@AnalyzeClasses(packages = "com.neuroassist.pac")

// ... etc para os outros serviços
```

---

## 5. Comandos Gradle por serviço

```bash
# Substitua <project-key> pela chave do Gradle (Seção 1)
./gradlew :services:<project-key>:architectureCheck    # ArchUnit -- BLOQUEANTE
./gradlew :services:<project-key>:test                 # unit tests (@Tag("unit"))
./gradlew :services:<project-key>:integrationTest      # integration + BDD (Testcontainers)
./gradlew :services:<project-key>:build                # full build + all checks

# Todos os serviços
./gradlew build                               # full monorepo build
./gradlew check                               # lint + test + archUnit
```

---

## 6. Event Catalog (cross-reference)

> Catálogo completo de eventos é mantido em `docs/architecture/shared/contratos/event-catalog.md`.
> A tabela abaixo é um resumo populado por `/be-architect-vision <MODULE>` conforme cada
> módulo declara seus eventos.

| Evento | Producer | Key consumers | Queue name |
|---|---|---|---|

| _(nenhum evento declarado ainda)_ | — | — | — |

---

## 7. Feign Client Registry

> Lista populada por `/be-architect-vision <MODULE>` conforme integrações inter-service são identificadas.

Levantado do código em 2026-08-21 (`grep -rl "@FeignClient" src/main`).

| Feign interface | Consumer | Provider | `@FeignClient(name = ...)` | Estado |
|---|---|---|---|---|
| `AcsDirectoryClient` | PAC | ACS | `acs-service` | ✅ ativo |
| `GestDetectabilityClient` | PAC | GEST | `gest-service` | ✅ ativo |
| `EdgeDeviceClient` | PAC | EDGE | `edge-service` | ✅ ativo |
| `HistFactClient` | PAC | HIST | `hist-service` | ✅ ativo — **identidade de serviço** |
| `DetectionProfileClient` | EDGE | GEST | `gest-service` | ✅ ativo — **identidade de serviço** |
| `AcsServiceTokenClient` | EDGE | ACS | *(RestClient, não Feign)* | ✅ ativo |
| `AcsActorDirectoryClient` | HIST | ACS | `acs-directory` | ✅ ativo |
| `EdgePairingClient` | GEST | EDGE | `edge-pairing-client` | ⚠️ `url` default aponta para 8090 (DASH), não 8087 |
| `HistAuditClient` | ACS | HIST | `hist-service` | ❌ **stub vazio** — causa raiz de AUD-01/02 |
| `MigProvisioningClient` | ACS | MIG | `neuroassist-ai-migrations` | ❌ **stub vazio** — provisionar tenant não chama migrations |
| `PacPatientResolveClient` | ACS | PAC | `pac-service` | — |
| `CaregiverRoutingClient` | EVNT | ACS | `acs-service` | ❌ **stub vazio** + porta default errada (8084=GEST) |
| `AuditTrailClient` | EVNT | HIST | `hist-service` | ❌ **stub vazio** + porta default errada (8087=EDGE) |
| `CommandLookupClient` | EVNT | PAC | `pac-service` | ❌ **stub vazio** + porta default errada (8081) |
| `NotificationDispatchClient` | EVNT | DASH | `dash-service` | ❌ **stub vazio** + porta default errada (8086) |
| `GestValidationClient` | EVNT | GEST | `gest-service` | ⚠️ verificar |
| `AcsClient` / `PacClient` / `EvntClient` / `HistClient` / `EdgeClient` | DASH | vários | — | agregação |

> **Os 4 clients do EVNT são stubs vazios** (`// TODO: add methods`) e os ports
> correspondentes são servidos por adapters fake `@Primary`
> (`CaregiverRoutingHardcodedAdapter`, `AuditTrailNoOpAdapter`,
> `NotificationDispatchLogAdapter`, `CommandLookupHardcodedAdapter`). Detalhe em
> `plan-e2e-integration.md` §11.1c.

---

## 8. Superfície pública (API Gateway)

> **Normativo.** Definido por [ADR-008](../adr/ADR-008-public-api-surface-normalization.md),
> implementado e verificado em `E2E-014` (2026-08-21).

A superfície pública do backend é **`/api/v1/<svc>/**`** para os 7 serviços de domínio, mais
`/oauth2/**` (ACS como Authorization Server). Cliente externo — o frontend — fala **apenas com
o GWY**; nenhum adapter deve apontar para porta de serviço.

**O contrato público é propriedade do gateway, não do controller.** Os `@RequestMapping` dos
serviços não mudam; o GWY reconcilia com `RewritePath`.

### 8.1 Mapa de rotas

| Rota pública (GWY) | Rewrite | `@RequestMapping` real |
|---|---|---|
| `/oauth2/**`, `/.well-known/**` | — | ACS `/oauth2` |
| `/api/v1/acs/**` | — | `/api/v1/acs/{tenants,caregivers,directory}` |
| `/api/v1/hist/**` | — | `/api/v1/hist/{history,audit-facts,audit-records,fact-types,patients}` |
| `/api/v1/edge/**` | — | `/api/v1/edge/{devices,heartbeat}` |
| `/api/v1/pac/**` | → `/api/pac/v1/**` | `/api/pac/v1/pacientes` |
| `/api/v1/evnt/**` | → `/api/v1/**` | `/api/v1/{events,reports}` |
| `/api/v1/dash/**` | → `/panel/**` | `/panel` |
| `/api/v1/gest/eligibility` | → `/gest-service/eligibility` | `/gest-service/eligibility` |
| `/api/v1/gest/tipo-gesto/**` | → `/gest-service/tipo-gesto/**` | `/gest-service/tipo-gesto` |
| `/api/v1/<svc>/actuator/**` | `StripPrefix=3` | `/actuator/**` (os 7) |

As rotas vivem em `neuroassist-ai-api-gateway/src/main/resources/application.yml` (**base**,
não no perfil docker) — assim o GWY roteia igual dentro e fora do compose.

### 8.2 O que **não** é público — e por quê

O `RewritePath` funciona como **allow-list**: um path só é alcançável pela borda se tiver rota.
Não há regra a lembrar; é por construção.

| Superfície | Convenção | Por quê fica fora |
|---|---|---|
| `/internal/**` | DASH | Service-to-service (EVNT→DASH). O rewrite do DASH mapeia para `/panel/**`, então `/internal/notifications` é inatingível de fora |
| `/gest-service/detection-profile/**` | GEST | Consumido pelo EDGE no heartbeat, não por cliente externo |
| `/gest-service/detection-contract/**` | GEST | Interno |
| `/edge-service/**` | EDGE | Interno (profile pointer) |
| `/evnt-service/**` | EVNT | Interno (notification orchestration) |

> **Por isso o GEST tem rota por endpoint, não wildcard.** Ele não tem superfície pública
> própria — os 4 mappings estão todos em `/gest-service/**`. Um `/api/v1/gest/**` genérico
> publicaria `detection-profile` e `detection-contract` junto, anulando a regra acima.

### 8.3 Regra para endpoint novo

1. **É para cliente externo?** Se não, não ganha rota no GWY. Use a convenção interna
   (`/<svc>-service/**` ou `/internal/**`).
2. **Se sim**, o path público é `/api/v1/<svc>/<recurso>`. Se o `@RequestMapping` do controller
   não seguir essa forma, adicione/ajuste o `RewritePath` da rota — **não** mude o controller.
3. **Onde a superfície interna é ampla** (caso do GEST), use predicado por endpoint em vez de
   wildcard, para não publicar o resto junto.
4. Atualize a tabela 8.1 e verifique com os critérios `RWT-01..09` de `E2E-014`.

### 8.4 Verificação

`E2E-014` define 9 critérios (`RWT-01..09`). Os dois que importam para segurança:

- **RWT-07/08** — superfície interna inalcançável: `/api/v1/dash/internal/notifications` e
  `/api/v1/gest/detection-{contract,profile}` devem dar **404 no gateway**.
- **RWT-09** — sem regressão service-to-service: as chamadas Feign da tabela §7 continuam
  funcionando (o GWY não está no caminho delas; elas usam `lb://` direto via Eureka).

> ⚠️ **Health não prova alcançabilidade de API.** As rotas de health têm `StripPrefix=3`, então
> `/api/v1/pac/actuator/health` → `200` mesmo quando `/api/v1/pac/pacientes` dá 404 no serviço.
> São predicados diferentes — foi exatamente assim que o defeito G8 passou pelo `INF-02`.

---

## Referências

- `docs/architecture/backend/standards/hexagonal-patterns.md`
- `docs/architecture/backend/standards/java-conventions.md`
- `docs/architecture/backend/standards/platform-patterns.md` (JMS, Feign, Flyway, MDC, Saga)
- `docs/architecture/shared/multi-empresa.md` (ADR-001 schema-per-empresa)
- `docs/architecture/shared/distributed-architecture.md`
- `docs/architecture/backend/adr/ADR-008-public-api-surface-normalization.md` (superfície pública)
- `docs/architecture/backend/standards/service-identity.md` (auth service-to-service)
