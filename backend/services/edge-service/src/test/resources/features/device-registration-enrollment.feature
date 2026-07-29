# Feature: Device registration and enrollment (bootstrap trust)
# Covers: SPEC-11, SPEC-06, api-contract §2/§6.3, data-model §2.2/§2.3, ADR-005
# Invariants:
#   - every new device starts in state 'registered'
#   - enrollment token is one-time, TTL-bounded (default 15 min), hash-stored
#   - device credential (client_credentials) issued once; secret returned once, stored hashed
#   - registration is operator-driven (edge.manage); enroll is token-guarded (no Bearer)

Feature: Device registration and enrollment

  Background:
    Given the tenant "empresa-test" is active
    And the tenant schema is provisioned

  # --- Registration (operator) ---

  Scenario: Operator registers a device and receives a one-time enrollment token
    Given the caller has role "edge.manage"
    When the caller registers a device with label "Camera quarto do Marcio"
    Then the response status is 201
    And the response body has pairingState "registered"
    And the response body contains an enrollmentToken
    And the response body has an enrollmentExpiresAt within 15 minutes
    And the enrollment token is stored only as a hash

  Scenario: A caregiver without the conductor role cannot register a device
    Given the caller has role "edge.read"
    When the caller registers a device with label "Camera sala"
    Then the response status is 403
    And the error type is "/errors/insufficient-scope"

  # --- Enrollment (edge agent) ---

  Scenario: The edge agent exchanges a valid enrollment token for its device credential
    Given a device is registered with a valid enrollment token
    When the edge agent enrolls with that token and agentVersion "1.4.2"
    Then the response status is 200
    And the response body contains a clientId and a clientSecret
    And the clientSecret is returned exactly once
    And the credential is stored only as a hash
    And exactly one active credential exists for the device

  Scenario: Enrolling with an expired token is rejected
    Given a device is registered with an enrollment token that expired 1 minute ago
    When the edge agent enrolls with that token
    Then the response status is 401
    And the error type is "/errors/enrollment-invalid"

  Scenario: An enrollment token cannot be used twice
    Given a device has already completed enrollment with its token
    When the edge agent enrolls again with the same token
    Then the response status is 401
    And the error type is "/errors/enrollment-invalid"

  Scenario: Enrolling an already-enrolled device is a conflict
    Given a device is already enrolled and in state "paired"
    When the edge agent enrolls that device again
    Then the response status is 409
    And the error type is "/errors/already-enrolled"

  # --- Retire ---

  Scenario: Retiring a device revokes its credential and is terminal
    Given a device "d7b1" is enrolled and paired
    And the caller has role "edge.manage"
    When the caller retires device "d7b1"
    Then the response status is 200
    And the response body has pairingState "retired"
    And the device credential status is "revoked"
    And the device row is not deleted
