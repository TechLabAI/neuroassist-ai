# INT-TREI-001 -- INT: TREI -> ACS (revocation blacklist + scope enforcement)

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto

TREI depende fortemente de ACS para ambas as funções de identidade: (1) validação de
assinatura JWT via `jwk-set-uri` (configuração Spring Security) e (2) checagem de
revogação por request via `AcsSessionPort` (fail-closed). A dependência é **hard**:
se ACS está inacessível, nenhuma request de autoria avança (health retorna `DEGRADED`).
Este item valida a integração real: contrato do endpoint de blacklist, autenticação
machine-to-machine TREI→ACS, e enforcement de escopos distintos nos dois endpoints
de aprovação (ADR-003/ADR-012).

## Spec de referencia

- SPEC-06 (security — ACS revocation, dual-actor gate)
- integration-contracts.md (hub-integration.md §1 — AcsSessionPort fail-closed)
- api-contract.md §4 (authentication — required claims, revocation, forbidden-scope)

## Escopo

- [ ] confirmar endpoint de blacklist no `acs-service` (URL, método HTTP, contrato de resposta) — documentar em `integration-contracts.md` TREI se houver gap
- [ ] configurar `AcsSessionAdapter` (Feign) com autenticação `client_credentials` TREI→ACS: `tres.acs.client-id`, `trei.acs.client-secret` no `application.yml`
- [ ] testar integração de ponta-a-ponta com `acs-service` rodando localmente (via docker compose): token revogado → 401 `session-revoked` em endpoint TREI
- [ ] verificar que approval endpoint `/aprovacoes/cuidadora` rejeita token com scope `treinamento.no.aprovar.paciente` com 403 (e vice-versa) — enforcement via ACS realm
- [ ] criar `src/test/java/com/neuroassist/trei/integration/AcsIntegrationTest.java` — teste de contrato usando WireMock para simular ACS blacklist; verificar fail-closed quando WireMock não responde
- [ ] documentar `ACS_JWK_SET_URI` e `trei.acs.*` no `setup-local.md`

## Resultado esperado

TREI valida JWT e blacklist via ACS real; fail-closed confirmado; dual-actor scope
enforcement funcional no realm ACS.

## Criterios de aceite

- [ ] `./gradlew :trei-service:test --tests "*AcsIntegrationTest*"` sai 0
- [ ] Teste com WireMock simula ACS down → request TREI retorna 401 (não passa como autenticado)
- [ ] `setup-local.md` documenta as env vars ACS necessárias (ACS_JWK_SET_URI, client credentials)

## Dependencias

- TREI-015 (AcsSessionAdapter + RevocationFilter)

## Estimativa

M

## Prioridade

P5
