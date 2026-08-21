# HIST-012 -- AcsActorDirectoryClient (Feign/RestClient) + ActorDirectoryAdapter

## Contexto

Única dependência outbound de runtime do HIST: resolve o escopo de pacientes de uma
cuidadora via ACS `GET /api/v1/acs/directory/caregivers/{id}/patient-scope`. Usado
exclusivamente em leituras de `hist:read` (não em ingestão — ADR-005). O adapter
implementa fail-closed via `ActorDirectoryUnavailableException` e Resilience4j
circuit breaker (SPEC-06, SPEC-10, `integration-contracts.md §2`).

## Spec de referencia

- SPEC-06 (scope de leitura de cuidadora — fail-closed)
- SPEC-10 (integração ACS: timeout 2s, CB, fallback)
- `contracts/integration-contracts.md §2`
- `generator-config.yml` — `feign_clients`

## Escopo

**`com.neuroassist.hist.infrastructure.acs`:**
- `AcsActorDirectoryClient` (Feign client ou `RestClient` `@Bean`): `GET /api/v1/acs/directory/caregivers/{id}/patient-scope` → `List<UUID>`. URL via `neuroassist.acs.url`. Timeout 2s connect+read.
- `ActorDirectoryAdapter` (`@Component`): implementa `ActorDirectoryPort`. Chama `AcsActorDirectoryClient`; envolve em try-catch de `FeignException`/`RestClientException` e lança `ActorDirectoryUnavailableException` em timeout, 5xx, ou CB OPEN.
- Configuração Resilience4j CB para `acs-actor-directory` (nome do CB): threshold, waitDuration, fallback lança a exception.
- Bean de autenticação service-credential (client_credentials JWT) para chamadas ao ACS directory scope.
- WireMock stub em `application-test.yml` para simular ACS nos testes de integração.

## Resultado esperado

Teste de integração com WireMock: (1) ACS responde com lista de patientIds → adapter retorna
lista; (2) ACS retorna timeout → adapter lança `ActorDirectoryUnavailableException`;
(3) CB OPEN → exception imediata sem chamar ACS.

## Criterios de aceite

- [ ] `ActorDirectoryAdapter` nunca é chamado em `POST /audit-facts` (só em leituras de `hist:read`).
- [ ] Timeout 2s na chamada ACS — configurável via `hist.acs.timeout-ms`.
- [ ] `FeignException` / timeout → `ActorDirectoryUnavailableException` lançada.
- [ ] CB OPEN lança `ActorDirectoryUnavailableException` sem tentar a chamada.
- [ ] Resposta de `hist:read:all` nunca chama `ActorDirectoryAdapter`.
- [ ] WireMock stub simula sucesso (200 com lista) e timeout (no response) corretamente nos testes.

## Dependencias

- HIST-004 (ActorDirectoryPort interface)
- HIST-001 (scaffold com Feign/ResilienceJ dependency)

## Estimativa

M

## Prioridade

P4
