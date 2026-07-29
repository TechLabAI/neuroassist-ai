# SPEC-06 — Security

> Auth (JWT + device), header hardening (anti-spoof), CORS, TLS. Enforces INV-1, INV-2,
> INV-5 and mitigates R-2. Shared ADR-003 (multi-tenancy). EARS per standard.

## Objetivo
Especificar os controles de segurança de borda do gateway: autenticação, propagação segura
de tenant, CORS e TLS.

## Escopo
- **In:** validação JWT de cuidador (JWK/ACS), validação de credencial de dispositivo
  (SPEC-03), strip de headers internos inbound, CORS, terminação TLS, autorização coarse.
- **Out:** autorização fina por recurso (fica em cada serviço de domínio), WAF/bot
  mitigation (infra), mTLS entre serviços internos (futuro).

## Decisões técnicas
- Spring Security **reactive** resource-server; validação JWT contra JWK set do ACS
  (`jwk-set-uri`), checando assinatura, issuer, audience e expiry.
- **Fail closed** (INV-1): tráfego não autenticado é rejeitado na borda.
- **Anti-spoofing** (INV-2, R-2): headers internos (`X-Empresa-Id`, `X-Principal-*`) são
  **removidos** de toda requisição inbound antes de o gateway os definir a partir do
  principal.
- **Autorização coarse** por rota (ex.: rotas de cuidador exigem `kind=caregiver`); authz
  fina permanece no serviço (G-2).
- TLS termina no LB externo ou no gateway conforme ambiente (G-4).

## Implementação
- `security/SecurityConfig` (reactive) define matchers: rotas `public` liberadas, demais
  exigem principal autenticado.
- `TenantContextPropagationFilter` executa o strip+set (SPEC-02).
- `JwkClient` cacheia chaves com TTL + stale-while-revalidate (R-4).
- Todo erro de segurança sai como RFC 7807 (INV-5).

## Casos de borda
- JWT com `kid` desconhecido → 1 refresh do JWK set antes de 401.
- Requisição com `X-Empresa-Id` forjado → header descartado; tenant vem do principal.
- Origem não permitida → sem header `Access-Control-Allow-Origin`.

## Critérios de aceite (EARS)
- THE SYSTEM SHALL reject unauthenticated traffic at the edge with `401` before it reaches
  any service (INV-1).
- IF an inbound request carries `X-Empresa-Id`/`X-Principal-*`, THEN THE SYSTEM SHALL strip
  those headers before setting its own from the authenticated principal (INV-2).
- THE SYSTEM SHALL derive `X-Empresa-Id` exclusively from the authenticated principal.
- WHEN a JWT references an unknown `kid`, THE SYSTEM SHALL refresh the JWK set once before
  rejecting the token.
- THE SYSTEM SHALL admit CORS requests only from configured origins and answer pre-flight
  `OPTIONS` at the gateway.
- THE SYSTEM SHALL return every gateway-originated security error as
  `application/problem+json` (INV-5).
