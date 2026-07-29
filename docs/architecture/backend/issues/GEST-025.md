# GEST-025 -- Implement Resilience4j circuit breaker on EdgePairingClient (50%/10-call, 30s wait, 503 fallback) + LGPD audit

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto
Quando `edge-service` fica indisponivel, `gest-service` NAO deve bypassar a verificacao de pareamento silenciosamente. O Resilience4j circuit breaker no `EdgePairingAdapter` monitora falhas e abre o circuito (50% de falhas em janela de 10 chamadas), retornando `503 Service Unavailable` enquanto o circuito estiver aberto. Alem disso, esta issue consolida o audit LGPD: verifica que nenhum dado biometrico aparece em erros, logs ou respostas em todo o servico.

## Spec de referencia
- SPEC-06, SPEC-09, integration-contracts.md §1.1, §1.2

## Escopo
- [ ] configurar Resilience4j em `application.yml` para `EdgePairingClient`: `failureRateThreshold=50`, `slidingWindowSize=10`, `waitDurationInOpenState=30s`, `permittedNumberOfCallsInHalfOpenState=3`
- [ ] adicionar `@CircuitBreaker(name="edgePairing", fallbackMethod="edgePairingFallback")` em `EdgePairingAdapter` com fallback que lanca `ServiceUnavailableException` (mapeada para 503)
- [ ] adicionar 1 retry em `ConnectException`/`ReadTimeoutException` (idempotente, GET) via Resilience4j Retry antes do circuit breaker
- [ ] executar audit LGPD: ler todos os `@RestControllerAdvice`, DTOs de response, e logs de request/response e verificar que nenhum campo biometrico (video, image, landmark, confidence, timingWindow) e exposto em erro ou log fora de contexto de validacao
- [ ] testes: circuit opens apos 5+ falhas consecutivas no WireMock; fallback retorna 503; half-open permite 3 tentativas

## Resultado esperado
Indisponibilidade de EDGE resulta em `503` rapido (circuito aberto) sem bypass de seguranca; zero dados biometricos em qualquer superficie de erro.

## Criterios de aceite
- [ ] apos 5 falhas consecutivas de EDGE (WireMock retornando 500), proximo request retorna `503 Service Unavailable` imediatamente (circuito aberto)
- [ ] apos 30s (simulado com `@CircuitBreakerProperties` em teste), circuito entra em HALF_OPEN e permite 3 chamadas de teste
- [ ] `GET /devices/{deviceId}/detection-profile` com circuito aberto retorna `503` com ProblemDetail (nao NPE ou 500)
- [ ] nenhum campo biometrico encontrado em response bodies de erro ou logs (audit LGPD passa — zero ocorrencias de palavras-chave proibidas)

## Dependencias
GEST-015 (EdgePairingClient base).

## Estimativa
M

## Prioridade
P9
