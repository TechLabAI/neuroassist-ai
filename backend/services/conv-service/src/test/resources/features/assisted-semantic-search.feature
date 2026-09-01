# Ref: SPEC-03 (assisted search), integration-contracts §1.1, api-contract §2.2
Feature: Assisted semantic search

Background:
  Given a patient session with scope conv.arvore.navegar for patient p-1024
  And the active session locale is en
  And the arvore-fala index has ativo en frase nodes for patient p-1024

Scenario: Semantic search returns the closest authored node
  Given Elasticsearch is reachable
  When the patient searches for the intent some water please
  Then the response status is 200
  And the response field retrieval is semantic
  And the response field degraded is false
  And the top match noId is n-42
  And every returned match has an ativo en frase node

Scenario: Search never returns text absent from the curated tree
  Given Elasticsearch is reachable
  When the patient searches for the intent order me a taxi
  Then no returned match texto is outside the patient curated tree

Scenario: Fail open to static when Elasticsearch is unavailable
  Given Elasticsearch is unreachable
  When the patient searches for the intent some water please
  Then the response status is 200
  And the response field degraded is true
  And the response field retrieval is static
  And the response field matches is empty
  And the response header X-Degraded is true

Scenario Outline: The k parameter is defaulted and clamped
  When the patient searches with k <k>
  Then the effective k is <effective>

  Examples:
    | k   | effective |
    | 0   | 5         |
    | 3   | 3         |
    | 200 | 50        |

Scenario: Reject a blank intent
  # D-492: <blank> in a plain Scenario is literal text, not an Outline placeholder.
  # Using a descriptive step so the step-def can send intent="" without <> notation.
  When the patient sends a search request with no intent text
  Then the response status is 400
  And the response problem type ends with invalid-request
