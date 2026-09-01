# SPEC-01 — API Surface

## Objetivo
Definir a superfície REST pública do `conv-service` sob `/api/v1/conv/**`, com envelope de erros RFC 7807,
headers obrigatórios e as invariantes por endpoint. Fonte canônica dos contratos:
[`../../contracts/api-contract.md`](../../contracts/api-contract.md). EARS por
[`../../../../standards/ears-notation.md`](../../../../standards/ears-notation.md).

## Escopo
**In:** endpoints `GET /tree`, `POST /search`, `POST /nodes/{id}/select`, `POST /bindings/resolve`,
`GET /health`; ProblemDetail; headers `Authorization`, `X-Correlation-Id`, `Accept-Language`,
`X-Degraded`; versionamento `/v1`→`/v2`.
**Out:** lógica de busca (SPEC-03), seleção/áudio (SPEC-02/12), segurança detalhada (SPEC-06).

## Decisões técnicas
- Superfície normalizada `/api/v1/conv/**` roteada por GWY (shared `ADR-008`).
- Tenant/paciente derivados **exclusivamente** do JWT (nunca do corpo/query).
- Guard de não-ingestão biométrica no boundary (`400`), consistente com SEC-002.

## Implementação
- Controllers finos em `api/`; DTOs com bean-validation; `@ControllerAdvice` central emitindo
  `ProblemDetail` com `correlationId`.
- `X-Correlation-Id` lido/injetado por GWY e ecoado; propagado a ACS/PAC/GEST/HIST.
- `Accept-Language` resolve override de locale dentro de `localesSuportados`.

## Casos de borda
- Ausência de `X-Correlation-Id` → aceitar o injetado por GWY.
- Corpo com dados de imagem/landmark → `400 no-biometric-ingress`.
- Locale fora de `localesSuportados` → `400 unsupported-locale`.

## Critérios de aceite (EARS)
- THE SYSTEM SHALL expose exactly the endpoints of api-contract §2 under `/api/v1/conv`.
- WHEN a request lacks `X-Correlation-Id`, THE SYSTEM SHALL accept the GWY-injected value and echo it.
- THE SYSTEM SHALL return every error as an RFC 7807 `ProblemDetail` with `type`, `status`, `detail`, `instance`, `correlationId`.
- IF a request carries image/frame/landmark data, THEN THE SYSTEM SHALL reject it with `400`.
- WHEN a breaking change is needed, THE SYSTEM SHALL introduce `/api/v2/conv` and keep `/api/v1/conv`.
