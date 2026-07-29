# EDGE-INT-002 -- Integration with ACS — OAuth2 JWT validation (jwk-set-uri) and per-device client_credentials registration coordination

**Status:** INTACT -- gerado 2026-07-29
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto
ACS e o provedor de identidade do projeto. EDGE valida todos os JWTs contra `jwk-set-uri` de ACS. Os device credentials (clientId/secret) emitidos por EDGE sao exercidos no endpoint `/oauth2/token` do ACS — o device troca seu credential por um access token JWT com claims `deviceId+tenant`. Esta issue fecha o open item de hub-integration §4: estrategia de registro de device clients no ACS.

## Spec de referencia
- SPEC-06, hub-integration.md

## Escopo
- [ ] definir e documentar a estrategia de registro de device clients no ACS: EDGE chama API de administracao do ACS ao emitir credential (dynamic registration), ou ACS consulta EDGE para validar um clientId desconhecido (delegated validation); documentar decisao em `contracts/hub-integration.md §4.2 (ACS coordination)`
- [ ] criar stub de ACS (WireMock ou MockBean) para testes de `edge-service` (nao depender do ACS real em testes de integracao); incluir stub do endpoint JWK (`/oauth2/jwks`)
- [ ] validar que `jwk-set-uri` funciona quando ACS disponivel; validar comportamento com ACS indisponivel (tokens invalidos rejeitados, tokens previamente validados em cache curto de JWK)
- [ ] adicionar teste de integracao: JWT valido aceito; JWT com signature invalida rejeitado com 401

## Resultado esperado
EDGE valida JWTs contra ACS com sucesso; estrategia de registro de device credential no ACS documentada e aprovada.

## Criterios de aceite
- [ ] JWT valido de ACS aceito na rota protegida
- [ ] JWT com signature invalida rejeitado com 401
- [ ] Estrategia de registro de device client no ACS documentada em hub-integration.md §4.2
- [ ] Teste de integracao usando stub de JWK endpoint funciona sem ACS real

## Dependencias
- EDGE-012 (precede)

## Estimativa
M

## Prioridade
P5
