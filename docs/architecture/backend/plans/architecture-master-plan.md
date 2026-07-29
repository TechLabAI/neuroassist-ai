# Architecture Master Plan

> Rastreia a camada de arquitetura (Layer 1 -- Architect) de cada modulo.
> Atualizado automaticamente (master-plan.py, D-371). NAO editar a mao.

Legenda de status: `pendente` | `em andamento` | `OK` | `bloqueado` | `N/A`

## Status por modulo

| Modulo | Servico | Vision | Specs | Specs-Validate | Backlog | Scaffold | Atualizado |
|---|---|---|---|---|---|---|---|
| GWY | neuroassist-ai-api-gateway | OK | OK | N/A | OK | OK | 2026-07-27 |
| MIG | neuroassist-ai-migrations | OK | OK | N/A | OK | OK | 2026-07-27 |
| REG | neuroassist-ai-service-registry | OK | OK | N/A | OK | OK | 2026-07-27 |
| GEST | gest-service | OK | OK | OK | OK | OK | 2026-07-28 |
| EVNT | evnt-service | OK | OK | OK | OK | OK | 2026-07-28 |
| PAC | pac-service | OK | OK | OK | OK | OK | 2026-07-29 |
| EDGE | edge-service | OK | OK | OK | OK | OK | 2026-07-29 |
| ACS | acs-service | OK | OK | OK | OK | pendente | 2026-07-30 |

## Ledger de gates diferidos

| Data | Escopo | Gate | Comando de recuperacao | Status |
|---|---|---|---|---|
| 2026-07-27 | GEST | bddGate | `python scripts/be-gates-replay.py --run --update --service gest-service --gate bddGate` | diferido |

## Log cronologico
- 2026-07-27T19:23:59 -- GWY -- Backlog GWY concluido: 21 issues funcionais + 5 de integracao, plan-GWY.md, generator-config.yml (type=gateway)
- 2026-07-27T19:32:58 -- GWY -- Scaffold GWY concluido: skeleton gateway (com.neuroassist.gwy), 13 arquivos, compileJava+ArchUnit OK; fix D-528 no be-module-generator.py (package suffix infra services)
- 2026-07-27T21:06:21 -- GEST -- M2 -- BE Specs OK -- 3 contratos gerados (api-contract, data-model, event-contracts); detection contract congelado v1.0.0
- 2026-07-27T21:22:30 -- GEST -- Specs-Validate -- OK (gate deferido) -- EARS gate PASS; 4 features Gherkin (~54 cenarios); SPEC-01..10; C4; --generate-only; bddGate diferido para pos-scaffold
- 2026-07-27T21:24:17 -- GEST -- M3 -- BE Specs-Validate OK (--generate-only) -- EARS gate PASS, 4 features Gherkin ~54 cenarios, SPEC-01..10, C4 diagrams; bddGate DIFERIDO p/ pos-scaffold
- 2026-07-27T21:49:15 -- GEST -- M4 -- BE Backlog OK -- 25 issues funcionais (GEST-001..025) + 7 integracao (INT-GEST-001..007), plan-GEST.md P1..P9, generator-config.yml (port 8084)
- 2026-07-28T02:36:08 -- GEST -- M5 -- BE Scaffold OK -- gest-service gerado (90 base files, V1 DDL, 4 BDD features, 5 step-def classes); compileJava OK; ArchUnit 12/12 PASS; D-530 (find_decisions_dir fix + verbos transition/pull/validate)
- 2026-07-28T07:13:01 -- EVNT -- Specs-Validate -- OK -- todos os 5 gates PASS (estrutura, EARS, SQL, Gherkin, cobertura M2)
- 2026-07-28T07:35:14 -- EVNT -- Backlog -- OK -- 25 issues funcionais + 7 integracao; 3 gaps M2 resolvidos (INT-EVNT-001/004/007)
- 2026-07-28T07:51:01 -- EVNT -- Scaffold -- OK -- compileJava OK, architectureCheck OK (RULE-01..06); 97 arquivos + migration V1 + 4 BDD features; D-531 registrado (3 correcoes de gerador)
- 2026-07-28T12:14:02 -- PAC -- M1 -- Vision OK -- 01..06 + 3 ADRs (calibration-authoring, emergency-gate, versioned-retire); M1.5 brief sync OK (BE autoritativo)
- 2026-07-28T18:55:47 -- PAC -- M2 -- BE Specs OK -- api-contract (CRUD Paciente+Comando, lifecycle, emergency-gate fail-closed, catalogo-ativo); data-model (Paciente+Comando SQL, history-preserving, ETag); event-contracts (N/A broker, 3 ports outbound)
- 2026-07-28T19:15:59 -- PAC -- M3 -- BE Specs-Validate OK (gate deferido) -- EARS gate PASS; 4 features Gherkin (49 cenarios: paciente, command-lifecycle, emergency-gate, catalogo-pub); SPEC-01..10; bddGate DIFERIDO p/ pos-scaffold
- 2026-07-28T19:41:49 -- PAC -- M4 -- BE Backlog OK -- 25 issues funcionais (PAC-001..025) + 6 integracao (PAC-INT-001..006); plan-PAC.md P1..P9; generator-config.yml (type=standard, 2 aggregates, 4 app services, 4 feign clients)
- 2026-07-29T06:40:05 -- PAC -- M5 -- BE Scaffold OK -- 86 arquivos (hexagonal); compileJava OK; architectureCheck OK (RULE-01..06); BDD dry-run OK (49 tests); D-532 registrado (3 correcoes de gerador)
- 2026-07-29T10:47:28 -- EDGE -- M1 Vision -- OK -- 9 artifacts gerados (docs 01-06 + 2 ADRs + brief); device auth model + heartbeat-derived liveness
- 2026-07-29T11:08:33 -- EDGE -- M2 BE Specs -- OK -- api-contract.md + data-model + events + 11 SPECs + 4 features Cucumber; credencial=OAuth2 client_credentials por device; heartbeat=30s, staleness=90s
- 2026-07-29T11:17:57 -- EDGE -- M3 BE Specs-Validate -- OK (generate-only) -- Gate1 EARS PASS (122 SHALL, 0 violations); 38 BDD test cases; Gradle gate DIFERIDO pos-scaffold
- 2026-07-29T11:48:29 -- EDGE -- M4 BE Backlog -- OK -- 21 issues funcionais + 7 INT = 28 issues; plan-EDGE.md; serverTime em LivenessView declarado obrigatorio (EDGE-004); ACS client registration deferred EDGE-INT-002
- 2026-07-29T12:09:13 -- EDGE -- M5 BE Scaffold -- OK -- compileJava OK + ArchUnit RULE-01..06 OK; D-533 (EDGE verbs enroll/pair/unpair/record); BDD gate DIFERIDO (ambiguous @PostMapping em DeviceRegistrationController - fix no P1)
- 2026-07-29T18:24:22 -- EDGE -- M5 BE BDD gate -- OK -- 49/49 testes; fixes inline: DeviceRegistrationController ambiguous @PostMapping + HeartbeatLivenessSteps {string}->{int}
- 2026-07-29T20:00:38 -- ACS -- M2 -- BE Specs OK -- api-contract (Auth/OAuth2/JWT, Tenant provisioning, Caregiver CRUD, Caregiver-patient links, Directory surface); data-model (tenant+signing_key global, cuidador+caregiver_patient_link empresa); event-contracts (6 domain facts to HIST, MigProvisioningPort+PacPatientResolvePort); SPEC-01..10
- 2026-07-29T20:14:34 -- ACS -- M3 -- BE Specs-Validate OK (gate deferido) -- EARS gate PASS (94 clausulas, 5 style gaps); 40 cenarios BDD (5 .feature files); C4 context diagram; Gradle gate DIFERIDO pos-scaffold
- 2026-07-30T00:00:00 -- PEA (Python Edge Agent) -- Layer 1 COMPLETO -- docs 01-06 + 5 ADRs + api-consumed + events-published + data-model + 8 SPECs (EARS) + 6 .feature files; D-534; co-location constraint: mesmo servidor que Java services, webcam integrada (MVP); sem Scaffold (Python, nao Java); repo separado: neuroassist-edge-agent/

## Componentes externos (nao Java)

| Componente | Repo | Layer 1 | Decisao | Notas |
|---|---|---|---|---|
| PEA (Python Edge Agent) | `neuroassist-edge-agent/` (futuro) | OK 2026-07-30 | D-534 | Python 3.11+, MediaPipe+ONNX; co-located; docs em docs/architecture/edge-agent/python-edge-agent/ |
- 2026-07-30T00:22:06 -- ACS -- M4 Backlog -- OK (skip: plan-ACS.md já existia)
