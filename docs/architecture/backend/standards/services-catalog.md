# Services Catalog (NeuroAssist AI Backend)

> Catálogo de referência rápida de todos os microserviços do backend:
> pacote Java canônico, project key Gradle, responsabilidade em uma linha, ports e eventos chave.

Use este arquivo quando:
- Iniciar serviço novo -- encontrar pacote canônico e project key.
- Escrever `applyTo` em CI ou referência cruzada.
- Entender qual serviço é dono de qual fronteira de domínio.

> **Atualização:** este catálogo é atualizado por `/be-architect-vision <MODULE>` quando
> um novo módulo é registrado. Edite manualmente apenas se você adicionar capacidades
> a um serviço existente (eventos, Feign clients).

---

## 1. Service Map

| Componente | Gradle project key | Pacote canônico | Módulo |
|---|---|---|---|
| `` | `:services:` | `com.neuroassist.gest` | GEST |
| `` | `:services:` | `com.neuroassist.evnt` | EVNT |
| `` | `:services:` | `com.neuroassist.pac` | PAC |
| `` | `:services:` | `com.neuroassist.acs` | ACS |
| `` | `:services:` | `com.neuroassist.hist` | HIST |
| `` | `:services:` | `com.neuroassist.dash` | DASH |
| `` | `:services:` | `com.neuroassist.edge` | EDGE |

| `neuroassist-ai-migrations` | `:services:neuroassist-ai-migrations` | `com.neuroassist.migrations` | -- |

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

| Feign interface | Consumer service | Provider service | `@FeignClient(name = ...)` |
|---|---|---|---|
| _(populated as services declare Feign integrations)_ | — | — | — |

A lista será expandida conforme integrações forem identificadas em SPECs por serviço.

---

## Referências

- `docs/architecture/backend/standards/hexagonal-patterns.md`
- `docs/architecture/backend/standards/java-conventions.md`
- `docs/architecture/backend/standards/platform-patterns.md` (JMS, Feign, Flyway, MDC, Saga)
- `docs/architecture/shared/multi-empresa.md` (ADR-001 schema-per-empresa)
- `docs/architecture/shared/distributed-architecture.md`
