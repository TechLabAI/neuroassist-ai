# Ref: SPEC-02 (select), SPEC-05 (idempotency), SPEC-12 (degradation), api-contract §2.3
Feature: Node selection and audio playback

Background:
  Given a patient session with scope conv.no.selecionar for patient p-1024
  And the active session locale is en

Scenario: Select a frase node with cached audio present
  Given node n-42 is a frase ativo in en with a present audio reference
  When the patient selects node n-42 via gesto
  Then the response status is 200
  And the response field texto is I want water with ice
  And the response field playbackMode is audio-cacheado
  And a SpeechNodeSelected fact is emitted to HIST for node n-42

Scenario: Fall back to Web Speech when audio is absent
  Given node n-42 is a frase ativo in en with no audio reference
  When the patient selects node n-42 via toque
  Then the response status is 200
  And the response field audioRef present is false
  And the response field playbackMode is web-speech
  And the response field degraded is true
  And a SpeechNodeSelected fact records the web-speech fallback

Scenario: Selecting a categoria node is rejected
  Given node n-root is a categoria
  When the patient selects node n-root via toque
  Then the response status is 409
  And the response problem type ends with node-not-selectable

Scenario: Selecting a node pending in the active locale is rejected
  Given node n-51 is ativo in pt and pendente in en
  When the patient selects node n-51 via toque
  Then the response status is 409
  And the response problem type ends with content-pending-locale

Scenario: Repeated selection with the same clientEventId is audited once
  Given node n-42 is a frase ativo in en
  When the patient selects node n-42 with clientEventId ev-777 twice
  Then the response status is 200 both times
  And exactly 1 SpeechNodeSelected fact exists for clientEventId ev-777

Scenario: Selection still succeeds when HIST is unavailable
  Given node n-42 is a frase ativo in en
  And HIST is unreachable
  When the patient selects node n-42 via toque
  Then the response status is 200
  And the audit fact is buffered in conv_audit_outbox with status pendente
