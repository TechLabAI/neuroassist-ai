# neuroassist-ai-migrations

NeuroassistAiMigrations — Serviço de domínio NeuroAssist AI tool.

## Stack

- Java 21 (eclipse-temurin:21)
- Spring Boot 3.5.11 + Gradle 8.x
- PostgreSQL 15+ (schema-per-tenant: `t_<tenantId>`, migrations em neuroassist-ai-migrations)
- ActiveMQ Artemis
- Spring Cloud MIG (Eureka + OpenFeign + Resilience4j)

## Como buildar

```bash
./gradlew :neuroassist-ai-migrations:build
./gradlew :neuroassist-ai-migrations:architectureCheck  # bloqueante — rodar antes de commit
```

## Como testar

```bash
./gradlew :neuroassist-ai-migrations:test              # unitários (sem Docker)
./gradlew :neuroassist-ai-migrations:integrationTest   # integração (requer Docker)
```

## Como rodar localmente

```bash
docker compose up -d
./gradlew :neuroassist-ai-migrations:bootRun --args='--spring.profiles.active=local'
```

Disponível em: `http://localhost:8093`

## Referências

- [Setup Local](../../docs/architecture/tools/neuroassist-ai-migrations/setup-local.md)
- [Service Spec](../../docs\architecture\backend\services\neuroassist-ai-migrations\neuroassist-ai-migrations-spec.md)
- [ADRs](../../docs/architecture/tools/neuroassist-ai-migrations/adr/README.md)
- [CLAUDE.md](../../CLAUDE.md)
