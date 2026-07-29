# EVNT-010 -- Create JPA entities + adapters (Evento, NotificationDispatch, ReportIdempotency + repositories)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
Creates the JPA infrastructure layer for the three EVNT tables defined in `contracts/data-model.md`:
`EventoJpaEntity`, `NotificationDispatchJpaEntity`, `ReportIdempotencyJpaEntity`. Each entity maps to
the tenant schema (set at connection time by `TenantDataSourceRouter`, EVNT-012). Corresponding Spring
Data JPA repositories implement the port interfaces declared in EVNT-001, making the application
services unit-testable without a database.

## Spec de referencia
- SPEC-04 (data model + migrations)
- contracts/data-model.md (canonical DDL)

## Escopo
- [ ] Create `EventoJpaEntity` mapping `evento` table: all columns, `@Column(unique=true)` on `reportId`, `@GeneratedValue` on `seq` (read-only via `insertable=false, updatable=false` — DB handles `bigserial`)
- [ ] Create `NotificationDispatchJpaEntity` mapping `notification_dispatch` table: FK to `Evento`, `@Enumerated(EnumType.STRING)` for `tier`/`status` enums
- [ ] Create `ReportIdempotencyJpaEntity` mapping `report_idempotency` table: `reportId` as `@Id`
- [ ] Create `EventoJpaRepository extends JpaRepository` with custom query methods: `findByReportId`, `findActiveByTenant(feedback='nao_avaliado', ordered by urgencia/recognizedAt)`, `findPagedByFilters`, `findFalsePositiveRateByComando`
- [ ] Create `DispatchJpaRepository` with `findByEventoId`, `findPendingByTenant(status IN queued/failed, attempts < max)`
- [ ] Create `IdempotencyJpaRepository` with `findByReportId`
- [ ] Implement port interfaces (`EventoRepositoryPort`, `DispatchRepositoryPort`, `IdempotencyRepositoryPort`) as adapter classes delegating to the JPA repos
- [ ] Integration test with Testcontainers PostgreSQL: insert + read back for each entity; unique constraint violation on `report_id`

## Resultado esperado
All three JPA entities compile, map correctly to the data model DDL, and implement the port interfaces.
Integration test confirms persistence with a real PostgreSQL container.

## Criterios de aceite
- [ ] `EventoJpaEntity.reportId` has a unique constraint that causes `DataIntegrityViolationException` on duplicate insert
- [ ] `EventoJpaEntity.seq` is auto-assigned by the DB (`bigserial`) — app never sets it
- [ ] `NotificationDispatchJpaEntity` FK to `evento.id` is represented as `@ManyToOne` (no cross-schema FK)
- [ ] Port adapters pass all port interface method calls through to the JPA repos
- [ ] Testcontainers integration test: persist + retrieve `Evento`, `NotificationDispatch`, `ReportIdempotency` in correct tenant schema

## Dependencias
- EVNT-001 (port interfaces)
- EVNT-011 (Flyway migrations — run before integration tests to set up schema)
- EVNT-012 (TenantDataSourceRouter — needed to resolve schema at connection time)

## Estimativa
M

## Prioridade
P3
