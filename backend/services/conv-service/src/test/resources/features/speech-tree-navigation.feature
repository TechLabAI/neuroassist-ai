# Ref: SPEC-01 (API surface), SPEC-02 (navigation), api-contract §2.1
# Reusable response-status steps live in SharedSteps (see SPEC-08); do not redefine them here.
Feature: Speech tree navigation

Background:
  Given a patient session with scope conv.arvore.navegar for patient p-1024 in tenant clinica-acme
  And patient p-1024 has a speech tree supporting locales pt and en

Scenario: Fetch the full tree in the active locale
  Given the active session locale is en
  When the patient requests GET /api/v1/conv/tree
  Then the response status is 200
  And the response field localeServed is en
  And every categoria node is returned with selecionavel false
  And every frase node with ativo en content is returned with selecionavel true

Scenario: Tree structure is identical across locales
  When the patient requests the tree in locale pt
  And the patient requests the tree in locale en
  Then the node ids, parentId, tipo and ordem are identical in both responses
  And only the localized texto and audioRef differ

Scenario: A node pending in the active locale is non-selectable
  Given node n-51 is ativo in pt and pendente in en
  And the active session locale is en
  When the patient requests GET /api/v1/conv/tree
  Then node n-51 is returned with contentStatus pending_locale
  And node n-51 is returned with texto null
  And node n-51 is returned with selecionavel false

Scenario: Reject an unsupported locale override
  When the patient requests the tree with Accept-Language fr
  Then the response status is 400
  And the response problem type ends with unsupported-locale

Scenario: Reject a request whose body carries biometric data
  When the patient sends a tree request body containing landmark frame data
  Then the response status is 400
  And the response problem type ends with no-biometric-ingress
