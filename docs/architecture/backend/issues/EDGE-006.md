# EDGE-006 -- Implement DeviceRegistrationService.enroll() — exchange enrollment token for OAuth2 client_credentials, return credential once

**Status:** INTACT -- gerado 2026-07-29
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto
O agente edge (nao autenticado com JWT) troca o enrollment token one-time pelo seu credential de device (clientId + clientSecret OAuth2 client_credentials). Endpoint nao requer Bearer. O clientSecret e retornado uma unica vez; apenas seu hash e armazenado. Criterio de seguranca G1 (ADR-005, api-contract §2.2). A resposta inclui `tokenEndpoint` e `tokenScope` para que o agente saiba onde obter seu access token.

## Spec de referencia
- SPEC-11, SPEC-06

## Escopo
- [ ] implementar `DeviceRegistrationService.enroll(EnrollRequest)` sem SecurityContext de JWT (endpoint aberto — guarda e o enrollment token)
- [ ] buscar `DispositivoEnrollment` pelo SHA-256 hash do token fornecido; rejeitar com 401 `enrollment-invalid` se desconhecido, expirado ou ja consumido (sem revelar qual condicao falhou — SPEC-06)
- [ ] gerar par `clientId=edge-{deviceId}` + `clientSecret=cs_{uuid}`, persistir apenas `secretHash` via `DispositivoCredentialRepositoryPort.save`
- [ ] marcar `enrollmentToken` como consumido (`consumedAt = now`) atomicamente com a criacao do credential (transacao unica)
- [ ] atualizar `DispositivoEdge.versao = agentVersion` e retornar `EnrollResponse` com `clientId`, `clientSecret` (plaintext, unica vez), `tokenEndpoint=http://acs-service/oauth2/token`, `tokenScope=edge.device`, `recommendedHeartbeatSeconds=30`

## Resultado esperado
`POST /api/v1/edge/devices/enroll` retorna 200 com `clientId` e `clientSecret` (uma vez). Reuso do token retorna 401. Device ja enrolled retorna 409.

## Criterios de aceite
- [ ] 200 OK com `clientId` e `clientSecret` presentes no body
- [ ] banco armazena apenas `secret_hash`, nunca o plaintext
- [ ] segunda tentativa com o mesmo enrollment token retorna 401 `enrollment-invalid`
- [ ] device ja enrolled retorna 409 `already-enrolled`
- [ ] enrollment token expirado retorna 401 sem indicar qual das condicoes falhou

## Dependencias
- EDGE-005 (precede)

## Estimativa
M

## Prioridade
P2
