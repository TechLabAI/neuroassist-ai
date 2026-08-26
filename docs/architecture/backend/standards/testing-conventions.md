# Testing Conventions (NeuroAssist AI Backend)

> Pirâmide de testes: Unit · Integration · BDD/Cucumber · CDC/Pact.

## Propósito

Define as convenções de teste para todos os microserviços `backend/services/`. Prescreve
pirâmide, layout de pastas, dependências, lifecycle (Testcontainers / WireMock), BDD com
Cucumber, CDC com Pact, estrutura de tasks Gradle e PR checklist.

## Escopo

- Aplica-se a todos os serviços `backend/services/`.
- Unit tests: JUnit 5 + Mockito (sem Spring context, sem banco).
- Integration tests: Testcontainers (PostgreSQL + ActiveMQ Artemis reais) + Spring Boot context.
- BDD / Acceptance: Cucumber + JUnit 5 -- obrigatório para mudanças de API.
- CDC: Pact JVM -- verificação contrato consumer-provider.

---

## 1. Pirâmide de testes

| Camada | Ferramentas | Escopo | Gradle task |
|---|---|---|---|
| Unit | JUnit 5 · Mockito · AssertJ | Lógica de domínio -- sem Spring, sem DB | `test` |
| Integration | JUnit 5 · Testcontainers · SpringBootTest | Persistência, messaging, Feign | `integrationTest` |
| BDD | Cucumber · JUnit 5 · Testcontainers | Contratos comportamentais de API | `bddTest` |
| CDC | Pact JVM | Contratos HTTP consumer-provider | `contractTest` |

---

## 2. Layout de diretórios de teste

```
src/test/
├── java/com/neuroassist/<module>/
│   ├── unit/                        # @Tag("unit") -- Java puro, sem Spring
│   │   └── domain/                  # Testes de domain model + domain service
│   ├── integration/                 # @Tag("integration") -- BaseIntegrationTest
│   │   └── infrastructure/          # Persistência, messaging adapters
│   ├── cucumber/
│   │   ├── steps/                   # Step definitions (finas -- delegam para support)
│   │   ├── context/                 # ScenarioContext (state entre steps)
│   │   └── config/                  # @CucumberContextConfiguration
│   └── contract/                    # @Tag("contract") -- Pact consumer/provider
└── resources/
    ├── features/                    # .feature files por domínio
    │   └── <domain>/
    │       └── <resource>-<capability>.feature
    ├── fixtures/                    # SQL fixtures e dados de teste
    ├── mocks/                       # WireMock stub mappings
    └── application-test.yml         # Spring config específica de test
```

---

## 3. Unit Tests

**Regras:**
- Anote com `@Tag("unit")`.
- Sem `@SpringBootTest` -- JUnit 5 + Mockito puros.
- Teste exclusivamente `domain.model` e `domain.service`.
- Sem banco, sem fila, sem HTTP.
- Cada teste completa em milissegundos.

**Exemplo:**
```java
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class <Resource>DomainServiceTest {
    @Test
    void shouldRejectInvalidInput() {
        var service = new <Resource>DomainService();
        assertThatThrownBy(() -> service.validar(invalidInput()))
            .isInstanceOf(ValidationException.class);
    }
}
```

---

## 4. Integration Tests com Testcontainers

**Regras:**
- Anote com `@Tag("integration")`.
- Estenda `BaseIntegrationTest` (Testcontainers compartilhado).
- Use PostgreSQL real via Testcontainers -- **nunca H2 in-memory**.
- Use `@DynamicPropertySource` para injetar props de conexão.
- ActiveMQ Artemis **embedded** (`spring.artemis.mode=embedded`) -- nunca container Artemis.

**BaseIntegrationTest (singleton container pattern -- obrigatório):**

> **IMPORTANTE:** use singleton container (static initializer), NÃO `@Testcontainers` + `@Container`.
> A extensão `@Testcontainers` do JUnit 5 tem race condition com `@DynamicPropertySource` --
> a porta é lida antes do container subir.

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

Infraestrutura adicional de teste por serviço:

- `src/test/resources/init-schema.sql` -- cria os schemas necessários
- `src/test/resources/application-test.yml` -- ver `snippets.md`
- `src/test/java/.../config/TestSecurityConfig.java` -- ver `snippets.md`

---

### 4.1 O schema do teste é o schema de produção (E2E-022)

**`ddl-auto: validate`, nunca `create-drop`.** As migrations reais são aplicadas ao container
antes de o Spring subir — exatamente o que o deploy faz.

Com `create-drop` quem desenha o schema nos testes é o **Hibernate, a partir das entities**,
enquanto em produção quem desenha é o **Flyway**. As entities concordam consigo mesmas por
construção, então nenhuma divergência com as migrations é detectável. O projeto pagou duas vezes:

- **C-12** — `hist_outbox.status` era ENUM nativo no Flyway e `String` na entity. O Postgres não
  faz coerção implícita de varchar para ENUM em INSERT, então *toda* gravação no outbox falhava em
  produção. Nos testes a coluna nascia varchar e o ENUM nunca entrava em cena.
- **E2E-017** — o evnt-service quase mapeou a `hist_outbox` do pac-service. Os serviços
  compartilham o schema do tenant, e o nome estava tomado. O IT rodava contra PostgreSQL real e
  não viu: a tabela do PAC não existia lá, porque quem criava o schema era a entity.

Referência de implementação: `pac-service/src/test/java/.../integration/SchemaMigrations.java` e o
bloco estático de `BaseIntegrationTest`.

```java
POSTGRES.start();
// Global antes de empresa, como no deploy: o template de empresa pode referenciar o
// registro global, e o inverso não.
SchemaMigrations.applyTo(POSTGRES, List.of("empresa-abc", "empresa-xyz", "t_emp_test"));
```

**Quatro detalhes que custam tempo se descobertos na marra:**

1. **A URL do Testcontainers já vem com query string** (`?loggerLevel=OFF`). Concatenar um segundo
   `?` faz o driver ignorar o parâmetro em silêncio — o `currentSchema` some e a validação falha
   por um motivo que não é o real.
2. **`currentSchema` precisa ter um schema de tenant à frente do global.** Com multi-tenancy o
   Hibernate valida contra a conexão de bootstrap, que não tem tenant vinculado; sem o schema de
   tenant no `search_path` ele reprova por não achar as tabelas, e não por divergência de
   mapeamento — que é o que se quer medir.
3. **O placeholder `hist_service_role`** precisa ser o usuário do container. Em produção é
   `neuroassist`; sem sobrescrever, `V0006__hist_patient_encryption_key.sql` falha com
   `role "neuroassist" does not exist` e o teste passa a medir a ausência de uma role.
4. **`outOfOrder(true)`** é obrigatório: as faixas de V por módulo (PAC V0200-V0299, GEST
   V0400-V0499, …) fazem toda migration nova de faixa baixa ser, por construção, out of order.

**As migrations são lidas do diretório, não do classpath.** Elas vivem noutro módulo Gradle;
depender dele traria a aplicação inteira para o classpath de teste. Apontar para o diretório
mantém uma única fonte — os testes leem exatamente os arquivos que o deploy aplica.

**O que esperar ao ligar isto num serviço.** Toda falha nova é um achado:

| Sintoma | O que significa |
|---|---|
| `Schema-validation: missing table/column` | entity e migration divergem — descubra qual lado está errado antes de mexer |
| Violação de constraint numa fixture | a fixture descreve um estado que produção rejeita |

No PAC o segundo caso apareceu de imediato: `ux_comando_active_mapping` é um índice único
**parcial** que proíbe dois comandos ativos com o mesmo mapeamento por paciente, e uma fixture
criava quatro. O Hibernate nunca criava esse índice. Corrigiu-se a **fixture**, não a migration.

## 5. BDD / Acceptance Tests (Cucumber + JUnit 5)

**Regras:**
- Obrigatório para qualquer PR que muda API surface (controllers, DTOs, OpenAPI specs).
- Feature files são spec canônica do comportamento; alinhados aos SPECs em `docs/architecture/backend/services/<service>/`.
- Step definitions devem ser finos -- delegar lógica pesada para `cucumber/context/` helpers.

**Localização do feature file:**
```
src/test/resources/features/<domain>/<resource>-<capability>.feature
```

Exemplo: `features/<module>/<resource>-criacao.feature`

**Step definitions:**
```
src/test/java/com/neuroassist/<module>/cucumber/steps/<Resource>Steps.java
```

**Convenções Gherkin:**
- Linguagem de negócio alinhada aos SPECs (evitar detalhes de implementação).
- Tags: `@smoke`, `@integration`, `@slow`, `@wip`, `@idempotency`, `@pending`, `@multi-empresa`.
- Nomes de cenário: frase curta descrevendo comportamento de negócio.
- **`@pending`:** obrigatório para cenários cujos steps dependem de serviço externo
  não implementado. Excluídos do run default via `not @pending`, mas ficam como documentação.
  Quando o serviço estiver pronto, remova `@pending` e implemente os steps.

- **`@multi-empresa`:** para cenários que validam isolamento entre empresas.

```gherkin
@pending
Scenario: Cenário que depende de serviço não implementado
  Given precondição
  When ação que chama serviço externo
  Then resultado esperado
```

**CucumberIT runner deve excluir `@pending`:**
```java
@ConfigurationParameter(
    key = Constants.FILTER_TAGS_PROPERTY_NAME,
    value = "not @pending"
)
```

**Exemplo Gherkin (em português):**
```gherkin
# language: pt
Funcionalidade: Cadastro de <recurso>

  Contexto:
    Dado que o empresa "<empresa-id>" está ativo
    E o usuário "admin@example.com" está autenticado
  @smoke
  Cenário: Criação bem-sucedida de <recurso>
    Dado uma requisição válida de criação de <recurso>
    Quando o cliente envia a requisição para POST /api/v1/<resource>
    Então o status de resposta é 201
    E o campo "id" é retornado

```

**Runner class:**
```java
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
    value = "com.neuroassist.<module>.cucumber")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
    value = "json:build/cucumber-report.json")
class CucumberRunner {}
```

---

## 6. CDC Tests (Pact JVM)

**Regras:**
- Use Pact JVM para verificação consumer-provider.
- Consumer-side: define Pact no test do consumer e publica no Pact Broker.
- Provider-side: verifica Pact no CI contra provider rodando.
- Anote com `@Tag("contract")`.

**Consumer test (stub provider com Pact):**
```java
@Tag("contract")
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "")
class <Resource>ClientPactTest {
    @Pact(consumer = "")
    RequestResponsePact get<Resource>ByIdPact(PactDslWithProvider builder) {
        return builder.given("<resource> exists")
            .uponReceiving("get <resource> by id")
                .path("/api/v1/<resource>/uuid-001")
                .method("GET")
                .headers(Map.of("X-Empresa-Id", "<empresa-id>"))
            .willRespondWith()
                .status(200)
                .body(LambdaDsl.newJsonBody(b ->
                    b.stringType("id", "uuid-001")
                     .stringType("nome", "Exemplo")).build())
            .toPact();
    }
}
```

---

## 7. Gradle Kotlin DSL -- Tasks e dependências

```kotlin
val integrationTestSourceSet = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
}

dependencies {
    // Unit
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Integration + BDD (infra compartilhada)
    val itImpl by configurations.getting
    itImpl("org.testcontainers:postgresql:1.20.0")
    itImpl("io.cucumber:cucumber-junit-platform-engine:7.18.0")
    itImpl("io.cucumber:cucumber-java:7.18.0")
    itImpl("io.cucumber:cucumber-spring:7.18.0")
    itImpl("org.wiremock:wiremock-standalone:3.9.1")
    // CDC
    itImpl("au.com.dius.pact.consumer:junit5:4.6.5")
}

tasks.register<Test>("integrationTest") {
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
}

tasks.register<Test>("bddTest") {
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    useJUnitPlatform { includeTags("bdd") }
}

tasks.register<Test>("contractTest") {
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    useJUnitPlatform { includeTags("contract") }
}

// unit-only (rápido) -- task default
tasks.test {
    useJUnitPlatform { includeTags("unit") }
}
```

---

## 8. CI Enforcement: hard-fail para mudanças de API

- Qualquer PR modificando API surface (controllers, OpenAPI, DTOs) deve incluir/atualizar
  o feature file correspondente em `src/test/resources/features/`.
- Job CI `feature-coverage-check` mapeia controllers modificados para feature paths esperados
  e falha quando não há feature correspondente.
- Opt-out label: `no-bdd-required` -- aplicado explicitamente e revisado.

**SPEC → Feature mapping** (manter em `docs/architecture/backend/standards/bdd-mapping.md`):
```
SPEC-02-<resource>-criacao.md  → features/<module>/<resource>-criacao.feature
SPEC-11-idempotencia.md        → features/<module>/idempotencia.feature
```

---

## 9. Multi-empresa em testes

Toda BDD que toca dado de empresa DEVE ter ao menos 1 cenário `@multi-empresa` validando
isolamento:
- empresa-A cria dado → empresa-B não vê.
- empresa-A não consegue alterar dado de empresa-B (`<Parent>NotFoundException`).
- Troca de empresa ativa via `EmpresaContextHolder` em runtime de teste.

---

## 10. Reporting e evidência

- Cucumber plugin: `json:build/cucumber-report.json`.
- Anexe reports como artefatos CI em falha.
- Inclua Pact Broker URL no environment CI para publish/verify de contratos.

---

## 11. PR Checklist

- [ ] Mudanças de API incluem/atualizam feature files em `src/test/resources/features/`.
- [ ] Step definitions em `cucumber/steps/` e são finos -- lógica pesada em `cucumber/context/`.
- [ ] BDD tagged `@smoke` ou `@integration` rodam em CI.
- [ ] Pelo menos 1 cenário `@multi-empresa` se o serviço toca dado de empresa.
- [ ] Integration tests usam Testcontainers (PostgreSQL real), não H2.
- [ ] ArchUnit verde (`./gradlew :<service>:architectureCheck`).
- [ ] Unit tests (`./gradlew :<service>:test`) verdes com apenas `@Tag("unit")`.
- [ ] Se cobertura é intencionalmente omitida, PR inclui justificativa e label `no-bdd-required`.

---

## Referências

- [Cucumber-JVM](https://cucumber.io/)
- [Testcontainers](https://testcontainers.com/)
- [Pact JVM](https://docs.pact.io/implementation_guides/jvm)
- [WireMock](https://wiremock.org/)
- `hexagonal-patterns.md` -- regras de boundary que afetam testes
- `docs/architecture/shared/multi-empresa.md` -- isolamento entre empresas
