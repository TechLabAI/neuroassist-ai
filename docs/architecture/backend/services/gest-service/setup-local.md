# gest-service — Local Setup

> Module: **GEST — Detecção de Gesto (Edge)** · Service: `gest-service`
> Port: **8084** (GEST reserved in NeuroAssist port allocation)

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
- `edge-service` — device pairing; mock with `@MockBean EdgePairingClient` in tests
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

Copy `.env.example` to `.env` in `backend/services/gest-service/` and set:

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/neuroassist
SPRING_DATASOURCE_USERNAME=neuroassist
SPRING_DATASOURCE_PASSWORD=<local-password>

# Service Registry
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://localhost:8761/eureka/

# OAuth2 (jwk-set-uri — use acs-service or a local Keycloak)
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=http://localhost:8080/oauth2/jwks

# Edge service (device pairing) — set to a test stub or skip with MockBean in tests
EDGE_SERVICE_URL=http://localhost:8087

# Service port
SERVER_PORT=8084

# Multi-tenancy (schema prefix)
NEUROASSIST_TENANT_SCHEMA_PREFIX=t_
```

## 4. Apply GEST migrations

GEST does not run Flyway locally — it contributes to MIG. For local development, apply the GEST
migration scripts manually to a test schema:

```bash
# Generate the migration SQL from the data model
python scripts/be-migration-generator.py --service gest-service

# Create a test tenant schema and apply
psql -h localhost -U neuroassist -d neuroassist -c "CREATE SCHEMA IF NOT EXISTS t_empresa_test;"
psql -h localhost -U neuroassist -d neuroassist -f backend/services/gest-service/src/main/resources/db/migration/empresa/V0400__gest_initial_schema.sql
```

Or run the `gest-service` integration tests — they use Testcontainers with a fresh PostgreSQL
container and apply the migration automatically.

## 5. Run the service

```bash
cd backend/services/gest-service

# Build and run
./gradlew :services:gest-service:bootRun

# Or build the JAR and run
./gradlew :services:gest-service:bootJar
java -jar build/libs/gest-service-*.jar
```

Service starts on `http://localhost:8084`.

## 6. Verify

```bash
# Health check
curl http://localhost:8084/actuator/health

# List primitive catalog (requires a valid JWT — use a test token from ACS or a stub)
curl -H "Authorization: Bearer <jwt>" \
     -H "X-Correlation-Id: local-test-001" \
     http://localhost:8084/api/v1/gest/tipos-gesto

# Get detection contract descriptor
curl -H "Authorization: Bearer <jwt>" \
     http://localhost:8084/api/v1/gest/detection-contract
```

## 7. Run BDD tests (post-scaffold)

```bash
# BDD gate (Cucumber integration tests — requires Testcontainers)
./gradlew :services:gest-service:test -Dcucumber.filter.tags="@integration"

# Or replay deferred gate after scaffold
python scripts/be-gates-replay.py --run --update --service gest-service --gate bddGate
```
