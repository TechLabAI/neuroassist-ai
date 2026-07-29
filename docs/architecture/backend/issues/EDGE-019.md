# EDGE-019 -- Wire Cucumber integration test infrastructure (Testcontainers PostgreSQL, Spring Security test stubs, device-credential mock, tenant schema setup)

**Status:** INTACT -- gerado 2026-07-29
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto
A infraestrutura de teste de integracao (BDD) precisa de: Testcontainers para PostgreSQL, mocks de seguranca (device token JWT, human token com scopes), schema de tenant para testes, e WireMock para GEST (DetectionProfileClient). Os 4 arquivos .feature do Cucumber (device-registration-enrollment, device-pairing, heartbeat-liveness, profile-pointer-drift) gerados no M3 precisam desse scaffold para rodar.

## Spec de referencia
- SPEC-08

## Escopo
- [ ] configurar `CucumberSpringContext.java` (ja existe do M3 BDD scaffold) com `@SpringBootTest(webEnvironment=RANDOM_PORT)` + `@Testcontainers` + PostgreSQL container
- [ ] criar (ou completar) `SharedSteps.java` com: setup de tenant schema (CREATE SCHEMA t_test), criacao de JWT de teste (human/device/service via TestJwtFactory), configuracao de WireMock para GEST (`/api/v1/gest/detection-profiles/active`)
- [ ] criar `application-test.yml` com: datasource apontando para Testcontainers, `jwk-set-uri` apontando para stub local, GEST URL apontando para WireMock
- [ ] verificar que os 4 StepDefs ja gerados no M3 (DeviceRegistrationSteps, DevicePairingSteps, HeartbeatLivenessSteps, ProfilePointerDriftSteps) compilam com o contexto e `CucumberIT` encontra as features

## Resultado esperado
`./gradlew :services:edge-service:test -Dcucumber.filter.tags=@integration` executa sem falha de configuracao (features podem ter cenarios pendentes nesta issue).

## Criterios de aceite
- [ ] Testcontainers inicia PostgreSQL e aplica V0700 migration corretamente
- [ ] JWT de teste (human com `edge.manage` e device com `edge.device`) e aceito pelo SecurityConfig de teste
- [ ] WireMock stub para GEST configurado e chamado pelo HeartbeatService no teste
- [ ] `CucumberIT` executa os 4 arquivos de feature sem `ClassNotFoundException` ou `AmbiguousStepDefinitionsException`

## Dependencias
- EDGE-011 (precede)
- EDGE-012 (precede)

## Estimativa
M

## Prioridade
P8
