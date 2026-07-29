# Refs: SPEC-03 (live delivery channel), FE ADR-005 · api-contract §3.2/§3.3
# This feature closes the M1 handoff open item: the SSE vs polling live-channel shape.
Feature: Live event delivery channel (SSE with polling fallback)

Background:
  Given a tenant "empresa_demo" with patient "pac-1"
  And a caregiver session for "cg-1" authorized for patient "pac-1"

Scenario: A new emergency event is pushed to an open SSE stream
  Given the caregiver has an open SSE connection to "/api/v1/events/stream"
  When an emergency Evento "evt-live-1" is persisted for patient "pac-1"
  Then the caregiver's stream receives an "evento.created" frame for "evt-live-1"
  And the frame data field "urgencia" is "emergencia"
  And the frame data field "tier" is "full_screen_alarm"
  And the frame carries an "id" equal to the Evento sequence

Scenario: Hydration on load re-shows an in-flight alarm
  Given an emergency Evento "evt-live-2" is active and unconfirmed for tenant "empresa_demo"
  When the caregiver requests "/api/v1/events/active"
  Then the response status is 200
  And the response field "activeEvent.eventoId" is "evt-live-2"
  And the response field "activeEvent.urgencia" is "emergencia"

Scenario: Reconnect with Last-Event-ID replays missed frames
  Given the caregiver's stream dropped after sequence 50
  And emergency Evento frames 51 and 52 were emitted while disconnected
  When the caregiver reconnects to "/api/v1/events/stream" with Last-Event-ID "50"
  Then the stream replays frames with sequence 51 and 52
  And no active event is missed

Scenario: Polling fallback returns changed events only
  Given the SSE stream is unavailable
  And an Evento changed state at "2026-07-28T14:03:12Z"
  When the caregiver requests "/api/v1/events/active" with query "since=2026-07-28T14:03:11Z"
  Then the response status is 200
  And the response includes the changed Evento

Scenario: An idle stream is kept alive
  Given the caregiver has an open SSE connection to "/api/v1/events/stream"
  And no event occurs for the keep-alive interval
  Then the caregiver's stream receives a "keepalive" frame

Scenario: The stream is tenant-scoped
  Given a caregiver of tenant "empresa_other" has an open SSE connection
  When an emergency Evento is persisted for tenant "empresa_demo"
  Then the "empresa_other" stream receives no frame for that Evento
