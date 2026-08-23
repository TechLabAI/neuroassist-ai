# SPEC ref: plan-e2e-integration.md §11.2 (C-30/C-42/C-43) — issue E2E-015
#
# O PAC gravava `paciente_cuidador` e parava ai. O ACS — que e quem o EVNT consulta para
# decidir quem recebe um alarme — lia `caregiver_patient_link`, em outro schema. Nada ligava
# as duas, entao todo paciente criado pelo PAC nascia com directory vazio: alarme sem
# destinatario. O Flow-03 so passou porque o roteiro criava o vinculo no ACS a mao.
Feature: Directory propagation — o vinculo cuidador-paciente chega ao ACS

  Background:
    Given the PAC service is running
    And a valid JWT bearer token is present with tenant claim "empresa-abc"
    And the request header "X-Empresa-Id" is "empresa-abc"
    And the directory outbox is empty

  # EARS: WHEN a patient is registered with caregivers, THE SYSTEM SHALL record the intent to
  # propagate each link to the ACS directory, in the same transaction.
  Scenario: Registrar paciente com cuidadora enfileira o vinculo para o directory
    Given ACS resolves any caregiver as belonging to "empresa-abc"
    When a POST request is made to "/api/pac/v1/pacientes" with body:
      """
      {
        "nome": "Márcio Amaral",
        "cuidadorResponsavelIds": ["cuid-001"]
      }
      """
    Then the response status is 201
    And the directory outbox has 1 pending "LINK" entry

  # EARS: WHEN the drain runs and the ACS accepts, THE SYSTEM SHALL mark the entry delivered.
  Scenario: O dreno entrega o vinculo ao ACS e a entrada sai da fila
    Given ACS resolves any caregiver as belonging to "empresa-abc"
    And ACS accepts caregiver-patient links
    When a POST request is made to "/api/pac/v1/pacientes" with body:
      """
      {
        "nome": "Márcio Amaral",
        "cuidadorResponsavelIds": ["cuid-001"]
      }
      """
    And the directory outbox is drained
    Then ACS received a link request for the created patient
    And the directory outbox has 0 pending entries
    And the directory outbox has 1 "sent" entry

  # EARS: IF the ACS is unavailable, THEN the link SHALL remain pending — never dropped.
  # Esta e a propriedade que o `hist_outbox` tinha no papel e nao tinha de fato (C-44):
  # a linha era durável e nada a reentregava.
  Scenario: ACS fora do ar deixa o vinculo pendente, nao perdido
    Given ACS resolves any caregiver as belonging to "empresa-abc"
    And ACS is unavailable for caregiver-patient links
    When a POST request is made to "/api/pac/v1/pacientes" with body:
      """
      {
        "nome": "Márcio Amaral",
        "cuidadorResponsavelIds": ["cuid-001"]
      }
      """
    And the directory outbox is drained
    Then the directory outbox has 1 pending "LINK" entry
    And the pending entry has 1 delivery attempt

  # EARS: WHERE the ACS refuses terminally, THE SYSTEM SHALL stop retrying and record why.
  # 409 = invariante de cobertura do ACS (D-204): nao se revoga o ultimo cuidador ativo.
  # Reentregar isso para sempre esconderia uma recusa permanente atras de um contador.
  Scenario: Recusa terminal do ACS vira 'failed' com o motivo registrado
    Given a pending "UNLINK" entry exists in the directory outbox
    And ACS refuses the revocation as the last active link
    When the directory outbox is drained
    Then the directory outbox has 0 pending entries
    And the directory outbox has 1 "failed" entry with a recorded reason
