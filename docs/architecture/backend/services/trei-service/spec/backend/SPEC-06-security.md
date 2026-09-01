# SPEC-06 — Security (OAuth2, two actors, revocation)

> EARS acceptance criteria (D-057). Decision: local ADR-003; identity per shared ADR-012.

## Objetivo

Definir a postura de segurança de TREI: resource server OAuth2 no realm ACS, dois atores distintos
(cuidadora e patient-with-session), fail-closed com blacklist, e a sensibilidade de dados (SEC-002/LGPD).

## Escopo

- **In:** validação de JWT (assinatura via `jwk-set-uri` + blacklist ACS), escopos por endpoint, guarda
  do ator correto por aprovação, service-to-service (`client_credentials`) p/ PAC, PHI/LGPD.
- **Out:** emissão/provisionamento de identidade (ACS owns, ADR-012).

## Decisões técnicas

- **Escopos:** cuidadora — `treinamento.sessao.conduzir`, `treinamento.no.propor`,
  `treinamento.no.aprovar.cuidadora`, `treinamento.no.descartar`; paciente —
  `treinamento.no.aprovar.paciente`.
- Nenhum ator único detém os dois escopos de aprovação (invariante do gate, ADR-003).
- Blacklist ACS checada **a cada** request via `AcsSessionPort` (fail-closed).
- **PHI:** nunca enviar texto clínico à Anthropic/ElevenLabs; registro cotidiano apenas (SEC-002, ADR-017).

## Implementação

- `application-test.yml` usa `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (JWKS estático de
  teste), **não** `issuer-uri` (evita OIDC discovery no boot — Learning D-492).
- `@RestControllerAdvice` mapeia falhas de auth para `401/403` ProblemDetail (`unauthenticated`,
  `session-revoked`, `forbidden-scope`).
- S2S para PAC via OAuth2 `client_credentials`, escopo de leitura de paciente/vocabulário.

## Casos de borda

- Token com autoridade do ator errado no endpoint de aprovação → `403 forbidden-scope`.
- JWT na blacklist apesar de não expirado → `401 session-revoked`.
- Ingestão biométrica no body → `400 no-biometric-ingress`.

## Critérios de aceite (EARS)

- THE SYSTEM SHALL validate every JWT by signature (`jwk-set-uri`) AND against the ACS revocation blacklist
  on every request.
- IF a JWT is on the ACS blacklist, THEN THE SYSTEM SHALL reject with `401` (`session-revoked`) regardless
  of non-expiry.
- IF a token carries the wrong actor authority for an approval endpoint, THEN THE SYSTEM SHALL reject with
  `403`.
- THE SYSTEM SHALL NOT transmit clinical/diagnostic text to Anthropic or ElevenLabs (SEC-002 / PHI caveat).
- THE SYSTEM SHALL require distinct scopes issued to distinct identities for the caregiver and patient
  approvals (dual authorization).
