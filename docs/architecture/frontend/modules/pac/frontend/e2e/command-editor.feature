Feature: Calibração — command editor and lifecycle
  As a family caregiver authoring the command catalog
  I want to create, calibrate and publish comandos with a fail-closed emergency gate
  So that an emergency comando is never mapped to a gesture the engine cannot detect

  Background:
    Given I am authenticated with scope "pac:command:read pac:command:write pac:command:publish"
    And an active empresa is selected and the empresa header is sent
    And a paciente "Márcio Amaral" is registered for the active empresa
    And pac-service is available with mock responses

  # ── Editor routes (SPEC-01) ──────────────────────────────────────────────────

  Scenario: Novo comando route renders the command editor with a blank draft in editing state
    When I open the novo comando route
    Then the command editor renders a blank draft in editing state

  Scenario: Editar comando route renders the command editor pre-filled from that comando
    When I open the editar comando route for an existing comando
    Then the command editor is rendered pre-filled from that comando

  Scenario: Editar route with unknown comandoId redirects to the not-found view
    Given the editar route references a comandoId unknown to the active empresa
    When I open the editar comando route
    Then the system redirects to the not-found view rather than a blank editor

  Scenario: Caller lacking comando write permission has the command editor route blocked and editor hidden
    Given the caller lacks the comando write permission
    When I open the novo comando route
    Then the command editor route is blocked and the command editor is kept hidden

  # ── Synchronous validation invariants (SPEC-03) ──────────────────────────────

  Scenario: Blank rotulo keeps the salvar comando action disabled
    Given I am in the command editor with a blank draft
    When I leave the rotulo empty
    Then the salvar comando action is kept disabled with an inline hint

  Scenario: Timing window minimo not below maximo keeps the salvar comando action disabled
    Given I am in the command editor with a blank draft
    When I set the timing window minimo not below the maximo
    Then the salvar comando action is kept disabled with an inline hint

  Scenario: Composite primitivo secundario equal to primitivo principal keeps salvar comando disabled
    Given I am in the command editor with a blank draft
    When I set the composite primitivo secundario equal to the primitivo principal
    Then the salvar comando action is kept disabled

  Scenario: Sensibilidade is rendered only as baixa media or alta labels never a raw threshold
    Given I am in the command editor
    Then sensibilidade is rendered only as baixa media or alta labels never a raw threshold value

  # ── Lifecycle action gating (SPEC-03) ────────────────────────────────────────

  Scenario: Drafted editor comando enables validar and keeps publicar disabled
    Given the editor comando is drafted
    Then the validar action is enabled and the publicar action is kept disabled

  Scenario: Validated editor comando enables publicar
    Given the editor comando is validated
    Then the publicar action is enabled

  Scenario: Published editor comando enables recalibrar and keeps validar disabled
    Given the editor comando is published
    Then the recalibrar action is enabled and the validar action is kept disabled

  Scenario: Retired editor comando keeps every lifecycle action disabled
    Given the editor comando is retired
    Then every lifecycle action is kept disabled

  Scenario: Caller lacking comando publish permission has validar publicar recalibrar and retirar disabled
    Given the caller lacks the comando publish permission
    When I open a comando in the editor
    Then the validar publicar recalibrar and retirar lifecycle actions are disabled

  # ── Save + idempotency + concurrency (SPEC-04) ───────────────────────────────

  Scenario: Saving a new comando posts the draft carrying an idempotency key
    Given I am in the command editor with a valid routine draft
    When I save the new comando
    Then the draft is posted carrying an idempotency key
    And the comando becomes pendente de sincronização

  Scenario: Retried comando save reuses the same idempotency key so no duplicate comando is created
    Given a comando save was retried with the same draft
    When the save is retried
    Then the same idempotency key is reused so no duplicate comando is created

  Scenario: Saving an edit to an existing comando sends the last-read ETag as If-Match precondition
    Given an existing comando was read with an ETag
    When I save an edit to that existing comando
    Then the last-read ETag is sent as the If-Match precondition

  Scenario: Optimistic comando save rejected by the backend rolls back the row and preserves the draft
    Given an optimistic comando save is rejected by the backend
    When the save fails
    Then the optimistic row is rolled back and the draft is preserved

  Scenario: Reload the comando list after a successful lifecycle transition reflects authoritative state
    Given a successful lifecycle transition on a comando
    When the transition completes
    Then the comando list is reloaded so the catalog reflects the authoritative server state

  Scenario: Editing identity of a comando that is not drafted routes the caregiver to recalibrar
    Given a comando that is not drafted
    When I try to edit its identity fields
    Then the caregiver is routed to recalibrar or retirar-e-recriar instead of a draft edit

  # ── Validate / publish / emergency gate (SPEC-03, SPEC-07, ADR-005) ──────────

  Scenario: Validar a drafted comando transitions it to validated
    Given the editor comando is drafted
    When I click validar
    Then the comando transitions to validated

  Scenario: Publicar a validated emergency comando with a supported gesture publishes it
    Given the editor comando is validated with urgencia emergencia and a supported gesture
    When I click publicar
    Then the emergency comando transitions to published

  Scenario: Emergency-not-detectable on publicar blocks publish with a blocking modal and leaves comando validated
    Given the backend responds emergency-not-detectable on publicar
    When I click publicar
    Then the publish is blocked with a blocking modal and the emergency comando is left validated

  Scenario: Eligibility-gate-unavailable on publicar shows a retryable toast and comando not published
    Given the backend responds eligibility-gate-unavailable on publicar
    When I click publicar
    Then a retryable toast is shown and the emergency comando is not marked published

  Scenario: Rotina comando publish response with advisory warnings shows a non-blocking advisory banner
    Given a rotina comando publish response contains advisory warnings
    When I click publicar
    Then a non-blocking advisory banner is shown and the publish still completes

  # ── Recalibrate / retire (SPEC-04, SPEC-07) ──────────────────────────────────

  Scenario: Recalibrar a published comando increments calibracaoVersao and returns to pendente de sincronização
    Given the editor comando is published
    When I recalibrar the tuning fields
    Then calibracaoVersao increases by one and the comando returns to pendente de sincronização

  Scenario: Retirar a comando makes it terminal and rejects further transitions
    Given a comando in a non-retired estado
    When I retirar the comando
    Then the comando becomes retired and further lifecycle transitions are rejected

  # ── ProblemDetail mappings (SPEC-07) ─────────────────────────────────────────

  Scenario: Validation ProblemDetail on a comando field shows the inline hint and keeps the draft
    Given the backend responds validation for a comando field
    When I save the comando
    Then the field-level inline hint is shown and the caregiver draft is kept

  Scenario: Missing-tenant ProblemDetail shows a session banner prompting re-authentication
    Given the backend responds missing-tenant
    When I save the comando
    Then a session banner prompting re-authentication is shown

  Scenario: Insufficient-scope ProblemDetail shows a permission toast for the blocked action
    Given the backend responds insufficient-scope
    When I run the blocked action
    Then a permission toast for the blocked action is shown

  Scenario: Tenant-mismatch ProblemDetail shows a session-inconsistency banner without leaking the empresa
    Given the backend responds tenant-mismatch
    When I save the comando
    Then a session-inconsistency banner is shown without leaking whether the empresa exists

  Scenario: Not-found ProblemDetail for a comando redirects to the catalog not-found view
    Given the backend responds not-found for a comando
    When I open the editar comando route
    Then the system redirects to the catalog not-found view rather than a blank editor

  Scenario: Conflict-precondition on a stale ETag shows a blocking modal to reload and reapply the edit
    Given the backend responds conflict-precondition on a stale ETag
    When I save the comando
    Then a blocking modal asks the caregiver to reload and reapply the edit

  Scenario: Invalid-lifecycle-transition shows a toast and leaves the displayed comando estado unchanged
    Given the backend responds invalid-lifecycle-transition
    When I attempt the transition
    Then a toast is shown and the displayed comando estado is left unchanged

  Scenario: Duplicate-active-mapping shows an inline error on the gesture selection and comando not saved
    Given the backend responds duplicate-active-mapping
    When I save the comando
    Then an inline error is shown on the gesture selection and the comando is not marked saved

  Scenario: Any comando mutation failure preserves the draft and does not mark the comando saved or ativo
    Given any comando mutation fails
    When the mutation returns a ProblemDetail
    Then the draft is preserved and the comando is not marked saved or ativo

  # ── GEST composition + new-type delegation (SPEC-04, SPEC-06) ────────────────

  Scenario: Requesting a new tipo de gesto from GEST selects only the returned primitivo id
    Given the tipo de gesto I need is not in the list
    When I request a new tipo de gesto from GEST
    Then only the returned primitivo id is selected

  Scenario: Composing the GEST live-test at the shell page does not import gest scope into any PAC library
    Given I am editing an unsaved draft
    When the shell page composes the GEST live-test with the current draft
    Then the GEST live-test runs without importing gest scope into any PAC library

  # ── Feature flags (SPEC-05) ──────────────────────────────────────────────────

  Scenario: Inline novo tipo flag off hides the inline novo tipo de gesto affordance
    Given the inline novo tipo flag is off
    When I open the command editor gesture picker
    Then the inline novo tipo de gesto affordance is hidden

  Scenario: Deferred edge sync flag off collapses the sync badge to a single saved state
    Given the deferred edge sync flag is off
    When I view a saved comando
    Then the sync badge collapses to a single saved state

  # ── Shell integration (SPEC-06) ──────────────────────────────────────────────

  Scenario: Shell loading the PAC descriptor contributes the Calibração nav item without eagerly loading PAC
    When the shell loads the PAC descriptor
    Then the Calibração nav item is contributed without eagerly loading the PAC module

  Scenario: PAC bootstrap init primes the paciente read so the tenant patient guard resolves
    When the PAC bootstrap init runs
    Then the paciente read is primed so the tenant patient guard resolves on first navigation

  Scenario: PAC bootstrap destroy resets the PAC facade signals and aborts the in-flight catalog request
    When the PAC bootstrap destroy runs
    Then the PAC facade signals are reset and any in-flight catalog request is aborted
