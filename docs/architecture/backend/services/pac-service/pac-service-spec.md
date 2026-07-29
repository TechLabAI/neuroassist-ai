---
module: PAC
service: pac-service
package: com.neuroassist.pac
version: "1.0.0"
status: specs-validate-complete
adrs:
  - ADR-004-command-calibration-authoring-source.md
  - ADR-005-emergency-eligibility-gate.md
  - ADR-006-command-versioned-retire-history-preserving.md
generatedAt: "2026-07-29"
---

# pac-service Spec Entry Point

> Module: **PAC — Pacientes e Catálogo de Comandos** · Service: `pac-service`
> Package: `com.neuroassist.pac` · Multi-tenant: schema-per-empresa
> Pipeline phase: **specs-validate** complete (--generate-only; BDD gate deferred to post-scaffold)

## Purpose

`pac-service` is the **authoring source of truth** for the NeuroAssist AI platform. It owns:
- The `Paciente` record (system of record — identity, tenant link, device/caregiver references)
- The `Comando` catalog (gesture → family label → need, per-command calibration)
- The `Comando` lifecycle state machine: `drafted → validated → published → recalibrated → retired`
- The emergency eligibility gate: enforces GEST detectability before publishing an emergency command
- The active catalog publication endpoint: exposes calibrated commands so GEST can build detection profiles

PAC stores only catalog metadata and primitive ids — never pixels, landmarks, or biometric data.
It is a LGPD data controller for patient identity (nome, tenant linkage) but holds no health measurements.

## Document map

| Document | Path | Status |
|---|---|---|
| Vision (01-06) | `01..06-*.md` | complete (M1) |
| ADR-004 calibration authoring source | `adr/ADR-004-command-calibration-authoring-source.md` | complete (M1) |
| ADR-005 emergency eligibility gate | `adr/ADR-005-emergency-eligibility-gate.md` | complete (M1) |
| ADR-006 versioned retire | `adr/ADR-006-command-versioned-retire-history-preserving.md` | complete (M1) |
| **API contract** | `contracts/api-contract.md` | complete (M2) |
| **Data model** | `contracts/data-model.md` | complete (M2) |
| **Event contracts** | `contracts/event-contracts.md` | complete (M2) |
| SPEC-01 API surface | `spec/backend/SPEC-01-api-surface.md` | complete (M3) |
| SPEC-02 Command lifecycle orchestration | `spec/backend/SPEC-02-command-lifecycle-orchestration.md` | complete (M3) |
| SPEC-03 Emergency eligibility gate | `spec/backend/SPEC-03-emergency-eligibility-gate.md` | complete (M3) |
| SPEC-04 Data model/migrations | `spec/backend/SPEC-04-data-model-migrations.md` | complete (M3) |
| SPEC-05 Idempotency/concurrency | `spec/backend/SPEC-05-idempotency-concurrency.md` | complete (M3) |
| SPEC-06 Security | `spec/backend/SPEC-06-security.md` | complete (M3) |
| SPEC-07 Observability/health | `spec/backend/SPEC-07-observability-health.md` | complete (M3) |
| SPEC-08 BDD scenarios | `spec/backend/SPEC-08-bdd-test-scenarios.md` | complete (M3) |
| SPEC-09 Messaging architecture | `spec/backend/SPEC-09-messaging-architecture.md` | complete (M3) |
| SPEC-10 Integrations | `spec/backend/SPEC-10-integrations.md` | complete (M3) |
| Gherkin features (4) | `tests/cucumber/*.feature` | complete (M3, --generate-only) |
| Event catalog | `events/event-catalog.md` | complete (M4) |
| Setup local | `setup-local.md` | complete (M5) |

## Key invariants at a glance

| # | Invariant | Enforcement |
|---|---|---|
| 1 | Emergency command eligible only when ALL referenced primitives = `supported` (GEST) | `EmergencyEligibilityGate` — fail-closed, timeout ≤ 2 s |
| 2 | Routine command publish skips GEST gate (advisory only) | `ComandoLifecycleService.publish` branch on `urgencia` |
| 3 | `Comando` is never hard-deleted — retire transitions to `retired` state | `ck_comando_lifecycle` DB CHECK + `ComandoLifecycleService.retire` |
| 4 | `catalogoVersao` bumps atomically on every publish/recalibrate/retire | `PacienteVersionBumper` outbound port — DB function |
| 5 | Tenant isolation enforced at schema level — no cross-tenant queries | `X-Empresa-Id` header → schema routing in datasource interceptor |
| 6 | `pac-service` never stores or receives pixels, landmarks, or images | Port contract: GestDetectabilityPort receives only primitive ids |
| LGPD | Patient nome/vínculo is personal data — scope `pac:admin` required for write | OAuth2 scope matrix in SPEC-06 |

## Next step

`/be-architect-scaffold neuroassist-ai PAC`
