# Feature: Eligibility check — emergency-command guard
# Covers: SPEC-03, api-contract §3, ADR-006 invariant #1
# Safety invariant: a comando of urgencia 'emergencia' is only eligible
# when EVERY referenced primitive is in state 'supported'.
# Read-only: endpoint must produce no side effects / state mutations.

Feature: Eligibility check — emergency-command guard

  Background:
    Given the tenant "empresa-test" is active
    And the caller is authenticated with role "gest.read"

  # --- Emergency urgency: ALL primitives must be 'supported' ---

  Scenario: Emergency command is eligible when all primitives are supported
    Given primitive "3f1c-aaa" is in state "supported" in tenant "empresa-test"
    And primitive "9a20-bbb" is in state "supported" in tenant "empresa-test"
    When the caller checks eligibility with urgencia "emergencia" for primitives ["3f1c-aaa", "9a20-bbb"]
    Then the response status is 200
    And "eligible" is true
    And result for "3f1c-aaa" has eligible true
    And result for "9a20-bbb" has eligible true

  Scenario: Emergency command is NOT eligible when any primitive is 'registered'
    Given primitive "3f1c-aaa" is in state "supported" in tenant "empresa-test"
    And primitive "9a20-bbb" is in state "registered" in tenant "empresa-test"
    When the caller checks eligibility with urgencia "emergencia" for primitives ["3f1c-aaa", "9a20-bbb"]
    Then the response status is 200
    And "eligible" is false
    And result for "9a20-bbb" has eligible false with reason "primitive is not 'supported'"

  Scenario: Emergency command is NOT eligible when any primitive is 'pending_training'
    Given primitive "3f1c-aaa" is in state "pending_training" in tenant "empresa-test"
    When the caller checks eligibility with urgencia "emergencia" for primitives ["3f1c-aaa"]
    Then the response status is 200
    And "eligible" is false
    And result for "3f1c-aaa" has eligible false

  Scenario: Emergency composite gesture is eligible only when both primitives are supported
    # invariant #1 — for composite gestures both primitives SHALL be 'supported'
    Given primitive "3f1c-aaa" is in state "supported" in tenant "empresa-test"
    And primitive "9a20-bbb" is in state "supported" in tenant "empresa-test"
    When the caller checks eligibility with urgencia "emergencia" for primitives ["3f1c-aaa", "9a20-bbb"]
    Then the response status is 200
    And "eligible" is true

  Scenario: Emergency composite gesture is ineligible when one primitive is not supported
    Given primitive "3f1c-aaa" is in state "supported" in tenant "empresa-test"
    And primitive "9a20-bbb" is in state "pending_training" in tenant "empresa-test"
    When the caller checks eligibility with urgencia "emergencia" for primitives ["3f1c-aaa", "9a20-bbb"]
    Then the response status is 200
    And "eligible" is false

  # --- Routine urgency: primitives must exist and not be deprecated ---

  Scenario: Routine command is eligible when all primitives are not deprecated
    Given primitive "3f1c-aaa" is in state "pending_training" in tenant "empresa-test"
    When the caller checks eligibility with urgencia "rotina" for primitives ["3f1c-aaa"]
    Then the response status is 200
    And "eligible" is true

  Scenario: Routine command is NOT eligible when any primitive is deprecated
    Given primitive "3f1c-aaa" is in state "deprecated" in tenant "empresa-test"
    When the caller checks eligibility with urgencia "rotina" for primitives ["3f1c-aaa"]
    Then the response status is 200
    And "eligible" is false

  # --- Unknown primitives ---

  Scenario: Unknown primitiveId in check returns 422
    When the caller checks eligibility with urgencia "emergencia" for primitives ["unknown-id-xyz"]
    Then the response status is 422
    And the error type is "/errors/unknown-primitive"
    And the error detail lists "unknown-id-xyz"

  Scenario: Mix of known and unknown primitiveIds returns 422 listing all unknowns
    Given primitive "3f1c-aaa" is in state "supported" in tenant "empresa-test"
    When the caller checks eligibility with urgencia "emergencia" for primitives ["3f1c-aaa", "ghost-1", "ghost-2"]
    Then the response status is 422
    And the error type is "/errors/unknown-primitive"
    And the error detail lists "ghost-1" and "ghost-2"

  # --- Idempotency / read-only ---

  Scenario: Eligibility check is idempotent — repeated calls produce same result without mutation
    Given primitive "3f1c-aaa" is in state "supported" in tenant "empresa-test"
    When the caller checks eligibility with urgencia "emergencia" for primitives ["3f1c-aaa"]
    And the caller checks eligibility with urgencia "emergencia" for primitives ["3f1c-aaa"] again
    Then both responses are 200 with identical "eligible" value
    And the detectabilityState of "3f1c-aaa" has not changed

  # --- Cross-tenant isolation ---

  Scenario: Primitives from another tenant are not visible to the eligibility check
    Given primitive "3f1c-aaa" exists in tenant "empresa-other" in state "supported"
    When a caller from tenant "empresa-test" checks eligibility for primitives ["3f1c-aaa"]
    Then the response status is 422
    And the error type is "/errors/unknown-primitive"
