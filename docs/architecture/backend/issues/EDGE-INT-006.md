# EDGE-INT-006 -- Integration with PAC — provide EdgeDevicePort (GET /patients/{id}/device and GET /devices/{id}) for device validation

**Status:** INTACT -- gerado 2026-07-29
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto
PAC chama EDGE para validar `paciente.dispositivo_edge_id` ao escrever um paciente. EDGE garante referential honesty (invariante #5): `200` so retornado se device existe e esta `paired` para aquele paciente. PAC usa service token com scope `edge.internal`. Cross-tenant retorna 404 (nao 403) para evitar enumeracao. Integration-contracts §2.

## Spec de referencia
- SPEC-01, integration-contracts.md

## Escopo
- [ ] garantir que `GET /api/v1/edge/patients/{pacienteId}/device` retorna device paired ou 404 `patient-not-paired`
- [ ] garantir que service token com scope `edge.internal` e aceito neste endpoint (nao requer `edge.read`)
- [ ] garantir que tenant do service token coincide com tenant do device (cross-tenant retorna 404, nao 403 — evitar enumeracao)
- [ ] criar teste de integracao: PAC service token com tenant correto retorna 200 com device `paired`; tenant incorreto retorna 404

## Resultado esperado
PAC consegue validar `dispositivo_edge_id` via EDGE com service token; cross-tenant retorna 404 (referential honesty invariante #5).

## Criterios de aceite
- [ ] Service token com scope `edge.internal` aceito no endpoint `GET /patients/{id}/device`
- [ ] 200 retornado apenas quando device existe E esta `paired` para o paciente correto
- [ ] 404 `patient-not-paired` quando nao ha device paired para o paciente
- [ ] Tenant de outro service token retorna 404 (nao 403) para evitar cross-tenant enumeration

## Dependencias
- EDGE-009 (precede)
- EDGE-012 (precede)

## Estimativa
S

## Prioridade
P4
