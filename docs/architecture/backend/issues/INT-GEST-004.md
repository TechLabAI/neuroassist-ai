# INT-GEST-004 -- Integration with REG — Eureka service discovery registration + graceful deregistration

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Contexto
`gest-service` registra-se no Eureka (REG) no startup e se desregistra graciosamente no shutdown. O `EdgePairingClient` Feign usa `lb://edge-service` (resolucao via REG). A health check URL registrada no Eureka aponta para `/actuator/health`. A readiness do GEST fica `DOWN` enquanto o registro no Eureka nao for confirmado.

## Spec de referencia
- hub-integration.md §2, SPEC-01, setup-local.md §1

## Escopo
- [ ] validar configuracao `eureka.client.serviceUrl.defaultZone` em `application.yml` e que `@EnableDiscoveryClient` esta presente
- [ ] validar que `GestServiceApplication` sobe e registra com `spring.application.name=gest-service` (instancia visivel no dashboard Eureka)
- [ ] validar graceful shutdown: `SIGTERM` remove a instancia do Eureka antes de fechar o processo (eureka.instance.lease-expiration-duration-in-seconds configurado)

## Resultado esperado
`gest-service` aparece no Eureka como `UP`; `EdgePairingClient` resolve `edge-service` via `lb://` sem URL hardcoded; desregistro limpo no shutdown.

## Criterios de aceite
- [ ] servico aparece no Eureka dashboard (ou Eureka REST API) como `GEST-SERVICE` com status `UP`
- [ ] `EdgePairingClient` resolve `lb://edge-service` sem necessidade de `EDGE_SERVICE_URL` hardcoded em producao
- [ ] graceful shutdown via `SIGTERM`: instancia removida do Eureka antes do processo terminar

## Dependencias
GEST-001, GEST-015.

## Estimativa
S

## Prioridade
P4
