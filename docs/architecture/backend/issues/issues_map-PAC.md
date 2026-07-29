# Issues Map — PAC (Pacientes e Catálogo de Comandos)

> Service: `pac-service` · Generated: 2026-07-28
> Source specs: SPEC-01..SPEC-10 · Contracts: api-contract, data-model, event-contracts

---

## Por prioridade

| ID | Título | Prio | Estimativa |
|---|---|---|---|
| PAC-001 | Criar subprojeto Gradle pac-service | P1 | M |
| PAC-002 | Criar DTOs request/response | P1 | M |
| PAC-003 | Criar interfaces de port (application) | P1 | M |
| PAC-004 | Criar entidades JPA com constraints | P1 | M |
| PAC-005 | Implementar PacienteController | P2 | M |
| PAC-006 | Implementar PacienteService | P2 | M |
| PAC-007 | Implementar PacienteJpaAdapter | P2 | M |
| PAC-008 | Criar migrations Flyway V0001–V0004 | P3 | M |
| PAC-009 | Implementar TenantSchemaResolver | P3 | M |
| PAC-010 | Implementar ComandoJpaAdapter | P3 | M |
| INT-PAC-002 | AcsDirectoryClient Feign | P4 | S |
| INT-PAC-003 | EdgeDeviceClient Feign | P4 | S |
| INT-PAC-005 | FeignHeaderPropagator (header propagation) | P4 | S |
| PAC-011 | Implementar ComandoController | P4 | L |
| PAC-012 | ComandoAuthoringService.createDraft | P4 | M |
| PAC-013 | ComandoAuthoringService.editDraft | P4 | S |
| PAC-014 | ComandoLifecycleService.validate | P5 | M |
| PAC-015 | ComandoLifecycleService.publish | P5 | L |
| PAC-016 | ComandoLifecycleService.retire | P5 | S |
| PAC-017 | EmergencyEligibilityGate | P5 | M |
| INT-PAC-001 | GestDetectabilityClient Feign + Resilience4j CB | P5 | M |
| PAC-018 | ComandoLifecycleService.recalibrate | P6 | M |
| PAC-019 | PacienteVersionBumper (catalogoVersao) | P6 | S |
| PAC-020 | CatalogoAtivoController + CatalogoAtivoService | P6 | S |
| INT-PAC-004 | HistFactClient Feign + outbox retry | P6 | M |
| PAC-021 | OAuth2 resource server + TenantJwtValidator | P7 | M |
| PAC-022 | GlobalExceptionHandler RFC 7807 | P7 | M |
| PAC-023 | Idempotency-Key dedup | P8 | M |
| INT-PAC-006 | WireMock stubs GEST/ACS/EDGE/HIST | P8 | M |
| PAC-024 | Actuator + Micrometer + MDC structured logging | P9 | M |
| PAC-025 | BDD Cucumber step definitions + runner | P9 | L |

---

## Por SPEC de referencia

| SPEC | Issues cobertas |
|---|---|
| SPEC-01 (API Surface) | PAC-001, PAC-002, PAC-005, PAC-011, PAC-020, PAC-021, PAC-022, PAC-023 |
| SPEC-02 (Lifecycle Orchestration) | PAC-012, PAC-013, PAC-014, PAC-015, PAC-016, PAC-017, PAC-018, PAC-019 |
| SPEC-03 (Emergency Gate) | PAC-017, PAC-015, PAC-018, INT-PAC-001 |
| SPEC-04 (Data Model / Migrations) | PAC-004, PAC-007, PAC-008, PAC-009, PAC-010, PAC-019 |
| SPEC-05 (Idempotency) | PAC-023 |
| SPEC-06 (Security) | PAC-021 |
| SPEC-07 (Observability) | PAC-024 |
| SPEC-08 (BDD) | PAC-025, INT-PAC-006 |
| SPEC-09 (Messaging Architecture) | INT-PAC-004 |
| SPEC-10 (Integrations) | PAC-003, INT-PAC-001, INT-PAC-002, INT-PAC-003, INT-PAC-004, INT-PAC-005 |

---

## Por camada arquitetural

| Camada | Issues |
|---|---|
| `api` (controllers, DTOs, exception handler) | PAC-002, PAC-005, PAC-011, PAC-020, PAC-022 |
| `application` (services, ports, gate) | PAC-003, PAC-006, PAC-012, PAC-013, PAC-014, PAC-015, PAC-016, PAC-017, PAC-018, PAC-019 |
| `domain` (entities, value objects) | PAC-004 |
| `infrastructure` (persistence) | PAC-007, PAC-008, PAC-009, PAC-010 |
| `infrastructure` (integration / feign) | INT-PAC-001, INT-PAC-002, INT-PAC-003, INT-PAC-004, INT-PAC-005 |
| `infrastructure` (security) | PAC-021 |
| `infrastructure` (idempotency) | PAC-023 |
| `infrastructure` (observability) | PAC-024 |
| `test` (BDD, WireMock) | PAC-025, INT-PAC-006 |
| scaffold | PAC-001 |

---

## Issues de integração

| ID | Descrição | Port implementado | Prio |
|---|---|---|---|
| INT-PAC-001 | GestDetectabilityClient | `GestDetectabilityPort` | P5 |
| INT-PAC-002 | AcsDirectoryClient | `AcsDirectoryPort` | P4 |
| INT-PAC-003 | EdgeDeviceClient | `EdgeDevicePort` | P4 |
| INT-PAC-004 | HistFactClient + outbox | `HistFactPort` | P6 |
| INT-PAC-005 | FeignHeaderPropagator | (cross-cutting) | P4 |
| INT-PAC-006 | WireMock stubs (test only) | (test infra) | P8 |

---

## Totais

- Issues funcionais: 25 (PAC-001..PAC-025)
- Issues de integração: 6 (INT-PAC-001..INT-PAC-006)
- Total: **31 issues**
- Prioridades: P1–P9
