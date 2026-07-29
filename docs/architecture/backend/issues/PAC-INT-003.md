# PAC-INT-003 -- Implementar EdgeDeviceClient Feign (resolveDispositivo, timeout 2s/3s, rejeitar write com 503 se indisponível)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto
Adapter Feign para `EdgeDevicePort`. Usado no write de `Paciente` quando `dispositivoEdgeId` é
fornecido — valida que o dispositivo existe no EDGE e pertence à mesma empresa. Mesma política de
resilience que ACS: timeout 2s/3s, rejeita write com `503` se indisponível (SPEC-10 §6.3).

## Spec de referencia
- SPEC-10 §6 (EdgeDevicePort — port, REST shape, resilience)
- `contracts/event-contracts.md` §2.3

## Escopo
- [ ] implementar `EdgeDeviceClient` (`@FeignClient(name="edge-service")`):
      `GET /api/edge/v1/dispositivos/{id}` → `{id, pacienteId, empresaId}`
- [ ] implementar `EdgeDeviceAdapter implements EdgeDevicePort`:
      `resolveDispositivo` → `DispositivoEdgeRef`;
      `404` → `CrossServiceRefNotFoundException` (`422`);
      dispositivo de outra empresa → `CrossServiceRefNotFoundException` (`422`);
      timeout/5xx → `ExternalServiceUnavailableException` (`503`)
- [ ] configurar timeout: `connectTimeout=2000, readTimeout=3000` para `edge-service`
- [ ] TODO[agent]: itens adicionais de escopo

## Criterios de aceite
- [ ] `resolveDispositivo` com dispositivo de outra empresa → `422`
- [ ] `resolveDispositivo` com EDGE indisponível → `503`
- [ ] `POST /pacientes` sem `dispositivoEdgeId` NÃO chama `EdgeDevicePort` (campo opcional)
- [ ] Todos os calls propagam `X-Correlation-Id` e `X-Empresa-Id`
- [ ] TODO[agent]: criterio de aceite verificavel

## Dependencias
- PAC-003 (EdgeDevicePort interface)
- INT-PAC-005 (FeignHeaderPropagator)

## Estimativa
S

## Prioridade
P4
