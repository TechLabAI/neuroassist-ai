# acs-service

Acs — Serviço de domínio NeuroAssist AI tool.

## Stack

- Java 21 (eclipse-temurin:21)
- Spring Boot 3.5.11 + Gradle 8.x
- PostgreSQL 15+ (schema-per-tenant: `t_<tenantId>`, migrations em neuroassist-ai-migrations)
- ActiveMQ Artemis
- Spring Cloud ACS (Eureka + OpenFeign + Resilience4j)

## Como buildar

```bash
./gradlew :acs-service:build
./gradlew :acs-service:architectureCheck  # bloqueante — rodar antes de commit
```

## Como testar

```bash
./gradlew :acs-service:test              # unitários (sem Docker)
./gradlew :acs-service:integrationTest   # integração (requer Docker)
```

## Como rodar localmente

```bash
docker compose up -d
./gradlew :acs-service:bootRun --args='--spring.profiles.active=local'
```

Disponível em: `http://localhost:8088`

## Referências

- [Setup Local](../../docs/architecture/tools/acs-service/setup-local.md)
- [Service Spec](../../docs\architecture\backend\services\acs-service\acs-service-spec.md)
- [ADRs](../../docs/architecture/tools/acs-service/adr/README.md)
- [CLAUDE.md](../../CLAUDE.md)
