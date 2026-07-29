Feature: Edge routing and authentication

# Refs: SPEC-01 (route surface), SPEC-02 (pipeline), SPEC-06 (security)
# Invariants: INV-1 (fail closed), INV-4 (discovery only)

Background:
  Given the Service Registry has a healthy instance of "hist-service"
  And ACS exposes a valid JWK set

Scenario: Authenticated caregiver request is routed to the target service
  Given a caregiver holds a valid JWT for empresa "emp_familia_amaral"
  When the caregiver sends "GET /api/hist/events" with that JWT
  Then the gateway forwards the request to "hist-service" via discovery
  And the upstream response is streamed back to the caller

Scenario: Unauthenticated request is rejected at the edge
  Given a request carries no Authorization header
  When it is sent to "GET /api/hist/events"
  Then the gateway responds "401"
  And the response content type is "application/problem+json"
  And no downstream service is contacted

Scenario: Expired caregiver token is rejected
  Given a caregiver holds a JWT that expired 1 minute ago
  When the caregiver sends "GET /api/hist/events" with that JWT
  Then the gateway responds "401"
  And the problem type ends with "/errors/unauthenticated"

Scenario: Unknown path matches no route
  Given a caregiver holds a valid JWT
  When the caregiver sends "GET /api/unknown/thing" with that JWT
  Then the gateway responds "404"
  And the problem type ends with "/errors/no-route"

Scenario: No healthy instance of the target service
  Given the Service Registry has no healthy instance of "hist-service"
  And a caregiver holds a valid JWT
  When the caregiver sends "GET /api/hist/events" with that JWT
  Then the gateway responds "503"
  And the problem type ends with "/errors/upstream-unavailable"

Scenario Outline: Upstream failures are mapped to typed problems
  Given a caregiver holds a valid JWT
  And the upstream "hist-service" will "<upstream_behaviour>"
  When the caregiver sends "GET /api/hist/events" with that JWT
  Then the gateway responds "<status>"
  And the problem type ends with "<type_suffix>"

  Examples:
    | upstream_behaviour | status | type_suffix               |
    | return 500         | 502    | /errors/upstream-error    |
    | time out           | 504    | /errors/upstream-timeout  |
