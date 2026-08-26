# Ref: SPEC-02-tenant-provisioning-saga.md, SPEC-08-bdd-test-scenarios.md §2
Feature: Tenant Provisioning

  Background:
    Given I am authenticated as a platform conductor with authority "acs.tenant.provisionar"

  Scenario: Successful tenant provisioning when MIG is available
    Given MIG is available and can provision schemas
    When I POST a tenant with nome "Família Amaral" and tipo "familia"
    Then the response status is 201
    And the tenant status is "active"
    And schema_name is not null
    And the domain fact TenantProvisionado is recorded to HIST

  # E2E-029 — a empresa nasce utilizável, não só provisionada.
  #
  # Antes disto o tenant ficava inalcançável: todo token pertence a um cuidador e todo cuidador a
  # um tenant, então uma empresa sem ninguém dentro não tinha ator possível. Criar o coordenador
  # por fora exigiria autoridade para criar pessoas em outra empresa — exatamente o que o
  # 403 acs.tenant.mismatch existe para impedir.
  Scenario: O primeiro coordenador consegue autenticar na empresa recém-criada
    Given MIG is available and can provision schemas
    When I POST a tenant with nome "Família Amaral" and tipo "familia"
    Then the response status is 201
    And the first coordinator can authenticate into the new tenant

  Scenario: Tenant provisioning returns 202 when MIG times out
    Given MIG times out on schema request after 5 seconds
    When I POST a tenant with nome "Família Souza" and tipo "familia"
    Then the response status is 202
    And the tenant status is "provisioning"
    And schema_name is null

  Scenario: Retry a stuck tenant provisioning
    Given a tenant with status "provisioning" exists
    And MIG is now available
    When I POST provisioning:retry for that tenant
    Then the response status is 200
    And the tenant status is "active"
    And schema_name is not null

  Scenario: Retry an already active tenant is a no-op
    Given a tenant with status "active" exists
    When I POST provisioning:retry for that tenant
    Then the response status is 200
    And MIG is not called
    And the tenant status remains "active"

  Scenario: Retry a suspended tenant returns 409
    Given a tenant with status "suspended" exists
    When I POST provisioning:retry for that tenant
    Then the response status is 409
    And the error type is "/errors/tenant-suspended"

  Scenario Outline: Invalid tipo is rejected
    When I POST a tenant with nome "Test" and tipo "<tipo>"
    Then the response status is 400
    And the error type is "/errors/validation"

    Examples:
      | tipo        |
      | unknownType |
      | hospital    |
      |             |

  Scenario: Tenant cannot be returned as active with null schema_name
    Given a persisted tenant row with status "active" and schema_name NULL exists (data defect)
    When I GET /api/v1/acs/tenants/"that"
    Then the response status is 500
    # DB constraint ck_tenant_active_needs_schema prevents this state at write time
