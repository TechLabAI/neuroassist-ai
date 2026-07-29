Feature: Rate limiting and CORS at the edge

# Refs: SPEC-05 (rate limiting), SPEC-06 (CORS), SPEC-01 (error model)
# Back-pressure contract for the edge outbox (429) and dashboard CORS policy.

Background:
  Given the Service Registry has a healthy instance of "evnt-service"
  And the "edge-ingest" rate-limit bucket allows 2 requests per second

Scenario: Edge ingest is throttled when the bucket is exhausted
  Given an edge agent holds a valid device token
  When the edge agent POSTs 5 events within one second to "/api/evnt/events"
  Then at least one response has status "429"
  And each "429" response includes a "Retry-After" header
  And the "429" problem type ends with "/errors/rate-limited"

Scenario: Throttled edge agent paces via its outbox and later succeeds
  Given an edge agent received a "429" for an event
  When the edge agent retries the same event after the Retry-After delay
  Then the gateway forwards the event to "evnt-service"
  And the response status is "2xx"

Scenario: Pre-flight from the allowed dashboard origin is answered at the gateway
  Given "http://localhost:4200" is an allowed CORS origin
  When an OPTIONS pre-flight for "GET /api/hist/events" arrives from "http://localhost:4200"
  Then the gateway answers the pre-flight without forwarding to any service
  And the response includes "Access-Control-Allow-Origin" equal to "http://localhost:4200"

Scenario: Request from a disallowed origin gets no allow-origin header
  Given "http://evil.example" is not an allowed CORS origin
  When a browser request for "GET /api/hist/events" arrives from "http://evil.example"
  Then the response does not include an "Access-Control-Allow-Origin" header
