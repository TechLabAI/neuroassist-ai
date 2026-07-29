# EDGE-009 -- Implement LivenessService.evaluate() — read-time liveness derivation, liveness crossing detection, and liveness endpoints

**Status:** INTACT -- gerado 2026-07-29
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto
`status` (online/offline/n-a) nunca e armazenado — e derivado em tempo de leitura comparando `now - ultima_sincronizacao` com `stalenessThreshold=90s` (ADR-006). Ao observar um cruzamento online->offline, o servico registra a transicao e publica `DispositivoFicouOffline`. O ETag incorpora o `status` derivado (bucket) para suportar 304 no poll do DASH. `serverTime` na resposta e hard dependency do FE.

## Spec de referencia
- SPEC-02, SPEC-03

## Escopo
- [ ] implementar `application/service/LivenessService.evaluate(DispositivoEdge)` retornando `LivenessResult` (status: online|offline|n-a, derivado de `now - ultima_sincronizacao <= 90s`; n-a para unpaired/retired)
- [ ] ao detectar cruzamento online->offline: append `kind=liveness, fromState=online, toState=offline, actor=system` (idempotente — verificar ultima transition antes de duplicar) + publicar `DispositivoFicouOffline`
- [ ] implementar `LivenessService.resolveDeviceForPatient(pacienteId, tenantId)` retornando device paired ou lancar `PatientNotPairedException` (404)
- [ ] implementar `LivenessService.resolveTenant(deviceId, tenantId)` para endpoint §4.2 (GEST) retornando apenas `{deviceId, tenantRef}`
- [ ] calcular ETag para `LivenessView` baseado no bucket de status (online vs offline) — nao apenas em `ultima_sincronizacao` — para que cruzamento invalide mesmo sem novo heartbeat
- [ ] implementar condicional GET com `If-None-Match` -> 304 quando ETag coincide e status bucket nao mudou; incluir `serverTime` no body sempre

## Resultado esperado
GET /devices/{id}/liveness retorna status derivado com `serverTime`; 304 quando ETag e status bucket inalterados; dispositivo unpaired/retired retorna status=n-a.

## Criterios de aceite
- [ ] device com `ultima_sincronizacao` ha mais de 90s retorna `status=offline`
- [ ] device nunca enviou heartbeat retorna `status=offline`
- [ ] device unpaired ou retired retorna `status=n-a`
- [ ] 304 retornado quando `If-None-Match` coincide com ETag atual e status nao mudou
- [ ] ETag invalida quando status muda de online para offline mesmo sem novo heartbeat
- [ ] cruzamento online->offline nao gera linha duplicada em `dispositivo_edge_transition`

## Dependencias
- EDGE-008 (precede)

## Estimativa
M

## Prioridade
P2
