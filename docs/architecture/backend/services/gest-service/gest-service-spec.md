---
module: GEST
service: gest-service
package: com.neuroassist.gest
version: "1.0.0"
status: specs-validate-complete
detectionContractVersion: "1.0.0"
adrs:
  - ADR-005-edge-cloud-detection-split.md
  - ADR-006-tipogesto-detectability-lifecycle.md
generatedAt: "2026-07-27"
---

# gest-service Spec Entry Point

> Module: **GEST — Detecção de Gesto (Edge)** · Service: `gest-service`
> Package: `com.neuroassist.gest` · Multi-tenant: schema-per-empresa
> Pipeline phase: **specs-validate** complete (--generate-only; BDD gate deferred to post-scaffold)

## Purpose

`gest-service` is the **cloud-side detection authority** for the NeuroAssist AI platform. It owns:
- The TipoGesto primitive vocabulary (facial-movement catalog per tenant)
- The detectability lifecycle state machine (`registered → pending_training → supported → deprecated`)
- Versioned, immutable detection profiles pulled by the edge `GestureDetectionEngine`
- The frozen detection contract (schemas for `RecognizedCommandReport` and `EdgeRecognitionSignal`)

It holds no pixels, images, or biometric data — only abstract primitive identifiers, lifecycle
states, opaque engine handles, and classification parameters. GEST is outside the LGPD sensitive
data surface.

## Document map

| Document | Path | Status |
|---|---|---|
| Vision (01-06) | `01..06-*.md` | complete (M1) |
| ADR-005 edge/cloud split | `adr/ADR-005-edge-cloud-detection-split.md` | complete (M1) |
| ADR-006 lifecycle | `adr/ADR-006-tipogesto-detectability-lifecycle.md` | complete (M1) |
| **API contract** | `contracts/api-contract.md` | complete (M2) |
| **Data model** | `contracts/data-model.md` | complete (M2) |
| **Event contracts** | `contracts/event-contracts.md` | complete (M2) |
| Hub integration | `contracts/hub-integration.md` | complete (M3) |
| Integration contracts | `contracts/integration-contracts.md` | complete (M3) |
| SPEC-01 API surface | `spec/backend/SPEC-01-api-surface.md` | complete (M3) |
| SPEC-02 Primitive catalog | `spec/backend/SPEC-02-primitive-catalog-orchestration.md` | complete (M3) |
| SPEC-03 Eligibility + Profile | `spec/backend/SPEC-03-eligibility-and-detection-profile.md` | complete (M3) |
| SPEC-04 Data model/migrations | `spec/backend/SPEC-04-data-model-migrations.md` | complete (M3) |
| SPEC-05 Idempotency/concurrency | `spec/backend/SPEC-05-idempotency-concurrency.md` | complete (M3) |
| SPEC-06 Security | `spec/backend/SPEC-06-security.md` | complete (M3) |
| SPEC-07 Observability/health | `spec/backend/SPEC-07-observability-health.md` | complete (M3) |
| SPEC-08 BDD scenarios | `spec/backend/SPEC-08-bdd-test-scenarios.md` | complete (M3) |
| SPEC-09 Messaging | `spec/backend/SPEC-09-messaging-architecture.md` | complete (M3) |
| SPEC-10 Integrations | `spec/backend/SPEC-10-integrations.md` | complete (M3) |
| Gherkin features (4) | `tests/cucumber/*.feature` | complete (M3, --generate-only) |
| Event catalog | `events/event-catalog.md` | complete (M3) |
| C4 diagrams | `diagrams/c4-{context,container,component}.puml` | complete (M3) |
| Setup local | `setup-local.md` | complete (M3) |

## Key invariants at a glance

| # | Invariant | Enforcement |
|---|---|---|
| 1 | Emergency command eligible only when ALL primitives = `supported` | `/eligibility:check` logic |
| 2 | Profile includes only `supported` primitives at publish time | `PublishProfileUseCase` |
| 3 | Profile version strictly monotonic, never reused | `ux_profile_version` UNIQUE constraint |
| 4 | No silent regression from `supported` (reason required) | `TipoGesto` domain aggregate |
| 5 | `engineFeatureRef` required before `supported` | `ck_tipo_gesto_supported_needs_ref` DB CHECK |
| LGPD | No biometric data persisted or echoed | Stateless validate-only; `RecognizedCommandReport` never persisted |

## Next step

`/be-architect-backlog neuroassist-ai GEST`
