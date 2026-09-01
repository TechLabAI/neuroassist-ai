# SPEC-02 — LLM-assisted authoring session; the LLM proposes only, never publishes (ADR-016, TECH-007).

Feature: LLM-assisted authoring proposals

Background:
  Given a caregiver with scope treinamento.sessao.conduzir
  And a patient p-1024 in tenant clinica-acme

Scenario: Starting a session records the concrete model id and discloses AI
  When the caregiver starts a session for patient p-1024 in locale en
  Then a session in state ativa is created with locale_autoria en
  And the session records a concrete Anthropic llm_model_id
  And the response flag aiDisclosed is true

Scenario: A proposal turn persists every candidate before returning it
  Given an active session s-77 in locale en
  When the caregiver requests proposals with an instruction about asking to change position
  Then each returned candidate is persisted as a trei_proposta_llm row first
  And no candidate is embedded, synthesized, or indexed

Scenario: The LLM never triggers publication
  Given an active session s-77 in locale en
  When the LLM returns a phrase candidate
  Then the candidate stays in state proposta
  And the publication pipeline is not started

Scenario: LLM unavailable fails the turn cleanly
  Given the Anthropic API is unreachable
  When the caregiver requests proposals
  Then the response status is 503
  And the ProblemDetail type suffix is llm-unavailable
  And no partial proposal row is persisted

Scenario: Materializing a proposal creates a pending draft
  Given a persisted proposal pr-1 with text about changing position in locale en
  When the caregiver materializes pr-1 under parent n-root as a frase
  Then a node n-90 is created with content status pendente
  And both approval flags are false
  And the proposal pr-1 is marked materializada
