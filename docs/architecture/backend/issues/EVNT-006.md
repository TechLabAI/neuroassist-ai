# EVNT-006 -- Implement IngestRecognizedReport + GenerateEvento use cases (idempotency check -> GEST validate -> persist Evento + EventoCriado)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
Implements the golden path for event creation: the `ReportIngressService` application service that
executes the durable-first sequence — idempotency check → GEST validation → `GenerateEvento`
(snapshot Comando from PAC, persist `evento` + `report_idempotency` in one transaction, publish
`EventoCriado`) — as specified in SPEC-02 and ADR-004.
Ports `GestValidationPort`, `CommandLookupPort`, `EventoRepositoryPort`, and `IdempotencyRepositoryPort`
are called; actual adapters are mocked in tests (real adapters land in EVNT-010 through EVNT-016).

## Spec de referencia
- SPEC-02 (notification orchestration — ingress + generate)
- SPEC-05 (idempotency + concurrency)

## Escopo
- [ ] Create `ReportIngressService` in `application` package with `ingestReport(RecognizedCommandReportDto)` method
- [ ] Idempotency check: call `IdempotencyRepositoryPort.findByReportId(reportId)` — if present, return original `EventoDto` (duplicate path, no side effects)
- [ ] GEST validation: call `GestValidationPort.validate(report)` — on rejection (`GestRejectedException`) propagate `422`; on timeout for `emergencia` throw `UpstreamUnavailableException` (`502`)
- [ ] Resolve `Comando` snapshot: call `CommandLookupPort.findByRef(commandRef)` — snapshot `label`, `urgencia`, `pacienteId`; on timeout for `emergencia` fail closed
- [ ] Persist `Evento` + `report_idempotency` atomically (`@Transactional` in application service); generate `eventoId`, `seq`
- [ ] Publish `EventoCriado` via `ApplicationEventPublisher` within the transaction boundary
- [ ] Unit tests with mocked ports: new report → 201 flow; duplicate reportId → 200; GEST rejects → 422; GEST timeout on emergencia → 502

## Resultado esperado
`ReportIngressService.ingestReport` correctly gates on idempotency, validates with GEST, snapshots
Comando from PAC, and atomically persists one `Evento` before publishing `EventoCriado`. All tests
pass with ports mocked.

## Criterios de aceite
- [ ] Exactly one `Evento` is persisted per unique `reportId` across concurrent requests (SPEC-05)
- [ ] A duplicate `reportId` returns the original `EventoDto` with `status=duplicate`, no new `Evento` persisted, no `EventoCriado` published
- [ ] GEST rejection results in `422` with no `Evento` created
- [ ] GEST or PAC timeout on an `emergencia` results in `502/504` — no `Evento` created, failure recorded
- [ ] `EventoCriado` is published within the same transaction boundary as the `Evento` insert

## Dependencias
- EVNT-001 (scaffold + port interfaces)
- EVNT-010 (JPA adapters needed for integration test; mocked for unit test)
- EVNT-015 (GestFeignAdapter needed for integration; mocked for unit test)
- EVNT-016 (PacFeignAdapter needed for integration; mocked for unit test)

## Estimativa
L

## Prioridade
P2
