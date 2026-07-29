# EVNT-017 -- Implement AcsFeignAdapter (CaregiverRoutingPort, routing + confirmador resolve) + DashFeignAdapter (fail-open delivery) + HistFeignAdapter (retryable audit)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
Implements the remaining three Feign adapters:
- `AcsFeignAdapter` (`CaregiverRoutingPort`): resolves the responsible caregiver for a patient
  (`GET /api/v1/pacientes/{id}/responsavel`) and resolves `confirmadoPor` identity (`GET /api/v1/cuidadores/{id}`). Timeout on emergency: fail closed.
- `DashFeignAdapter` (`NotificationDispatchPort`): delivers the notification to DASH
  (`POST /api/v1/notifications`). **Fail-open**: a DASH failure leaves the dispatch `failed` for retry;
  it never blocks the `Evento`.
- `HistFeignAdapter` (`AuditTrailPort`): appends audit facts (`POST /api/v1/audit-facts`).
  Retryable; never on the emergency critical path.

## Spec de referencia
- SPEC-10 (integrations)
- contracts/hub-integration.md §2.3, §2.4, §2.5

## Escopo
- [ ] Create `AcsFeignAdapter` implementing `CaregiverRoutingPort`: `findResponsavelByPaciente` → `GET /pacientes/{pacienteId}/responsavel`; `findById` → `GET /cuidadores/{id}`; `404` → `CaregiverUnresolvableException`; timeout on emergency → fail closed + `UpstreamUnavailableException`
- [ ] Create `DashFeignAdapter` implementing `NotificationDispatchPort`: `POST /api/v1/notifications`; `200`/`202` → success; any `5xx` or timeout → `DeliveryFailedException` (caller marks dispatch `failed` and re-queues — fail-open)
- [ ] Create `HistFeignAdapter` implementing `AuditTrailPort`: `POST /api/v1/audit-facts`; failure throws a retryable exception (caught by `ConfirmEvento`, does not roll back the verdict)
- [ ] Configure Resilience4j circuit breakers for each client (config in `application.yml`): `gest`, `pac`, `acs` → fail-closed (fallback = exception); `dash`, `hist` → fail-open (fallback = log + exception)
- [ ] Propagate `X-Correlation-Id` and tenant on all outbound calls
- [ ] Unit tests with WireMock: ACS 404 → exception; DASH 503 → `DeliveryFailedException`; HIST 500 → retryable exception; all propagate `X-Correlation-Id`

## Resultado esperado
All three adapters correctly implement their ports with the correct fail-open/fail-closed semantics,
header propagation, and Resilience4j circuit breakers.

## Criterios de aceite
- [ ] `CaregiverRoutingPort.findResponsavelByPaciente` throws `CaregiverUnresolvableException` on ACS `404`
- [ ] `NotificationDispatchPort.dispatch` throws `DeliveryFailedException` on any DASH `5xx` or timeout (caller re-queues; does not propagate to HTTP response as 5xx)
- [ ] `AuditTrailPort.appendFact` failure is retried without blocking the confirmation response
- [ ] `X-Correlation-Id` appears in every outbound call to ACS, DASH, and HIST
- [ ] Resilience4j circuit breaker for GEST/PAC/ACS opens after threshold failures and throws `UpstreamUnavailableException`

## Dependencias
- EVNT-001 (port interfaces)
- EVNT-007 (OrchestrateNotification — calls ACS + DASH)
- EVNT-008 (ConfirmEvento — calls ACS + HIST)

## Estimativa
M

## Prioridade
P5
