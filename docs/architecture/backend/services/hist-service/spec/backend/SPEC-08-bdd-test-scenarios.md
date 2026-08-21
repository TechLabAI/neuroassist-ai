# SPEC-08 — BDD Test Scenarios

> Service: `hist-service` · Module: HIST · Refs: ADR-004, ADR-005, ADR-006 · EARS notation (D-057).
> Gherkin features (executáveis) em `tests/cucumber/*.feature`. Esta spec descreve steps reutilizáveis,
> dados de exemplo e o mapeamento feature → SPEC.

## Objetivo

Ancorar os cenários BDD que provam os invariantes de HIST e mapear cada feature às SPECs/ADRs.

## Features (arquivos `.feature`)

| Feature | Cobre | SPEC |
|---|---|---|
| `audit-fact-ingestion.feature` | append write-once, 201/200, validações | SPEC-02 |
| `idempotency-and-concurrency.feature` | exactly-once por factId, ordenação de stream | SPEC-05 |
| `hash-chain-verification.feature` | intact / broken@N / empty | SPEC-03, ADR-006 |
| `history-query.feature` | timeline filtrada, feedback-verdict, scope, paginação | SPEC-03 |

## Steps reutilizáveis (glue)

- `Given a provisioned tenant "<empresa>"` — pina schema `t_<empresa>`.
- `Given a producer token with scope "hist:write"` / `a caregiver token with scope "hist:read"` /
  `a coordinator token with scope "hist:read:all"`.
- `Given an audit fact <factId> of type "<factType>" for subject <type>:<id> by caregiver <cid>`.
- `When it is submitted to POST /audit-facts` / `When it is submitted twice`.
- `Then the response status is <code>` / `Then the record has sequenceNo <n>` /
  `Then the stream "<streamKey>" verifies as "<status>"`.

## Dados de exemplo

- `factType` válidos: `evento.confirmado`, `comando.recalibrado`, `acesso.concedido`, `dispositivo.pareado`.
- `factType` inválido (para 422): `evento.xpto`.
- `payload` proibido (para 422): `{ "landmarks": [...] }`.
- genesis `prevHash`: 64 zeros.

## Critérios de aceite (EARS)

- THE SYSTEM SHALL be covered by at least four Gherkin feature files exercising ingestion, idempotency,
  chain verification and history query.
- WHEN the BDD suite runs, THE SYSTEM SHALL demonstrate write-once (no update/delete), exactly-once per
  `factId`, gap-free `sequenceNo`, and correct `intact/broken/empty` verification outcomes.
- THE SYSTEM SHALL demonstrate a `422` for an unknown `factType` and for a forbidden biometric payload key.
