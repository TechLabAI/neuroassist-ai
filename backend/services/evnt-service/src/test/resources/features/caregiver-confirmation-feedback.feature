# Refs: SPEC-03? no -> SPEC-02/ADR-006 · api-contract §3.4 · event-contracts §2.4/§2.5
Feature: Caregiver confirmation and false-positive feedback

Background:
  Given a tenant "empresa_demo" with an Evento "evt-1" in feedback state "nao_avaliado"
  And a caregiver session for "cg-1" authorized for the Evento's patient
  And ACS resolves "cg-1" as a valid Cuidador

Scenario: A caregiver confirms an event as real
  When the caregiver posts verdict "real" to "/api/v1/events/evt-1/confirmation"
  Then the response status is 200
  And the Evento "evt-1" has feedback "real"
  And the Evento "evt-1" records confirmadoPor "cg-1" and a confirmadoAt timestamp
  And an audit fact "EventoConfirmado" was appended to HIST

Scenario: A caregiver marks an event as a false positive
  When the caregiver posts verdict "falso_positivo" to "/api/v1/events/evt-1/confirmation"
  Then the response status is 200
  And the Evento "evt-1" has feedback "falso_positivo"
  And an audit fact "FalsoPositivoMarcado" was appended to HIST
  And the false-positive rate for the Evento's command reflects the new verdict

Scenario: Feedback is terminal - a conflicting second verdict is rejected
  Given the Evento "evt-1" already has feedback "real"
  When the caregiver posts verdict "falso_positivo" to "/api/v1/events/evt-1/confirmation"
  Then the response status is 409
  And the ProblemDetail field "type" is "/errors/feedback-terminal"
  And the Evento "evt-1" still has feedback "real"

Scenario: Re-submitting the same verdict is idempotent
  Given the Evento "evt-1" already has feedback "real"
  When the caregiver posts verdict "real" to "/api/v1/events/evt-1/confirmation"
  Then the response status is 200

Scenario: A verdict requires a resolvable confirmer
  Given ACS cannot resolve the acting caregiver
  When the caregiver posts verdict "real" to "/api/v1/events/evt-1/confirmation"
  Then the response status is 422
  And the ProblemDetail field "type" is "/errors/confirmador-unresolved"
  And the Evento "evt-1" still has feedback "nao_avaliado"

Scenario: A caregiver from another tenant cannot confirm the event
  # E2E-022 note: with schema-per-tenant routing (MultiTenantConnectionProvider), the Evento
  # lives in "empresa_demo".evento and is NOT visible in "empresa_other".evento. The request is
  # correctly rejected, but the response is 404 — the event does not exist in that tenant's schema —
  # rather than 403. The application-level tenant-mismatch check at EventConfirmationService#confirm
  # (defense-in-depth) is unreachable in practice because the schema isolation prevents the Evento
  # from being found in the first place. 404 is actually safer (does not reveal resource existence).
  Given a caregiver session whose tenant is not the Evento's tenant
  When the caregiver posts verdict "real" to "/api/v1/events/evt-1/confirmation"
  Then the response status is 404
