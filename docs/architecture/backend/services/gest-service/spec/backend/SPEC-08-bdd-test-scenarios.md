# SPEC-08 — BDD Test Scenarios (`gest-service`)

> **Module:** GEST — Detecção de Gesto (Edge)
> **Generated from:** EARS invariants in `contracts/api-contract.md`,
> `contracts/data-model.md`, `contracts/event-contracts.md`
> **Gherkin files (Cucumber):** `tests/cucumber/*.feature`
> **EARS guide:** `docs/architecture/backend/standards/ears-notation.md`

## 1. Objective

Index and rationale for the BDD feature files. This doc is a companion to the `.feature` files —
it provides reusable step documentation, cross-references to SPECs, and the coverage map.
The actual executable scenarios live in the `.feature` files.

## 2. Gherkin feature index

| File | Invariants covered | SPEC ref |
|---|---|---|
| `tipo-gesto-lifecycle.feature` | Registration, state machine, #4 no-silent-regression, #5 ref-required, audit | SPEC-02 |
| `eligibility-check.feature` | #1 emergency all-supported, rotina, unknown primitive, idempotency, tenant isolation | SPEC-03 |
| `detection-profile.feature` | #2 supported-only snapshot, #3 monotonic version, single-active, ETag/304 | SPEC-03 |
| `report-validation.feature` | Stateless validation, LGPD privacy, idempotency, composite, confidence range | SPEC-05 |

## 3. Coverage map — invariants to scenarios

| Invariant | Feature | Scenario |
|---|---|---|
| `ck_tipo_gesto_supported_needs_ref` (#5) | `tipo-gesto-lifecycle` | `Transition to supported without engineFeatureRef` |
| No silent regression (#4) | `tipo-gesto-lifecycle` | `Regression from supported requires explicit reason` |
| Emergency only all-supported (#1) | `eligibility-check` | `Emergency NOT eligible when any primitive is registered` |
| Profile includes only supported (#2) | `detection-profile` | `Publish profile includes only supported primitives` |
| Monotonic version (#3) | `detection-profile` | `Version is strictly monotonic` |
| Single active profile | `detection-profile` | `Publishing deactivates prior profile` |
| Report validate-only (LGPD) | `report-validation` | `Report validated but never persisted` |
| `ux_profile_single_active` | `detection-profile` | `There is exactly one active profile after publish` |
| Conditional GET 304 | `detection-profile` | `Edge conditional GET returns 304 when ETag matches` |
| Tenant isolation | `tipo-gesto-lifecycle`, `eligibility-check` | Tenant isolation scenarios |
| Composite report requires two primitives | `report-validation` | `Composite report with only one primitive is invalid` |
| Confidence range [0.0, 1.0] | `report-validation` | `Scenario Outline: Confidence value` |

## 4. Reusable background steps

Steps shared across all features and their expected Spring integration test wiring:

| Step | Wiring |
|---|---|
| `Given the tenant "<id>" is active` | `@BeforeEach`: create schema `t_<id>`, run migrations, set routing context |
| `And the caller is authenticated with role "gest.read"` | Mock OAuth2 JWT with `gest.read` scope via `@WithMockJwt` |
| `And the caller is authenticated with role "gest.manage"` | Mock JWT with `gest.manage` scope (conductor) |
| `And the device credential for "<deviceId>" is valid for tenant "<id>"` | Mock device JWT with `deviceId` + `tenant` claims |
| `Given primitive "<id>" is in state "<state>"` | Insert row via `TipoGestoRepository` fixture helper |

## 5. Test profile

Tests run under Spring `@ActiveProfiles("test")`:
- PostgreSQL: `Testcontainers` (`postgres:16-alpine`) with schema-per-tenant initialization.
- OAuth2: `spring-security-test` `MockMvc` JWT support (no running Keycloak needed).
- EDGE pairing: mocked via `@MockBean EdgePairingClient` (EDGE service is out-of-boundary).
- No broker (messaging disabled; no Artemis/Kafka Testcontainer needed).

## 6. Gate deferred note (`--generate-only` mode)

This spec-validate run was invoked with `--generate-only`. The Gherkin files are generated and
ready; the **BDD gate** (`compileTestJava` + Cucumber integration test run) is deferred until
the scaffold generates the `gest-service` Spring Boot project and `be-bdd-generator.py` copies
the `.feature` files into `backend/services/gest-service/src/test/resources/features/`.

Replay command (after scaffold):
```bash
python scripts/be-gates-replay.py --run --update --service gest-service --gate bddGate
```

## 7. Scenario count summary

| Feature | Scenarios |
|---|---|
| `tipo-gesto-lifecycle.feature` | 13 |
| `eligibility-check.feature` | 11 |
| `detection-profile.feature` | 14 |
| `report-validation.feature` | 11 + 1 outline (5 rows) |
| **Total** | **~54 scenarios** |
