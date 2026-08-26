# Hexagonal Patterns (NeuroAssist AI Backend)

> Ports & Adapters, Application vs Domain Services, EmpresaContext.

## Purpose

Prescriptive guide for applying Hexagonal architecture across every `backend/services/` microservice.
It guarantees: clear separation of concerns, testability, security and operational predictability.

## Scope

- Backend engineers, code reviewers and automation agents working on any service under `backend/services/`.
- Java 21 / Spring Boot 3.5.0 / Gradle (Kotlin DSL).
- Reference model: see `services-catalog.md`.

---

## 1. Canonical package layout

Every service uses the following layout under `com.neuroassist.<module>`:

```
com.neuroassist.<module>/
├── api/
│   ├── controller/          # @RestController — HTTP entry points
│   ├── dto/                 # Request/Response DTOs (Java records preferred)
│   └── exception/           # @RestControllerAdvice (ProblemDetail — RFC 7807)
├── application/
│   ├── service/             # @Service — use-case impl (@Transactional here)
│   └── port/
│       ├── in/              # Use-case interfaces (commands / queries)
│       └── out/             # Repository + external service interfaces
├── domain/
│   ├── model/               # Entities, value objects — plain POJO, zero Spring/JPA
│   └── service/             # Cross-entity domain rules — no infra
└── infrastructure/
    ├── clients/             # @FeignClient — inter-service HTTP
    ├── config/              # @Configuration — Spring beans, Resilience4j, DomainServiceConfig
    ├── persistence/
    │   ├── entity/          # @Entity JPA
    │   ├── repository/      # Spring Data JPA @Repository interfaces
    │   └── adapter/         # Implements port.out — maps domain <-> JPA
    └── security/            # Spring Security filters, MDC, CorrelationIdInterceptor, EmpresaContextHolder
```

**Dependency rules between layers:**

| Layer | May depend on | Forbidden |
|---|---|---|
| `domain` | Only other `domain` classes | Any other layer, any framework |
| `application` | `domain`, `port.in`, `port.out` | `infrastructure`, `api` |
| `infrastructure` | `domain`, `port.out`, external libs | `api`, `application` |
| `api` | `application`, `domain` (VOs, enums) | `infrastructure` directly |

---

## 2. Ports & Adapters -- practical rules

### Ports (domain-level interfaces, `application/port/out/`)
- Methods carry business intent (e.g. `findByBusinessKey`) instead of being infra-centric (`findById(Long)`).
- Ports use domain types and throw domain exceptions.
- Output ports in `application.port.out`; use-case interfaces in `application.port.in`.

### Adapters (infra implementations, `infrastructure/persistence/adapter/`)
- They translate between infra (JPA entities, HTTP payloads) and domain objects.
- Adapters do NOT contain orchestration or business-process logic.
- Annotated with `@Component` or `@Repository`, they live exclusively in `infrastructure`.

### Anti-patterns to avoid
- Ports exposing JPA IDs as primary identifiers when a business key exists.
- Adapters holding business logic or opening their own transactions.

---

## 3. Application Services vs Domain Services

### Application Services (`application/service/`)
- They implement interfaces from `application/port/in/`.
- They orchestrate port calls, apply Resilience4j policies, and delimit transactions with `@Transactional`.
- They call `EmpresaContextHolder.require()` before any empresa operation (RULE-07).
- Example: `Create<Resource>Service implements Create<Resource>UseCase`.

### Domain Services (`domain/service/`)
- Pure domain rules that span multiple entities but do not belong to a single one.
- No infra dependency; unit-testable without Spring.
- Example: `<Resource>DomainService` -- validates business rules, computes derived state.
- They **MUST NOT** use `@Service`, `@Component` or any Spring annotation (ArchUnit RULE-02).

### Bean registration (mandatory)
Domain services must be registered through a `@Configuration` in `infrastructure/config/`.
**Always** create `DomainServiceConfig.java` providing a `@Bean` for each domain service.

```java
// infrastructure/config/DomainServiceConfig.java
@Configuration
public class DomainServiceConfig {
    @Bean
    public <Resource>DomainService <resource>DomainService() {
        return new <Resource>DomainService();
    }
}
```

---

## 4. Transaction boundaries

- `@Transactional` **only** on Application Services (RULE-03).
- Controllers and infrastructure adapters do NOT declare `@Transactional`.
- Explicit propagation (`REQUIRES_NEW`) only when justified (e.g. idempotency key write).
- `@Version` for optimistic locking on entities subject to concurrent modification.

---

## 4b. JPA Entity Persistence -- domain-assigned UUIDs

When the domain layer generates the UUID (e.g. `UUID.randomUUID()` inside the static factory
`static create(...)` on the domain record), the JPA entity MUST use:

```java
@Entity
@Table(name = "...")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class FooJpaEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;                // set by the caller — never auto-generated
    @Version
    @Column(name = "version")
    private Long version;           // nullable — Spring Data uses
                                    // version==null to detect NEW entities
    // ... other columns ...
}
```

**Do NOT combine** the following -- each permutation breaks CREATE (errors: "uninitialized
version value", "detached entity passed to persist", a silent
`ObjectOptimisticLockingFailureException` in `em.merge`):

- ❌ `@UuidGenerator` + a UUID pre-set by the domain
- ❌ `implements Persistable<UUID>` + `@Version` together
- ❌ `@Version` initialized as `0L` in the builder
- ❌ `@GeneratedValue(strategy = UUID)` with UUIDs generated in the domain

**Fixtures and adapters MUST call `.id(UUID.randomUUID())`** (or `.id(domain.id())`)
on every `builder().build()` -- there is no auto-generated fallback.

---

## 5. EmpresaContext propagation

> **Multi-tenancy variant:** this pattern implements **corporate multi-tenant** --
> tenants are known units of the same economic group, isolated by Postgres schema.
> The difference from SaaS multi-tenant (unknown external customers) is in tenant
> discovery (static vs. dynamic), not in context propagation -- which follows the same
> ThreadLocal pattern in both variants (D-400).

### Synchronous requests
- `EmpresaContextHolder` (ThreadLocal) populated in a `OncePerRequestFilter` / `HandlerInterceptor` under `infrastructure/security/`.
- Set the MDC: `MDC.put("empresaId", empresaId)` for correlation in structured logs.
- Clear the ThreadLocal and the MDC in `finally`.

```java
// infrastructure/security/EmpresaContextHolder.java
public final class EmpresaContextHolder {
    private static final ThreadLocal<String> EMPRESA = new ThreadLocal<>();
    public static void set(String e) {
        EMPRESA.set(e);
        MDC.put("empresaId", e);
    }
    public static String require() {
        String e = EMPRESA.get();
        if (e == null) throw new IllegalStateException("EmpresaContext not set");
        return e;
    }
    public static String get() { return EMPRESA.get(); }
    public static void clear() {
        EMPRESA.remove();
        MDC.remove("empresaId");
    }
}
```

### Cross-Empresa Access Guard (mandatory on child-entity creation)

Any application service that creates a child entity linked to a parent MUST load the parent via
`findByIdAndEmpresaId(parentId, ctx.empresaId())`
and throw `<Parent>NotFoundException` if absent -- **before** instantiating the child.

`EmpresaContextHolder.require()` only proves WHO the caller is, not WHICH resources it may touch.
Silently skipping the parent-empresa check lets empresa-A create children inside the aggregate of empresa-B.

---

## 6. DTOs, mapping & Anti-Corruption Layer

- `api` uses DTOs (`api.dto.*`) for input/output -- never JPA entities.
- Use MapStruct: Entity <-> Domain <-> DTO.
- The Anti-Corruption Layer (ACL) for external integrations lives in `infrastructure.clients` -- it translates external schemas into the internal domain model.

---

## 7. External integrations & resilience

- Apply Resilience4j `@Retry` and `@CircuitBreaker` on the adapter/client.
- Adapters translate infra errors into domain exceptions.
- Application Services decide retry/compensation based on the domain exceptions.
- Apply `@Bulkhead` to cap concurrent calls to slow external systems.

---

## 8. Idempotency and concurrency

- Implement the idempotency token pattern: store an idempotency key (scoped by empresa + operation)
  before processing; unique constraint in the database.
- Combine it with optimistic locking (`@Version`) for entities under concurrent update.

---

## 9. Error and exception handling

- Domain exceptions in `domain.exception` -- they express business failures in domain language.
- Infra exceptions in `infrastructure.*` -- translated to domain ones before reaching the application.
- `@RestControllerAdvice` in `api.exception` maps domain exceptions to `ProblemDetail` (RFC 7807).
- See `java-conventions.md` §Exception Handling.

---

## 10. ArchUnit Rules & Gradle integration

Create `ArchitectureTest.java` enforcing the boundaries:

```java
@AnalyzeClasses(packages = "com.neuroassist.{module}")
public class ArchitectureTest {

    @ArchTest static final ArchRule RULE_01 =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..");
    @ArchTest static final ArchRule RULE_02 =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..");
    @ArchTest static final ArchRule RULE_03 =
        noMethods().that().areAnnotatedWith(Transactional.class)
            .should().beDeclaredInClassesThat()
                .doNotResideInAPackage("..application.service..");
    @ArchTest static final ArchRule RULE_04 =
        noClasses().that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");
    @ArchTest static final ArchRule RULE_05 =
        noClasses().that().resideInAPackage("..infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("..api..");
    @ArchTest static final ArchRule RULE_06 =
        classes().that().areAnnotatedWith(FeignClient.class)
            .should().resideInAPackage("..infrastructure.clients..");

    @ArchTest static final ArchRule RULE_07 = // (manual review + check in integration test)
        // every method in application.service that touches empresa data MUST call
        // EmpresaContextHolder.require() on the first line. Enforced via custom ArchUnit rule.
        methods().that().arePublic().and()
            .areDeclaredInClassesThat().resideInAPackage("..application.service..")
            .should(callEmpresaContextHolderRequire());

    @ArchTest static final ArchRule RULE_09 =
        classes().should().resideInAPackage("com.neuroassist..");
}
```

Add to `build.gradle.kts`:
```kotlin
dependencies {
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}
tasks.register("architectureCheck") { dependsOn(tasks.named("test")) }
```

---

## 11. PR Checklist

### Automated
- ArchUnit rules green (`./gradlew :<service>:architectureCheck`).
- Unit + integration tests green.

### Manual
- Ports in `application/port/out` (or `port/in`) -- not in `domain`.
- Adapters implementing ports in `infrastructure/persistence/adapter` or `infrastructure/clients`.
- No `@Transactional` in controllers or adapters.
- API controllers use DTOs; entities never returned directly.
- `EmpresaContextHolder.require()` called in application services before empresa operations.
- Base package `com.neuroassist.<module>` (RULE-09).
- `DomainServiceConfig.java` present if the service has domain services.

---

## References

- `java-conventions.md` -- Java/Lombok/JPA conventions
- `snippets.md` -- reference code per class type
- `platform-patterns.md` -- Feign, Flyway, MDC, Saga, multi-schema
- `testing-conventions.md` -- test pyramid
- `services-catalog.md` -- microservice catalog
- `docs/architecture/shared/multi-empresa.md` -- ADR-001 schema-per-empresa
