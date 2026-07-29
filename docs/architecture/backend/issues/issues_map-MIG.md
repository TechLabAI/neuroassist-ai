# Issues Map -- MIG

| Issue | Title | Kind | Priority | Status |
|---|---|---|---|---|
| MIG-001 | Job invocation contract (--mode dispatch, exit codes 0/1/2/3) | BE | P1 | INTACT |
| MIG-002 | Internal HTTP API skeleton (provision, status, run) com escopos OAuth2 | BE | P1 | INTACT |
| MIG-003 | RFC 7807 ProblemDetail mapper com codigos estaveis | BE | P1 | INTACT |
| MIG-004 | MigrationPlan com ordenacao global-antes-de-tenant | BE | P2 | INTACT |
| MIG-005 | Fan-out de tenant schemas a partir do registry (migrate-all) | BE | P2 | INTACT |
| MIG-006 | Run report JSON agregado + mapeamento para exit code | BE | P2 | INTACT |
| MIG-007 | Migration global baseline (extensions + tabela tenant registry) | BE | P3 | INTACT |
| MIG-008 | Migration baseline do tenant-template (paciente, cuidador, tipo_gesto, comando, evento, dispositivo_edge) | BE | P3 | INTACT |
| MIG-009 | SchemaResolver com defaultSchema por target de execucao | BE | P3 | INTACT |
| MIG-010 | ProvisionTenantSchema use case (create-if-absent + migrate to head) | BE | P4 | INTACT |
| MIG-011 | Idempotencia de re-provisionamento (no-op em tenant ja corrente) | BE | P4 | INTACT |
| MIG-012 | Advisory lock por schema para serializar execucoes concorrentes | BE | P5 | INTACT |
| MIG-013 | Checksum drift fail-fast (validateOnMigrate) sem auto-repair | BE | P5 | INTACT |
| MIG-014 | Role de DDL privilegiada isolada das roles DML dos servicos de negocio | BE | P6 | INTACT |
| MIG-015 | Enforcement de escopo OAuth2 (migrations:internal / migrations:admin) nos endpoints internos | BE | P6 | INTACT |
| MIG-016 | Propagacao/geracao de Correlation ID em toda execucao | BE | P7 | INTACT |
| MIG-017 | Activity Trail Reporter best-effort (D-423) | BE | P7 | INTACT |
| MIG-018 | Health/readiness actuator endpoint | BE | P7 | INTACT |
| MIG-019 | Wiring do Cucumber runner contra Testcontainers PostgreSQL | BE | P8 | INTACT |
| MIG-020 | Guardrail de ausencia de broker (config assertion + teste de arquitetura) | BE | P9 | INTACT |
| INT-MIG-001 | Pre-flight check PostgreSQL (conectividade + role privilegiada + registry legivel) -> exit 3 | BE | P3 | INTACT |
| INT-MIG-002 | Gate do pipeline de deploy baseado em exit code | BE | P3 | INTACT |
| INT-MIG-003 | Contrato do chamador sincrono ACS (contract test do endpoint de provisioning) | BE | P4 | INTACT |
| INT-MIG-004 | Registro no Eureka (REG) da face HTTP interna | BE | P6 | INTACT |
| INT-MIG-005 | Guard de nao-dependencia do TechLab AI (sem Feign client, scan de dependencias) | BE | P6 | INTACT |
