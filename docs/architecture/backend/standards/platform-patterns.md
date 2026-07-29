# Platform Patterns (NeuroAssist AI Backend)

> JMS Artemis, OpenFeign + Resilience4j, Flyway centralizado, MDC/CorrelationId,
> multi-schema (empresa), Saga choreography, Eureka.

## Propósito

Padrões cross-cutting que todos os microserviços `backend/services/` devem implementar.
Garante consistência operacional, observabilidade e resilência.

---

## 1. ActiveMQ Artemis -- Padrões JMS

### 1.1 Broker e protocolo

- Broker: **ActiveMQ Artemis** (não ActiveMQ Classic).
- Protocolo: **AMQP 1.0** -- configurar em `application.yml`:

```yaml
spring:
  artemis:
    mode: native            # native quando broker dedicado; embedded para dev/testes
    broker-url: ${ARTEMIS_URL}
    user: ${ARTEMIS_USER}
    password: ${ARTEMIS_PASSWORD}
  jms:
    pub-sub-domain: false   # queues, não topics
```

Em testes de integração: usar `mode: embedded` (NUNCA `org.testcontainers:activemq`).

### 1.2 Event Envelope (obrigatório)

Todos os eventos publicados devem conformar a este envelope JSON:

```java
// infrastructure/messaging/producer/EventEnvelope.java
public record EventEnvelope(
    String eventId,        // UUID v4 -- único por ocorrência
    String eventType,      // canonical name (ex.: "neuroassist-ai.<module>.<entity>.<event>")
    String occurredAt,     // ISO 8601 UTC
    String correlationId,  // do MDC -- trace cross-service
    String version,        // payload schema version (ex.: "1.0")
    String empresaId,      // sempre presente em eventos operacionais
    String userId,         // quando aplicável
    Object payload         // dados específicos do evento
) {}
```

**Uso:**
```java
public void publish(VendaConfirmadaPayload payload, String empresaId) {
    var envelope = new EventEnvelope(
        UUID.randomUUID().toString(),
        "neuroassist-ai.<module>.<entity>.<event>",
        Instant.now().toString(),
        MDC.get("correlationId"),
        "1.0",
        empresaId,
        SecurityContextHolder.getContext().getAuthentication().getName(),
        payload
    );
    jms.convertAndSend("neuroassist-ai.<module>.<entity>.<event>", envelope);
}
```

### 1.3 Producer Pattern

- Localização: `infrastructure/messaging/producer/`
- Use `JmsTemplate` injetado por construtor.
- Nunca produza eventos diretamente de Application Services; delegue a um publisher dedicado.

```java
// infrastructure/messaging/producer/VendaConfirmadaPublisher.java
@Component
@RequiredArgsConstructor
@Slf4j
class VendaConfirmadaPublisher {
    private final JmsTemplate jms;
    public void publish(VendaConfirmadaEvent event) {
        log.info("publishing neuroassist-ai.<module>.<entity>.<event> vendaId={} empresaId={}",
            event.vendaId(), event.empresaId());
        jms.convertAndSend("neuroassist-ai.<module>.<entity>.<event>", event.toEnvelope());
    }
}
```

### 1.4 Consumer Pattern (idempotente)

- Localização: `infrastructure/messaging/consumer/`
- Anotar com `@JmsListener(destination = "queue.name")`.
- Consumer deve ser idempotente -- check `processed` antes de agir.
- Setar `EmpresaContextHolder` explicitamente do envelope (ThreadLocal não propaga cross-thread).
- Limpar `EmpresaContextHolder` em `finally`.

```java
// infrastructure/messaging/consumer/VendaConfirmadaConsumer.java
@Component
@RequiredArgsConstructor
@Slf4j
class VendaConfirmadaConsumer {
    private final EmitirNFeUseCase emitirNFe;
    private final ProcessedEventPort processedEvents;
    @JmsListener(destination = "neuroassist-ai.<module>.<entity>.<event>")
    public void onVendaConfirmada(EventEnvelope envelope) {
        if (processedEvents.alreadyProcessed(envelope.eventId())) {
            log.warn("evento duplicado ignorado eventId={}", envelope.eventId());
            return;
        }
        MDC.put("correlationId", envelope.correlationId());
        EmpresaContextHolder.set(envelope.empresaId());
        try {
            emitirNFe.execute(EmitirNFeCommand.from(envelope));
            processedEvents.markProcessed(envelope.eventId());
        } finally {
            EmpresaContextHolder.clear();
            MDC.clear();
        }
    }
}
```

### 1.5 Event Catalog

Catálogo canônico de eventos em `docs/architecture/backend/standards/services-catalog.md` §6.

---

## 2. OpenFeign -- Inter-service HTTP

### 2.1 Setup

- Habilitar em main class:
  `@EnableFeignClients(basePackages = "com.neuroassist.<module>.infrastructure.clients")`
- Ou por módulo via `@EnableFeignClients` numa `@Configuration` em `infrastructure/config/`.

### 2.2 Interface Pattern

- Localização: `infrastructure/clients/` (RULE-06).
- Apenas interface -- Spring gera a implementação.
- Propagar `X-Correlation-Id`, `X-Empresa-Id`, `X-User-Id` via `RequestInterceptor`.

```java
// infrastructure/clients/CadastrosClienteClient.java
@FeignClient(name = "<provider-service>")
public interface CadastrosClienteClient {
    @GetMapping("/api/v1/clientes/{id}")
    ClienteDto getById(
        @PathVariable UUID id,
        @RequestHeader("X-Empresa-Id") String empresaId);
}
```

### 2.3 RequestInterceptor -- propagação de contexto

```java
// infrastructure/config/FeignContextInterceptor.java
@Component
class FeignContextInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        template.header("X-Correlation-Id", MDC.get("correlationId"));
        template.header("X-Empresa-Id",     MDC.get("empresaId"));
        template.header("X-User-Id",        MDC.get("userId"));
    }
}
```

### 2.4 Resilience4j em chamadas Feign

- Aplicar no método **Application Service** que chama o Feign port -- não na interface Feign.
- Configurar em `application.yml`:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      <provider-service>:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
  retry:
    instances:
      <provider-service>:
        maxAttempts: 2
        waitDuration: 200ms
  timelimiter:
    instances:
      <provider-service>:
        timeoutDuration: 2s
```

```java
@CircuitBreaker(name = "<provider-service>")
@Retry(name = "<provider-service>")
public Cliente buscarCliente(UUID id, String empresaId) {
    return cadastrosClienteClient.getById(id, empresaId);
}
```

---

## 3. Flyway -- Database Migrations (CENTRALIZADAS)

### 3.1 Estratégia (decidida em ADR-001 shared)

- **`neuroassist-ai-migrations`** é o único projeto que rodar Flyway.
- Microserviços individuais usam `spring.jpa.hibernate.ddl-auto=validate` apenas.
- Migrações nunca em projetos de serviço -- sempre em `neuroassist-ai-migrations/src/main/resources/db/migration/`.

### 3.2 Layout de migrações

```
backend/services/neuroassist-ai-migrations/src/main/resources/db/migration/
├── global/                              # aplica em neuroassist_global (uma vez)
│   ├── V1__iam_schema.sql               # IAM (usuário, papel, permissão, ...)
│   ├── V2__catalogos_fiscais.sql        # NCM, CFOP, CST, ...
│   ├── V3__plano_contas_master.sql
│   └── V4__empresas_seed.sql            # registra as empresas
├── empresa/                             # aplica em CADA emp_*
│   ├── V1__cadastros.sql                # CAD: cliente, fornecedor, produto, ...
│   ├── V2__comercial.sql                # COM: orçamento, venda, ...
│   ├── V3__fiscal.sql                   # FIS: nfe, cte, ...
│   ├── V4__financeiro.sql               # FIN: caixa, conta_pagar, ...
│   ├── V5__estoque.sql                  # EST: movimento_estoque, ...
│   ├── V6__crm.sql                      # CRM: lead, oportunidade, ...
│   ├── V7__logistica.sql                # LOG: entrega, frete, ...
│   └── V8__auditoria.sql                # AUD: evento (opcional, se cada empresa terá sua trilha)
```

### 3.3 Configuração

`neuroassist-ai-migrations/src/main/resources/application.yml`:
```yaml
spring:
  flyway:
    enabled: false           # control manual via Java (multi-schema)

neuroassist-ai:
  migrations:
    global-schema: neuroassist_global
    empresas:
      - <empresa-schema-1>
      - <empresa-schema-2>
      - <empresa-schema-3>
      - <empresa-schema-4>
      - <empresa-schema-5>
    global-location: classpath:db/migration/global
    empresa-location: classpath:db/migration/empresa
```

`MigrationsRunner.java`:
```java
@SpringBootApplication
public class NeuroAssist AIMigrationsApp implements CommandLineRunner {

    @Autowired private DataSource ds;
    @Autowired private NeuroAssist AIMigrationsConfig config;
    public static void main(String[] args) {
        SpringApplication.run(NeuroAssist AIMigrationsApp.class, args);
    }
    @Override
    public void run(String... args) {
        // 1. Global
        migrate(config.globalSchema(), config.globalLocation());
        // 2. Cada empresa
        for (String empresa : config.empresas()) {
            migrate(empresa, config.empresaLocation());
        }
    }
    private void migrate(String schema, String location) {
        log.info("Flyway migrate schema={}", schema);
        Flyway.configure()
            .dataSource(ds)
            .schemas(schema)
            .locations(location)
            .baselineOnMigrate(true)
            .load()
            .migrate();
    }
}
```

### 3.4 Regras

- Nunca modifique migração já aplicada em produção.
- Scripts idempotentes quando possível (`IF NOT EXISTS`, `IF EXISTS`).
- Para adicionar empresa nova: adicionar em `neuroassist-ai.migrations.empresas` e rerodar
  `neuroassist-ai-migrations` -- o Flyway aplicará todas as Vs no schema novo.
- `ddl-auto: validate` em TODOS os outros serviços.

---

## 4. MDC e CorrelationId -- Observabilidade

### 4.1 Campos MDC obrigatórios

Todo serviço propaga estes campos MDC em todo request:

| MDC key | Origem | Descrição |
|---|---|---|
| `correlationId` | `X-Correlation-Id` request header | Trace cross-service |
| `empresaId` | `X-Empresa-Id` request header | Empresa do contexto |
| `userId` | `X-User-Id` request header | Usuário atuante |
| `service` | static -- nome do serviço | Quem produziu o log |

### 4.2 CorrelationIdInterceptor (obrigatório)

```java
// infrastructure/security/CorrelationIdInterceptor.java
@Component
public class CorrelationIdInterceptor implements HandlerInterceptor {

    @Value("${spring.application.name}")
    private String serviceName;
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        String corrId = req.getHeader("X-Correlation-Id");
        if (corrId == null) corrId = UUID.randomUUID().toString();
        MDC.put("correlationId", corrId);
        MDC.put("empresaId",     req.getHeader("X-Empresa-Id"));
        MDC.put("userId",        req.getHeader("X-User-Id"));
        MDC.put("service",       serviceName);
        res.setHeader("X-Correlation-Id", corrId);    // ecoa para o caller
        return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res,
                                Object handler, Exception ex) {
        MDC.clear();
    }
}
```

Registrar em `WebMvcConfigurer`:
```java
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final CorrelationIdInterceptor correlationIdInterceptor;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(correlationIdInterceptor);
    }
}
```

### 4.3 Pattern de logging

```yaml
logging:
  pattern:
    console: "%d{ISO8601} [%X{correlationId}] [%X{empresaId}] [%X{service}] %-5level %logger{36} - %msg%n"
  level:
    com.neuroassist: INFO
    org.springframework.security: WARN
```

Em produção: `logstash-logback-encoder` para JSON estruturado.

---

## 5. Multi-Schema Tenancy (Empresa)

### 5.1 EmpresaContextHolder

Ver `hexagonal-patterns.md` §5 -- código canônico de `EmpresaContextHolder`.

### 5.2 Schema Routing (Hibernate)

Como os schemas NeuroAssist AI são **fixos** (1 global + 5 emp_*, não dinâmicos por cliente),
a abordagem mais simples é configurar `search_path` por conexão via interceptor JDBC:

```java
// infrastructure/persistence/EmpresaSearchPathInterceptor.java
@Component
@RequiredArgsConstructor
class EmpresaSearchPathInterceptor {
    @PostConstruct
    void registerHikariCallback() {
        // Configurar HikariCP setConnectionInitSql dinamicamente,
        // OU usar AbstractRoutingDataSource com 1 datasource por schema.
    }
}
```

**Alternativa preferida:** usar Hibernate `MultiTenantConnectionProvider` apontando para
um único DataSource e executando `SET search_path TO emp_<empresa>, neuroassist_global, public`
no início de cada session.

```java
// infrastructure/persistence/EmpresaConnectionProvider.java
@Component
@RequiredArgsConstructor
class EmpresaConnectionProvider implements MultiTenantConnectionProvider {

    private final DataSource ds;
    @Override
    public Connection getConnection(Object tenantId) throws SQLException {
        Connection c = ds.getConnection();
        try (Statement s = c.createStatement()) {
            s.execute("SET search_path TO " + tenantId + ", neuroassist_global, public");
        }
        return c;
    }
    @Override
    public void releaseConnection(Object tenantId, Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("SET search_path TO DEFAULT");
        }
        c.close();
    }
    // ... outros métodos obrigatórios
}
```

E `CurrentTenantIdentifierResolver`:
```java
@Component
class EmpresaIdentifierResolver implements CurrentTenantIdentifierResolver<String> {
    @Override
    public String resolveCurrentTenantIdentifier() {
        return EmpresaContextHolder.require();
    }
    @Override
    public boolean validateExistingCurrentSessions() { return false; }
}
```

`application.yml`:
```yaml
spring:
  jpa:
    properties:
      hibernate:
        multiTenancy: SCHEMA
        tenant_identifier_resolver: com.neuroassist.<module>.infrastructure.persistence.EmpresaIdentifierResolver
        multi_tenant_connection_provider: com.neuroassist.<module>.infrastructure.persistence.EmpresaConnectionProvider
```

---

## 6. Saga Choreography (Onboarding de Empresa Nova)

Cenário raro mas importante: cliente decide adicionar nova empresa ao grupo.

### 6.1 Steps

```
1. iam-service publish: neuroassist-ai.empresa.created (com empresa_id, nome, parametros)
2. neuroassist-ai-migrations consume: cria schema <new-empresa-schema> e roda todas as Vs (manual ou via job)
3. <provider-service> consume: importa catálogos compartilhados
4. fiscal-service consume: registra empresa na config SEFAZ
5. financeiro-service consume: cria plano de contas template
6. auditoria-service grava: neuroassist-ai.empresa.onboarding.completed
```

### 6.2 Compensação

- Se qualquer step falhar após commit, publica evento de compensação (`neuroassist-ai.empresa.onboarding.failed`).
- Cada consumer trata compensação e rollback local idempotente.
- Nunca transações distribuídas -- consistência eventual + consumers idempotentes.

---

## 7. Eureka -- Service Discovery

### 7.1 Registro de cliente (obrigatório)

```yaml
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://neuroassist-ai-service-registry:8761/eureka/}
    register-with-eureka: true
    fetch-registry: true
  instance:
    prefer-ip-address: true
    instance-id: ${spring.application.name}:${random.value}
```

### 7.2 Resolução de Feign

Com `@FeignClient(name = "service-name")`, Spring Cloud + Eureka resolve a instância
automaticamente. Sem URLs hardcoded em produção.

Para dev local sem Eureka:
```yaml
# application-local.yml
eureka.client.enabled: false
spring.cloud.openfeign.client.config.<provider-service>.url: http://localhost:8082
```

---

## 8. Checklist (Platform Patterns -- serviço novo)

- [ ] `CorrelationIdInterceptor` registrado em `WebMvcConfigurer`
- [ ] `EmpresaContextHolder` em `infrastructure/security/`
- [ ] `EmpresaFilter` (`OncePerRequestFilter`) popula e limpa contexto por request
- [ ] Todos os JMS producers usam `EventEnvelope`
- [ ] Todos os JMS consumers setam `EmpresaContextHolder` do envelope e limpam em `finally`
- [ ] Todos os JMS consumers são idempotentes (`processedEventPort.alreadyProcessed`)
- [ ] Feign clients propagam `X-Correlation-Id`, `X-Empresa-Id`, `X-User-Id` via `RequestInterceptor`
- [ ] Resilience4j CircuitBreaker + Retry aplicado na borda do application service
- [ ] `spring.jpa.hibernate.ddl-auto: validate` em `application.yml`
- [ ] `spring.jpa.open-in-view: false` em `application.yml` (RULE-08)
- [ ] Hibernate `multiTenancy: SCHEMA` + `EmpresaIdentifierResolver` configurado
- [ ] Eureka registration configurado
- [ ] Sem migration própria (delegada para `neuroassist-ai-migrations`)

---

## Referências

- `hexagonal-patterns.md` -- `EmpresaContextHolder` e cross-empresa guard
- `services-catalog.md` -- catálogo de microserviços e eventos
- `rbac.md` -- modelo RBAC + claims JWT
- `docs/architecture/shared/multi-empresa.md` -- ADR-001
- `docs/architecture/shared/distributed-architecture.md` -- topologia
