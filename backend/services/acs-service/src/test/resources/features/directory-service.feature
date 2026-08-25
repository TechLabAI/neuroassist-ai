# Ref: SPEC-01-api-surface.md §4, SPEC-05-caregiver-linkage-coverage.md §5 (directory consistency)
# Gap coverage: directory surface not covered by SPEC-08 original scenarios
Feature: Directory Service

  Background:
    Given I am authenticated with authority "acs.directory.read"

  Scenario: Routing query returns active caregivers for a patient
    Given active caregiver "5c1a" is linked to patient "p_9a3f" in tenant "t_0f8a"
    And active caregiver "6d2b" is linked to patient "p_9a3f" in tenant "t_0f8a"
    When I GET /api/v1/acs/directory/patients/p_9a3f/caregivers
    Then the response status is 200
    And the response body contains caregivers "5c1a" and "6d2b"
    And the response Cache-Control header includes "max-age=60"

  Scenario: Routing query returns empty array (not 404) when no active caregivers exist
    Given patient "p_orphan" has no active caregiver links and no coordinator in tenant "t_0f8a"
    When I GET /api/v1/acs/directory/patients/p_orphan/caregivers
    Then the response status is 200
    And the response body contains "responsibleCaregivers": []

  Scenario: Directory reflects link revocation immediately (same-transaction consistency)
    Given caregiver "5c1a" and caregiver "6d2b" are both linked to patient "p_9a3f"
    And I revoke the link for caregiver "5c1a"
    When I GET /api/v1/acs/directory/patients/p_9a3f/caregivers
    Then the response status is 200
    And the response body does NOT contain caregiver "5c1a"
    And the response body contains caregiver "6d2b"

  Scenario: Tenant validity query returns active tenant record
    Given an active tenant "t_0f8a" exists
    When I GET /api/v1/acs/directory/tenants/t_0f8a
    Then the response status is 200
    And the response body contains "status": "active"

  Scenario: Tenant validity query returns 404 for unknown tenant
    When I GET /api/v1/acs/directory/tenants/t_unknown
    Then the response status is 404

  Scenario: Caregiver scope query returns patient-scope for data access
    Given active caregiver "5c1a" is linked to patients "p_9a3f" and "p_1b2c"
    When I GET /api/v1/acs/directory/caregivers/5c1a/patient-scope
    Then the response status is 200
    And the response body contains patients "p_9a3f" and "p_1b2c"

  # DESEMPENHO — fora do gate de correcao (tag @performance).
  #
  # O SLA do SPEC-10 §3.2 continua valendo; o que muda e ONDE ele e verificado.
  # Este cenario media RELOGIO DE PAREDE dentro da suite de correcao, num runner
  # compartilhado — afere a maquina tanto quanto o codigo. Vinha vermelho desde
  # 24/08 por esse motivo, e um check que sempre falha e um check que ninguem
  # olha: foi assim que o guard de drift ficou dias morrendo no import sem que
  # ninguem percebesse.
  #
  # Ha ainda um problema de nome: ele diz "p99" e mede UMA requisicao. Um unico
  # ponto nao e percentil; um pico isolado reprova, e uma lentidao consistente
  # abaixo do limite passa.
  #
  # Para rodar: ./gradlew :services:acs-service:test -Dcucumber.filter.tags="@performance"
  @performance
  Scenario: Directory endpoint responds within 100ms p99
    Given 100 active caregivers with links to patient "p_perf" in tenant "t_0f8a"
    When I GET /api/v1/acs/directory/patients/p_perf/caregivers
    Then the response time is below 100 milliseconds
    # SLA: THE SYSTEM SHALL respond to directory reads within 100ms p99 (SPEC-10 §3.2)

  Scenario: Unauthenticated request to directory surface is rejected
    Given I am NOT authenticated
    When I GET /api/v1/acs/directory/tenants/t_0f8a
    Then the response status is 401
    And the error type is "/errors/unauthenticated"
