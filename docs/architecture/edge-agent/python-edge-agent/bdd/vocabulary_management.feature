Feature: Detection profile and command vocabulary management
  As the PEA runtime
  I want to load and maintain the gesture detection profile and command vocabulary
  So that the CommandMatcher always operates with the latest patient-specific configuration

  Background:
    Given GEST is running at http://localhost:8080/gest
    And PAC is running at http://localhost:8080/pac
    And a valid token is cached for device "device-001"

  Scenario: Detection profile loaded from GEST on startup
    When the PEA starts and calls GET /gest/devices/device-001/detection-profile
    Then the response is cached in detection_profile_cache
    And patientId is extracted from the profile

  Scenario: Cached detection profile used when GEST unreachable on startup
    Given detection_profile_cache contains a previously fetched profile
    And GEST returns a connection error
    When the PEA starts
    Then the cached profile is used
    And a WARN log event is emitted

  Scenario: PEA exits when GEST returns 404 for device
    Given GET /gest/devices/device-001/detection-profile returns 404 Not Found
    When the PEA starts
    Then a CRITICAL log event is emitted with "device not enrolled in GEST"
    And the process exits with code 1

  Scenario: Command vocabulary loaded from PAC on startup
    Given detection_profile_cache.patientId is "pac-001"
    When the PEA calls GET /pac/patients/pac-001/commands/active
    Then the response is cached in command_cache
    And a CommandVocabulary is built in memory from the response

  Scenario: Command cache invalidated and refreshed on EVNT 422 unknown-command
    Given EVNT returns 422 with error_code="unknown-command" for an event
    When the EventPublisher processes the 422 response
    Then CommandCache.invalidate() is called
    And an immediate PAC refresh is triggered out of schedule

  Scenario: Detection profile and vocabulary refreshed on schedule
    Given the refresh_interval is set to 15 minutes
    When 15 minutes elapse since the last refresh
    Then GET /gest/devices/device-001/detection-profile is called
    And GET /pac/patients/pac-001/commands/active is called
    And the in-memory CommandVocabulary is updated

  Scenario: New ONNX model hot-swapped when modelVersion changes
    Given the current model is version "v1.0.0"
    When a scheduled refresh returns detection-contract with modelVersion="v1.1.0"
    Then the new ONNX model is downloaded and its checksum verified
    And the GestureClassifier is atomically replaced in the pipeline
    And an INFO log event is emitted with "model hot-swapped to v1.1.0"

  Scenario: Hot-swap aborted on checksum mismatch
    Given a new model version is returned with modelVersion="v1.1.0"
    When the downloaded model fails SHA-256 verification
    Then the current model is retained
    And an ERROR log event is emitted with checksum details
