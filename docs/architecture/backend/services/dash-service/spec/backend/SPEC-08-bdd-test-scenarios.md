# SPEC-08 — BDD Test Scenarios

## Objetivo
Descrever, em prosa, os cenários BDD do DASH e os steps reutilizáveis. Os `.feature` puros vivem em
`tests/cucumber/`.

## Features (Gherkin puro)
- `tests/cucumber/panel-composition.feature` — composição BFF, degradação por seção, forward de filtros HIST.
- `tests/cucumber/notification-delivery.feature` — entrega SSE, catch-up, idempotência, tier vindo do EVNT.
- `tests/cucumber/write-through-actions.feature` — write-through PAC/EVNT, relay de rejeição, escopo/403/404.

## Steps reutilizáveis (World compartilhado)
- **Auth/scope:** `a caregiver "<X>" authenticated ... for tenant "<T>"`, `<P> is in <X>'s scope`,
  `<P> is NOT in <X>'s scope`.
- **Upstream availability:** `<SVC> is available|unreachable`, `PAC rejects ... with status <n>`.
- **SSE session:** `<X> has an open|no SSE session`, `<X> connects to /panel/stream`.
- **Dispatch:** `EVNT dispatches notification "<d>" with tier "<t>" for caregiver "<X>"`.
- **Assertions:** `DASH reports delivered("<d>")`, `DASH pushes an SSE "notification" event carrying dispatchId`,
  `response status is <n>`, `degradedSections contains "<svc>"`.

## Estratégia de teste
- **Contract/slice tests** com upstreams stubbados (WireMock) — DASH não tem banco, então sem Testcontainers
  de Postgres no MVP.
- **SSE** testado via cliente `WebTestClient`/`SseEmitter` capturando eventos.
- **Idempotência** verificada por contagem de callbacks ao stub do EVNT.

## Cobertura mínima (mapeada a SPECs)
| Cenário | SPEC |
|---|---|
| Fan-out + degradação por seção | SPEC-02 |
| Entrega SSE + catch-up + tier | SPEC-03 |
| Idempotência dispatch/ack | SPEC-05 |
| Escopo 403 sem vazar existência | SPEC-06 |
| Write-through + relay de erro | SPEC-02/API §5 |
