# Ref: SPEC-06 (security), hub-integration §2, api-contract §3/§4
Feature: Session revocation and scope enforcement

Background:
  Given the shared Keycloak realm issues patient-with-session tokens

Scenario: A revoked (blacklisted) session is rejected immediately
  Given a patient session token that is on the ACS blacklist
  When the patient requests GET /api/v1/conv/tree
  Then the response status is 401
  And the response problem type ends with session-revoked

Scenario: A token missing the required scope is rejected
  Given a patient session token without scope conv.no.selecionar
  When the patient selects node n-42 via toque
  Then the response status is 403
  And the response problem type ends with forbidden-scope

Scenario: A token carrying caregiver authority is rejected on CONV endpoints
  Given a token that carries caregiver authority and scope conv.arvore.navegar
  When the patient requests GET /api/v1/conv/tree
  Then the response status is 403
  And the response problem type ends with forbidden-scope

Scenario: Requests fail closed when ACS is unreachable
  Given ACS is unreachable
  When the patient requests GET /api/v1/conv/tree
  Then the response status is 401
  And the health projection reports acs UNREACHABLE and status DEGRADED
