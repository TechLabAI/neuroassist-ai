# Feature: Heartbeat and derived liveness
# Covers: SPEC-02, api-contract §3/§4.4/§5, data-model §2.1/§2.4, ADR-006 invariants #2/#3
# Invariants:
#   #2 — the acting deviceId comes from the authenticated token, never the body (anti-spoofing)
#   #3 — status is a pure function of (now - ultimaSincronizacao) vs staleness threshold; not stored
#   - ultimaSincronizacao is stamped with the SERVER clock (mitigates edge clock skew)
#   - default heartbeat 30s, staleness threshold 90s (3x), DASH poll hint 15s

Feature: Heartbeat and derived liveness

  Background:
    Given the tenant "empresa-test" is active
    And device "d7b1" is paired to patient "9c2a"
    And the device credential for "d7b1" is valid
    And the staleness threshold is 90 seconds

  # --- Heartbeat acceptance ---

  Scenario: Accepted heartbeat marks the device online and server-stamps the sync time
    When the edge device "d7b1" sends a heartbeat with agentVersion "1.4.2" and runningProfileVersion 6
    Then the response status is 200
    And the response body has status "online"
    And the response body ultimaSincronizacao equals the server time
    And the device versao is recorded as "1.4.2"

  Scenario: Heartbeat identity comes from the token, never the body - invariant #2
    Given the device credential authenticates as device "d7b1"
    When a heartbeat body claims deviceId "e8c2"
    Then the response status is 403
    And the error type is "/errors/identity-mismatch"

  Scenario: Heartbeat is rejected for a retired device
    Given device "d7b1" is in state "retired"
    When the edge device "d7b1" sends a heartbeat
    Then the response status is 409
    And the error type is "/errors/device-retired"

  Scenario: Heartbeat carrying media is rejected - privacy invariant #6
    When the edge device "d7b1" sends a heartbeat containing an image field
    Then the response status is 400
    And the error type is "/errors/validation"

  # --- Derived liveness ---

  Scenario Outline: Liveness is derived from heartbeat freshness at read time - invariant #3
    Given the last accepted heartbeat for device "d7b1" was <age> seconds ago
    When DASH polls the liveness of device "d7b1"
    Then the response status is 200
    And the response body has status "<status>"
    And the response header "X-Staleness-Threshold-Seconds" equals "90"

    Examples:
      | age | status  |
      | 10  | online  |
      | 89  | online  |
      | 91  | offline |
      | 600 | offline |

  Scenario: A paired device that never sent a heartbeat reads offline
    Given device "d7b1" is paired but has never sent a heartbeat
    When DASH polls the liveness of device "d7b1"
    Then the response status is 200
    And the response body has status "offline"

  Scenario: An unpaired device reports liveness n/a
    Given device "d7b1" is in state "unpaired"
    When DASH polls the liveness of device "d7b1"
    Then the response status is 200
    And the response body has status "n/a"

  # --- Offline crossing is audited, never silent (invariant #4) ---

  Scenario: Observing an online-to-offline crossing appends a liveness transition
    Given device "d7b1" last heartbeat was 200 seconds ago and was previously online
    When DASH polls the liveness of device "d7b1"
    Then the response body has status "offline"
    And a liveness transition "online -> offline" is appended with actor "system"

  Scenario: A resumed heartbeat records the device back online
    Given device "d7b1" is currently offline
    When the edge device "d7b1" sends a heartbeat
    Then the response body has status "online"
    And a liveness transition "offline -> online" is appended with actor "system"

  # --- Conditional GET for cheap polling ---

  Scenario: Liveness poll returns 304 when the derived status bucket is unchanged
    Given device "d7b1" is online with a known liveness ETag
    When DASH polls liveness with If-None-Match equal to that ETag
    Then the response status is 304
    And the response body is empty

  Scenario: The ETag invalidates when the device crosses into offline without a new heartbeat
    Given device "d7b1" was online with liveness ETag "v-online"
    And 200 seconds pass with no heartbeat
    When DASH polls liveness with If-None-Match "v-online"
    Then the response status is 200
    And the response body has status "offline"
