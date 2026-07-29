# evnt-service

NeuroAssist AI — Serviço de domínio NeuroAssist AI tool.

## Stack

- Java 21 (eclipse-temurin:21)
- Spring Boot 3.5.11 + Gradle 8.x
- PostgreSQL 15+ (schema-per-tenant: `t_<tenantId>`, migrations em neuroassist-ai-migrations)
- ActiveMQ Artemis
- Spring Cloud EVNT (Eureka + OpenFeign + Resilience4j)

## Como buildar

```bash
./gradlew :evnt-service:build
./gradlew :evnt-service:architectureCheck  # bloqueante — rodar antes de commit
```

## Como testar

```bash
./gradlew :evnt-service:test              # unitários (sem Docker)
./gradlew :evnt-service:integrationTest   # integração (requer Docker)
```

## Como rodar localmente

```bash
docker compose up -d
./gradlew :evnt-service:bootRun --args='--spring.profiles.active=local'
```

Disponível em: `http://localhost:8085`

## Referências

- [Setup Local](../../docs/architecture/tools/evnt-service/setup-local.md)
- [Service Spec](../../docs\architecture\backend\services\evnt-service\evnt-service-spec.md)
- [ADRs](../../docs/architecture/tools/evnt-service/adr/README.md)
- [CLAUDE.md](../../CLAUDE.md)
