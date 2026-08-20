# EDGE-005 -- Implement DeviceRegistrationService.register() — mint one-time enrollment token (hash-only), respond 201 with plain token

**Status:** DONE
**Maturidade:** 85 -- Maduro
**Done/Total:** 5/5 itens

## Contexto
O operador (conductor, scope edge.manage) registra um dispositivo fisico. O servico cria o registro `DispositivoEdge` em estado `registered`, gera o enrollment token aleatorio, armazena apenas seu SHA-256 hash, retorna o token em plaintext UMA UNICA VEZ na resposta 201. Token tem TTL de 15 min e uso unico (G4, api-contract §2.1). O tenantId e derivado exclusivamente do JWT — nao aceito do body (anti-spoofing).

## Spec de referencia
- SPEC-11

## Escopo
- [x] implementar `application/service/DeviceRegistrationService.register(RegisterDeviceRequest, tenantId, actor)` retornando `RegisterDeviceResponse`
- [x] gerar enrollment token (SecureRandom, formato `et_<uuid-base64url>`) e persistir apenas SHA-256 hash via `DispositivoEnrollmentRepositoryPort.save` com `expiresAt = now + 900s`
- [x] salvar `DispositivoEdge` em estado `registered` via `DispositivoEdgeRepositoryPort.save` e publicar `DispositivoRegistrado` via `ApplicationEventPublisher` (pos-commit)
- [x] validar que scope `edge.manage` esta presente no SecurityContext antes de executar (lancar `InsufficientScopeException` se ausente — 403)
- [x] rejeitar request body com `tenantId` diferente do JWT com 403 `identity-mismatch`

## Resultado esperado
`POST /api/v1/edge/devices` retorna 201 com `enrollmentToken` em plaintext e `enrollmentExpiresAt = now + 15min`; banco persiste apenas o hash.

## Criterios de aceite
- [ ] 201 Created retornado com `enrollmentToken` e `deviceId` no body
- [ ] banco nao tem coluna com token plaintext (apenas `token_hash`)
- [ ] segundo GET /devices/{id} nao retorna `enrollmentToken` (retornado unico no 201)
- [ ] request body com `tenantId` diferente do JWT retorna 403 `identity-mismatch`
- [ ] scope `edge.manage` ausente retorna 403 `insufficient-scope`

## Dependencias
- EDGE-003 (precede)
- EDGE-004 (precede)

## Estimativa
M

## Prioridade
P2
