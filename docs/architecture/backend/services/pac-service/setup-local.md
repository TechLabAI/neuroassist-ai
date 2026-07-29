# pac-service — Local Setup

> Module: **PAC — Pacientes e Catálogo de Comandos** · Service: `pac-service`
> Port: **8082** (PAC reserved in NeuroAssist port allocation)

## 1. Prerequisites

| Dependency | Version | Notes |
|---|---|---|
| Java | 21 | OpenJDK or GraalVM |
| Gradle | 8.x (wrapper) | `./gradlew` in the project root |
| PostgreSQL | 16 | Local or Docker (see §2) |
| Docker Compose | 2.x | Optional — for infra stack |

**Hub services that must be reachable:**
- `neuroassist-service-registry` (REG) — default port `8761`
- `acs-service` — for `jwk-set-uri` (can use a test stub; see §3)

**Hub services that are optional locally (can be mocked):**
- `gest-service` — emergency eligibility gate; mock with `@MockBean GestDetectabilityClient` in tests
- `edge-service` — device reference; mock with `@MockBean EdgeDeviceClient` in tests
- `hist-service` — domain fact recording; mock with `@MockBean HistFactClient` in tests
- `neuroassist-api-gateway` — not needed for direct local calls

## 2. Infra stack (Docker Compose)

```bash
# From techlab-infra (sibling clone)
docker compose -f ../techlab-infra/docker-compose.techlab.yml up -d postgres

# Verify PostgreSQL is up
docker compose -f ../techlab-infra/docker-compose.techlab.yml ps
```

PostgreSQL is exposed on port `5432` (or `5435` if running alongside pgVector — check compose file).

## 3. Environment variables

Copy `.env.example` to `.env` in `backend/services/pac-service/` and set:

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/neuroassist
SPRING_DATASOURCE_USERNAME=neuroassist
SPRING_DATASOURCE_PASSWORD=<local-password>

# Service Registry
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://localhost:8761/eureka/

# OAuth2 (jwk-set-uri — use acs-service or a local Keycloak)
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=http://localhost:8080/oauth2/jwks

# Downstream services
NEUROASSIST_INTEGRATION_GEST_URL=http://localhost:8084
NEUROASSIST_INTEGRATION_ACS_URL=http://localhost:8083
NEUROASSIST_INTEGRATION_EDGE_URL=http://localhost:8087
NEUROASSIST_INTEGRATION_HIST_URL=http://localhost:8086

# Service port
SERVER_PORT=8082

# Multi-tenancy (schema prefix)
NEUROASSIST_TENANT_SCHEMA_PREFIX=t_
```

## 4. Apply PAC migrations

PAC does not run Flyway locally — it contributes to MIG. For local development, apply the PAC
migration scripts manually to a test schema:

```bash
# Generate the migration SQL from the data model
python scripts/be-migration-generator.py --service pac-service

# Create a test tenant schema and apply
psql -h localhost -U neuroassist -d neuroassist -c "CREATE SCHEMA IF NOT EXISTS t_empresa_test;"
psql -h localhost -U neuroassist -d neuroassist \
  -f backend/services/pac-service/src/main/resources/db/migration/empresa/V0001__pac_paciente.sql
```

Or run the `pac-service` integration tests — they use Testcontainers with a fresh PostgreSQL
container and apply the migration automatically.

## 5. Run the service

```bash
cd backend/services/pac-service

# Build and run
./gradlew :services:pac-service:bootRun

# Or build the JAR and run
./gradlew :services:pac-service:bootJar
java -jar build/libs/pac-service-*.jar
```

Service starts on `http://localhost:8082`.

## 6. Verify

```bash
# Health check
curl http://localhost:8082/actuator/health

# List patients (requires a valid JWT — use a test token from ACS or a stub)
curl -H "Authorization: Bearer <jwt>" \
     -H "X-Empresa-Id: empresa-test-001" \
     -H "X-Correlation-Id: local-test-001" \
     http://localhost:8082/api/v1/pac/pacientes

# Get active command catalog for a patient (read-model for GEST)
curl -H "Authorization: Bearer <jwt>" \
     -H "X-Empresa-Id: empresa-test-001" \
     http://localhost:8082/api/v1/pac/pacientes/{pacienteId}/catalogo-ativo
```

## 7. Run BDD tests (post-scaffold)

```bash
# BDD gate (Cucumber integration tests — requires Testcontainers)
./gradlew :services:pac-service:test -Dcucumber.filter.tags="@integration"

# Or replay deferred gate after scaffold
python scripts/be-gates-replay.py --run --update --service pac-service --gate bddGate
```
