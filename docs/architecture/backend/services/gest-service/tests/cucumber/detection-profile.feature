# Feature: Detection profile versioning and edge pull
# Covers: SPEC-04, api-contract §4, data-model §2.3/§2.4, ADR-006 invariants #2/#3
# Invariants:
#   #2 — profile includes ONLY 'supported' primitives at publish time
#   #3 — ux_profile_version: strictly monotonic, never reused per tenant
#   ux_profile_single_active: at most one active profile per tenant
#   ETag/304 conditional GET for bandwidth-efficient edge polling

Feature: Detection profile versioning and edge pull

  Background:
    Given the tenant "empresa-test" is active
    And the caller is authenticated

  # --- Publishing ---

  Scenario: Publish a profile — includes only supported primitives — invariant #2
    Given primitive "3f1c-aaa" is in state "supported" with engineFeatureRef "mp.blink.double.v1"
    And primitive "9a20-bbb" is in state "pending_training"
    And the caller has role "gest.manage"
    When the caller publishes a detection profile with default classification params
    Then the response status is 201
    And the response body contains version 1
    And the profile primitives list contains "3f1c-aaa" with engineFeatureRef "mp.blink.double.v1"
    And the profile primitives list does NOT contain "9a20-bbb"
    And the profile has active true

  Scenario: Cannot publish a profile with zero supported primitives
    Given no primitives are in state "supported" in tenant "empresa-test"
    And the caller has role "gest.manage"
    When the caller publishes a detection profile
    Then the response status is 422
    And the error type is "/errors/empty-profile"

  Scenario: Version is strictly monotonic — invariant #3
    Given a profile version 1 already exists for tenant "empresa-test"
    And primitive "3f1c-aaa" is in state "supported"
    And the caller has role "gest.manage"
    When the caller publishes a new detection profile
    Then the response status is 201
    And the returned version is 2
    And no version is ever reused

  Scenario: Publishing a new profile deactivates the prior one — ux_profile_single_active
    Given profile version 1 is active for tenant "empresa-test"
    And primitive "3f1c-aaa" is in state "supported"
    And the caller has role "gest.manage"
    When the caller publishes a new detection profile
    Then the response status is 201
    And the new profile has active true
    And profile version 1 has active false
    And there is exactly one active profile for tenant "empresa-test"

  Scenario: Profile content is immutable after publish — only the active flag may change
    Given profile version 1 is active for tenant "empresa-test" with primitives ["3f1c-aaa"]
    And primitive "9a20-bbb" is in state "supported" and added after the profile was published
    When the system deactivates profile version 1 upon publishing version 2
    Then the primitives list of profile version 1 still contains only ["3f1c-aaa"]

  # --- Retrieval ---

  Scenario: Get active profile returns 200 with X-Profile-Version header
    Given profile version 3 is active for tenant "empresa-test"
    And the caller has role "gest.read"
    When the caller gets the active detection profile
    Then the response status is 200
    And the response header "X-Profile-Version" equals "3"
    And the response body has version 3

  Scenario: Get active profile returns 404 when no profile has ever been published
    Given no detection profile exists for tenant "empresa-test"
    And the caller has role "gest.read"
    When the caller gets the active detection profile
    Then the response status is 404
    And the error type is "/errors/no-active-profile"

  Scenario: Get profile by version returns the exact immutable content
    Given profile version 5 exists for tenant "empresa-test" with primitives ["3f1c-aaa"]
    And the caller has role "gest.read"
    When the caller gets profile by version 5
    Then the response status is 200
    And the response body has version 5
    And the primitives list matches exactly what was recorded at publish time

  Scenario: Get profile by unknown version returns 404
    And the caller has role "gest.read"
    When the caller gets profile by version 999
    Then the response status is 404

  # --- Edge pull: conditional GET (ETag/304) ---

  Scenario: Edge pulls active profile for its device — fresh pull returns 200 with X-Profile-Version
    Given device "D-12" is paired to tenant "empresa-test"
    And profile version 4 is active for tenant "empresa-test"
    And the device credential is valid
    When the edge device "D-12" gets its detection profile without If-None-Match
    Then the response status is 200
    And the response header "X-Profile-Version" equals "4"

  Scenario: Edge conditional GET returns 304 when profile has not changed — ETag match
    Given device "D-12" is paired to tenant "empresa-test"
    And profile version 4 is active for tenant "empresa-test"
    And the device credential is valid
    When the edge device "D-12" gets its detection profile with If-None-Match "4"
    Then the response status is 304
    And the response body is empty

  Scenario: Edge conditional GET returns 200 with new profile when version changes
    Given device "D-12" is paired to tenant "empresa-test"
    And profile version 4 is active for tenant "empresa-test"
    And the device credential is valid
    When the edge device "D-12" gets its detection profile with If-None-Match "3"
    Then the response status is 200
    And the response header "X-Profile-Version" equals "4"

  Scenario: Edge pull rejected for device not paired to the caller's tenant
    Given device "D-12" is paired to tenant "empresa-other"
    And the device credential presents tenant "empresa-test"
    When the edge device "D-12" gets its detection profile
    Then the response status is 403
    And the error type is "/errors/device-not-paired"

  # --- Authorization ---

  Scenario: Non-conductor role cannot publish a profile
    Given primitive "3f1c-aaa" is in state "supported"
    And the caller has role "gest.read"
    When the caller publishes a detection profile
    Then the response status is 403
    And the error type is "/errors/insufficient-scope"
