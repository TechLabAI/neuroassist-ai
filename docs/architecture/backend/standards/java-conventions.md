# Java Conventions (NeuroAssist AI Backend)

> Convenções de código Java 21 + Spring Boot para todos os microserviços em `backend/services/`.

## Lombok

- Use `@Value` + `@Builder` para value objects imutáveis.
- Use `@RequiredArgsConstructor` para DI (constructor injection é obrigatório).
- Use `@Slf4j` para logging (nunca instanciar Logger diretamente).
- Nunca use `@Data` em entidades JPA (quebra equals/hashCode com lazy loading e causa problemas de serialização).
- Para entidades JPA, implemente `equals`/`hashCode` apenas em identificadores imutáveis (id), nunca em coleções ou campos mutáveis.

## SLF4J / Logging

- Sempre via Lombok `@Slf4j`.
- Nunca `System.out` para logging.
- Nunca instanciar Logger manualmente.
- Use placeholders (`{}`) para interpolação, nunca concatenação de string.
- Prefira structured logging (campos separados) sobre interpolação.
- Níveis: `info` para eventos normais, `warn` para situações inesperadas, `error` para falhas, `debug` para detalhes de desenvolvimento.
- Nunca exponha dados sensíveis em logs.

## Spring Dependency Injection

- Sempre constructor injection.
- Nunca `@Autowired` em campo ou método.
- `@RequiredArgsConstructor` é o padrão para services e components.
- Prefira beans imutáveis quando possível.

## JPA Entities

- Sempre declare `@Getter`/`@Setter` explicitamente.
- `@Version` obrigatório para optimistic locking quando aplicável.
- Nunca serialize entidades JPA direto para HTTP (use DTOs).
- Construtor protected no-args para JPA.
- Evite lógica de negócio em entidades; mantenha como modelo de dados.
- Prefira Java records para DTOs (imutáveis, compactos).

## Naming Conventions

- Classes: PascalCase (`ClienteService`).
- Métodos e variáveis: camelCase (`calcularTotal`).
- Constantes: UPPER_SNAKE_CASE.
- Pacotes: lowercase curto, sem underscores.

## Organização de pacotes (canônica -- todos os serviços novos)

```
com.neuroassist.<module>/
├── api/           → controllers, DTOs, exception handlers
├── application/   → services, port.in, port.out
├── domain/        → model (POJO), domain services (zero Spring)
└── infrastructure/ → persistence, clients, messaging, config, security
```

`<module>` é o nome do módulo (lowercase) — um dos prefixos listados em
`services-catalog.md` (`gest`, `evnt`, `pac`, `acs`, `hist`, ...).

Ver `hexagonal-patterns.md` para detalhes por camada.

## Optional

- Use `Optional` para tipos de retorno que podem ausentar; nunca para parâmetros ou campos de entidade.
- Nunca retorne `null` em vez de `Optional`.

## Exception Handling

- Exceções de domínio em `domain.exception`.
- Mapeie exceções de domínio para HTTP em `@RestControllerAdvice` localizado em `api.exception`.
- Use `ProblemDetail` (RFC 7807) para respostas padronizadas.
- Adapters podem lançar exceções infra-específicas; Application Services traduzem para domain.
- Nunca capture exceções genéricas sem tratamento apropriado.

## Imutabilidade e Thread Safety

- Beans singleton devem ser thread-safe.
- Prefira objetos imutáveis para dados compartilhados.

## Documentação

- Javadoc em classes/métodos públicos.
- Documente `@param` e `@return`.

## Configuração externa e Profiles

- Use `application.yml` + Spring profiles (`@Profile`) para separar ambientes (dev, test, prod, mock).
- Nunca commitar segredos, senhas ou chaves.
- Use variáveis de ambiente -- `application.yml` usa sintaxe `${VAR_NAME}`.

## Validação

- Use Bean Validation (`@Valid`, `@NotNull`, `@Size`, etc.) em DTOs e request bodies.
- Validação de entrada em controllers; Application Services adicionam validações de negócio.

## DTOs e Mapeamento

- Sempre use DTOs para input/output API; nunca retorne entidades JPA direto.
- Use MapStruct para mapeamento performático entre Entity ↔ Domain ↔ DTO.
- Adicione MapStruct processor em `annotationProcessor` no `build.gradle.kts`.

## Global Exception Handling

- `@RestControllerAdvice` para handling centralizado e respostas padronizadas.
- Nunca exponha stack traces ou detalhes internos em respostas de erro.

## Logging Configuration

- Configure via `application.yml`.
- Use Logback com `logstash-logback-encoder` para JSON estruturado em produção.
- Pattern de log deve incluir `correlationId` e `empresaId` do MDC.

## API Documentation

- Use SpringDoc OpenAPI para documentar REST APIs.
- Anote DTOs e endpoints com OpenAPI annotations.
- OpenAPI gerado por cada serviço alimenta `docs/architecture/shared/contratos/<service>-openapi.yaml`.

## Testing

- **Unit tests:** JUnit 5 + Mockito -- sem Spring context, sem banco.
- **Integration tests:** Testcontainers com PostgreSQL real -- nunca H2 in-memory.
- **BDD tests:** Cucumber + JUnit 5 para cobertura comportamental/API.
- **CDC tests:** Pact JVM para verificação contrato consumer-provider.
- Use `@ActiveProfiles("test")` e `application-test.yml`.
- Ver `testing-conventions.md` para convenções completas.

## Transactions

- `@Transactional` apenas em Application Services (RULE-03).
- Nunca em controllers ou infrastructure adapters.
- Propagação explícita (`REQUIRES_NEW`) apenas quando justificado.

## Performance

- `@Cacheable` onde aplicável.
- Monitore N+1; otimize fetch strategies.
- `spring.jpa.open-in-view: false` obrigatório (RULE-08).

## Security

- Nunca exponha endpoints sem autenticação (exceto `/actuator/health` e `/actuator/prometheus`).
- Spring Security com JWT resource server (`oauth2ResourceServer.jwt`).
- Nunca exponha dados sensíveis em logs ou respostas.

## API Versioning

- Versione endpoints REST (ex.: `/api/v1/`). Breaking changes exigem nova versão.
- Endpoints mock/dev usam `/api/v0/` (ver `api-mock-convention.md`).

## Feign Clients (inter-service HTTP)

- Declare como interfaces com `@FeignClient(name = "service-name")`.
- Localize em `infrastructure.clients` (RULE-06).
- Constructor injection via `@RequiredArgsConstructor`.
- Aplique Resilience4j `@CircuitBreaker` e `@Retry` em métodos de service que chamam Feign.
- Nunca chame Feign clients de `domain` ou `api`.

## Multi-empresa

- Toda Application Service que toca dado de empresa deve começar com `EmpresaContextHolder.require()` (RULE-07).
- Detalhes em `docs/architecture/shared/multi-empresa.md`.

## ResponseEntity

- Prefira `ResponseEntity<T>` para respostas HTTP customizadas em controllers.

## Referências

- `hexagonal-patterns.md` -- Ports & Adapters, layout de pacotes
- `snippets.md` -- código reference por tipo de classe
- `platform-patterns.md` -- JMS, Feign, Flyway, MDC, Saga, multi-schema
- `testing-conventions.md` -- pirâmide de testes
- `rbac.md` -- modelo de papéis e permissões
- `services-catalog.md` -- catálogo de microserviços + pacotes canônicos
