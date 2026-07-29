# SPEC-08 — BDD test scenarios

> Prose companion to the Gherkin features in `../../tests/cucumber/*.feature`. Documents
> reusable steps, example data and traceability. The `.feature` files are the executable
> source; this file is never parsed by `be-bdd-generator.py`.

## Feature files
| File | Covers | SPEC / invariants |
|---|---|---|
| `routing-and-authentication.feature` | route forwarding, 401/404/503/502/504 | SPEC-01, SPEC-02, SPEC-06 · INV-1, INV-4 |
| `tenant-and-correlation-propagation.feature` | X-Empresa-Id derivation + strip, X-Correlation-Id | SPEC-02, SPEC-06, SPEC-07 · INV-2, INV-3 |
| `rate-limiting-and-cors.feature` | 429 back-pressure, Retry-After, CORS pre-flight | SPEC-05, SPEC-06, SPEC-01 |

## Reusable steps (step definitions)
- `Given the Service Registry has a healthy instance of "<service>"` — stub Eureka registry
  with one healthy instance (or none).
- `Given ACS exposes a valid JWK set` — stand up a WireMock JWK endpoint with a known key.
- `Given a caregiver holds a valid JWT for empresa "<empresa>"` — mint a JWT signed by the
  test key with `empresa_id=<empresa>`.
- `Given an edge agent holds a valid device token bound to empresa "<empresa>"` — mint a
  device token (`kind=device`, `device_id`, `empresa_id`) per ADR-005 proposta.
- `When ... sends "<METHOD> <path>" with that JWT` — issue the HTTP call through the gateway.
- `Then the forwarded request carries header "<h>" equal to "<v>"` — assert against the
  captured upstream request (WireMock verify).
- `Then the gateway responds "<status>"` / `... problem type ends with "<suffix>"` — assert
  status + RFC 7807 body.

## Example data
- Empresas: `emp_familia_amaral`, `emp_clinica_norte`.
- Routes exercised: `GET /api/hist/events` (caregiver read), `POST /api/evnt/events`
  (edge/caregiver ingest).
- Rate-limit test bucket `edge-ingest`: 2 req/s to make throttling deterministic.

## Test harness notes
- Upstream services are stubbed with **WireMock** (assert forwarded headers, simulate 5xx /
  timeout).
- Eureka is stubbed or replaced by a static `DiscoveryClient` in tests.
- The gateway runs on a random reactive port (`@SpringBootTest(WebEnvironment.RANDOM_PORT)`
  with WebFlux `WebTestClient`).
- **No database / broker** test containers — the gateway needs none (SPEC-04, SPEC-09).

## Coverage of invariants
- INV-1 fail closed → `Unauthenticated request is rejected at the edge`.
- INV-2 tenant from principal + strip → `Spoofed inbound tenant header is stripped`.
- INV-3 correlation always present → `Correlation id is generated when absent`.
- INV-4 discovery only → routing scenarios use `lb://` targets, no host asserted.
- INV-5 RFC 7807 → every error scenario asserts `application/problem+json`.
