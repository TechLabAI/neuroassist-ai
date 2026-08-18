# hist-service

Hist — Serviço de domínio NeuroAssist AI tool.

## Stack

- Java 21 (eclipse-temurin:21)
- Spring Boot 3.5.11 + Gradle 8.x
- PostgreSQL 15+ (schema-per-tenant: `t_<tenantId>`, migrations em neuroassist-ai-migrations)
- ActiveMQ Artemis
- Spring Cloud ACS (Eureka + OpenFeign + Resilience4j)

## Como buildar

```bash
./gradlew :hist-service:build
./gradlew :hist-service:architectureCheck  # bloqueante — rodar antes de commit
```

## Como testar

```bash
./gradlew :hist-service:test              # unitários (sem Docker)
./gradlew :hist-service:integrationTest   # integração (requer Docker)
```

## Como rodar localmente

```bash
docker compose up -d
./gradlew :hist-service:bootRun --args='--spring.profiles.active=local'
```

Disponível em: `http://localhost:8089`

## Referências

- [Setup Local](../../docs/architecture/tools/hist-service/setup-local.md)
- [Service Spec](../../docs\architecture\backend\services\hist-service\hist-service-spec.md)
- [ADRs](../../docs/architecture/tools/hist-service/adr/README.md)
- [CLAUDE.md](../../CLAUDE.md)
