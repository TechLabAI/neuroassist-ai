# Ref: SPEC-05-caregiver-linkage-coverage.md §5, SPEC-08-bdd-test-scenarios.md §5
Feature: Caregiver-Patient Linkage

  Background:
    Given an active tenant "t_0f8a" exists
    And the pac-service is authenticated for tenant "t_0f8a" with authority "acs.vinculo.criar"

  Scenario: Successfully link a caregiver to a patient
    Given active caregiver "5c1a" in tenant "t_0f8a"
    And PAC confirms patient "p_9a3f" belongs to tenant "t_0f8a"
    When I POST a link between caregiver "5c1a" and patient "p_9a3f"
    Then the response status is 201
    And the link status is "active"
    And the domain fact CuidadorVinculadoAoPaciente is recorded to HIST
    And caregiver "5c1a" appears in GET /api/v1/acs/directory/patients/p_9a3f/caregivers

  Scenario: Duplicate active link is idempotent (200 not 201)
    Given an active link between caregiver "5c1a" and patient "p_9a3f" exists
    When I POST the same link again
    Then the response status is 200
    And no duplicate link row is created

  Scenario: Cross-tenant link is rejected
    Given caregiver "5c1a" in tenant "t_0f8a"
    And PAC reports patient "p_9a3f" belongs to tenant "t_11cd"
    When I POST a link between caregiver "5c1a" and patient "p_9a3f"
    Then the response status is 422
    And the error type is "/errors/cross-tenant-link"

  Scenario: Link to unknown patient is rejected with 422
    Given PAC returns 404 for patient "p_unknown"
    When I POST a link between caregiver "5c1a" and patient "p_unknown"
    Then the response status is 422
    And the error type is "/errors/unknown-patient"

  Scenario: Link creation fails with 502 when PAC is unreachable
    Given PAC does not respond within 2 seconds
    When I POST a link between caregiver "5c1a" and patient "p_9a3f"
    Then the response status is 502
    And the error type is "/errors/upstream-unavailable"

  Scenario: Revoke the last caregiver link is blocked by coverage invariant
    Given caregiver "5c1a" is the only active link for patient "p_9a3f"
    And no coordinator exists in tenant "t_0f8a"
    And the pac-service is authenticated with authority "acs.vinculo.revogar"
    When I DELETE the link for caregiver "5c1a" and patient "p_9a3f"
    Then the response status is 409
    And the error type is "/errors/last-caregiver-link"

  Scenario: Revoke a link when another caregiver still covers the patient
    Given caregiver "5c1a" and caregiver "6d2b" are both linked to patient "p_9a3f"
    And the pac-service is authenticated with authority "acs.vinculo.revogar"
    When I DELETE the link for caregiver "5c1a" and patient "p_9a3f"
    Then the response status is 200
    And the link status is "revoked"
    And the domain fact CuidadorDesvinculado is recorded to HIST
    And caregiver "5c1a" does not appear in GET /api/v1/acs/directory/patients/p_9a3f/caregivers

  Scenario: Revoke already-revoked link is idempotent
    Given a revoked link between caregiver "5c1a" and patient "p_9a3f"
    And the pac-service is authenticated with authority "acs.vinculo.revogar"
    When I DELETE the link
    Then the response status is 200
    And no additional HIST fact is recorded

  Scenario: Caller without acs.vinculo.criar is rejected
    Given I am authenticated with authorities ["acs.cuidador.read"]
    When I POST a link between caregiver "5c1a" and patient "p_9a3f"
    Then the response status is 403
    And the error type is "/errors/insufficient-authority"
