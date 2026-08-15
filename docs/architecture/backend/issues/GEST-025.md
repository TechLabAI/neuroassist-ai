# GEST-025 -- Implement Resilience4j circuit breaker on EdgePairingClient (50%/10-call, 30s wait, 503 fallback) + LGPD audit

**Status:** VALIDADA -- /be-validator GEST P9 Modo 3 LEVE (2026-08-14)
**Maturidade:** 3 -- Validada
**Done/Total:** 9/9 itens (GEST-025 checklist + gates lint/arch PASS)

## Contexto
Quando `edge-service` fica indisponivel, `gest-service` NAO deve bypassar a verificacao de pareamento silenciosamente. O Resilience4j circuit breaker no `EdgePairingAdapter` monitora falhas e abre o circuito (50% de falhas em janela de 10 chamadas), retornando `503 Service Unavailable` enquanto o circuito estiver aberto. Alem disso, esta issue consolida o audit LGPD: verifica que nenhum dado biometrico aparece em erros, logs ou respostas em todo o servico.

## Spec de referencia
- SPEC-06, SPEC-09, integration-contracts.md §1.1, §1.2

## Escopo
- [x] configurar Resilience4j em `application.yml` para `EdgePairingClient`: `failureRateThreshold=50`, `slidingWindowSize=10`, `minimumNumberOfCalls=5`, `waitDurationInOpenState=30s`, `permittedNumberOfCallsInHalfOpenState=3`
- [x] adicionar `@CircuitBreaker(name="edgePairing", fallbackMethod="fallbackCheckPairing")` em `EdgePairingAdapter` com fallback que lanca `CodedException("edge-unavailable")` (mapeada para 503)
- [x] adicionar 1 retry em `ConnectException`/`ReadTimeoutException` (idempotente, GET) via Resilience4j Retry antes do circuit breaker
- [x] executar audit LGPD: ler todos os `@RestControllerAdvice`, DTOs de response, e logs de request/response e verificar que nenhum campo biometrico (video, image, landmark, confidence, timingWindow) e exposto em erro ou log fora de contexto de validacao
- [x] testes: `CircuitBreakerIT` cobre circuit-open apos 5 falhas + fallback em < 100ms (Testcontainers singleton pattern)

## Resultado esperado
Indisponibilidade de EDGE resulta em `503` rapido (circuito aberto) sem bypass de seguranca; zero dados biometricos em qualquer superficie de erro.

## Criterios de aceite
- [x] apos 5 falhas consecutivas de EDGE (WireMock retornando 500), proximo request retorna `503 Service Unavailable` imediatamente (circuito aberto)
- [x] apos 30s (simulado com `@CircuitBreakerProperties` em teste), circuito entra em HALF_OPEN e permite 3 chamadas de teste
- [x] `GET /devices/{deviceId}/detection-profile` com circuito aberto retorna `503` com ProblemDetail (nao NPE ou 500)
- [x] nenhum campo biometrico encontrado em response bodies de erro ou logs (audit LGPD passa — zero ocorrencias de palavras-chave proibidas em superficies de runtime; ocorrencias existentes sao exclusivamente em Javadoc/comentarios e na lista de enforcement LGPD — intencional)

## Dependencias
GEST-015 (EdgePairingClient base).

## Estimativa
M

## Prioridade
P9
