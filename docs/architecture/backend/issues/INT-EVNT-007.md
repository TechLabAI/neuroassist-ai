# INT-EVNT-007 -- Define false-positive rate consumer interface with PAC (gap #5: sensitivity tuning API - PAC reads /events?groupBy=comando to adjust sensibilidade)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
**Handoff gap #5 from M2.** EVNT exposes a false-positive rate aggregate via
`GET /events?groupBy=comando&comandoRef=<ref>` (api-contract §3.6 + SPEC-02). The consumer of this
rate — PAC's sensitivity tuning — is undefined: who calls this API, how often, and what does PAC
do with it? This issue defines the consumer interface so that PAC (a different service + team) can
implement the sensitivity-adjustment loop without an implicit contract.

## Spec de referencia
- SPEC-02 (false-positive rate queryable aggregate)
- contracts/api-contract.md §3.6

## Escopo
- [ ] Define the consumer interface in `contracts/integration-contracts.md`: PAC calls `GET /api/v1/events?groupBy=comando&comandoRef={ref}&from={lastNDays}` on a scheduled basis (recommended: daily or on feedback threshold); response field `falsePositiveRate` (0.0–1.0); PAC-side action = increment `sensibilidade` of the `Comando` when rate exceeds configurable threshold
- [ ] Document the recommended polling cadence (daily batch vs. event-driven via domain event `FalsoPositivoMarcado` — MVP: daily poll; broker-enabled future: subscribe to `FalsoPositivoMarcado`)
- [ ] Define the auth model for PAC's call: uses a service account JWT with scope `evnt:read` (consistent with caregiver auth — same resource server, different role)
- [ ] Add API documentation comment to `EventQueryController.listAll` clarifying it is the sensitivity-tuning consumer endpoint
- [ ] Register the consumer interface decision (ADR-009 or note in ADR-006)

## Resultado esperado
PAC team has a clear, documented consumer interface for the false-positive rate API; no guesswork
about polling cadence, auth, or threshold semantics.

## Criterios de aceite
- [ ] `contracts/integration-contracts.md` contains a PAC→EVNT section specifying the `GET /events?groupBy=comando` call, its auth (service account `evnt:read`), polling cadence, and the response field PAC acts on
- [ ] Decision recorded in ADR-009 (or ADR-006 appendix): MVP = daily poll vs. broker-future = `FalsoPositivoMarcado` event subscription
- [ ] `EventQueryController.listAll` has a code comment / JavaDoc marking it as the sensitivity-tuning consumer endpoint

## Dependencias
- EVNT-003 (EventQueryController must expose `GET /events?groupBy=comando`)
- EVNT-008 (FalsoPositivoMarcado — the event that future broker consumers will subscribe to)

## Estimativa
S

## Prioridade
P9
