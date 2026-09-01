# TREI-015 -- ACS revocation blacklist + dual-actor scope enforcement

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto

Além da validação de assinatura JWT (TREI-014), TREI cheque o blacklist de revogação ACS
por request via `AcsSessionPort`. Se o `jti` do JWT está no blacklist → 401 `session-revoked`
imediato, independentemente de expiração (ADR-003/ADR-012 fail-closed). O `RevocationFilter`
corre após a validação de assinatura Spring Security. Dual-actor enforcement: token de
caregiver no endpoint de aprovação do paciente (e vice-versa) → 403 `forbidden-scope`.

## Spec de referencia

- SPEC-06 (security — revocation, dual-actor gate)
- api-contract.md §4 (authentication — ACS revocation blacklist on every call)
- hub-integration.md §1 (AcsSessionPort fail-closed)

## Escopo

- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/acs/AcsSessionAdapter.java` — implementa `AcsSessionPort`; Feign client para `acs-service` endpoint de blacklist; `isRevoked(jti: String) : boolean`
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/security/RevocationFilter.java` — `OncePerRequestFilter`; extrai `jti` do JWT; chama `AcsSessionPort.isRevoked`; se `true` → retorna 401 com ProblemDetail `session-revoked`; se ACS inacessível → fail-closed (rejeitar request)
- [ ] `RevocationFilter` roda APÓS `JwtAuthenticationFilter` do Spring Security (configurado via `addFilterAfter`)
- [ ] dual-actor enforcement: `SecurityConfig` configura regras que `POST /aprovacoes/cuidadora` exige scope `treinamento.no.aprovar.cuidadora` E rejeita scope `treinamento.no.aprovar.paciente` com 403 (e vice-versa)
- [ ] criar `src/test/java/com/neuroassist/trei/infrastructure/acs/AcsSessionAdapterTest.java` — mock do Feign client; verificar 401 session-revoked quando ACS retorna revogado; verificar fail-closed quando ACS inacessível

## Resultado esperado

Toda request com JWT revogado retorna 401 antes de atingir qualquer controller;
dual-actor enforcement via Spring Security scope rules.

## Criterios de aceite

- [ ] `./gradlew :trei-service:test --tests "*AcsSessionAdapterTest*"` sai 0
- [ ] Teste verifica que JWT com `jti` na blacklist retorna 401 `session-revoked` (não chega no controller)
- [ ] Teste verifica que ACS inacessível → request rejeitada (fail-closed — não passa como se aprovado)
- [ ] `RevocationFilter` NÃO chama `AcsSessionPort` para o endpoint `GET /health` (livre de auth)

## Dependencias

- TREI-003 (AcsSessionPort interface)
- TREI-014 (SecurityConfig — precede, pois RevocationFilter é configurado junto)

## Estimativa

M

## Prioridade

P5
