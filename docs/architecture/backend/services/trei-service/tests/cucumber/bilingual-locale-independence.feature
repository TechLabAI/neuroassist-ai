# SPEC-03 / SPEC-11 — Bilingual independent authoring per locale (ADR-018). Structure is locale-invariant.

Feature: Independent bilingual authoring per locale

Background:
  Given a patient p-1024 in tenant clinica-acme
  And a node n-90 of type frase under parent n-root

Scenario: Each locale is approved and published independently
  Given node n-90 locale en is ativo
  And node n-90 locale pt is in sub-state rascunho
  Then node n-90 locale pt content status is pendente
  And CONV can retrieve node n-90 only in locale en

Scenario: Node structure is shared across locales
  When node n-90 is materialized in locale pt
  Then the same node id n-90 is reused
  And a new content row for node n-90 locale pt is added
  And parent, tipo, and engineFeatureRef are unchanged

Scenario: One approval in one locale does not publish the other locale
  Given node n-90 locale en has both approvals
  And node n-90 locale pt has only the caregiver approval
  When the publication gate is evaluated
  Then only locale en is published
  And locale pt stays pendente

Scenario: Each locale is a separate Elasticsearch document
  Given node n-90 is ativo in locale en and locale pt
  Then two Elasticsearch documents exist with ids n-90_en and n-90_pt
  And each carries its own embedding and audio_ref

Scenario: Inspecting a node never bleeds one locale text into another
  When the caregiver inspects node n-90
  Then locale en shows its own texto
  And locale pt shows texto null while still rascunho
