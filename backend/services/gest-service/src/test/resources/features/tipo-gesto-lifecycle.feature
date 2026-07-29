# Feature: TipoGesto detectability lifecycle
# Covers: ADR-006, SPEC-02, data-model §2.1/§2.2
# Invariants:
#   #4 — no silent regression: transition from supported requires non-null reason
#   #5 — supported requires engine_feature_ref (ck_tipo_gesto_supported_needs_ref)
#   append-only audit trail in tipo_gesto_transition

Feature: TipoGesto detectability lifecycle

  Background:
    Given the tenant "empresa-test" is active
    And the caller is authenticated with role "gest.manage"
    And no TipoGesto named "piscar duplo" exists in the tenant

  # --- Registration ---

  Scenario: Register a new primitive — entry state is always 'registered'
    When the conductor registers a primitive with nome "piscar duplo"
    Then the response status is 201
    And the returned TipoGesto has detectabilityState "registered"
    And the returned TipoGesto has engineFeatureRef null
    And a transition record exists with fromState null and toState "registered"

  Scenario: Duplicate nome rejected for active primitives
    Given a TipoGesto "piscar duplo" exists in state "pending_training"
    When the conductor registers a primitive with nome "Piscar Duplo"
    Then the response status is 409
    And the error type is "/errors/tipo-gesto-duplicate"

  Scenario: Deprecated nome can be re-registered
    Given a TipoGesto "piscar duplo" exists in state "deprecated"
    When the conductor registers a primitive with nome "piscar duplo"
    Then the response status is 201
    And the returned TipoGesto has detectabilityState "registered"

  Scenario: Blank nome rejected
    When the conductor registers a primitive with nome ""
    Then the response status is 400
    And the error type is "/errors/validation"

  Scenario: Nome exceeding 120 characters rejected
    When the conductor registers a primitive with nome of length 121
    Then the response status is 400
    And the error type is "/errors/validation"

  # --- Lifecycle transitions ---

  Scenario: Transition registered -> pending_training
    Given a TipoGesto "piscar duplo" exists in state "registered"
    When the conductor transitions "piscar duplo" to state "pending_training" with reason "começando treinamento"
    Then the response status is 200
    And the returned TipoGesto has detectabilityState "pending_training"
    And a new audit record exists for the transition with toState "pending_training"

  Scenario: Transition pending_training -> supported requires engineFeatureRef — invariant #5
    Given a TipoGesto "piscar duplo" exists in state "pending_training" with engineFeatureRef null
    When the conductor transitions "piscar duplo" to state "supported" without engineFeatureRef
    Then the response status is 422
    And the error type is "/errors/engine-feature-ref-required"
    And the detectabilityState of "piscar duplo" remains "pending_training"

  Scenario: Transition pending_training -> supported with engineFeatureRef succeeds
    Given a TipoGesto "piscar duplo" exists in state "pending_training"
    When the conductor transitions "piscar duplo" to state "supported" with engineFeatureRef "mp.blink.double.v1"
    Then the response status is 200
    And the returned TipoGesto has detectabilityState "supported"
    And the returned TipoGesto has engineFeatureRef "mp.blink.double.v1"
    And a new audit record exists for the transition with toState "supported"

  Scenario: Illegal transition registered -> supported (not a seed path) is rejected
    Given a TipoGesto "piscar duplo" exists in state "registered"
    When the conductor transitions "piscar duplo" to state "deprecated"
    Then the response status is 409
    And the error type is "/errors/illegal-transition"
    And the detectabilityState of "piscar duplo" remains "registered"

  Scenario: Regression from supported requires explicit reason — invariant #4
    Given a TipoGesto "piscar duplo" exists in state "supported"
    When the conductor transitions "piscar duplo" to state "deprecated" without reason
    Then the response status is 400
    And the error type is "/errors/regression-reason-required"

  Scenario: Regression from supported -> deprecated with reason is allowed
    Given a TipoGesto "piscar duplo" exists in state "supported" with engineFeatureRef "mp.blink.double.v1"
    When the conductor transitions "piscar duplo" to state "deprecated" with reason "feature removida do engine v2"
    Then the response status is 200
    And the returned TipoGesto has detectabilityState "deprecated"
    And a new audit record exists for the transition with toState "deprecated"

  Scenario: Transition audit is append-only — history is never modified
    Given a TipoGesto "piscar duplo" has gone through transitions "registered" -> "pending_training" -> "supported"
    When the audit trail is queried for "piscar duplo"
    Then exactly 2 transition records exist in chronological order
    And no transition record has been modified

  # --- Authorization ---

  Scenario: Non-conductor role cannot register primitives
    Given the caller has role "gest.read" only
    When the caller registers a primitive with nome "novo gesto"
    Then the response status is 403
    And the error type is "/errors/insufficient-scope"

  Scenario: Non-conductor role cannot drive lifecycle transitions
    Given a TipoGesto "piscar duplo" exists in state "registered"
    And the caller has role "gest.read" only
    When the caller transitions "piscar duplo" to state "pending_training"
    Then the response status is 403
    And the error type is "/errors/insufficient-scope"

  # --- Isolation ---

  Scenario Outline: Tenant isolation — primitives are not visible across tenants
    Given a TipoGesto "<nome>" exists in tenant "empresa-test"
    When a caller from tenant "empresa-other" lists the primitive catalog
    Then the response items do not contain a primitive named "<nome>"

    Examples:
      | nome         |
      | piscar duplo |
      | sorriso leve |
