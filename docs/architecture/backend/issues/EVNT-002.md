# EVNT-002 -- Implement ReportIngressController (POST /reports) + biometric-payload guard

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/7 itens

## Contexto
Exposes the edge ingress endpoint `POST /api/v1/reports` that receives a `RecognizedCommandReport`
from an edge agent. The controller is the HTTP adapter only: validates the DTO via Bean Validation,
rejects biometric payloads (LGPD hard rule), and delegates to `ReportIngressService` (implemented in
EVNT-006, stubbed here). Returns `201 Created` for a new event and `200 OK` for a duplicate `reportId`.

## Spec de referencia
- SPEC-01 (API surface §3.1)
- contracts/api-contract.md §3.1

## Escopo
- [ ] Create `RecognizedCommandReportDto` with `@NotNull`/`@Valid` Bean Validation on all required fields (`reportId`, `pacienteId`, `commandRef`, `primitives[]`, `confidence`, `timingWindow`, `recognizedAt`, `deviceId`)
- [ ] Create `ReportIngressController` in `api` package (`@RestController`, base path `/api/v1`)
- [ ] Implement `POST /reports` handler: call `ReportIngressService.ingestReport(dto)`; return `201` + `Location: /api/v1/events/{eventoId}` on new event; `200` on duplicate
- [ ] Implement biometric-payload guard filter/validator: presence of any image/frame/landmark field → `400` with `type=/errors/forbidden-biometric-payload`
- [ ] Add `X-Correlation-Id` echo filter (accept from GWY or generate UUID if absent) applied to all EVNT responses
- [ ] Unit test: valid report → 201; duplicate signal from service → 200; biometric field → 400; missing `reportId` → 400 RFC 7807
- [ ] Unit test: absent `X-Correlation-Id` in request → echoed generated value in response

## Resultado esperado
`POST /api/v1/reports` is reachable, validates the DTO, blocks biometric payloads, and delegates to the
ingress service. All tests pass with the service mocked.

## Criterios de aceite
- [ ] `POST /api/v1/reports` with valid body returns `201` and `Location: /api/v1/events/{id}`
- [ ] Duplicate `reportId` (service signals duplicate) returns `200`, same body, no new side effect
- [ ] Body containing any biometric field returns `400` with `type=/errors/forbidden-biometric-payload`
- [ ] Missing `reportId` or malformed body returns `400` RFC 7807 `ProblemDetail` with `correlationId`
- [ ] `X-Correlation-Id` is echoed on every response (generated if absent from request)

## Dependencias
- EVNT-001 (project scaffold + port interfaces)
- EVNT-005 (GlobalExceptionHandler — can be implemented in parallel; required before integration tests)

## Estimativa
M

## Prioridade
P1
