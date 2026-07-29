# INT-EVNT-001 -- Freeze device-token auth contract with EDGE (gap #1: mTLS vs OAuth2 client_credentials, interim JWT-shaped token boundary)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
**Handoff gap #1 from M2.** The device-token auth for `POST /reports` is jointly owned with EDGE and
still open: SPEC-06 carries the interim JWT-shaped contract, but the final shape (mTLS vs OAuth2
`client_credentials`) must be frozen with EDGE before EVNT-019 can be hardened beyond the interim
implementation. This is an architecture alignment issue — its output is a decision record and an
updated `contracts/integration-contracts.md`, not code.

## Spec de referencia
- SPEC-06 (security — device-token open item)
- contracts/integration-contracts.md (EDGE ↔ EVNT boundary)

## Escopo
- [ ] Convene EDGE team to decide: mTLS (mutual TLS certificate) vs OAuth2 `client_credentials` (service account JWT from IAM) for the edge-to-EVNT auth channel
- [ ] Document the decision: update `contracts/integration-contracts.md` (EVNT side) and corresponding EDGE service contract
- [ ] If OAuth2 `client_credentials`: specify the IAM token endpoint, `client_id`/`scope` to request, token lifetime, and EVNT JWK validation config
- [ ] If mTLS: specify the CA chain, certificate lifecycle, and how EVNT extracts `deviceId`/`tenant` from the client certificate (SAN or custom extension)
- [ ] Update EVNT-019 scope to reflect the decided shape — replace the interim JWT filter implementation with the frozen one
- [ ] Register as architectural decision (ADR-007 or similar in `evnt-service/adr/`)

## Resultado esperado
The device-token auth shape is frozen and documented; EVNT-019 is updated to implement the agreed
mechanism; no "interim" qualifier remains in the security model.

## Criterios de aceite
- [ ] Decision recorded in `evnt-service/adr/` as ADR-007 with rationale and selected option
- [ ] `contracts/integration-contracts.md` updated with the agreed auth shape and token/certificate exchange protocol
- [ ] EVNT-019 scope updated to reference the frozen shape
- [ ] No ambiguity remains between "interim JWT" and the final mechanism

## Dependencias
- EVNT-019 (implementor of the decided shape — blocked on this issue for final hardening)

## Estimativa
S

## Prioridade
P1
