# SPEC-03 — Dual-authorization gate (ADR-003). The gate is a domain invariant.

Feature: Dual-authorization gate for publishing a speech node

Background:
  Given a patient p-1024 in tenant clinica-acme
  And an authoring session started by the caregiver in locale en
  And a draft node n-90 with locale en in sub-state rascunho
  And both approval flags are false

Scenario: Neither approval — nothing is published
  When no approval has been issued for node n-90 locale en
  Then the publication pipeline is not started
  And no call is made to the embedding sidecar, ElevenLabs, or Elasticsearch
  And the content status for node n-90 locale en stays pendente

Scenario: Caregiver only — still gated
  Given the caregiver approves node n-90 locale en
  Then the flag confirmadoCuidadora is true
  And the flag confirmadoPaciente is false
  And the sub-state is aguardando_aprovacao
  And publicacaoDisparada is false

Scenario: Patient only — still gated
  Given the patient approves node n-90 locale en
  Then the flag confirmadoPaciente is true
  And the flag confirmadoCuidadora is false
  And publicacaoDisparada is false

Scenario: Both approvals close the gate and start publication exactly once
  Given the caregiver approves node n-90 locale en
  And the patient approves node n-90 locale en
  Then both approval flags are true
  And the sub-state is publicando
  And publicacaoDisparada is true
  And exactly one set of outbox steps embed, tts, index is created

Scenario: Wrong actor is rejected
  When a caregiver token calls the patient approval endpoint for node n-90 locale en
  Then the response status is 403
  And the ProblemDetail type suffix is forbidden-scope

Scenario: Revoked patient session is fail-closed
  Given the patient token is on the ACS revocation blacklist
  When the patient approval is attempted for node n-90 locale en
  Then the response status is 401
  And the ProblemDetail type suffix is session-revoked

Scenario: Re-issuing an approval is idempotent
  Given the caregiver approves node n-90 locale en
  When the caregiver approval for node n-90 locale en is re-issued
  Then the response status is 200
  And no duplicate approval side effect occurs
