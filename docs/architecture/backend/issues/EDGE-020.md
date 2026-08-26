# EDGE-020 -- Implement full BDD feature coverage — wire all 4 Gherkin features (~38 scenarios: device-registration-enrollment, device-pairing, heartbeat-liveness, profile-pointer-drift)

**Status:** CLOSED -- pos-validacao 2026-08-23
**Maturidade:** 100 -- Maduro (soft avaliado: observabilidade completa + RULE-07 com prova executavel em MultiTenantIsolationIT)
**Done/Total:** 10/10 itens

> **Correção de estado — BASELINE 2026-08-22 (`/be-autopilot EDGE`).** Esta issue estava marcada
> `DONE` sem que os cenários executassem. O runner `CucumberIT` está com
> `EXECUTION_DRY_RUN_PROPERTY_NAME = "true"`, e em dry-run o Cucumber **parseia e pula** cada
> cenário, reportando não-falha — foi assim que "120/120 PASS" conviveu com 118
> `PendingException` nos step definitions. A validação que fechou o P8 foi Modo 3 LIGHT
> (`plan-EDGE.md` linha 47), que não roda a suíte e portanto não consegue distinguir cenário
> implementado de cenário pulado. Os outros seis microserviços têm `dry-run=false` e zero
> pendentes.

## Contexto
Os 4 arquivos Gherkin gerados no M3 (device-registration-enrollment.feature, device-pairing.feature, heartbeat-liveness.feature, profile-pointer-drift.feature) precisam de step definitions completas implementadas com chamadas REST reais ao servico em Testcontainers. Todos os cenarios devem passar (green) no gate BDD.

## Spec de referencia
- SPEC-08

## Escopo
- [x] implementar step definitions completas em `DeviceRegistrationEnrollmentSteps.java` (cenarios: register, enroll, token-expired, already-enrolled, retire, scope-guard)
- [x] implementar step definitions completas em `DevicePairingSteps.java` (cenarios: pair, unpair, exclusive-pairing-conflict, pair-retired-device, re-pair-with-credential-rotation)
- [x] implementar step definitions completas em `HeartbeatLivenessSteps.java` (cenarios: online, offline-after-staleness, offline->online transition, device-retired heartbeat, ETag 304, serverTime present)
- [x] implementar step definitions completas em `ProfilePointerDriftSteps.java` (cenarios: no-drift, drift-detected, GEST-unavailable degraded, no-active-profile)
- [x] garantir que todos os ~38 cenarios passam no `./gradlew :services:edge-service:test -Dcucumber.filter.tags=@integration`

## Resultado esperado
Todos os cenarios Gherkin passam no CI; gate BDD verde.

## Criterios de aceite
- [x] Todos os ~38 cenarios do `@integration` tag passam (exit 0)
- [x] Nenhum cenario com status `Pending` ou `Undefined`
- [x] Cenario de credential enrollment retorna `clientSecret` uma unica vez e invalida token na segunda tentativa
- [x] Cenario de liveness: poll apos staleness retorna `status=offline`
- [x] Cenario de GEST degraded: heartbeat aceito com `profilePointerDegraded=true`

## Dependencias
- EDGE-019 (precede)

## Estimativa
L

## Prioridade
P8
