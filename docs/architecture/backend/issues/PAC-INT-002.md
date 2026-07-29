# PAC-INT-002 -- Implementar AcsDirectoryClient Feign (resolveTenant + resolveCuidador, timeout 2s/3s)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto
Adapter Feign para `AcsDirectoryPort`. Usado durante criação/atualização de `Paciente` para
validar referências de `Empresa` e `Cuidador`. Se ACS estiver indisponível, rejeita o write
com `503` (não é safety-critical — o caller deve retry). Timeout 2s/3s (SPEC-10 §5.3).

## Spec de referencia
- SPEC-10 §5 (AcsDirectoryPort — port, REST shapes, resilience)
- `contracts/event-contracts.md` §2.2

## Escopo
- [ ] implementar `AcsDirectoryClient` (`@FeignClient(name="acs-service")`):
      `GET /api/acs/v1/empresas/{id}` e `GET /api/acs/v1/cuidadores/{id}`
- [ ] implementar `AcsDirectoryAdapter implements AcsDirectoryPort`:
      `resolveTenant` → `EmpresaRef`; `resolveCuidador` → `CuidadorRef`;
      `404` → lança `CrossServiceRefNotFoundException` (`422`);
      timeout/5xx → lança `ExternalServiceUnavailableException` (`503`)
- [ ] configurar timeout: `connectTimeout=2000, readTimeout=3000` para `acs-service`
- [ ] TODO[agent]: itens adicionais de escopo

## Criterios de aceite
- [ ] `resolveCuidador` com id de outra empresa → `CrossServiceRefNotFoundException`
- [ ] `resolveTenant` com ACS retornando `404` → `CrossServiceRefNotFoundException` → `422`
- [ ] `resolveCuidador` com ACS timeout → `ExternalServiceUnavailableException` → `503`
- [ ] Todos os calls propagam `X-Correlation-Id` e `X-Empresa-Id`
- [ ] TODO[agent]: criterio de aceite verificavel

## Dependencias
- PAC-003 (AcsDirectoryPort interface)
- INT-PAC-005 (FeignHeaderPropagator)

## Estimativa
S

## Prioridade
P4
