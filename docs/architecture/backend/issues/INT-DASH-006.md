# INT-DASH-006 -- DASH -> ACS scope resolution: scope cache TTL=30s, JWK-set cache, hard-503 on ACS unavailable

## Contexto

O ACS e a hard-dependency de identidade do DASH. O DASH valida JWTs do painel localmente
contra o JWK set do ACS (buscado uma vez na inicializacao + refresh periodico) e resolve o
escopo do caregiver (lista de patientIds) a cada request — cacheado com TTL de 30s. Este
issue valida o contrato de integracao DASH→ACS do ponto de vista de seguranca e resiliencia.

## Spec de referencia

- SPEC-06 (JWK set local, scope resolution cacheada, ACS = hard-dependency)
- SPEC-10 (`IdentityDirectoryPort`, upstream: `acs-service`, `AcsRestAdapter`)
- `contracts/integration-contracts.md §1` (ACS: critico, TTL 30s, hard-503 se down)

## Escopo

- WireMock ACS respondendo `GET /caregivers/{id}/scope` → scope correto retornado; segunda chamada dentro do TTL usa cache (1 request real ao WireMock)
- Cache expirado (avancado artificialmente): segunda chamada gera novo request ao ACS
- ACS down na resolucao de escopo → `503 upstream-unavailable` (sem view autorizada)
- JWK set URI configurada via `DASH_OAUTH2_JWK_SET_URI`; JWT com assinatura invalida → `401`

## Resultado esperado

Scope de caregiver resolvido com no maximo 1 request ACS por TTL; ACS down bloqueia toda view.

## Criterios de aceite

- [ ] Scope correto retornado na primeira chamada
- [ ] Segunda chamada dentro do TTL: 0 requests adicionais ao ACS (cache hit)
- [ ] ACS down em `resolveScope` → `503`
- [ ] JWT com assinatura invalida → `401` (validacao local contra JWK set)

## Dependencias

- DASH-016 (AcsRestAdapter)
- DASH-021 (OAuth2 config)

## Estimativa

M

## Prioridade

P5
