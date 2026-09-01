# conv-service

Conv — Serviço de domínio NeuroAssist AI tool.

## Stack

- Java 21 (eclipse-temurin:21)
- Spring Boot 3.5.11 + Gradle 8.x
-  (schema-per-tenant: `t_<tenantId>`, migrations em neuroassist-ai-migrations)
- ActiveMQ Artemis
- Spring Cloud ACS (Eureka + OpenFeign + Resilience4j)

## Como buildar

```bash
./gradlew :conv-service:build
./gradlew :conv-service:architectureCheck  # bloqueante — rodar antes de commit
```

## Como testar

```bash
./gradlew :conv-service:test              # unitários (sem Docker)
./gradlew :conv-service:integrationTest   # integração (requer Docker)
```

## Como rodar localmente

```bash
docker compose up -d
./gradlew :conv-service:bootRun --args='--spring.profiles.active=local'
```

Disponível em: `http://localhost:8090`

## Referências

- [Setup Local](../../docs/architecture/tools/conv-service/setup-local.md)
- [Service Spec](../../docs\architecture\backend\services\conv-service\conv-service-spec.md)
- [ADRs](../../docs/architecture/tools/conv-service/adr/README.md)
- [CLAUDE.md](../../CLAUDE.md)
