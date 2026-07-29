# Backend Snippets (NeuroAssist AI)

> Snippets opinados (6-12 linhas) + pseudocódigo para implementar ports/adapters/use-cases + testes.
> Cheat-sheet. Para racional completo, ver `hexagonal-patterns.md`.

**Formato:** Título → caminho-alvo → snippet → notas → refs

---

## 1) Port -- Persistence (semantic repository)

- Alvo: `backend/services/<service>/.../application/port/out/**`
- Exemplo: `com.neuroassist.<module>.application.port.out.ClientePort`

```java
// application/port/out/ClientePort.java
public interface ClientePort {
    Optional<Cliente> findByCpfCnpj(String cpfCnpj);
    Cliente save(Cliente cliente);
}
```

- Notas: Port expõe intenção (`findByCpfCnpj`) -- evita API persistence-leaky como `findById(Long)` quando há business key clara.

---

## 2) Port -- Use Case (input port)

- Alvo: `backend/services/<service>/.../application/port/in/**`

```java
// application/port/in/CriarClienteUseCase.java
public interface CriarClienteUseCase {
    ClienteResult execute(CriarClienteCommand cmd);
}
```

- Notas: Use-case interfaces são fronteira entre `api` e `application`. Controllers dependem destas, nunca de classes service concretas.

---

## 3) Port -- External Integration (adapter contract)

- Alvo: `backend/services/<service>/.../application/port/out/**`

```java
// application/port/out/SefazAdapterPort.java
public interface SefazAdapterPort {
    NFeResponse emitir(NFeRequest request) throws ExternalServiceException;
}
```

- Notas: Adapter implementations traduzem HTTP/SOAP/XML para tipos de domínio e mapeiam erros para `ExternalServiceException`.

---

## 4) Adapter -- JPA (persistence)

- Alvo: `backend/services/<service>/.../infrastructure/persistence/adapter/**`

```java
@Component
@RequiredArgsConstructor
class ClienteJpaAdapter implements ClientePort {
    private final ClienteJpaRepository repo;
    private final ClienteMapper mapper;
    public Optional<Cliente> findByCpfCnpj(String cpfCnpj) {
        return repo.findByCpfCnpj(cpfCnpj).map(mapper::toDomain);
    }
    public Cliente save(Cliente cliente) {
        return mapper.toDomain(repo.save(mapper.toEntity(cliente)));
    }
}
```

- Notas: Mapeamento via MapStruct `ClienteMapper`. Adapter nunca abre sua própria transação.

---

## 5) Adapter -- Feign HTTP client (inter-service)

- Alvo: `backend/services/<service>/.../infrastructure/clients/**`

```java
// infrastructure/clients/CadastrosClienteClient.java
@FeignClient(name = "cadastros-service")
public interface CadastrosClienteClient {
    @GetMapping("/api/v1/clientes/{id}")
    ClienteDto getById(@PathVariable UUID id,
                       @RequestHeader("X-Empresa-Id") String empresaId);
}
```

- Notas: Feign clients registrados via `@EnableFeignClients`. Aplicar Resilience4j no método do application service que chama.

---

## 6) Adapter -- External HTTP (Resilience4j)

```java
@Component
@RequiredArgsConstructor
class SefazAdapter implements SefazAdapterPort {
    private final WebClient webClient;
    @Retry(name = "sefaz")
    @CircuitBreaker(name = "sefaz")
    @Bulkhead(name = "sefaz")
    public NFeResponse emitir(NFeRequest r) {
        return webClient.post().uri("/nfe/emitir")
            .bodyValue(r)
            .retrieve()
            .bodyToMono(NFeResponse.class)
            .block();
    }
}
```

- Notas: Adapter lança exceções domain-específicas em falhas; Application Service decide compensação.

---

## 7) Application Service (transactional use-case)

- Alvo: `backend/services/<service>/.../application/service/**`

```java
@Service
@RequiredArgsConstructor
class CriarClienteService implements CriarClienteUseCase {
    private final ClientePort clientePort;
    private final ClienteDomainService domainService;
    @Transactional
    public ClienteResult execute(CriarClienteCommand cmd) {
        EmpresaContextHolder.require();         // RULE-07 -- obrigatório
        var cliente = Cliente.from(cmd);
        domainService.validar(cliente);
        return ClienteResult.from(clientePort.save(cliente));
    }
}
```

- Notas: `@Transactional` só aqui (RULE-03). Adapters chamados dentro mas NÃO abrem suas próprias transações.

---

## 8) Anti-pattern -- @Transactional na camada errada

```java
// ❌ BAD: @Transactional em controller mistura concern web + DB transaction
@RestController class ClienteController {
    @Transactional
    @PostMapping public ResponseEntity<?> create(...) { ... }
}

// ❌ BAD: @Transactional em adapter
@Component class ClienteJpaAdapter implements ClientePort {
    @Transactional
    public Cliente save(Cliente c) { ... }
}
```

---

## 9) EmpresaContextHolder + EmpresaFilter

- Alvo: `backend/services/<service>/.../infrastructure/security/**`

```java
public final class EmpresaContextHolder {
    private static final ThreadLocal<String> EMPRESA = new ThreadLocal<>();
    public static void set(String e) {
        EMPRESA.set(e);
        MDC.put("empresaId", e);
    }
    public static String require() {
        String e = EMPRESA.get();
        if (e == null) throw new IllegalStateException("EmpresaContext not initialized");
        return e;
    }
    public static void clear() { EMPRESA.remove(); MDC.remove("empresaId"); }
}

@Component
class EmpresaFilter extends OncePerRequestFilter {
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            EmpresaContextHolder.set(req.getHeader("X-Empresa-Id"));
            chain.doFilter(req, res);
        } finally { EmpresaContextHolder.clear(); }
    }
}
```

- Notas: Para `@JmsListener`, passar empresa explicitamente do envelope do evento; nunca depender de ThreadLocal de thread web.

---

## 10) JMS Producer (event publisher)

- Alvo: `backend/services/<service>/.../infrastructure/messaging/producer/**`

```java
@Component
@RequiredArgsConstructor
class VendaConfirmadaPublisher {
    private final JmsTemplate jms;
    public void publish(VendaConfirmadaEvent event) {
        jms.convertAndSend("neuroassist-ai.<module>.<entity>.<event>", event.toEnvelope());
    }
}
```

- Notas: Evento conforma ao envelope (`eventId`, `eventType`, `occurredAt`, `correlationId`, `version`, `empresaId`, `payload`). Ver `platform-patterns.md` §1.2.

---

## 11) JMS Consumer (idempotente)

- Alvo: `backend/services/<service>/.../infrastructure/messaging/consumer/**`

```java
@Component
@RequiredArgsConstructor
@Slf4j
class VendaConfirmadaConsumer {
    private final EmitirNFeUseCase emitirNFe;
    private final ProcessedEventPort processedEvents;
    @JmsListener(destination = "neuroassist-ai.<module>.<entity>.<event>")
    public void onVendaConfirmada(EventEnvelope envelope) {
        if (processedEvents.alreadyProcessed(envelope.eventId())) return;
        EmpresaContextHolder.set(envelope.empresaId());       // propagação explícita
        try {
            emitirNFe.execute(EmitirNFeCommand.from(envelope));
            processedEvents.markProcessed(envelope.eventId());
        } finally { EmpresaContextHolder.clear(); }
    }
}
```

- Notas: Consumer DEVE ser idempotente -- check antes de agir.

---

## 12) Idempotency pattern

- Alvo: `backend/services/<service>/.../application/service/**`

```java
@Transactional
public NFeResult execute(EmitirNFeCommand cmd) {
    EmpresaContextHolder.require();
    if (idempotencyPort.exists(cmd.empresaId(), cmd.idempotencyKey()))
        return idempotencyPort.previousResult(cmd.empresaId(), cmd.idempotencyKey());
    idempotencyPort.register(cmd.empresaId(), cmd.idempotencyKey(), Instant.now());
    // ... processa
}
```

- Notas: idempotency key composta por (empresaId, operationKey).

---

## 13) MapStruct Mapper

- Alvo: `backend/services/<service>/.../infrastructure/persistence/**`

```java
@Mapper(componentModel = "spring")
public interface ClienteMapper {
    Cliente toDomain(ClienteJpaEntity entity);
    ClienteJpaEntity toEntity(Cliente domain);
    ClienteResponseDto toDto(Cliente domain);
}
```

- Notas: Use `componentModel = "spring"` para injection via construtor. Adicione `annotationProcessor("org.mapstruct:mapstruct-processor:...")` em `build.gradle.kts`.

---

## 14) Controller (mínimo)

- Alvo: `backend/services/<service>/.../api/controller/**`

```java
@RestController
@RequestMapping("/api/v1/clientes")
@RequiredArgsConstructor
class ClienteController {
    private final CriarClienteUseCase criarCliente;
    @PostMapping
    @PreAuthorize("hasAuthority('cad.cliente.write')")
    public ResponseEntity<ClienteResponseDto> create(@Valid @RequestBody CriarClienteRequestDto dto) {
        var result = criarCliente.execute(dto.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(ClienteResponseDto.from(result));
    }
}
```

---

## 15) GlobalExceptionHandler (ProblemDetail -- RFC 7807)

- Alvo: `backend/services/<service>/.../api/exception/**`

```java
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    ProblemDetail onBusiness(BusinessException e) {
        var pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        pd.setProperty("errorCode", e.getErrorCode());
        pd.setProperty("empresaId", EmpresaContextHolder.get());
        return pd;
    }
    @ExceptionHandler(EntityNotFoundException.class)
    ProblemDetail onNotFound(EntityNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
```

---

## 16) Unit Test -- Application Service (mock ports)

- Alvo: `backend/services/<service>/src/test/**`

```java
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CriarClienteServiceTest {
    @Mock ClientePort clientePort;
    @Mock ClienteDomainService domainService;
    @InjectMocks CriarClienteService svc;
    @BeforeEach
    void setUp() { EmpresaContextHolder.set("test"); }
    @AfterEach
    void tearDown() { EmpresaContextHolder.clear(); }
    @Test
    void shouldCreateCliente() {
        when(clientePort.save(any())).thenReturn(fakeCliente());
        var result = svc.execute(fakeCommand());
        assertThat(result).isNotNull();
        verify(clientePort).save(any());
    }
}
```

---

## 17) Integration Test -- Testcontainers (PostgreSQL, singleton pattern)

- Alvo: `backend/services/<service>/src/test/**`

> **IMPORTANTE:** use singleton container (static initializer), NUNCA `@Testcontainers` + `@Container`
> -- race condition com `@DynamicPropertySource`.

```java
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    static final PostgreSQLContainer<?> postgres;
    static {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("neuroassist-ai_test")
                .withUsername("test")
                .withPassword("test")
                .withInitScript("init-schema.sql");
        postgres.start();
    }
    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }
}
```

- Notas: NUNCA H2 para integration tests -- sempre Testcontainers com PostgreSQL real.

---

## 18) WireMock Stub -- serviço externo

```java
@RegisterExtension
static WireMockExtension wm = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort()).build();

wm.stubFor(post(urlEqualTo("/nfe/emitir"))
    .willReturn(aResponse().withStatus(200).withBody("<ok/>")));
```

---

## 19) ArchUnit Rule Sample

- Alvo: `backend/services/<service>/src/test/**`

```java
@AnalyzeClasses(packages = "com.neuroassist.{module}")
class ArchitectureTest {
    @ArchTest
    static final ArchRule RULE_04_api_no_infrastructure =
        noClasses().that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");
    @ArchTest
    static final ArchRule RULE_01_domain_no_spring =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..");
    @ArchTest
    static final ArchRule RULE_09_pacote_base =
        classes().should().resideInAPackage("com.neuroassist..");
}
```

---

## 20) Gradle `build.gradle.kts` -- dependências e ArchUnit task

```kotlin
dependencies {
    // ArchUnit
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    // Cucumber BDD
    testImplementation("io.cucumber:cucumber-junit-platform-engine:7.18.0")
    testImplementation("io.cucumber:cucumber-java:7.18.0")
    testImplementation("io.cucumber:cucumber-spring:7.18.0")
    // Testcontainers
    testImplementation("org.testcontainers:postgresql:1.20.0")
    // WireMock
    testImplementation("org.wiremock:wiremock-standalone:3.9.1")
    // Embedded Artemis broker para JMS em testes
    testImplementation("org.apache.activemq:artemis-jakarta-server")
    // MapStruct processor
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.0")
    implementation("org.mapstruct:mapstruct:1.6.0")
}

tasks.register("architectureCheck") { dependsOn(tasks.named("test")) }
```

> **IMPORTANTE:** Nunca usar `org.testcontainers:activemq` (não existe para Artemis).
> Use `org.apache.activemq:artemis-jakarta-server` com `spring.artemis.mode=embedded`.

---

## 21) DomainServiceConfig (obrigatório)

- Alvo: `backend/services/<service>/.../infrastructure/config/**`

Domain services são POJOs puros (RULE-01: sem imports Spring). DEVEM ser registrados via `@Configuration`:

```java
// infrastructure/config/DomainServiceConfig.java
@Configuration
public class DomainServiceConfig {
    @Bean
    public ClienteDomainService clienteDomainService() {
        return new ClienteDomainService();
    }
    @Bean
    public OrcamentoDomainService orcamentoDomainService() {
        return new OrcamentoDomainService();
    }
}
```

- Notas: Um bean method por domain service. Se o domain service tem parâmetros (outros domain services), wire aqui.

---

## 22) TestSecurityConfig -- mock JwtDecoder

- Alvo: `backend/services/<service>/src/test/**/config/**`

```java
@Configuration
@Profile("test")
public class TestSecurityConfig {
    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> Jwt.withTokenValue(token)
                .header("alg", "none")
                .claim("sub", "test-user")
                .claim("empresa_id", "test")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
```

- Notas: Previne Spring de conectar ao OIDC issuer real durante testes.

---

## 23) `application-test.yml` -- configuração compreensiva de testes

- Alvo: `backend/services/<service>/src/test/resources/**`

```yaml
# Profile 'test' -- Testcontainers provê o DB real.
# Datasource properties são sobrescritos via @DynamicPropertySource.
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    properties:
      hibernate:
        default_schema: neuroassist_global
  flyway:
    enabled: false
  artemis:
    mode: embedded
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri:
  cloud:
    discovery:
      client:
        simple:
          instances:
            iam-service:
              - uri: http://localhost:19999
            cadastros-service:
              - uri: http://localhost:19999
            comercial-service:
              - uri: http://localhost:19999
            fiscal-service:
              - uri: http://localhost:19999
            financeiro-service:
              - uri: http://localhost:19999
            estoque-service:
              - uri: http://localhost:19999
            crm-service:
              - uri: http://localhost:19999
            logistica-service:
              - uri: http://localhost:19999
            auditoria-service:
              - uri: http://localhost:19999

eureka:
  client:
    enabled: false
```

- Notas: Override em `@DynamicPropertySource` para datasource. `simple.instances` provê URLs dummy para Feign clients quando Eureka está desabilitado.

---

## 24) `init-schema.sql` -- inicialização de schema de teste

- Alvo: `backend/services/<service>/src/test/resources/**`

```sql
-- Test schema initialization
CREATE SCHEMA IF NOT EXISTS neuroassist_global;
CREATE SCHEMA IF NOT EXISTS test;
```

- Notas: Necessário porque entidades JPA usam `@Table(schema = "neuroassist_global")` ou
  `@Table(schema = "emp_<empresa>")` mas Testcontainers PostgreSQL só tem `public` por default.
  Referenciado via `.withInitScript("init-schema.sql")` em `BaseIntegrationTest`.

---

## 25) JPA Entity -- UUID assigned no domain (canônico)

```java
// ✅ CORRETO
@Entity @Table(name = "cliente")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class ClienteJpaEntity {
    @Id private UUID id;
    @Version private Long version;   // nullable, sem @Builder.Default
    // ...
}

// ❌ ERRADO -- as 3 permutações produzem falhas de CREATE
@Id @UuidGenerator  // conflito 1: pre-set UUID + generator = "detached"
@Version private Long version;

implements Persistable<UUID> {       // conflito 2: Persistable + @Version
    @Builder.Default boolean isNew = true;
}

// toEntity()...
.builder().id(r.id()).version(0L).build()  // conflito 3: "detached entity passed to persist"
```

- Refs: `hexagonal-patterns.md` §4b (regra canônica).

---

## CI Recommendations

- ArchUnit em test deps → `architectureCheck` bloqueante.
- Testcontainers para todos integration jobs (PostgreSQL + Artemis embedded).
- WireMock para stubs HTTP externos em integration e BDD.
- MapStruct processor em `annotationProcessor`.

## PR Checklist

- [ ] Ports em `application/port/out` ou `application/port/in`.
- [ ] Adapters em `infrastructure/persistence/adapter` ou `infrastructure/clients`.
- [ ] Sem `@Transactional` em controllers ou adapters.
- [ ] DTOs usados para API; entities nunca retornadas direto.
- [ ] `EmpresaContextHolder.require()` chamado em application services.
- [ ] ArchUnit tests verdes (`./gradlew :<service>:architectureCheck`).
- [ ] Integration tests usam Testcontainers (não H2).
- [ ] Artemis embedded em tests, não Testcontainers Artemis.
- [ ] Pacote base `com.neuroassist.<module>` (RULE-09).

## Referências

- `hexagonal-patterns.md` -- racional completo
- `platform-patterns.md` -- JMS envelope, Feign, multi-schema
- `testing-conventions.md` -- pirâmide completa
- `services-catalog.md` -- catálogo de microserviços
