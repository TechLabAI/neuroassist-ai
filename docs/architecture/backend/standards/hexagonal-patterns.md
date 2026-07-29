# Hexagonal Patterns (NeuroAssist AI Backend)

> Ports & Adapters, Application vs Domain Services, EmpresaContext.

## Propósito

Guia prescritivo para aplicar arquitetura Hexagonal em todos os microserviços `backend/services/`.
Garante: separação clara de responsabilidades, testabilidade, segurança e previsibilidade operacional.

## Escopo

- Engenheiros backend, code reviewers e agentes de automação trabalhando em qualquer serviço sob `backend/services/`.
- Java 21 / Spring Boot 3.5.0 / Gradle (Kotlin DSL).
- Modelo de referência: ver `services-catalog.md`.

---

## 1. Layout canônico de pacotes

Todos os serviços usam o seguinte layout sob `com.neuroassist.<module>`:

```
com.neuroassist.<module>/
├── api/
│   ├── controller/          # @RestController — HTTP entry points
│   ├── dto/                 # Request/Response DTOs (Java records preferidos)
│   └── exception/           # @RestControllerAdvice (ProblemDetail — RFC 7807)
├── application/
│   ├── service/             # @Service — use-case impl (@Transactional aqui)
│   └── port/
│       ├── in/              # Use-case interfaces (commands / queries)
│       └── out/             # Repository + external service interfaces
├── domain/
│   ├── model/               # Entities, value objects — POJO puro, zero Spring/JPA
│   └── service/             # Regras de domínio cross-entity — sem infra
└── infrastructure/
    ├── clients/             # @FeignClient — inter-service HTTP
    ├── config/              # @Configuration — Spring beans, Resilience4j, DomainServiceConfig
    ├── messaging/
    │   ├── producer/        # JmsTemplate event publishers
    │   └── consumer/        # @JmsListener event consumers
    ├── persistence/
    │   ├── entity/          # @Entity JPA
    │   ├── repository/      # Spring Data JPA @Repository interfaces
    │   └── adapter/         # Implementa port.out — mapeia domain ↔ JPA
    └── security/            # Spring Security filters, MDC, CorrelationIdInterceptor, EmpresaContextHolder
```

**Regras de dependência entre camadas:**

| Camada | Pode depender de | Proibido |
|---|---|---|
| `domain` | Apenas outras classes `domain` | Qualquer outra camada, qualquer framework |
| `application` | `domain`, `port.in`, `port.out` | `infrastructure`, `api` |
| `infrastructure` | `domain`, `port.out`, libs externas | `api`, `application` |
| `api` | `application`, `domain` (VOs, enums) | `infrastructure` diretamente |

---

## 2. Ports & Adapters — regras práticas

### Ports (interfaces de nível domain, `application/port/out/`)
- Métodos com intenção de negócio (ex.: `findByBusinessKey`) em vez de infra-centric (`findById(Long)`).
- Ports usam tipos de domínio e lançam exceções de domínio.
- Output ports em `application.port.out`; use-case interfaces em `application.port.in`.

### Adapters (implementações infra, `infrastructure/persistence/adapter/`)
- Traduzem entre infra (JPA entities, HTTP payloads) e objetos de domínio.
- Adapters NÃO contêm orquestração nem lógica de processo de negócio.
- Anotados com `@Component` ou `@Repository`, vivem exclusivamente em `infrastructure`.

### Anti-patterns a evitar
- Ports expondo IDs JPA como identificadores primários quando há business key.
- Adapters com lógica de negócio ou abrindo suas próprias transações.

---

## 3. Application Services vs Domain Services

### Application Services (`application/service/`)
- Implementam interfaces de `application/port/in/`.
- Orquestram chamadas a ports, aplicam policies Resilience4j, delimitam transações com `@Transactional`.
- Chamam `EmpresaContextHolder.require()` antes de qualquer operação de empresa (RULE-07).
- Exemplo: `Create<Resource>Service implements Create<Resource>UseCase`.

### Domain Services (`domain/service/`)
- Regras puras de domínio que atravessam múltiplas entidades mas não pertencem a uma só.
- Sem dependência de infra; unit-testáveis sem Spring.
- Exemplo: `<Resource>DomainService` -- valida regras de negócio, calcula estado derivado.
- **NÃO PODEM** usar `@Service`, `@Component` ou qualquer anotação Spring (ArchUnit RULE-02).

### Registro como Bean (obrigatório)
Domain services precisam ser registrados via `@Configuration` em `infrastructure/config/`.
**Sempre** crie `DomainServiceConfig.java` que provê `@Bean` para cada domain service.

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

## 4. Boundaries de transação

- `@Transactional` **apenas** em Application Services (RULE-03).
- Controllers e infrastructure adapters NÃO declaram `@Transactional`.
- Propagação explícita (`REQUIRES_NEW`) apenas quando justificado (ex.: idempotency key write).
- `@Version` para optimistic locking em entidades sujeitas a modificação concorrente.

---

## 4b. JPA Entity Persistence — UUIDs domain-assigned

Quando a camada domain gera UUID (ex.: `UUID.randomUUID()` dentro de factory estática
`static create(...)` no record domain), a entidade JPA DEVE usar:

```java
@Entity
@Table(name = "...")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class FooJpaEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;                // set pelo caller — nunca auto-gerado
    @Version
    @Column(name = "version")
    private Long version;           // nullable — Spring Data usa
                                    // version==null para detectar entidades NOVAS
    // ... outras colunas ...
}
```

**NÃO combinar** os seguintes — cada permutação quebra CREATE (erros: "uninitialized
version value", "detached entity passed to persist", `ObjectOptimisticLockingFailureException`
silencioso em `em.merge`):

- ❌ `@UuidGenerator` + pre-set UUID do domain
- ❌ `implements Persistable<UUID>` + `@Version` juntos
- ❌ `@Version` inicializado como `0L` no builder
- ❌ `@GeneratedValue(strategy = UUID)` com UUIDs gerados no domain

**Fixtures e adapters DEVEM chamar `.id(UUID.randomUUID())`** (ou `.id(domain.id())`)
em todo `builder().build()` -- não há fallback auto-gerado.

---

## 5. Propagação de EmpresaContext

> **Variante de multi-tenancy:** este padrão implementa **corporate multi-tenant** —
> tenants são unidades conhecidas do mesmo grupo econômico, isoladas por schema Postgres.
> A diferença para SaaS multi-tenant (clientes externos desconhecidos) está na descoberta
> de tenant (estática vs. dinâmica), não na propagação de contexto — que segue o mesmo
> padrão ThreadLocal em ambas as variantes (D-400).

### Requests síncronos
- `EmpresaContextHolder` (ThreadLocal) populado em `OncePerRequestFilter` / `HandlerInterceptor` em `infrastructure/security/`.
- Setar MDC: `MDC.put("empresaId", empresaId)` para correlação em logs estruturados.
- Limpar ThreadLocal e MDC em `finally`.

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

### Chamadas assíncronas e `@JmsListener`
- Para threads async, passar `EmpresaContext` explicitamente como header da mensagem JMS (`empresaId`).
- Consumer lê o header, popula ThreadLocal, processa, faz `clear()` no `finally`.
- **Nunca** confie em propagação ThreadLocal cross thread pool.

### Cross-Empresa Access Guard (obrigatório em criação de child-entity)

Qualquer application service que cria entidade-filho ligada a um pai DEVE carregar o pai via
`findByIdAndEmpresaId(parentId, ctx.empresaId())`
e lançar `<Parent>NotFoundException` se ausente -- **antes** de instanciar o filho.

`EmpresaContextHolder.require()` só prova QUEM é o caller, não QUAIS recursos pode tocar.
Pular o parent-empresa check silenciosamente permite que empresa-A crie filhos no agregado da empresa-B.

---

## 6. DTOs, mapeamento & Anti-Corruption Layer

- `api` usa DTOs (`api.dto.*`) para input/output -- nunca entidades JPA.
- Use MapStruct: Entity ↔ Domain ↔ DTO.
- Anti-Corruption Layer (ACL) para integrações externas vive em `infrastructure.clients` ou `infrastructure.messaging.consumer` -- traduz schemas externos para o modelo de domínio interno.

---

## 7. Integrações externas & resilience

- Aplique Resilience4j `@Retry` e `@CircuitBreaker` no adapter/client.
- Adapters traduzem erros de infra para exceções de domínio.
- Application Services decidem retry/compensation baseados nas exceções de domínio.
- Aplique `@Bulkhead` para limitar chamadas concorrentes a sistemas externos lentos.

---

## 8. Idempotência e concorrência

- Implemente padrão idempotency token: armazena uma idempotency key (scoped por empresa + operação)
  antes de processar; constraint unique no banco.
- Combine com optimistic locking (`@Version`) para entidades sob update concorrente.

---

## 9. Tratamento de erros & exceções

- Exceções de domínio em `domain.exception` -- expressam falhas de negócio em linguagem de domínio.
- Exceções de infra em `infrastructure.*` -- traduzidas para domain antes de chegar à application.
- `@RestControllerAdvice` em `api.exception` mapeia exceções de domínio para `ProblemDetail` (RFC 7807).
- Ver `java-conventions.md` §Exception Handling.

---

## 10. ArchUnit Rules & Gradle integration

Crie `ArchitectureTest.java` enforçando boundaries:

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

    @ArchTest static final ArchRule RULE_07 = // (verificação manual + check em integration test)
        // todo method em application.service que toca dado de empresa DEVE chamar
        // EmpresaContextHolder.require() na primeira linha. Enforced via custom ArchUnit rule.
        methods().that().arePublic().and()
            .areDeclaredInClassesThat().resideInAPackage("..application.service..")
            .should(callEmpresaContextHolderRequire());

    @ArchTest static final ArchRule RULE_09 =
        classes().should().resideInAPackage("com.neuroassist..");
}
```

Add em `build.gradle.kts`:
```kotlin
dependencies {
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}
tasks.register("architectureCheck") { dependsOn(tasks.named("test")) }
```

---

## 11. PR Checklist

### Automatizado
- ArchUnit rules verdes (`./gradlew :<service>:architectureCheck`).
- Unit + integration tests verdes.

### Manual
- Ports em `application/port/out` (ou `port/in`) -- não em `domain`.
- Adapters implementando ports em `infrastructure/persistence/adapter` ou `infrastructure/clients`.
- Sem `@Transactional` em controllers ou adapters.
- API controllers usam DTOs; entities nunca retornadas direto.
- `EmpresaContextHolder.require()` chamado em application services antes de operações de empresa.
- Pacote base `com.neuroassist.<module>` (RULE-09).
- `DomainServiceConfig.java` presente se o serviço tem domain services.

---

## Referências

- `java-conventions.md` -- convenções Java/Lombok/JPA
- `snippets.md` -- código reference por tipo de classe
- `platform-patterns.md` -- JMS, Feign, Flyway, MDC, Saga, multi-schema
- `testing-conventions.md` -- pirâmide de testes
- `services-catalog.md` -- catálogo de microserviços
- `docs/architecture/shared/multi-empresa.md` -- ADR-001 schema-per-empresa
