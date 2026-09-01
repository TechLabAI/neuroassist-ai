# SPEC-03 — Dual-Authorization Gate (core sub-feature)

> EARS acceptance criteria (D-057). Decision: local ADR-003 (materializes ADR-016).
> Feature: [`../../tests/cucumber/dual-authorization-gate.feature`](../../tests/cucumber/dual-authorization-gate.feature).

## Objetivo

Implementar o invariante de domínio central de TREI: um `NoArvoreFala` proposto só é publicado após
**duas ações RBAC distintas e obrigatórias** — `treinamento.no.aprovar.cuidadora` (via ACS/DASH) **e**
`treinamento.no.aprovar.paciente` (via patient-with-session, ADR-012) — assíncronas, em shells e
identidades diferentes. O gate vive na camada `application`, não como par de endpoints.

## Escopo

- **In:** `AprovarNoCuidadora`, `AprovarNoPaciente`, avaliação do gate por `(no_id, locale)`,
  disparo único do pipeline; fail-closed com blacklist ACS.
- **Out:** os passos do pipeline (SPEC-12); autoria/proposta (SPEC-02).

## Decisões técnicas

- Dois booleanos independentes em `conv_no_conteudo` (`confirmado_cuidadora`, `confirmado_paciente`),
  **por (nó, locale)** — CONV já os modela; TREI escreve.
- Aprovações são idempotentes; reemitir não duplica efeito (SPEC-05).
- Ator correto por endpoint: token de cuidadora não aprova pelo paciente e vice-versa (`403`).

## Implementação

- `AprovarNoPaciente` (e `AprovarNoCuidadora`) setam o flag, então chamam `NoArvoreFala.avaliarGate()`.
  Quando ambos `true`, emitem `DualAuthorizationGateCleared` → cria os 3 passos de outbox exatamente uma
  vez e inicia o pipeline (SPEC-12).
- Validação de sessão do paciente via `AcsSessionPort` (assinatura + blacklist) a cada request.

## Casos de borda

- Só um aprova → nó fica `aguardando_aprovacao` indefinidamente (ORG-001, sem expiração; descarte manual).
- Token do paciente revogado (blacklist) → `401 session-revoked` sempre (fail-closed).
- Reversão para Opção 2 (só cuidadora): ignorar o flag do paciente no gate — **sem mudança de schema**
  (ADR-003 §Reversão).

## Critérios de aceite (EARS)

- IF either approval flag is `false`, THEN THE SYSTEM SHALL NOT create outbox rows and SHALL NOT call the
  embedding sidecar, ElevenLabs, or ES.
- WHEN both `confirmado_cuidadora` AND `confirmado_paciente` are `true` for a `(no_id, locale)`, THE SYSTEM
  SHALL start the publication pipeline exactly once (idempotent on replay).
- IF a caregiver token calls the patient approval endpoint (or vice-versa), THEN THE SYSTEM SHALL reject
  with `403` even when a valid TREI scope is present.
- IF the patient token is on the ACS blacklist, THEN THE SYSTEM SHALL reject with `401` (`session-revoked`)
  on every call.
- WHEN an approval is re-issued for an already-approved `(no_id, locale)`, THE SYSTEM SHALL treat it as an
  idempotent no-op.
