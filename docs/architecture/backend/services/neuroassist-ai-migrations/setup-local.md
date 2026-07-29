# MIG — Local Setup (neuroassist-ai-migrations)

> Infrastructure DDL runner. **Run-to-completion job** with a small internal
> HTTP face (no public exposure, no GWY routing). No message broker, no
> TechLab AI. Local run needs **PostgreSQL up** with a privileged role and
> at least one row in the tenant registry to exercise fan-out.

## Run model and port

MIG's core is a **job** — it does not need an HTTP port for its critical
path. The internal HTTP surface (ACS's provisioning call, operator status)
uses:

| Surface | Port |
|---|---|
| neuroassist-ai-migrations (internal HTTP) | `8093` |

Actuator: `/actuator/health`, `/actuator/health/readiness`.

## Local dependencies

| Dependency | Needed for | Local |
|---|---|---|
| PostgreSQL 15+ | all DDL | run on `5432`; create the privileged role `neuroassist_migrator` |
| Tenant registry | fan-out enumeration | the `tenant` table in `neuroassist_global`, or `MIG_TENANT_LIST` for offline dev |

No Kafka/broker, no Eureka dependency for the core job (only the optional
HTTP face registers), no TechLab AI.

## Environment variables

| Var | Purpose | Example |
|---|---|---|
| `MIG_DB_URL` | JDBC URL | `jdbc:postgresql://localhost:5432/neuroassist` |
| `MIG_DB_USER` | privileged DDL role | `neuroassist_migrator` |
| `MIG_DB_PASSWORD` | role secret | `(secret-managed)` |
| `MIG_GLOBAL_SCHEMA` | fixed global schema | `neuroassist_global` |
| `MIG_TENANT_SCHEMA_PREFIX` | tenant schema prefix | `t_` |
| `MIG_TENANT_REGISTRY_MODE` | `db` (read global table) or `list` (offline) | `db` |
| `MIG_TENANT_LIST` | csv of tenantIds when `MIG_TENANT_REGISTRY_MODE=list` | `b3f1,9a2c` |
| `MIG_LOCK_TIMEOUT` | advisory lock timeout | `30s` |
| `MIG_INTERNAL_AUTH_SCOPE` | required OAuth2 scope for the internal API | `migrations:internal` |
| `SERVER_PORT` | internal HTTP port | `8093` |

## Run and test

```bash
# 1. PostgreSQL up + privileged role created (one-time)
#    CREATE ROLE neuroassist_migrator LOGIN PASSWORD '...' CREATEDB;

# 2. deploy-style full run (gates rollout in CI)
./gradlew :services:neuroassist-ai-migrations:bootRun --args='--mode=migrate-all'

# 3. provision a new tenant (ACS onboarding, job path)
./gradlew :services:neuroassist-ai-migrations:bootRun --args='--mode=provision-tenant --tenant=b3f1'

# 4. read-only status / dry-run
./gradlew :services:neuroassist-ai-migrations:bootRun --args='--mode=info'
./gradlew :services:neuroassist-ai-migrations:bootRun --args='--mode=migrate-all --dry-run'

# exit code is the gate
echo $?   # 0 converged | 1 invocation | 2 migration failure | 3 pre-flight failure
```

## Tests

```bash
./gradlew :services:neuroassist-ai-migrations:test       # unit (plan, ordering, validation)
./gradlew :services:neuroassist-ai-migrations:cucumber   # BDD (tests/cucumber/*.feature) — Testcontainers PostgreSQL
```
