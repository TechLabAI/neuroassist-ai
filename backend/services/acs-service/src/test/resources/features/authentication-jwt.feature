# Ref: SPEC-03-authentication-jwt.md §6, SPEC-08-bdd-test-scenarios.md §4
Feature: Authentication and JWT Issuance

  Scenario: Successful login returns a signed JWT
    Given an active caregiver with login "jamilly@familia-amaral" in active tenant "t_0f8a"
    When I POST /oauth2/token with correct credentials for "jamilly@familia-amaral"
    Then the response status is 200
    And the response contains fields "accessToken", "tenant", "papel", and "authorities"
    And the token expires in at most 900 seconds
    And the token is signed with an RS256 key present in /oauth2/jwks

  Scenario: Wrong password returns 401 without user enumeration
    Given a caregiver with login "jamilly@familia-amaral" in active tenant "t_0f8a"
    When I POST /oauth2/token with incorrect password for "jamilly@familia-amaral"
    Then the response status is 401
    And the error type is "/errors/invalid-credentials"
    And the error body does not indicate whether login or password was wrong

  Scenario: Inactive caregiver login is rejected
    Given a caregiver "5c1a" with status "inactive" in active tenant "t_0f8a"
    When I POST /oauth2/token with correct credentials for caregiver "5c1a"
    Then the response status is 401
    And the error type is "/errors/invalid-credentials"

  Scenario: Login against a non-active tenant is rejected
    Given a caregiver in tenant "t_provisionando" with status "provisioning"
    When I POST /oauth2/token with correct credentials
    Then the response status is 401
    And the error type is "/errors/invalid-credentials"

  Scenario: JWK set is accessible without authentication
    When I GET /oauth2/jwks without Authorization header
    Then the response status is 200
    And the response contains at least one key with "kid"
    And the response contains public JWK material ("n" and "e" for RS256)
    And the response does not contain "private_key_enc"
    And the Cache-Control header includes "max-age=300"

  Scenario: Raw password is not present in any response or log
    When I POST /oauth2/token with credentials
    Then no response body field contains the raw password value
    # LGPD guard: THE SYSTEM SHALL never log or return the raw senha

  Scenario: JWT authorities match the caregiver papel (cuidadora)
    Given an active caregiver with papel "cuidadora" in active tenant "t_0f8a"
    When I POST /oauth2/token with correct credentials
    Then the authorities in the JWT are exactly:
      | authority              |
      | acs.cuidador.read      |
      | acs.directory.read     |
      | evnt.read              |
      | evnt.confirm           |
      | dash.read              |

  Scenario: JWT authorities match the caregiver papel (coordenador_clinica)
    Given an active caregiver with papel "coordenador_clinica" in active tenant "t_0f8a"
    When I POST /oauth2/token with correct credentials
    Then the authorities in the JWT include "acs.cuidador.criar" and "acs.vinculo.criar"
