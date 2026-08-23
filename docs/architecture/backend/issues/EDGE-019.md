# EDGE-019 -- Wire Cucumber integration test infrastructure (Testcontainers PostgreSQL, Spring Security test stubs, device-credential mock, tenant schema setup)

**Status:** NEAR-CLOSED -- pos-validacao 2026-08-23
**Maturidade:** 90 -- Maduro
**Done/Total:** 8/8 itens

> **Correção de estado — BASELINE 2026-08-22 (`/be-autopilot EDGE`).** A infraestrutura foi de
> fato construída (Testcontainers, contexto Spring, WireMock), e o critério de aceite desta issue
> admitia cenários pendentes. O que não fecha é o runner permanecer em
> `EXECUTION_DRY_RUN_PROPERTY_NAME = "true"`: enquanto estiver assim, a infra existe mas nada a
> exercita, e o gate reporta sucesso sobre cenários que nunca rodaram. Desligar o dry-run é o
> item restante — e depende de EDGE-020 estar realmente implementada, senão os 118 steps pendentes
> passam a falhar.

## Contexto
A infraestrutura de teste de integracao (BDD) precisa de: Testcontainers para PostgreSQL, mocks de seguranca (device token JWT, human token com scopes), schema de tenant para testes, e WireMock para GEST (DetectionProfileClient). Os 4 arquivos .feature do Cucumber (device-registration-enrollment, device-pairing, heartbeat-liveness, profile-pointer-drift) gerados no M3 precisam desse scaffold para rodar.

## Spec de referencia
- SPEC-08

## Escopo
- [x] configurar `CucumberSpringContext.java` (ja existe do M3 BDD scaffold) com `@SpringBootTest(webEnvironment=RANDOM_PORT)` + `@Testcontainers` + PostgreSQL container
- [x] criar (ou completar) `SharedSteps.java` com: setup de tenant schema (CREATE SCHEMA t_test), criacao de JWT de teste (human/device/service via TestJwtFactory), configuracao de WireMock para GEST (`/api/v1/gest/detection-profiles/active`)
- [x] criar `application-test.yml` com: datasource apontando para Testcontainers, `jwk-set-uri` apontando para stub local, GEST URL apontando para WireMock
- [x] verificar que os 4 StepDefs ja gerados no M3 (DeviceRegistrationSteps, DevicePairingSteps, HeartbeatLivenessSteps, ProfilePointerDriftSteps) compilam com o contexto e `CucumberIT` encontra as features

## Resultado esperado
`./gradlew :services:edge-service:test -Dcucumber.filter.tags=@integration` executa sem falha de configuracao (features podem ter cenarios pendentes nesta issue).

## Criterios de aceite
- [x] Testcontainers inicia PostgreSQL e aplica V0700 migration corretamente
- [x] JWT de teste (human com `edge.manage` e device com `edge.device`) e aceito pelo SecurityConfig de teste
- [x] WireMock stub para GEST configurado e chamado pelo HeartbeatService no teste
- [x] `CucumberIT` executa os 4 arquivos de feature sem `ClassNotFoundException` ou `AmbiguousStepDefinitionsException`

## Dependencias
- EDGE-011 (precede)
- EDGE-012 (precede)

## Estimativa
M

## Prioridade
P8
