Feature: Device enrollment and heartbeat
  As the PEA runtime
  I want to register and maintain device presence in EDGE
  So that operators can monitor device health and session state

  Background:
    Given EDGE is running at http://localhost:8080/edge
    And device.id="device-001" is set in config.toml

  Scenario: Device enrolled via POST when not found in EDGE
    Given GET /edge/devices/device-001 returns 404 Not Found
    When the PEA runs the enrollment check
    Then POST /edge/devices is called with deviceId="device-001"
    And EDGE returns 201 Created
    And an INFO log event is emitted with "device enrolled"

  Scenario: 409 on enrollment treated as already enrolled
    Given GET /edge/devices/device-001 returns 404 Not Found
    And POST /edge/devices returns 409 Conflict
    When the PEA runs the enrollment check
    Then startup continues without error

  Scenario: Heartbeat sent every 30 seconds with correct status fields
    Given the PEA is running normally
    When 30 seconds elapse
    Then POST /edge/devices/device-001/heartbeat is called
    And the body includes: status, uptimeSeconds, queueDepth, lastMatchAt
    And status is "running"

  Scenario: Heartbeat reports degraded_auth when in DEGRADED_AUTH mode
    Given the PEA is in DEGRADED_AUTH mode
    When the next heartbeat interval fires
    Then the heartbeat body contains status="degraded_auth"

  Scenario: Re-enrollment triggered when heartbeat returns 404
    Given a heartbeat is sent to EDGE
    And EDGE returns 404 Not Found
    When the PEA processes the heartbeat response
    Then the enrollment flow is triggered again
    And a WARN log event is emitted

  Scenario: Final heartbeat with status=stopped sent on graceful shutdown
    When SIGTERM is received
    Then POST /edge/devices/device-001/heartbeat is called with status="stopped"
    Before the process exits
