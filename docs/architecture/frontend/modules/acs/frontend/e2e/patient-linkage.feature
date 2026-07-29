Feature: Patient linkage — link, unlink and coverage invariant
  As a coordenador_clinica binding caregivers to a paciente
  I want linking and unlinking to keep coverage honest and safe
  So that a paciente is never left without an alarm-eligible responder

  Background:
    Given I am authenticated as a coordinator with scope "acs.cuidador.read acs.vinculo.write"
    And an active empresa is selected and the empresa header is sent
    And acs-service is available with mock responses
    And a paciente exists on the active empresa

  # ── Render (SPEC-01, 03) ─────────────────────────────────────────────────────

  Scenario: The vinculos route renders the patient linkage panel scoped by the empresa header
    When I open the vinculos route for the paciente
    Then the patient linkage panel is fetched scoped by the empresa header
    And the linked caregivers and unlinked candidates render with a coberto coverage header

  # ── Link (SPEC-04) ───────────────────────────────────────────────────────────

  Scenario: Linking a candidate cuidador re-derives the paciente coverage
    Given a candidate cuidador is not yet linked to the paciente
    When I link that cuidador to the paciente
    Then the vinculo is created and the paciente coverage is re-derived from a fresh fetch

  Scenario: Linking a cuidador already linked to the paciente is treated as an idempotent success
    Given a cuidador is already linked to the paciente
    When I link that cuidador to the paciente again
    Then the idempotent link response is treated as success without creating a duplicate vinculo

  Scenario: Linking a cuidador from a different empresa is blocked with a blocking modal
    Given a cuidador belongs to a different empresa
    When I link that cuidador to the paciente
    Then the backend responds cross-tenant-link
    And the link is blocked with a blocking modal and the paciente coverage is unchanged

  Scenario: Linking an unknown paciente reference shows an inline error
    Given the paciente reference is unknown to the active empresa
    When I link a cuidador to that paciente
    Then the backend responds unknown-patient
    And an inline error is shown

  # ── Unlink + coverage guard (SPEC-04, 07, ADR-005) ───────────────────────────

  Scenario: Unlinking a non-last covering caregiver re-derives the paciente coverage
    Given a caregiver is not the last covering caregiver of the paciente
    When I unlink that cuidador from the paciente
    Then the vinculo is revoked and the paciente coverage is re-derived from a fresh fetch

  Scenario: Unlinking the last covering caregiver is blocked before the request
    Given a caregiver is the last covering caregiver of the paciente
    When I try to unlink that cuidador from the paciente
    Then the unlink is blocked and a blocking modal explains the paciente would be left without coverage
    And the vinculo is unchanged

  Scenario: A bypassed last-covering guard is re-enforced by the backend on unlink
    Given a caregiver is the last covering caregiver of the paciente and the client guard is bypassed
    When I unlink that cuidador from the paciente
    Then the backend blocks the unlink
    And a blocking modal is shown and the vinculo is unchanged

  Scenario: A link or unlink error re-fetches and re-derives the paciente coverage
    Given a link or unlink request fails with a problem detail
    When I retry loading the linkage panel
    Then the paciente coverage is re-fetched and re-derived to stay truthful

  # ── Coverage derivation (SPEC-03, ADR-005) ───────────────────────────────────

  Scenario: An inactive linked caregiver is excluded from the paciente coverage count
    Given the paciente is linked to one active caregiver and one inactive caregiver
    When I open the vinculos route for the paciente
    Then the inactive caregiver is excluded from the paciente coverage count

  # ── RBAC (SPEC-05) ───────────────────────────────────────────────────────────

  Scenario: The vincular and desvincular actions are disabled without vinculo write permission
    Given the caller lacks the vinculo write permission
    When I open the vinculos route for the paciente
    Then the vincular and desvincular actions are disabled

  Scenario: The patient linkage route is blocked without vinculo write permission
    Given the caller lacks the vinculo write permission
    When I open the vinculos route for the paciente
    Then the patient linkage route is blocked
