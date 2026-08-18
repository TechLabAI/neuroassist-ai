# DASH-016 -- Implement AcsRestAdapter (IdentityDirectoryPort): JWK-set cache + scope resolution cache (TTL=30s)

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto

O `IdentityDirectoryPort` tem duas responsabilidades: (1) prover o JWK set do ACS para
validacao local de JWTs dos paineis (uma chamada na inicializacao + refresh periodico), e
(2) resolver o escopo do caregiver (`caregiverId -> { tenantId, patientIds }`) para
autorizacao de recursos. A resolucao de escopo e cacheada com TTL de `SCOPE_CACHE_TTL_SECONDS`
(30s padrao) para evitar chamadas ACS por request.

## Spec de referencia

- SPEC-06 (auth: JWT validado localmente contra JWK set ACS; scope resolvido do ACS, TTL cache)
- SPEC-10 (IdentityDirectoryPort upstream map: `AcsRestAdapter`)

## Escopo

- [ ] `infrastructure/adapter/AcsRestAdapter implements IdentityDirectoryPort` usando Feign (`AcsClient`)
- [ ] `getJwkSetUri()`: retorna `${DASH_ACS_BASE_URL}/.well-known/jwks.json` (consumida pela config OAuth2)
- [ ] `resolveScope(caregiverId)`: `GET /caregivers/{caregiverId}/scope`; resultado cacheado em `Caffeine` ou `ConcurrentHashMap<UUID, ScopeResult>` com TTL `SCOPE_CACHE_TTL_SECONDS`
- [ ] ACS indisponivel no `resolveScope` → throw `UpstreamUnavailableException` (mapeado para 503 no handler)
- [ ] Teste unitario com WireMock: scope resolvido, cache hit na segunda chamada (apenas 1 HTTP real), ACS down → excecao

## Resultado esperado

Scope de caregiver resolvido em < 1 chamada ACS por request (cache), com fallback 503 limpo.

## Criterios de aceite

- [ ] `resolveScope` retorna `{ tenantId, patientIds[] }` corretos
- [ ] Segunda chamada dentro do TTL usa cache (sem chamada HTTP adicional ao ACS)
- [ ] ACS indisponivel → `UpstreamUnavailableException` com mensagem clara
- [ ] JWK-set URI configuravel via `DASH_OAUTH2_JWK_SET_URI`
- [ ] WireMock test cobrindo: hit, cache, ACS down

## Dependencias

- DASH-001 (estrutura de infraestrutura)
- DASH-002 (IdentityDirectoryPort interface)

## Estimativa

M

## Prioridade

P5
