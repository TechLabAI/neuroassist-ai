# Covers SPEC-03 (history query, feedback-verdict, scope), SPEC-06
Feature: History timeline query is filtered, paginated and tenant/scope bound

Background:
  Given a provisioned tenant "familia-silva"

Scenario: A coordinator queries the timeline for a subject ordered newest-first
  Given a coordinator token with scope "hist:read:all"
  And the stream "paciente:pac-1" has 3 records
  When GET /history is called with subjectType "paciente" and subjectId "pac-1"
  Then the response status is 200
  And the items are ordered by recordedAt descending
  And total is 3

Scenario: A feedback-verdict query filters by the event factTypes and returns the producer snapshot
  Given a coordinator token with scope "hist:read:all"
  And subject paciente "pac-1" has facts of types "evento.confirmado" and "comando.recalibrado"
  When GET /history is called with subjectType "paciente" subjectId "pac-1" and factType "evento.confirmado"
  Then the response status is 200
  And every item has factType "evento.confirmado"
  And each item payload carries the producer verdict snapshot unchanged

Scenario: subjectId without subjectType is rejected
  Given a coordinator token with scope "hist:read:all"
  When GET /history is called with subjectId "pac-1" and no subjectType
  Then the response status is 400
  And the problem type is "/errors/incomplete-subject-filter"

Scenario: An inverted time window is rejected
  Given a coordinator token with scope "hist:read:all"
  When GET /history is called with from "2026-08-16T10:00:00Z" and to "2026-08-15T10:00:00Z"
  Then the response status is 400
  And the problem type is "/errors/invalid-time-window"

Scenario: A caregiver only sees subjects within their ACS-resolved scope
  Given a caregiver token with scope "hist:read"
  And ACS resolves the caregiver scope to patients "pac-1"
  When GET /history is called with subjectType "paciente" and subjectId "pac-9"
  Then the response items are empty

Scenario: A caregiver read fails closed when ACS is unavailable
  Given a caregiver token with scope "hist:read"
  And ACS is unreachable
  When GET /history is called with subjectType "paciente" and subjectId "pac-1"
  Then the response status is 503
  And the problem type is "/errors/scope-unavailable"
