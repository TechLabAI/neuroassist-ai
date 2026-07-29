Feature: Cuidadores roster — list, coverage, invite and deactivate
  As a coordenador_clinica managing access on the active empresa
  I want an honest caregiver roster with coverage state
  So that no paciente is ever silently left without a responsible caregiver

  Background:
    Given I am authenticated as a coordinator with scope "acs.cuidador.read acs.cuidador.write"
    And an active empresa is selected and the empresa header is sent
    And acs-service is available with mock responses

  # ── Roster render / load (SPEC-01, 03) ───────────────────────────────────────

  Scenario: The Cuidadores route renders the caregiver roster for the active empresa
    When I open the Cuidadores roster
    Then the caregiver roster is fetched scoped by the empresa header
    And the caregiver rows render with nome papel status and coverage badges

  Scenario: The roster shows a loading roster state while the caregiver list is in flight
    Given the caregiver list request is slow
    When I open the Cuidadores roster
    Then a loading roster state is shown rather than stale rows

  Scenario: A failed roster load shows an error roster state with a retry
    Given the caregiver list request fails
    When I open the Cuidadores roster
    Then an error roster state with a retry is shown rather than an empty roster

  # ── Coverage honesty (SPEC-03, ADR-005) ──────────────────────────────────────

  Scenario: A paciente with zero active covering caregiver is marked sem cobertura and kept visible
    Given a paciente has zero active covering caregiver
    When I open the Cuidadores roster
    Then that paciente is marked sem cobertura and kept visible rather than hidden

  Scenario: The paciente coverage is derived from active links joined with each caregiver active status
    Given a paciente is linked to one active caregiver and one inactive caregiver
    When I open the Cuidadores roster
    Then the paciente coverage is derived from the active links joined with each caregiver active status
    And the inactive caregiver is excluded from the paciente coverage count

  # ── Invite (SPEC-04) ─────────────────────────────────────────────────────────

  Scenario: The coordinator invites a new cuidador carrying an idempotency key
    When I open the novo cuidador route
    And I fill in nome "Jamilly Amaral" login "jamilly@familia-amaral" papel "cuidadora" and a senha
    And I invite the cuidador
    Then the caregiver editor rendered with a blank draft in editing state before I filled it
    And the draft is posted carrying an idempotency key
    And the caregiver roster reloads

  Scenario: A retried cuidador invite reuses the same idempotency key
    Given I invite the cuidador and the first attempt times out
    When I retry the same cuidador invite
    Then the same idempotency key is reused so no duplicate cuidador is created

  Scenario: An invite with a duplicate login shows an inline login error and is not saved
    Given a login already exists in the empresa
    When I invite a cuidador with that login
    Then the backend responds login-duplicate
    And an inline error is shown on the login field and the cuidador is not saved

  Scenario: A blank nome keeps the salvar cuidador action disabled
    When I open the novo cuidador route
    And I leave the nome empty
    Then the salvar cuidador action is kept disabled

  Scenario: A blank login keeps the salvar cuidador action disabled
    When I open the novo cuidador route
    And I leave the login empty
    Then the salvar cuidador action is kept disabled

  Scenario: A blank senha on create keeps the salvar cuidador action disabled
    When I open the novo cuidador route
    And I leave the senha empty while creating a new cuidador
    Then the salvar cuidador action is kept disabled

  Scenario: An invite error preserves the coordinator draft
    Given the cuidador invite fails with a problem detail
    When I invite the cuidador
    Then a toast is shown and the draft is preserved and the cuidador is not saved

  Scenario: A validation problem on a cuidador field shows the inline hint and keeps the coordinator draft
    Given the backend responds validation for a cuidador field
    When I invite the cuidador
    Then the field-level inline hint is shown and the coordinator draft is kept

  Scenario: The roster renders papel only as cuidadora or coordenador_clinica labels and never echoes the senha
    Given a cuidador exists in the active empresa
    When I open the editar cuidador route for that cuidador
    Then the papel is rendered only as a cuidadora or coordenador_clinica label
    And the senha of the loaded cuidador is never echoed

  Scenario: A rejected optimistic roster edit rolls back the optimistic row and preserves the draft
    Given an optimistic roster edit is applied to a caregiver row
    When the optimistic roster edit is rejected by the backend
    Then the optimistic row is rolled back and the draft is preserved

  Scenario: The ACS bootstrap init primes the roster and coverage so the roster resolves on first navigation
    Given the ACS module is not yet loaded
    When the ACS bootstrap init runs
    Then the roster and coverage are primed so the roster resolves on first navigation

  Scenario: The ACS bootstrap destroy resets the facade signals and aborts any in-flight roster request
    Given the ACS roster surface is active with an in-flight roster request
    When the ACS bootstrap destroy runs
    Then the ACS facade signals are reset and the in-flight roster request is aborted

  # ── Editar route pre-fill (SPEC-01) ──────────────────────────────────────────

  Scenario: The editar cuidador route renders the caregiver editor pre-filled from that cuidador
    Given a cuidador exists in the active empresa
    When I open the editar cuidador route for that cuidador
    Then the caregiver editor is rendered pre-filled from that cuidador
    And the senha of the loaded cuidador is never echoed

  Scenario: The editar route for an unknown cuidadorId redirects to the not-found view
    Given the editar route references a cuidadorId unknown to the active empresa
    When I open that editar cuidador route
    Then the backend responds caregiver-not-found
    And the roster not-found view is shown rather than a blank editor

  # ── Deactivate + coverage guard (SPEC-04, 07, ADR-005) ───────────────────────

  Scenario: Deactivating a non-last covering caregiver sets it inactive and re-derives coverage
    Given a caregiver is not the last covering caregiver of any paciente
    When I deactivate that cuidador
    Then the cuidador status becomes inactive and the paciente coverage is re-derived from a fresh fetch

  Scenario: Deactivating the last covering caregiver is blocked before the request
    Given a caregiver is the last covering caregiver of a paciente
    When I try to deactivate that cuidador
    Then the desativar cuidador action is kept disabled and a blocking modal explains the paciente would be left without coverage
    And the cuidador stays active

  Scenario: A bypassed last-covering guard is re-enforced by the backend on deactivation
    Given a caregiver is the last covering caregiver of a paciente and the client guard is bypassed
    When I deactivate that cuidador
    Then the backend blocks the deactivation
    And a blocking modal is shown and the cuidador stays active

  # ── RBAC / nav gating (SPEC-05) ──────────────────────────────────────────────

  Scenario: The convidar cuidador entry point is hidden without cuidador write permission
    Given the caller lacks the cuidador write permission
    When I open the Cuidadores roster
    Then the convidar cuidador entry point is hidden

  Scenario: The desativar cuidador action is disabled without cuidador write permission
    Given the caller lacks the cuidador write permission
    When I open the Cuidadores roster
    Then the desativar cuidador action is disabled

  Scenario: The Cuidadores nav item is hidden and a non-coordinator is redirected off the roster
    Given the caller is not a coordinator and lacks the cuidador read permission
    When the shell renders the navigation
    Then the Cuidadores nav item is hidden
    And the caregiver is redirected away from the Cuidadores roster surface
