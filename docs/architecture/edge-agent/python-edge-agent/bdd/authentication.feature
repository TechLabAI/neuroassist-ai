Feature: Authentication and token management
  As the PEA runtime
  I want to obtain and maintain a valid ACS token
  So that all outbound service calls are authorized

  Background:
    Given ACS is running at http://localhost:8080/acs
    And config.toml contains device.client_id and device.client_secret

  Scenario: Token obtained from ACS on startup using client_credentials
    When the PEA starts and calls AuthClient.get_token()
    Then a POST is made to /acs/oauth/token with grant_type=client_credentials
    And scope=edge_agent is included
    And a valid JWT is returned and cached

  Scenario: Cached token returned without network call while not expired
    Given a cached token with expires_at 5 minutes from now
    When AuthClient.get_token() is called
    Then no network call is made
    And the cached token is returned

  Scenario: New token is fetched when cached token is expired
    Given a cached token with expires_at 10 seconds ago (expired)
    When AuthClient.get_token() is called
    Then a POST is made to /acs/oauth/token
    And the new token replaces the cached token

  Scenario: PEA exits with code 1 when ACS rejects credentials on startup
    Given ACS returns 401 Unauthorized for token request
    When the PEA is starting
    Then a CRITICAL log event is emitted with message containing "device credentials rejected"
    And the process exits with code 1

  Scenario: DEGRADED_AUTH entered after two consecutive 401 responses
    Given a valid session is running
    When ACS returns 401 for a token request
    And the immediate retry also returns 401
    Then PEA enters DEGRADED_AUTH mode
    And events accumulate in the outbox without delivery attempts
    And a CRITICAL log event is emitted

  Scenario: DEGRADED_AUTH exits and draining resumes after token recovery
    Given PEA is in DEGRADED_AUTH mode with 3 rows in event_outbox
    When ACS returns a valid token on the next 60-second retry
    Then DEGRADED_AUTH mode exits
    And the drain loop resumes immediately
    And the 3 outbox rows are delivered to EVNT

  @lgpd
  Scenario: client_secret never appears in any log output
    When the PEA runs for 5 minutes and logs are collected
    Then no log event contains the client_secret value
    And no log event contains a full JWT token string
