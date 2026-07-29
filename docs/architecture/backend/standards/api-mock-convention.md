# API v0 Mock Convention (NeuroAssist AI Backend)

> Convenção uniforme: todo microserviço deve implementar uma superfície `/api/v0/`
> com dados mockados, ativada via Spring profile `mock`. Objetivo: desbloquear FE cedo,
> permitir trabalho paralelo, prover API previsível em dev-time que espelhe contratos reais.

## Princípios

- **Atrito mínimo:** mocks rodam com 1 flag de profile ou comando.
- **Contract-first:** endpoints mock seguem o contrato (request/response shapes) definido nos SPECs.
- **Isolado:** confinado ao profile Spring `mock` -- nunca em produção.
- **Discoverable:** dados mock em path previsível na árvore do projeto.

---

## 1. Onde colocar mocks

```
src/main/java/com/neuroassist/<module>/
└── mock/                        # @Profile("mock") controllers + support code

src/main/resources/
└── mocks/
    └── api/
        └── v0/
            └── <domain>/        # JSON fixture files
```

Exemplo: `src/main/resources/mocks/api/v0/<resource>/get-123.json`

---

## 2. Como habilitar

- Ativação default via Spring profile `mock` apenas.
- Nunca inclua `mock` em `prod` ou `staging`.
- Nunca habilite `mock` em deploys de produção.

**Run local (Gradle Kotlin DSL):**
```powershell

.\gradlew ::bootRun -Dspring.profiles.active=mock
```

**Tarefa de conveniência opcional em `build.gradle.kts`:**
```kotlin
tasks.register<JavaExec>("bootRunMock") {
    group = "application"
    description = "Run with mock profile for frontend development"
    mainClass.set("org.springframework.boot.loader.launch.JarLauncher")
    systemProperty("spring.profiles.active", "mock")
    classpath = sourceSets["main"].runtimeClasspath
}
```

---

## 3. Regras de design

- Path prefix: `/api/v0/...`
- Espelhe os paths e shapes reais. Se SPEC define `/api/v1/<resource>`, crie `/api/v0/<resource>` com o mesmo DTO shape (mas conteúdo mockado).
- Forneça respostas canned em JSON sob `mocks/api/v0/` carregadas pelos controllers mock.
- Suporte latência simulada e error injection opcional via properties.
- Anote `@Profile("mock")` -- nunca ativo por default.

---

## 4. Exemplo de mock controller

```java
@RestController
@RequestMapping("/api/v0/<resource>")
@Profile("mock")
@RequiredArgsConstructor
@Slf4j
public class <Resource>MockController {

    private final ResourceLoader loader;
    @Value("${mock.response.delay.ms:0}")
    private long delayMs;
    @GetMapping("/{id}")
    public ResponseEntity<String> get(@PathVariable String id) throws IOException, InterruptedException {
        if (delayMs > 0) Thread.sleep(delayMs);
        Resource r = loader.getResource("classpath:mocks/api/v0/<resource>/get-" + id + ".json");
        if (!r.exists()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }
    @GetMapping
    public ResponseEntity<String> list() throws IOException {
        Resource r = loader.getResource("classpath:mocks/api/v0/<resource>/list-page-1.json");
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }
}
```

---

## 5. Organização de dados mock

```
src/main/resources/mocks/api/v0/
├── <resource-a>/
│   ├── get-123.json
│   ├── list-page-1.json
│   └── list-page-2.json

├── empresas/
│   └── get-empresa-<id>.json

└── <resource-b>/
    └── ...
```

---

## 6. `application-mock.yml` -- latência e error injection

```yaml
mock:
  response:
    delay:
      ms: 200          # latência de rede simulada
    error:
      rate: 0.0        # fração 0..1 chance de retornar 500 (chaos testing)

spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          # bypass JWT validation no profile mock
          issuer-uri: http://localhost:9999/mock-issuer

# Origens locais permitidas no profile mock
cors:
  allowed-origins: "http://localhost:4200,http://localhost:3000"

# Empresa mock default (multi-empresa)
neuroassist-ai:
  mock:
    empresa-default: empresa-default

```

---

## 7. Security Guard -- MockGuard

**Obrigatório em todo serviço que implementa o profile mock:**

```java
@Configuration
@Profile("mock")
class MockGuard {

    @PostConstruct
    void assertNotProduction() {
        String env = System.getenv("ENV");
        if ("prod".equals(env) || "production".equals(env)) {
            throw new IllegalStateException(
                "mock Spring profile must never be active in production. ENV=" + env);
        }
    }
}
```

---

## 8. Testes de mock controllers

- Inclua unit tests para validar carregamento de fixtures.
- CI deve garantir que `mock` não está no artefato de produção.
- Adicione smoke test que sobe o serviço com profile `mock` e valida endpoints-chave retornam 200.

---

## 9. Multi-empresa em mock

- O profile `mock` deve respeitar `EmpresaContextHolder` (mesmo enforcement de RULE-07).
- Endpoints mock leem `X-Empresa-Id` do header e populam o contexto antes de retornar fixture.
- Fixtures podem ter sufixo por empresa: `get-123-<empresa-a>.json`, `get-123-<empresa-b>.json`.
- Sem header `X-Empresa-Id`, use `neuroassist-ai.mock.empresa-default` do `application-mock.yml`.

---

## 10. PR Checklist para mock

- [ ] Endpoints mock existem sob `/api/v0/` espelhando o contrato SPEC.
- [ ] Fixtures em `src/main/resources/mocks/api/v0/`.
- [ ] Código mock anotado com `@Profile("mock")`.
- [ ] `application-mock.yml` com configuração específica.
- [ ] `MockGuard` implementado e bloqueia em ENV produção.
- [ ] Instruções de run local documentadas no README do serviço.
- [ ] Shapes alinhados com SPECs em `docs/architecture/backend/services/<service>/SPEC-*.md`.
- [ ] Multi-empresa respeitada (header ou default).

---

## Referências

- `docs/architecture/backend/standards/hexagonal-patterns.md`
- `docs/architecture/backend/services/<service>/SPEC-*.md` (contratos por serviço)
- `docs/architecture/shared/multi-empresa.md`
