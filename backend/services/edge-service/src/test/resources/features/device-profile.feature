@device-profile
Feature: Device detection profile retrieval
  As a browser station (authenticated as a device)
  I want to GET /api/v1/edge/profile
  So that I know what to detect, with what sensitivity, and for whom

  Background:
    Given the tenant "empresa-test" is active
    And device "d7b1" is paired to patient "9c2a"
    And the device credential for "d7b1" is valid

  # Scenario 1: paired device token receives full profile
  Scenario: Paired device receives full detection profile with status 200
    Given GEST has a full detection profile for device "d7b1"
    When device "d7b1" requests the detection profile
    Then the response status is 200
    And the profile response contains pacienteId for patient "9c2a"
    And the profile response contains at least one primitive

  # Scenario 2: user token (edge.read) is rejected — the station is a device, not a user
  Scenario: User token is rejected with 403
    When an operator with role "edge.read" requests the detection profile
    Then the response status is 403

  # Scenario 3: registered-not-paired device receives 403 device-not-paired
  Scenario: Unpaired device receives 403 with edge.device-not-paired code
    Given device "d7b1" is downgraded to registered state
    And GEST has a full detection profile for device "d7b1"
    When device "d7b1" requests the detection profile
    Then the response status is 403
    And the error code contains "device-not-paired"

  # Scenario 4: conditional GET — matching ETag triggers 304
  Scenario: Conditional GET with matching ETag returns 304
    Given GEST has a full detection profile for device "d7b1"
    And device "d7b1" requests the detection profile to capture the ETag
    Given GEST returns 304 for conditional GET from device "d7b1"
    When device "d7b1" requests the profile with the captured ETag
    Then the response status is 304

  # Scenario 5: GEST unavailable — must NOT return 200 with empty profile
  Scenario: GEST unavailable returns explicit error, not empty profile
    Given GEST is unavailable for profile requests from device "d7b1"
    When device "d7b1" requests the detection profile
    Then the response is an explicit error, not an empty profile
    And the response status is not 200
