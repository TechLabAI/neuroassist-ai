# GEST-022 -- Implement structured JSON logging with MDC (correlationId, tenantId, actor) — assert zero biometric/PII data in any log line

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto
Todos os logs do `gest-service` devem ser JSON estruturado com campos MDC padrao (`correlationId`, `tenantId`, `actor`). Nenhum log pode conter dados biometricos, video, imagem, landmarks ou qualquer PII de paciente (invariante de privacidade doc 02). O `logback-spring.xml` configura o JSON encoder (Logstash) e inclui os campos MDC em todos os logs. Um teste de compliance verifica que nenhum campo proibido aparece em logs de um request completo.

## Spec de referencia
- SPEC-07, api-contract.md §7 (correlationId em erros)

## Escopo
- [ ] implementar `backend/services/gest-service/src/main/resources/logback-spring.xml` (Logstash JSON encoder, MDC fields: correlationId, tenantId, actor; level INFO em producao, DEBUG em test)
- [ ] adicionar dependencia `net.logstash.logback:logstash-logback-encoder` em `build.gradle.kts`
- [ ] adicionar `actor` ao MDC em `TipoGestoService` e `DetectionProfileService` (extraido do JWT `sub` claim via SecurityContextHolder)
- [ ] implementar teste de compliance de privacidade: executa request de register/transition, captura logs em memoria (ListAppender), asserta que nenhuma linha contem `video`, `image`, `landmark`, `base64`, `confidence`, `timingWindow`

## Resultado esperado
Todos os logs emitem JSON valido com correlationId e tenantId; nenhum log contem dados de conteudo de gesto (privacy invariant).

## Criterios de aceite
- [ ] log de uma chamada POST /tipos-gesto e JSON com campos `correlationId`, `tenantId`, `level`, `message`
- [ ] nenhuma linha de log de um request contem as palavras `video`, `image`, `landmark`, `base64` (compliance test passa)
- [ ] `confidence` e `timingWindow` presentes em RecognizedCommandReport NAO aparecem em nenhum log de `validateReport()` (stateless validation)
- [ ] formato de log e JSON valido (parseavel por `ObjectMapper.readTree()`)

## Dependencias
GEST-018 (MDC populado pelo CorrelationIdFilter).

## Estimativa
S

## Prioridade
P7
