# SPEC-08 — E2E Scenarios — TREI (Frontend)

> The Gherkin scenarios that exercise the TREI-FE success signals (vision doc 01 §7) and
> the client-side invariants (doc 03 §5). Each bullet below is materialized as a `.feature`
> file in `../e2e/`. This SPEC lists scenarios only and carries **no** `## Criterios de
> aceite (EARS)` section; the EARS rules it validates live in SPEC-01..07 and are covered
> by the `Cenario:` blocks in the `.feature` files (matched by `fe-ears-coverage.py`).

## 1. Feature files and coverage

| `.feature` | Success signal / invariant | Covers EARS from |
|---|---|---|
| `autoria-e-aprovacao-cuidadora.feature` | Golden path: author a phrase via the assistant and approve the caregiver side; publication stays pending | SPEC-01, SPEC-03, SPEC-04 |
| `independencia-de-locale.feature` | Locale integrity: switching PT⇄EN re-projects proposals; approving one locale never flips the other | SPEC-03, SPEC-04 |
| `apenas-lado-cuidadora.feature` | Approval-side integrity (INV-1): zero patient-side writes from any TREI route | SPEC-04, SPEC-05 |
| `geracao-server-mediated.feature` | Zero client-side generation (INV-2): no `api.anthropic.com` call, no browser AI SDK | SPEC-04, SPEC-06 |
| `ciclo-de-sessao.feature` | No-loss session end + empty state + authoring affordance present | SPEC-01, SPEC-03, SPEC-06 |
| `erros-e-degradacao.feature` | Retriable LLM error, optimistic approval rollback, discard conflict, RBAC/guards | SPEC-01, SPEC-05, SPEC-07 |

## 2. Scenario intent (prose)

- **Golden path** — an empty state offers "Iniciar treinamento"; the caregiver starts a
  session (`POST /sessoes`), sends a guidance turn (`POST /sessoes/{id}/propostas`), the
  assistant proposes phrases, she materializes one (`POST /nos`) and approves her side
  (`POST /nos/{noId}/aprovacoes/cuidadora`); the card shows `✅ cuidadora / ⏳ paciente`
  and publication is **not** triggered (`publicacaoDisparada: false`).
- **Locale independence** — with a node approved in PT, switching to EN shows it still
  `⏳ cuidadora`; approving EN writes only the EN side; PT is unchanged.
- **Caregiver-side only** — the UI exposes no control that could set `confirmadoPaciente`;
  a network trace shows TREI never calls `/aprovacoes/paciente`.
- **Server-mediated generation** — across a full session, no request leaves the browser for
  `api.anthropic.com`; the bundle carries no Anthropic SDK; assistant text appears only
  after `trei-service` responds.
- **Session lifecycle** — ending a session with pending drafts shows a retention notice and
  loses nothing; the "não é o app do paciente" affordance is present on every route; no
  patient runtime surface is ever rendered.
- **Errors & degradation** — an `llm-unavailable` (503) shows a retriable banner while
  existing proposals stay approvable; an optimistic approve that fails rolls back and
  re-confirms; discarding an already-published locale shows a conflict toast; missing scopes
  hide/disable the right actions.
