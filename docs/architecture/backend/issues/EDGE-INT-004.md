# EDGE-INT-004 -- Integration with REG — Eureka service discovery registration and graceful deregistration

**Status:** INTACT -- gerado 2026-07-29
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Contexto
`edge-service` deve se registrar no Eureka (REG) ao iniciar para que GWY e GEST possam descobri-lo via load balancer. Ao desligar, deve desregistrar-se graciosamente para evitar requests para uma instancia morta. Health check URL registrado no Eureka metadata. Hub-integration §2.

## Spec de referencia
- SPEC-01, hub-integration.md

## Escopo
- [ ] verificar configuracao Eureka em `application.yml`: `spring.application.name=edge-service`, `eureka.client.serviceUrl.defaultZone`, `eureka.instance.prefer-ip-address=true`, health check URL `http://<host>:8087/actuator/health`
- [ ] configurar graceful shutdown: `server.shutdown=graceful`, `spring.lifecycle.timeout-per-shutdown-phase=30s`, `eureka.instance.lease-expiration-duration-in-seconds=30`
- [ ] criar smoke test: servico inicia, verifica registro no Eureka via `DiscoveryClient.getInstances('edge-service')` retornando instancia UP

## Resultado esperado
`edge-service` visivel no Eureka como `EDGE-SERVICE`; GWY consegue rotear via `lb://edge-service`; shutdown gracioso remove a instancia.

## Criterios de aceite
- [ ] Eureka dashboard mostra `EDGE-SERVICE` com status UP
- [ ] GWY consegue rotear request para `lb://edge-service`
- [ ] Shutdown gracioso remove a instancia do Eureka antes de parar de aceitar requests

## Dependencias
- EDGE-001 (precede)

## Estimativa
S

## Prioridade
P4
