# Feature: Eligibility check — emergency-command guard
# Covers: SPEC-03, api-contract §3, ADR-006 invariant #1
# Safety invariant: a comando of urgencia 'emergencia' is only eligible
# when EVERY primitive of the tenant is in state 'supported'.
# Read-only: endpoint must produce no side effects / state mutations.
#
# CORREÇÃO DE CONTRATO — a feature descrevia uma API por lista de primitivos que nunca existiu.
# O endpoint real é POST /gest-service/eligibility com corpo {"urgencyLevel": "..."} e devolve
# {"eligible": bool, "missingStates": [...]}: o guarda avalia TODOS os primitivos da empresa, e não
# os primitivos que um comando específico referencia. Os cenários abaixo foram reescritos para o
# contrato em vigor; os que dependem da lista por comando estão marcados @wip logo abaixo, com a
# justificativa — implementá-los contra o código atual só poderia produzir um step que não falha.

Feature: Eligibility check — emergency-command guard

  Background:
    Given the tenant "empresa-test" is active
    And the caller is authenticated with role "gest.read"

  # --- Emergency urgency: ALL primitives must be 'supported' ---

  Scenario: Emergency command is eligible when all primitives are supported
    Given primitive "3f1c-aaa" is in state "supported" in tenant "empresa-test"
    And primitive "9a20-bbb" is in state "supported" in tenant "empresa-test"
    When the caller checks eligibility with urgencia "emergencia"
    Then the response status is 200
    And "eligible" is true
    And missingStates is empty

  Scenario: Emergency command is NOT eligible when any primitive is 'registered'
    Given primitive "3f1c-aaa" is in state "supported" in tenant "empresa-test"
    And primitive "9a20-bbb" is in state "registered" in tenant "empresa-test"
    When the caller checks eligibility with urgencia "emergencia"
    Then the response status is 200
    And "eligible" is false
    And missingStates names "9a20-bbb" in state "REGISTERED"
    And missingStates does not name "3f1c-aaa"

  Scenario: Emergency command is NOT eligible when any primitive is 'pending_training'
    Given primitive "3f1c-aaa" is in state "pending_training" in tenant "empresa-test"
    When the caller checks eligibility with urgencia "emergencia"
    Then the response status is 200
    And "eligible" is false
    And missingStates names "3f1c-aaa" in state "PENDING_TRAINING"

  Scenario: Emergency command is NOT eligible when any primitive is 'deprecated'
    Given primitive "3f1c-aaa" is in state "supported" in tenant "empresa-test"
    And primitive "9a20-bbb" is in state "deprecated" in tenant "empresa-test"
    When the caller checks eligibility with urgencia "emergencia"
    Then the response status is 200
    And "eligible" is false
    And missingStates names "9a20-bbb" in state "DEPRECATED"

  # invariant #1 — for composite gestures BOTH primitives SHALL be 'supported'
  Scenario: Emergency composite gesture is ineligible when one primitive is not supported
    Given primitive "3f1c-aaa" is in state "supported" in tenant "empresa-test"
    And primitive "9a20-bbb" is in state "pending_training" in tenant "empresa-test"
    When the caller checks eligibility with urgencia "emergencia"
    Then the response status is 200
    And "eligible" is false

  # --- Routine urgency ---

  Scenario: Routine command is eligible even when a primitive is not supported
    Given primitive "3f1c-aaa" is in state "pending_training" in tenant "empresa-test"
    When the caller checks eligibility with urgencia "rotina"
    Then the response status is 200
    And "eligible" is true
    And missingStates is empty

  # --- Idempotency / read-only ---

  Scenario: Eligibility check is idempotent — repeated calls produce same result without mutation
    Given primitive "3f1c-aaa" is in state "supported" in tenant "empresa-test"
    When the caller checks eligibility with urgencia "emergencia"
    And the caller checks eligibility with urgencia "emergencia" again
    Then both responses are 200 with identical "eligible" value
    And the detectabilityState of "3f1c-aaa" has not changed
    And no transition record was created

  # --- Cross-tenant isolation ---

  Scenario: Primitives from another tenant are not visible to the eligibility check
    Given primitive "3f1c-aaa" is in state "supported" in tenant "empresa-test"
    And primitive "ghost" exists in tenant "empresa-other" in state "registered"
    When the caller checks eligibility with urgencia "emergencia"
    Then the response status is 200
    And "eligible" is true
    And missingStates does not name "ghost"

  # --- Authorization ---

  Scenario: Caller without gest.eligibility.read cannot run the guard
    Given the caller holds no eligibility authority
    When the caller checks eligibility with urgencia "emergencia"
    Then the response status is 403
    And the error type is "/errors/insufficient-scope"

  # ---------------------------------------------------------------------------
  # @wip — capacidade AUSENTE, não cenário difícil.
  #
  # EligibilityRequest é `record EligibilityRequest(@NotBlank String urgencyLevel)`: não há como o
  # chamador dizer QUAIS primitivos o comando referencia, e portanto não há como o serviço recusar
  # um primitivo desconhecido nem devolver resultado por primitivo. Os três cenários abaixo
  # descrevem o contrato do api-contract.md §3.1 que nunca foi construído. Ficam aqui, executáveis
  # no dia em que a lista existir, e fora da suíte enquanto não existir — em vez de virarem steps
  # de corpo vazio que passam sem testar nada.
  # ---------------------------------------------------------------------------

  @wip
  Scenario: Unknown primitiveId in check returns 422
    When the caller checks eligibility for primitives ["unknown-id-xyz"]
    Then the response status is 422
    And the error type is "/errors/unknown-primitive"

  @wip
  Scenario: Mix of known and unknown primitiveIds returns 422 listing all unknowns
    Given primitive "3f1c-aaa" is in state "supported" in tenant "empresa-test"
    When the caller checks eligibility for primitives ["3f1c-aaa", "ghost-1", "ghost-2"]
    Then the response status is 422
    And the error detail lists "ghost-1" and "ghost-2"

  @wip
  Scenario: Routine command is NOT eligible when a referenced primitive is deprecated
    Given primitive "3f1c-aaa" is in state "deprecated" in tenant "empresa-test"
    When the caller checks eligibility with urgencia "rotina" for primitives ["3f1c-aaa"]
    Then the response status is 200
    And "eligible" is false
