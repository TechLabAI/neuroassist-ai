Feature: Onboarding — register paciente (step 2)
  As a technical conductor onboarding a new patient
  I want to register the paciente on the active empresa
  So that the command catalog step is unlocked

  Background:
    Given I am authenticated with scope "pac:patient:read pac:patient:write"
    And an active empresa is selected and the empresa header is sent
    And the ACS-owned onboarding wizard is on step 2 "Paciente"
    And pac-service is available with mock responses

  # ── Register (SPEC-04) ───────────────────────────────────────────────────────

  Scenario: Register a paciente on the active empresa unlocks the command catalog step
    When I fill in nome "Márcio Amaral"
    And I save the paciente
    Then the paciente is posted scoped by the empresa header
    And the paciente step is marked done and the command catalog step is unlocked

  # ── RBAC (SPEC-05) ───────────────────────────────────────────────────────────

  Scenario: Caller lacking paciente write permission has the onboarding paciente save action disabled
    Given the caller lacks the paciente write permission
    When the onboarding paciente step renders
    Then the onboarding paciente save action is disabled

  Scenario: Blank nome keeps the onboarding paciente save action disabled
    When I leave the nome empty
    Then the onboarding paciente save action is kept disabled

  # ── Reference-resolution errors (SPEC-07) ────────────────────────────────────

  Scenario: Unknown dispositivo reference rejects the paciente with an inline validation error
    Given I supply a dispositivo edge reference unknown to EDGE
    When I save the paciente
    Then the backend responds validation for the dispositivo field
    And an inline validation error is shown and the paciente is not created

  Scenario: Unknown cuidador reference rejects the paciente with an inline validation error
    Given I supply a cuidador reference unknown to ACS
    When I save the paciente
    Then the backend responds validation for the cuidador field
    And an inline validation error is shown and the paciente is not created

  Scenario: ACS unreachable during the paciente write makes the caregiver retry
    Given ACS is unreachable during the paciente write
    When I save the paciente
    Then a retryable message is shown and the paciente is not created

  # ── Missing tenant (SPEC-07) ─────────────────────────────────────────────────

  Scenario: Missing-tenant on the paciente write shows a session banner prompting re-authentication
    Given the paciente write is sent with a missing-tenant
    When I save the paciente
    Then a session banner prompting re-authentication is shown

  # ── Breadcrumb ownership (SPEC-06) ───────────────────────────────────────────

  Scenario: Patient register feature composed inside the onboarding wizard contributes no PAC breadcrumb
    Given the patient register feature is composed inside the onboarding wizard
    When the paciente step renders
    Then no PAC breadcrumb is contributed for the paciente step
