# Ref: SPEC-05-caregiver-linkage-coverage.md §5, SPEC-08-bdd-test-scenarios.md §3
Feature: Caregiver Management

  Background:
    Given an active tenant "t_0f8a" exists
    And I am authenticated as a coordinator for tenant "t_0f8a" with authority "acs.cuidador.criar"

  Scenario: Create a caregiver in an active tenant
    When I POST a caregiver with login "jamilly@familia-amaral" and papel "cuidadora"
    Then the response status is 201
    And the caregiver status is "active"
    And the response body does not contain "credential_hash"
    And the response body does not contain "senha"

  Scenario: Duplicate login in same tenant is rejected
    Given an active caregiver with login "jamilly@familia-amaral" exists in tenant "t_0f8a"
    When I POST another caregiver with login "jamilly@familia-amaral"
    Then the response status is 409
    And the error type is "/errors/login-duplicate"

  Scenario: Create caregiver against a non-active tenant is rejected
    Given a tenant "t_1111" with status "provisioning" exists
    And I am authenticated as a coordinator for tenant "t_1111"
    When I POST a caregiver for tenant "t_1111"
    Then the response status is 409
    And the error type is "/errors/tenant-not-active"

  Scenario: Deactivate a caregiver with no active patient links
    Given an active caregiver "5c1a" with no active patient links
    And I am authenticated with authority "acs.cuidador.desativar"
    When I POST deactivation for caregiver "5c1a"
    Then the response status is 200
    And the caregiver status is "inactive"

  Scenario: Deactivated caregiver cannot authenticate afterwards
    Given a caregiver "5c1a" with status "inactive"
    When I POST /oauth2/token with credentials for caregiver "5c1a"
    Then the response status is 401
    And the error type is "/errors/invalid-credentials"

  Scenario: Deactivate last alarm-eligible caregiver is rejected by coverage invariant
    Given an active caregiver "5c1a" is the only active link for patient "p_9a3f"
    And no coordinator exists in tenant "t_0f8a"
    And I am authenticated with authority "acs.cuidador.desativar"
    When I POST deactivation for caregiver "5c1a"
    Then the response status is 409
    And the error type is "/errors/last-caregiver-link"

  Scenario: Caller without acs.cuidador.criar is rejected
    Given I am authenticated with authorities ["acs.cuidador.read"]
    When I POST a caregiver with login "test@example.com" and papel "cuidadora"
    Then the response status is 403
    And the error type is "/errors/insufficient-authority"

  Scenario Outline: Invalid papel value is rejected
    When I POST a caregiver with login "test@example.com" and papel "<papel>"
    Then the response status is 400
    And the error type is "/errors/validation"

    Examples:
      | papel      |
      | admin      |
      | superuser  |
      |            |
