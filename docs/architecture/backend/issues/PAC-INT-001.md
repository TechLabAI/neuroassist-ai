# PAC-INT-001 -- Implementar GestDetectabilityClient Feign + Resilience4j CB (resolveTipoGesto + isSupported, timeout 1s/2s)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto
Implementar o adapter Feign de `GestDetectabilityPort`. Tem dois endpoints críticos: `resolveTipoGesto`
(usado no `:validate`) e `isSupported` (usado no emergency gate `:publish`/`:recalibrate`). O adapter
configura Resilience4j: timeout 1s/2s, 1 retry automático em falha transiente (somente no path
emergência), circuit breaker que ao abrir retorna `UNAVAILABLE` para o gate.

## Spec de referencia
- SPEC-10 §4 (GestDetectabilityPort — port, REST shapes, resilience)
- SPEC-03 §6 (resilience policy — timeouts, CB config)
- `contracts/event-contracts.md` §2.1

## Escopo
- [ ] implementar `GestDetectabilityClient` (`@FeignClient(name="gest-service")`):
      `GET /api/gest/v1/tipos-gesto/{id}` e `GET /api/gest/v1/tipos-gesto/{id}/detectability`
- [ ] implementar `GestDetectabilityAdapter implements GestDetectabilityPort`:
      chama o Feign client, converte exceções Feign para domain exceptions
      (`FeignException.NotFound` → `UnknownPrimitiveException`; `FeignException` / timeout → propagado para CB)
- [ ] configurar Resilience4j CB em `application.yml`:
      `failure-rate-threshold=50`, `slow-call-duration-threshold=2s`,
      `sliding-window-size=5`, `wait-duration-in-open-state=30s`
- [ ] configurar timeout do Feign via `feign.client.config.gest-service.connectTimeout=1000`,
      `readTimeout=2000`
- [ ] TODO[agent]: itens adicionais de escopo

## Criterios de aceite
- [ ] `resolveTipoGesto` com id válido retorna `TipoGestoRef` populado
- [ ] `resolveTipoGesto` com id desconhecido lança `UnknownPrimitiveException`
- [ ] `isSupported` com GEST retornando `supported=false` retorna `SupportedResult(false)`
- [ ] `isSupported` com timeout > 2s lança exceção que aciona CB; CB OPEN retorna sem chamar GEST
- [ ] Todos os calls propagam `X-Correlation-Id` e `X-Empresa-Id` (via PAC-INT-005)
- [ ] TODO[agent]: criterio de aceite verificavel

## Dependencias
- PAC-001 (spring-cloud-starter-openfeign no build)
- PAC-003 (GestDetectabilityPort interface)
- INT-PAC-005 (FeignHeaderPropagator)

## Estimativa
M

## Prioridade
P5
