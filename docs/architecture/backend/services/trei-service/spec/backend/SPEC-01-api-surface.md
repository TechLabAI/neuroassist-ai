# SPEC-01 — API Surface

> Uses EARS acceptance criteria ([`../../../../standards/ears-notation.md`](../../../../standards/ears-notation.md), D-057).
> Canonical contract: [`../../contracts/api-contract.md`](../../contracts/api-contract.md).

## Objetivo

Definir a superfície REST de `trei-service`: os endpoints de autoria (sessão, proposta, materialização),
os dois endpoints de aprovação (cuidadora e paciente), inspeção/descarte e status de publicação — todos
sob `/api/v1/trei`, roteados pelo GWY, resource server no realm ACS.

## Escopo

- **In:** endpoints §2.1–2.9 da api-contract; RFC 7807; headers obrigatórios; escopos por endpoint.
- **Out:** runtime do CONV (navegação/seleção/kNN) — outro serviço; UI (DASH/patient shell).

## Decisões técnicas

- Base pública `/api/v1/trei` (shared `ADR-008-public-api-surface-normalization.md`).
- Dois atores autenticados no realm ACS: cuidadora (DASH) e patient-with-session (`ADR-012`).
- `empresa` e `pacienteId` derivam do JWT/escopo de sessão — nunca do body/query.
- Locale de autoria é **explícito** por operação (`pt`|`en`), não inferido de `Accept-Language` (ADR-018).

## Implementação

- Controllers em `api/` finos; regra de negócio em `application/`. O gate de dupla autorização vive na
  camada `application` (SPEC-03), não como justaposição de endpoints.
- Erros centralizados via `@RestControllerAdvice` → `ProblemDetail` (catálogo api-contract §3).

## Casos de borda

- Body com dado biométrico/imagem → `400 no-biometric-ingress`.
- Ator errado no endpoint de aprovação → `403 forbidden-scope`.
- `pacienteId` fora da `empresa` do chamador → `404 patient-not-found`.

## Critérios de aceite (EARS)

- THE SYSTEM SHALL expose exactly the endpoints of api-contract §2 under `/api/v1/trei`.
- WHEN a request lacks `X-Correlation-Id`, THE SYSTEM SHALL echo the GWY-injected value and propagate it
  to every downstream call (Anthropic, ElevenLabs, sidecar, ES, ACS, PAC, HIST).
- THE SYSTEM SHALL derive `empresa`/`pacienteId` from the validated JWT and SHALL ignore any such
  identifier in body/query.
- IF a request carries biometric/image/landmark data, THEN THE SYSTEM SHALL reject it with `400`
  (`no-biometric-ingress`).
- THE SYSTEM SHALL return every error as an RFC 7807 `ProblemDetail` with `type`, `status`, `detail`,
  `instance`, `correlationId`.
