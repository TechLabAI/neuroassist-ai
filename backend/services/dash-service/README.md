# dash-service

Dash — Serviço de domínio NeuroAssist AI tool.

## Stack

- Java 21 (eclipse-temurin:21)
- Spring Boot 3.5.11 + Gradle 8.x
- PostgreSQL 15+ (schema-per-tenant: `t_<tenantId>`, migrations em neuroassist-ai-migrations)
- ActiveMQ Artemis
- Spring Cloud ACS (Eureka + OpenFeign + Resilience4j)

## Como buildar

```bash
./gradlew :dash-service:build
./gradlew :dash-service:architectureCheck  # bloqueante — rodar antes de commit
```

## Como testar

```bash
./gradlew :dash-service:test              # unitários (sem Docker)
./gradlew :dash-service:integrationTest   # integração (requer Docker)
```

## Como rodar localmente

```bash
docker compose up -d
./gradlew :dash-service:bootRun --args='--spring.profiles.active=local'
```

Disponível em: `http://localhost:8090`

## Referências

- [Setup Local](../../docs/architecture/tools/dash-service/setup-local.md)
- [Service Spec](../../docs\architecture\backend\services\dash-service\dash-service-spec.md)
- [ADRs](../../docs/architecture/tools/dash-service/adr/README.md)
- [CLAUDE.md](../../CLAUDE.md)
