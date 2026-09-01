# SPEC-12 — Publication pipeline (embed -> tts -> index), broker-less outbox (ADR-004).

Feature: Broker-less publication pipeline with atomic activation

Background:
  Given a node n-90 with locale en whose dual-authorization gate has just cleared
  And the outbox holds steps embed, tts, index all pendente

Scenario: Happy path — all steps succeed then the locale becomes ativo
  When the embed step succeeds
  And the tts step succeeds
  And the index step succeeds
  Then the sub-state becomes ativo
  And the content status conv_no_conteudo for node n-90 locale en becomes ativo
  And publicadoEm is set

Scenario: Embed fails — locale never reaches ativo
  When the embed step fails
  Then the embed outbox step is pendente with a backoff time
  And the sub-state stays publicando
  And the content status stays pendente

Scenario Outline: A failing step keeps the locale out of CONV
  When the <step> step fails
  Then the sub-state stays publicando
  And the content status stays pendente

  Examples:
    | step  |
    | embed |
    | tts   |
    | index |

Scenario: TTS is synthesized only once per node and locale
  Given the tts step already produced a cached audio file for node n-90 locale en
  When the tts step is retried
  Then no new ElevenLabs synthesis is requested
  And the existing cached audio file is reused

Scenario: Index write carries all eight traceability fields
  When the index step runs for node n-90 locale en
  Then the Elasticsearch document id is the value n-90_en
  And the document carries all eight traceability fields
  And the document field status is the value ativo

Scenario: Retry budget exhausted surfaces without silent activation
  Given the index step has exhausted its retry budget
  Then the index outbox step is falha
  And the sub-state stays publicando
  And the publication status endpoint reports the failed step
