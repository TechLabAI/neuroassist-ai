Feature: Tenant onboarding — provision empresa and register primary caregiver
  As a technical conductor onboarding a new empresa
  I want to provision the empresa and register the primary caregiver with a link
  So that the paciente starts covered from day one

  Background:
    Given I am authenticated as a conductor with scope "acs.tenant.write acs.cuidador.write acs.vinculo.write"
    And the ACS-owned onboarding wizard is running on the scope:web shell page
    And acs-service is available with mock responses

  # ── Provision empresa (SPEC-03, 04) ──────────────────────────────────────────

  Scenario: Provisioning an empresa that returns active writes the active empresa context
    Given the onboarding wizard is on step 1 "Empresa"
    When I fill in nome "Família Amaral" and tipo "familia"
    And I create the empresa
    Then the tenant provisioning returns active
    And the active empresa context is written

  Scenario: Provisioning that is not yet confirmed shows a provisioning empresa state
    Given the onboarding wizard is on step 1 "Empresa"
    When I create the empresa and the tenant provisioning is not yet confirmed
    Then a provisioning empresa state is shown rather than an active empresa
    And the active empresa context is not written

  Scenario: A provisioning retry that succeeds writes the active empresa context
    Given the empresa is in a provisioning state
    When I retry the tenant provisioning and it succeeds
    Then the active empresa context is written

  Scenario: A provisioning retry that is still not active shows a retryable empresa banner
    Given the empresa is in a provisioning state
    When I retry the tenant provisioning
    Then the backend responds tenant-not-active
    And a retryable empresa banner is shown and the empresa is not reported active

  # ── RBAC (SPEC-05) ───────────────────────────────────────────────────────────

  Scenario: The criar empresa action is disabled without tenant write permission
    Given the caller lacks the tenant write permission
    When the onboarding wizard renders step 1
    Then the criar empresa action is disabled

  # ── Primary caregiver + link (SPEC-03, 04) ───────────────────────────────────

  Scenario: Registering the primary caregiver and linking it covers the paciente
    Given the onboarding wizard is on step 5 "Cuidador e vínculo" with an active empresa and a paciente
    When I register the primary cuidador and link it to the paciente
    Then the cuidador invite is posted carrying an idempotency key
    And the paciente becomes covered

  # ── Composition + breadcrumb ownership (SPEC-06) ─────────────────────────────

  Scenario: The composed tenant setup and caregiver link steps contribute no ACS breadcrumb
    Given the tenant setup and caregiver link steps are composed inside the onboarding wizard
    When the wizard steps render
    Then no ACS breadcrumb is contributed for those steps

  Scenario: The PAC and EDGE onboarding steps are composed only at the shell page level
    Given the onboarding wizard sequences PAC and EDGE step features
    When the wizard shell page renders the steps
    Then the PAC and EDGE steps are composed at the shell page level without importing pac or edge scope into any ACS library
