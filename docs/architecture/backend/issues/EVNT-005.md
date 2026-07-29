# EVNT-005 -- Implement GlobalExceptionHandler (RFC 7807 ProblemDetail + correlationId on all errors)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
A single `@ControllerAdvice` that maps every domain/validation failure to an RFC 7807
`application/problem+json` `ProblemDetail` body. Every response — success or error — must carry
`correlationId` (from the `X-Correlation-Id` filter set up in EVNT-002). Without this handler, Spring
returns different error shapes for different exception types, breaking the `api-contract.md §6` contract.

## Spec de referencia
- SPEC-01 (API surface §6 — errors)
- contracts/api-contract.md §6

## Escopo
- [ ] Create `EvntExceptionHandler` annotated `@ControllerAdvice` in `api` package
- [ ] Map `MethodArgumentNotValidException` → `400` `ProblemDetail` with field-level `detail`
- [ ] Map `ConstraintViolationException` → `400`
- [ ] Map each domain exception to its error `type` as per api-contract §6 table (e.g., `FeedbackTerminalException` → `409 /errors/feedback-terminal`, `EventoNotFoundException` → `404 /errors/event-not-found`, `ConfirmadorUnresolvedException` → `422`, `UpstreamUnavailableException` → `502/504`, etc.)
- [ ] Inject `correlationId` into every `ProblemDetail` from the current request attribute (set by X-Correlation-Id filter)
- [ ] Unit tests: each mapped exception → expected HTTP status + `type` + `correlationId` present

## Resultado esperado
All exceptions produce RFC 7807 `ProblemDetail` with the exact `type`, `status`, `detail`, `instance`,
and `correlationId` fields documented in `api-contract.md §6`.

## Criterios de aceite
- [ ] Every exception in api-contract §6 maps to the correct HTTP status and `type` URI
- [ ] `ProblemDetail` always contains `type`, `title`, `status`, `detail`, `instance`, `correlationId`
- [ ] `correlationId` is never null or empty in any error response
- [ ] Unknown/unmapped exceptions return `500` with `type=/errors/internal` — no stack traces in body

## Dependencias
- EVNT-001 (scaffold)

## Estimativa
S

## Prioridade
P1
