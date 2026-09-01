# TREI-004 -- REST DTOs + API controllers (9 endpoints, RFC 7807 ProblemDetail)

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/15 itens

## Contexto

Cria os controllers Spring MVC e todos os DTOs de request/response para os 9 endpoints
públicos do TREI (api-contract §2). O mapeamento de erros usa RFC 7807 `ProblemDetail`
com o namespace `https://neuroassist.ai/problems/`. Controllers fazem binding de JWT
claims (`empresa`, `pacienteId`, `scope`) — nunca aceitam tenant do body.

## Spec de referencia

- SPEC-01 (api-surface — os 9 endpoints com contratos completos)
- api-contract.md §2 (request/response examples)
- api-contract.md §3 (error model — 10 tipos de problema)

## Escopo

- [ ] criar `src/main/java/com/neuroassist/trei/api/SessaoTreinamentoController.java` — `POST /sessoes` (scope `treinamento.sessao.conduzir`) + `POST /sessoes/{sessaoId}/propostas` (scope `treinamento.no.propor`)
- [ ] criar `src/main/java/com/neuroassist/trei/api/NoAutoriaController.java` — `POST /nos`, `GET /nos/{noId}`, `DELETE /nos/{noId}?locale=`, `POST /nos/{noId}/aprovacoes/cuidadora`, `POST /nos/{noId}/aprovacoes/paciente`, `GET /nos/{noId}/publicacao`
- [ ] criar `src/main/java/com/neuroassist/trei/api/HealthController.java` — `GET /health` (sem auth — readiness endpoint)
- [ ] criar `src/main/java/com/neuroassist/trei/api/dto/StartSessaoRequest.java` — `{ pacienteId, localeAutoria }`
- [ ] criar `src/main/java/com/neuroassist/trei/api/dto/StartSessaoResponse.java` — `{ sessaoId, pacienteId, localeAutoria, estado, llmModelId, iniciadaEm }`
- [ ] criar `src/main/java/com/neuroassist/trei/api/dto/RequestPropostaRequest.java` — `{ instrucao, parentNoId, tipo }`
- [ ] criar `src/main/java/com/neuroassist/trei/api/dto/RequestPropostaResponse.java` — `{ propostas: [{propostaId, texto, tipo, locale}], aiDisclosed }`
- [ ] criar `src/main/java/com/neuroassist/trei/api/dto/MaterializeNoRequest.java` — `{ pacienteId, propostaId, parentNoId, tipo, locale, texto, engineFeatureRef }`
- [ ] criar `src/main/java/com/neuroassist/trei/api/dto/MaterializeNoResponse.java` — `{ noId, locale, subEstado, confirmadoCuidadora, confirmadoPaciente, contentStatus }`
- [ ] criar `src/main/java/com/neuroassist/trei/api/dto/AprovacaoRequest.java` — `{ locale }`
- [ ] criar `src/main/java/com/neuroassist/trei/api/dto/AprovacaoResponse.java` — `{ noId, locale, confirmadoCuidadora, confirmadoPaciente, subEstado, publicacaoDisparada }`
- [ ] criar `src/main/java/com/neuroassist/trei/api/dto/InspectNoResponse.java` — `{ noId, pacienteId, tipo, parentNoId, locales: [{locale, texto, subEstado, confirmadoCuidadora, confirmadoPaciente, embeddingModelId, audioRef, publicadoEm}] }`
- [ ] criar `src/main/java/com/neuroassist/trei/api/dto/PublicacaoStatusResponse.java` — `{ noId, locale, subEstado, passos: [{passo, estado, tentativas}] }`
- [ ] configurar `@ControllerAdvice` mapeando exceções de domínio para `ProblemDetail` com os 10 tipos canônicos (api-contract §3)
- [ ] nenhum controller aceita `empresa` ou `pacienteId` do body — derivar apenas do JWT

## Resultado esperado

Controllers compilando; DTOs com validação `@NotNull`/`@NotBlank` onde aplicável;
`ProblemDetail` retornado em todos os cenários de erro documentados.

## Criterios de aceite

- [ ] `./gradlew :trei-service:compileJava` sai 0
- [ ] `POST /sessoes` mapeado em `SessaoTreinamentoController` com scope correto e HTTP 201
- [ ] `POST /nos/{noId}/aprovacoes/cuidadora` e `/paciente` mapeados com scopes distintos; `/aprovacoes/paciente` rejeita caregiver token com 403
- [ ] `ControllerAdvice` retorna `application/problem+json` com campo `type` prefixado em `https://neuroassist.ai/problems/`

## Dependencias

- TREI-003 (precede — types do domínio devem existir para DTOs compilarem)

## Estimativa

L

## Prioridade

P1
