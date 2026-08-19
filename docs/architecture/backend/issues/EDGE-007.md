# EDGE-007 -- Implement PairingService.pair() and unpair() with exclusive-pairing enforcement, state machine, and audit transitions

**Status:** DONE
**Maturidade:** 85 -- Maduro
**Done/Total:** 5/5 itens

## Contexto
O pareamento e exclusivo: um device ativo por paciente (ADR-005 invariante #1). O `PairingService` aplica a maquina de estados (registered|unpaired -> paired; paired -> unpaired) e registra cada transicao no `DispositivoEdgeTransition` (append-only). Violacoes geram 409. Re-pairing revoga o credential anterior e emite novo.

## Spec de referencia
- SPEC-03

## Escopo
- [x] implementar `application/service/PairingService.pair(deviceId, pacienteId, actor, tenantId)` com verificacao de unicidade por `DispositivoEdgeRepositoryPort.findActivePairingForPatient`
- [x] implementar `PairingService.unpair(deviceId, reason, actor, tenantId)` verificando estado atual antes da transicao (deve estar `paired`)
- [x] em cada operacao: mudar `pairingState`, salvar, e chamar `DispositivoEdgeTransitionRepositoryPort.append` com `kind=pairing` + `fromState`, `toState`, `actor`, `reason`, `at`
- [x] publicar `DispositivoPareado` ou `DispositivoDespareado` via `ApplicationEventPublisher` pos-commit
- [x] ao realizar re-pairing (device em `unpaired` -> `paired`): revogar credential anterior via `DispositivoCredentialRepositoryPort.revokeByDeviceId` e gerar novo credential antes de transicionar

## Resultado esperado
POST /devices/{id}/pairings retorna 201 paired; DELETE /pairings/current retorna 204; violacoes de exclusividade retornam 409.

## Criterios de aceite
- [ ] 409 `pairing-conflict` se paciente ja tem device em `paired`
- [ ] 409 `pairing-conflict` se device ja esta `paired` (a qualquer paciente)
- [ ] 409 `device-retired` se device e `retired`
- [ ] transition row appended com `kind=pairing` em toda operacao bem-sucedida
- [ ] credential anterior revogado no re-pairing (status=revoked, revoked_at preenchido)

## Dependencias
- EDGE-006 (precede)

## Estimativa
M

## Prioridade
P2
