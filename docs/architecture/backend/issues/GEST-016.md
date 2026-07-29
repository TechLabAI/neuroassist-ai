# GEST-016 -- Implement conditional-GET (ETag/304) on GET /devices/{deviceId}/detection-profile for edge pull bandwidth optimization

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto
O edge `GestureDetectionEngine` faz polling periodico de `GET /devices/{deviceId}/detection-profile` para checar se o perfil mudou. Para evitar trafego desnecessario, o endpoint suporta conditional GET via `If-None-Match` / ETag. O ETag e o `version` do perfil ativo. Se o ETag do request coincidir com a versao atual, o servico retorna `304 Not Modified` sem body. Isso e especialmente importante para dispositivos em redes de baixa banda.

## Spec de referencia
- SPEC-01, SPEC-03, api-contract.md §4.4

## Escopo
- [ ] adicionar suporte a `If-None-Match` header em `DetectionProfileController.pullByDevice()` (se header presente e ETag = versao ativa, retornar `304 Not Modified`)
- [ ] adicionar header `ETag: "<version>"` e `X-Profile-Version: <version>` em todas as respostas `200 OK` de perfil ativo
- [ ] adicionar `DetectionProfileService.pullByDevice(String deviceId, String tenantId, Optional<String> eTag)` que chama `EdgePairingPort` e verifica ETag
- [ ] testes: sem If-None-Match retorna 200+ETag; com If-None-Match correto retorna 304; com If-None-Match desatualizado retorna 200 com novo perfil

## Resultado esperado
Polling do edge com ETag correto recebe `304 Not Modified`; polling com ETag desatualizado (perfil mudou) recebe `200` com novo perfil e novo ETag.

## Criterios de aceite
- [ ] primeiro request sem `If-None-Match` retorna `200` com `ETag` e `X-Profile-Version` no header
- [ ] segundo request com `If-None-Match` igual ao ETag recebido retorna `304 Not Modified` sem body
- [ ] apos novo publish (GEST-009), request com ETag antigo retorna `200` com novo perfil e novo ETag
- [ ] `304` resposta nao contem body (assercao no teste que `response.body()` e null/empty)

## Dependencias
GEST-009, GEST-015 (precede).

## Estimativa
S

## Prioridade
P4
