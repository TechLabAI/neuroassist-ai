# INT-DASH-001 -- EVNT -> DASH dispatch sink: validate POST /internal/notifications contract alignment and auth (SVC_EVNT)

## Contexto

O EVNT e o unico produtor que chama `POST /internal/notifications` no DASH. Este issue garante
que o contrato de dispatch (payload, auth service-to-service SVC_EVNT, resposta 202) esta alinhado
entre EVNT (produtor) e DASH (consumidor), e que a autenticacao `SVC_EVNT` esta devidamente
configurada nos dois lados.

## Spec de referencia

- SPEC-01 (POST /internal/notifications: payload, 202 delivered/no_open_session)
- SPEC-05 (idempotencia por dispatchId)
- `contracts/integration-contracts.md §3` (EVNT upstream: dispatch sink)
- `contracts/event-contracts.md §2` (NotificationDispatch payload canonico)

## Escopo

- Contrato de integracao: payload canonico com todos os campos obrigatorios (`dispatchId`, `tier`, `tenantId`, `targetCaregiverId`, `patientId`, `commandLabel`, `urgencia`, `raisedAt`)
- Auth: `SVC_EVNT` authority configurada no DASH; EVNT emite token com este authority no client-credentials flow
- Teste de contrato: `@SpringBootTest` no DASH com WireMock simulando EVNT enviando dispatch → DASH responde `202`; sem `SVC_EVNT` → `401`
- Verificar que DASH retorna `{ delivery: "no_open_session" }` quando nenhum painel conectado (EVNT deve lidar com isso)

## Resultado esperado

EVNT consegue entregar `NotificationDispatch` ao DASH com `SVC_EVNT` e recebe `202` com `delivery` correto.

## Criterios de aceite

- [ ] POST com payload canonico + auth `SVC_EVNT` → `202 { "delivery": "delivered" | "no_open_session" }`
- [ ] POST sem autorizacao (cuidador JWT, sem `SVC_EVNT`) → `401`
- [ ] Campo `dispatchId` ausente → `400`
- [ ] Redelivery do mesmo `dispatchId` (sessao ativa) → idempotente (nao duplica push SSE)

## Dependencias

- DASH-006 (InternalNotificationController)
- DASH-021 (auth SVC_EVNT configurada)

## Estimativa

M

## Prioridade

P2
