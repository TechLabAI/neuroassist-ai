# EVNT-001 -- Scaffold Gradle project evnt-service (hexagonal layout + port interfaces)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/7 itens

## Contexto
Initializes the `evnt-service` Gradle sub-project inside the NeuroAssist AI monorepo back-end.
Establishes the hexagonal package layout (`api`, `application`, `domain`, `infrastructure`) and declares
all port interfaces derived from `generator-config.yml` — so that every downstream issue has stable
contracts to implement against. No business logic in this issue; this is structural scaffolding only.

## Spec de referencia
- SPEC-01 (API surface — controllers that will live in `api`)
- SPEC-02 (notification orchestration — use cases and port names)

## Escopo
- [ ] Add `evnt-service` to `backend/settings.gradle.kts` (include) and `build.gradle.kts` (dependencies block)
- [ ] Create hexagonal package tree: `com.neuroassist.evnt.{api,application,domain,infrastructure}`
- [ ] Declare port interfaces: `GestValidationPort`, `CommandLookupPort`, `CaregiverRoutingPort`, `NotificationDispatchPort`, `AuditTrailPort`, `SseEventBroadcastPort`, `EventoRepositoryPort`, `DispatchRepositoryPort`, `IdempotencyRepositoryPort`
- [ ] Create `EvntServiceApplication.java` with `@SpringBootApplication`
- [ ] Add `src/main/resources/application.yml` with `server.port: 8085`, Eureka registration stubs, and `EVNT_*` env var references from `setup-local.md`
- [ ] Verify `./gradlew :services:evnt-service:compileJava` exits 0
- [ ] Verify `./gradlew :services:evnt-service:test` exits 0 (no tests yet — just empty run)

## Resultado esperado
A compilable, empty Spring Boot application registered as `evnt-service` in Eureka, with all port
interfaces declared and no business logic yet.

## Criterios de aceite
- [ ] `./gradlew :services:evnt-service:compileJava` exits 0 with no warnings on missing symbols
- [ ] All nine port interfaces exist in `domain` or `application` package; none have implementation stubs
- [ ] `application.yml` exposes `server.port: 8085` and references every env var listed in `setup-local.md`
- [ ] No Flyway or JPA auto-run at startup (`spring.flyway.enabled=false`, `spring.jpa.hibernate.ddl-auto=none`)

## Dependencias
Nenhuma — first issue in the chain.

## Estimativa
M

## Prioridade
P1
