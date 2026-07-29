# EVNT-015 -- Implement GestFeignAdapter (GestValidationPort, fail-closed on emergency timeout, 422/502 mapping)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
Implements the `GestValidationPort` as a Feign HTTP client calling `gest-service`'s validation
endpoint (`POST /api/v1/validations`). This is on the critical path of `IngestRecognizedReport`:
a GEST rejection means no `Evento` is created (Framing invariant #2); a timeout on an `emergencia`
report must fail closed (surface `502/504`, never silently create an unvalidated event).
WireMock stubs this in contract tests (INT-EVNT-002).

## Spec de referencia
- SPEC-10 (integrations — GEST contract)
- contracts/hub-integration.md §2.1

## Escopo
- [ ] Create `GestValidationClient` Feign interface with `POST /api/v1/validations` calling `RecognizedCommandReport` → `ValidationResult`
- [ ] Create `GestFeignAdapter` implementing `GestValidationPort`: call client; map `200` → valid; `422` → throw `GestRejectedException`; `5xx`/timeout → throw `UpstreamUnavailableException`
- [ ] Configure Feign timeout from `feign.client.config.gest-service.connectTimeout` / `readTimeout` (sourced from `EVNT_GEST_BASEURL`)
- [ ] Register with Eureka: `@FeignClient(name="gest-service", url="${evnt.gest.baseurl:}")`
- [ ] Propagate `X-Correlation-Id` and `Authorization` (service token) via Feign `RequestInterceptor`
- [ ] Unit test with WireMock: valid report → `valid=true`; 422 → `GestRejectedException`; 504 → `UpstreamUnavailableException`

## Resultado esperado
`GestValidationPort` calls GEST, correctly maps all response codes, and propagates correlation headers.
Timeout fails closed (exception surfaced to ingress controller).

## Criterios de aceite
- [ ] GEST `200 {valid:true}` → validation passes, ingress continues
- [ ] GEST `422` → `GestRejectedException` thrown; no `Evento` created; ingress controller returns `422`
- [ ] GEST timeout (simulated via WireMock delay) → `UpstreamUnavailableException`; ingress controller returns `502/504`
- [ ] `X-Correlation-Id` is present in every outbound request to GEST
- [ ] Feign client resolves base URL from `${evnt.gest.baseurl}` environment variable

## Dependencias
- EVNT-001 (GestValidationPort interface)
- EVNT-006 (IngestRecognizedReport — calls this port)

## Estimativa
M

## Prioridade
P5
