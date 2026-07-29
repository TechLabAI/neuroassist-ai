# Feature: Detection-profile pointer delivery and drift detection
# Covers: SPEC-02, SPEC-10, api-contract §3.1, integration-contracts §1, ADR-006 (EDGE transports, GEST authors)
# Invariants:
#   - EDGE fetches the ACTIVE profile version pointer from GEST and piggybacks it on the heartbeat
#   - profileDrift = (runningProfileVersion != currentProfileVersion)
#   - liveness NEVER depends on GEST availability (degraded pointer, heartbeat still accepted)

Feature: Detection-profile pointer delivery and drift detection

  Background:
    Given the tenant "empresa-test" is active
    And device "d7b1" is paired to patient "9c2a"
    And the device credential for "d7b1" is valid

  Scenario: Heartbeat delivers the current profile pointer when versions match
    Given GEST reports the active profile version is 7
    When the edge device "d7b1" sends a heartbeat with runningProfileVersion 7
    Then the response status is 200
    And the response body currentProfileVersion is 7
    And the response body profileDrift is false

  Scenario: Drift is flagged when the device runs an older profile
    Given GEST reports the active profile version is 7
    When the edge device "d7b1" sends a heartbeat with runningProfileVersion 6
    Then the response status is 200
    And the response body currentProfileVersion is 7
    And the response body profileDrift is true
    And a ProfileDriftDetected event is raised

  Scenario: Heartbeat still succeeds when GEST has no active profile yet
    Given GEST reports no active profile for the tenant
    When the edge device "d7b1" sends a heartbeat with runningProfileVersion null
    Then the response status is 200
    And the response body currentProfileVersion is null
    And the response body profileDrift is false

  Scenario: Liveness never depends on GEST - heartbeat accepted when GEST is unavailable
    Given GEST is unavailable and the circuit breaker is open
    When the edge device "d7b1" sends a heartbeat with runningProfileVersion 6
    Then the response status is 200
    And the response body has status "online"
    And the response body profilePointerDegraded is true

  # --- GEST resolves device -> tenant against EDGE (frozen inbound contract) ---

  Scenario: GEST resolves the tenant of a registered device
    Given device "d7b1" is registered in tenant "empresa-test"
    And the caller presents a valid service credential
    When GEST requests the tenant of device "d7b1"
    Then the response status is 200
    And the response body has deviceId "d7b1"
    And the response body has tenantRef "empresa-test"

  Scenario: GEST gets 404 for an unknown or retired device
    Given device "z9z9" is not registered
    When GEST requests the tenant of device "z9z9"
    Then the response status is 404
    And the error type is "/errors/device-not-found"

  Scenario: A service token from another tenant cannot resolve a device
    Given device "d7b1" is registered in tenant "empresa-test"
    And the caller presents a service credential for tenant "empresa-other"
    When GEST requests the tenant of device "d7b1"
    Then the response status is 404
