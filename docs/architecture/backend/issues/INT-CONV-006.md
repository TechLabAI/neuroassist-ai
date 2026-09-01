# INT-CONV-006 -- Provisionamento de client_credentials para CONV→PAC/GEST (escopo pac.vocabulary.read) — concern do ACS durante scaffold

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Contexto

O `VocabularyAdapter` (CONV-012) usa OAuth2 `client_credentials` com scope `pac.vocabulary.read`
para chamar PAC/GEST (ADR-007/SPEC-06). O cliente OAuth2 (`conv-service`) precisa ser provisionado
no Keycloak com esse scope. Este é um concern de ACS/infra durante o scaffold — o time de ACS
deve criar o client `conv-vocabulary-client` no realm Keycloak e emitir as credenciais
(`client_id`, `client_secret`) para o CONV configurar.

Gap G7 de `hub-integration.md` §3: "escopo confirmado como `pac.vocabulary.read`; provisionamento
do client é concern do scaffold/ACS."

## Spec de referencia

- SPEC-06 (security — auth client_credentials, scope pac.vocabulary.read)
- `contracts/hub-integration.md` §3 (gap G7)

## Escopo

- [ ] Criar task no backlog do ACS: provisionar `conv-vocabulary-client` no Keycloak realm `neuroassist` com scope `pac.vocabulary.read`
- [ ] Definir env vars necessárias em `application.yml` do CONV: `spring.security.oauth2.client.registration.conv-pac.client-id`, `client-secret`, `authorization-grant-type: client_credentials`, `scope: pac.vocabulary.read`
- [ ] Definir `spring.security.oauth2.client.provider.conv-pac.token-uri` apontando para o Keycloak realm token endpoint
- [ ] Documentar em `setup-local.md`: como configurar o client OAuth2 localmente (ex.: Keycloak dev com `docker-compose.techlab.yml`)

## Resultado esperado

Client OAuth2 provisionado no Keycloak; CONV configurado para obter tokens `client_credentials`; VocabularyAdapter funcional com auth real.

## Criterios de aceite

- [ ] `application.yml` tem as env vars de OAuth2 client para scope `pac.vocabulary.read`
- [ ] `setup-local.md` documenta o provisionamento do client no Keycloak local
- [ ] Task criada no backlog de ACS com referência a INT-CONV-006

## Dependencias

- CONV-012 (VocabularyAdapter — consome este provisionamento)

## Estimativa

S

## Prioridade

P5
