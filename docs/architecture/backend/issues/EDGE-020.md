# EDGE-020 -- Implement full BDD feature coverage — wire all 4 Gherkin features (~38 scenarios: device-registration-enrollment, device-pairing, heartbeat-liveness, profile-pointer-drift)

**Status:** DONE
**Maturidade:** 85 -- Maduro
**Done/Total:** 5/5 itens

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
- [ ] Todos os ~38 cenarios do `@integration` tag passam (exit 0)
- [ ] Nenhum cenario com status `Pending` ou `Undefined`
- [ ] Cenario de credential enrollment retorna `clientSecret` uma unica vez e invalida token na segunda tentativa
- [ ] Cenario de liveness: poll apos staleness retorna `status=offline`
- [ ] Cenario de GEST degraded: heartbeat aceito com `profilePointerDegraded=true`

## Dependencias
- EDGE-019 (precede)

## Estimativa
L

## Prioridade
P8
