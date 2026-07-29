# GEST-024 -- Implement full BDD feature coverage — wire all 4 Gherkin features (~54 scenarios: lifecycle, eligibility, detection-profile, report-validation)

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto
As 4 features Gherkin estao em `docs/architecture/backend/services/gest-service/tests/cucumber/`. Esta issue implementa os step definitions Java que transformam esses cenarios em testes de integracao executaveis. O total e ~54 cenarios cobrindo os 4 invariantes-chave: maquina de estados de ciclo de vida, guard de elegibilidade de emergencia, perfil de deteccao versionado e validacao estateless de report. Ao concluir, rodar o bddGate replay.

## Spec de referencia
- SPEC-08, event-contracts.md §3, api-contract.md (todos os endpoints)

## Escopo
- [ ] implementar `backend/services/gest-service/src/test/java/com/neuroassist/gest/bdd/steps/TipoGestoLifecycleSteps.java` (steps para tipo-gesto-lifecycle.feature: register, transition, invariantes ADR-006)
- [ ] implementar `backend/services/gest-service/src/test/java/com/neuroassist/gest/bdd/steps/EligibilityCheckSteps.java` (steps para eligibility-check.feature: emergencia/rotina, primitivos desconhecidos)
- [ ] implementar `backend/services/gest-service/src/test/java/com/neuroassist/gest/bdd/steps/DetectionProfileSteps.java` (steps para detection-profile.feature: publish, versao monotonica, conditional-GET)
- [ ] implementar `backend/services/gest-service/src/test/java/com/neuroassist/gest/bdd/steps/ReportValidationSteps.java` (steps para report-validation.feature: validate-report, LGPD compliance)
- [ ] copiar as 4 feature files para `src/test/resources/features/` (Cucumber classpath discovery)
- [ ] rodar `python scripts/be-gates-replay.py --run --update --service gest-service --gate bddGate` e registrar resultado no master-plan

## Resultado esperado
`./gradlew :services:gest-service:test -Dcucumber.filter.tags="@integration"` passa com 54/54 cenarios; bddGate muda de DEFERRED para PASS.

## Criterios de aceite
- [ ] todos os ~54 cenarios passam sem failures ou pending steps
- [ ] nenhum step e `PendingException` (todos implementados)
- [ ] `be-gates-replay.py` executa sem erro e registra `bddGate: PASS` no checklist
- [ ] feature `report-validation.feature` inclui cenario que asserta que nenhum log contem dados biometricos apos validateReport()

## Dependencias
GEST-023 (infraestrutura BDD), GEST-006..GEST-010 (logica de negocio implementada), GEST-015 (EdgePairingClient para cenarios de pull).

## Estimativa
L

## Prioridade
P8
