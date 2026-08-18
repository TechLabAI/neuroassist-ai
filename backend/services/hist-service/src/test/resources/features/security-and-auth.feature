# Covers SPEC-06 (security), SPEC-01 (API surface), api-contract §2.1/§2.2/§1.1
# Closes gaps GAP-01, GAP-02, GAP-03, GAP-04, GAP-05 identified in SPEC-VAL-bdd-coverage-report.md
Feature: Security, auth invariants and edge-case ingestion validations

Background:
  Given a provisioned tenant "familia-silva"

# GAP-04 — SPEC-06 EARS: 401, 403 missing scope, 403 tenant mismatch

Scenario: A request with no bearer token is rejected with 401
  When POST /audit-facts is attempted with no Authorization header
  Then the response status is 401
  And the problem type is "/errors/unauthorized"

Scenario: A caller without hist:write scope is rejected with 403
  Given a token with scope "hist:read" only
  When it is used to call POST /audit-facts
  Then the response status is 403
  And the problem type is "/errors/forbidden"

Scenario: A body tenant that differs from the JWT tenant claim is rejected with 403
  Given a producer token with scope "hist:write" for tenant "familia-silva"
  And the request body carries empresaId "outra-empresa"
  When it is submitted to POST /audit-facts
  Then the response status is 403
  And the problem type is "/errors/tenant-mismatch"

# GAP-01 — SPEC-02 "casos de borda": occurredAt in the future beyond 5 min

Scenario: An audit fact with occurredAt more than 5 minutes in the future is rejected
  Given a producer token with scope "hist:write"
  And an audit fact "018f-9001" of type "evento.confirmado" for subject paciente "pac-1" by caregiver "cg-1"
  And its occurredAt is 10 minutes in the future
  When it is submitted to POST /audit-facts
  Then the response status is 422
  And the problem type is "/errors/future-occurred-at"

# GAP-02 — api-contract §2.1: correctsFactId unresolvable → 422

Scenario: A correction referencing a non-existent prior factId is rejected
  Given a producer token with scope "hist:write"
  And an audit fact "018f-9002" of type "registro.corrigido" for subject paciente "pac-1" by caregiver "cg-1"
  And it corrects fact "018f-dead-beef-nonexistent"
  When it is submitted to POST /audit-facts
  Then the response status is 422
  And the problem type is "/errors/corrects-target-not-found"

# GAP-03 — api-contract §2.2: GET /audit-facts/{id} — fetch by id and cross-tenant 404 isolation

Scenario: A coordinator fetches a known audit record by id
  Given a coordinator token with scope "hist:read:all"
  And an audit record exists with id "018f-0099"
  When GET /audit-facts/018f-0099 is called
  Then the response status is 200
  And the record id is "018f-0099"

Scenario: Fetching an id that belongs to another tenant returns 404
  Given a coordinator token with scope "hist:read:all" for tenant "familia-silva"
  And an audit record "018f-0100" exists in tenant "outra-familia"
  When GET /audit-facts/018f-0100 is called
  Then the response status is 404
  And the problem type is "/errors/not-found"

# GAP-05 — api-contract §1.1 EARS: X-Correlation-Id generated when absent and echoed

Scenario: A request without X-Correlation-Id receives a generated one in the response
  Given a producer token with scope "hist:write"
  And an audit fact "018f-9003" of type "evento.confirmado" for subject paciente "pac-1" by caregiver "cg-1"
  And the request does not include an X-Correlation-Id header
  When it is submitted to POST /audit-facts
  Then the response status is 201
  And the response includes an X-Correlation-Id header
