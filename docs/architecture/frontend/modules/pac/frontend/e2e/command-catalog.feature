Feature: Calibração — command catalog
  As a family caregiver with comando read and write permission
  I want to see the patient's command catalog with honest sync state
  So that I know which commands are actually armed on the device

  Background:
    Given I am authenticated with scope "pac:patient:read pac:command:read pac:command:write pac:command:publish"
    And an active empresa is selected and the empresa header is sent
    And a paciente "Márcio Amaral" is registered for the active empresa
    And pac-service is available with mock responses

  # ── Catalog route rendering (SPEC-01, SPEC-04) ───────────────────────────────

  Scenario: Calibração route renders the command catalog for the active paciente
    When I open the Calibração route
    Then the command catalog for the active paciente is rendered

  Scenario: Opening the catalog fetches the comando list scoped by the empresa header
    When I open the Calibração route
    Then the comando list for the active paciente is fetched scoped by the empresa header
    And each comando row shows rótulo, gesto, urgência and sensibilidade

  # ── Catalog load states (SPEC-03) ────────────────────────────────────────────

  Scenario: Catalog load in flight shows a loading catalog state not stale rows
    Given the comando list request is in flight
    When I open the Calibração route
    Then a loading catalog state is shown rather than stale rows

  Scenario: Catalog load failure shows an error catalog state with retry
    Given the comando list request fails with a ProblemDetail
    When I open the Calibração route
    Then an error catalog state with a retry is shown rather than an empty catalog

  Scenario: Loaded catalog with no comando shows the empty state to add the first comando
    Given the loaded catalog contains no comando
    When I open the Calibração route
    Then the empty state is shown so the caregiver can add the first comando

  # ── KPI + honest sync state (SPEC-03, ADR-005) ───────────────────────────────

  Scenario: KPI counter derives comandos pendentes de sincronização from the catalog rows
    Given the catalog has 3 comandos of which 2 are pendente de sincronização
    When I open the Calibração route
    Then the KPI counter of comandos pendentes de sincronização is derived from the catalog rows as 2

  Scenario: Saved comando edit not acknowledged stays pendente de sincronização not ativo no dispositivo
    Given a comando saved edit is not acknowledged by the device
    When I open the Calibração route
    Then the comando is kept pendente de sincronização rather than ativo no dispositivo

  Scenario: Device-acknowledged comando shows ativo no dispositivo
    Given the device acknowledged the catalog version that includes the comando
    When I open the Calibração route
    Then the comando shows ativo no dispositivo

  # ── RBAC of UI (SPEC-05) ─────────────────────────────────────────────────────

  Scenario: Caller lacking comando write permission has the novo comando entry point hidden
    Given the caller lacks the comando write permission
    When I open the Calibração route
    Then the novo comando entry point is hidden

  Scenario: Caller lacking comando read permission has the Calibração nav item hidden
    Given the caller lacks the comando read permission
    When the shell renders the navigation
    Then the Calibração nav item is hidden

  # ── Nav visibility + guard redirect (SPEC-01) ────────────────────────────────

  Scenario: Registered paciente keeps the Calibração nav item visible
    Given a paciente is registered for the active empresa
    When the shell renders the navigation
    Then the Calibração nav item is kept visible

  Scenario: No registered paciente redirects the caregiver to the onboarding paciente step
    Given no paciente is registered for the active empresa
    When I open the Calibração route
    Then the caregiver is redirected to the onboarding paciente step
