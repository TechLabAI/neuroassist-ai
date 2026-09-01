# SPEC-06 — Security

## Objetivo
Especificar o CONV como **resource server puro** para o ator *paciente com sessão* (`ADR-003` → ADR-012):
validação de JWT, escopos, honra da blacklist do ACS, auth serviço-a-serviço e salvaguardas LGPD/PHI.

## Escopo
**In:** OAuth2 Resource Server (Spring Security), claims/escopos, blacklist por request, auth
`client_credentials` para `VocabularyPort`, guard anti-biométrico, sensibilidade de dados.
**Out:** emissão/revogação da sessão (ACS), modelagem do ator (ACS).

## Decisões técnicas
- CONV **não** possui a identidade — apenas consome o JWT e **honra a blacklist do ACS em toda chamada**
  (fail-closed) para revogação imediata da sessão não-expirável (`ADR-003`, risco R3).
- Escopos mínimos: `conv.arvore.navegar` / `conv.no.selecionar`; nenhuma autoridade de cuidadora/admin é
  aceita nesses tokens.
- Auth serviço-a-serviço (CONV→PAC/GEST) via `client_credentials` (padrão do shared `ADR-007`), escopo
  `pac.vocabulary.read` (resolve **G7**).
- **G3-adjacente:** nunca servir texto de locale errado (regra em SPEC-11) — vetor de segurança clínica.

## Implementação
- `application-*.yml`: `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (realm Keycloak
  compartilhado). **Em testes usar `jwk-set-uri` estático, nunca `issuer-uri`** (Learning D-492 —
  `issuer-uri` dispara OIDC discovery no boot e quebra a init do JPA).
- `AcsSessionAdapter` (`AcsSessionPort`): valida assinatura + consulta blacklist. Avaliar cache de
  blacklist com TTL curto vs. custo/latência por request (risco R3) — sem enfraquecer a imediaticidade.
- `@PreAuthorize`/converter de escopos por endpoint; rejeição de tokens com autoridade de cuidadora.
- Guard no boundary: corpo com imagem/frame/landmark → `400 no-biometric-ingress`.

## Casos de borda
- Token válido mas na blacklist → `401 session-revoked` em toda chamada.
- Token sem escopo do endpoint → `403 forbidden-scope`.
- Token com autoridade de cuidadora + escopo CONV → `403` (autoridade excedente rejeitada).
- ACS indisponível → fail-closed (`401`), health reporta `acs UNREACHABLE`.

## Critérios de aceite (EARS)
- IF the JWT is missing, malformed, expired-signature, or on the ACS blacklist, THEN THE SYSTEM SHALL reject with `401` on every call.
- IF the token lacks the endpoint's scope or carries caregiver/admin authority, THEN THE SYSTEM SHALL reject with `403`.
- WHILE ACS is unreachable, THE SYSTEM SHALL fail closed rather than serve unauthenticated requests.
- THE SYSTEM SHALL call PAC/GEST with `client_credentials` auth (scope `pac.vocabulary.read`) for vocabulary resolution.
- THE SYSTEM SHALL treat tree text and cached-voice audio as sensitive health/biometric data (LGPD/SEC-002) and SHALL NOT log raw intent text (only `intentHash`).
