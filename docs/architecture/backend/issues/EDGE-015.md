# EDGE-015 -- Implement DeviceRegistrationService.retire() with credential revocation and state-machine enforcement

**Status:** DONE
**Maturidade:** 85 -- Maduro
**Done/Total:** 5/5 itens

## Contexto
A aposentadoria de um device e uma transicao terminal (`retired` e final). Revogar o credential impede que um secret antigo se autentique. O device nunca e hard-deleted (referencia historica em Evento/Comando mantida). ADR-005 e api-contract §2.3. Se o device estava `paired`, o unpair deve ocorrer antes do retire (auditado).

## Spec de referencia
- SPEC-03, SPEC-06

## Escopo
- [x] implementar `DeviceRegistrationService.retire(deviceId, actor, tenantId)` com verificacao de scope `edge.manage`
- [x] transicao de estado: qualquer estado nao-retired -> retired (rejeitar retired -> retired com 409 `device-retired`)
- [x] revogar credential ativo: `DispositivoCredentialRepositoryPort.revokeByDeviceId(deviceId)` (status=revoked, revoked_at=now)
- [x] se device estava `paired`: fazer unpair primeiro (append `kind=pairing` transition + clear pacienteId, publicar `DispositivoDespareado`) antes de transitar para `retired`
- [x] append `kind=pairing` transition row para a aposentadoria (fromState=anterior, toState=retired, actor, at); nao hard-delete a row de `dispositivo_edge`

## Resultado esperado
POST /devices/{id}:retire retorna 200 com pairingState=retired; credential revogado; device permanece no banco.

## Criterios de aceite
- [ ] Device retired tem `pairing_state=retired` no banco
- [ ] Credential ativo tem `status=revoked, revoked_at IS NOT NULL`
- [ ] Nenhuma linha de `dispositivo_edge` deletada (soft retire)
- [ ] Novo heartbeat de device retired retorna 409 `device-retired`
- [ ] Scope `edge.manage` ausente retorna 403 `insufficient-scope`

## Dependencias
- EDGE-007 (precede)

## Estimativa
S

## Prioridade
P4
