# EVNT-012 -- Configure TenantDataSourceRouter + TenantContextHolder (schema-per-empresa, ADR-003)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
NeuroAssist AI uses schema-per-empresa multi-tenancy (ADR-003): every query must execute against the
`t_<empresa>` schema matching the JWT `tenant` claim. This issue configures a `TenantContextHolder`
(ThreadLocal) populated by the request filter (EVNT-019) and a Spring DataSource routing layer that
prepends `SET search_path = t_<empresa>` before each connection is returned from the pool, so JPA
queries automatically target the correct schema with no manual schema prefixing.

## Spec de referencia
- SPEC-04 (schema routing)
- contracts/data-model.md §5 (schema mapping)

## Escopo
- [ ] Create `TenantContextHolder` (ThreadLocal<String>) in `infrastructure.tenant` package
- [ ] Create `TenantAwareDataSource` extending `AbstractRoutingDataSource` (Spring): `determineCurrentLookupKey()` returns `TenantContextHolder.getTenant()`
- [ ] Implement a `DataSourceInitializer` or connection init SQL that executes `SET search_path = t_<empresa>` on each acquired connection
- [ ] Register `TenantAwareDataSource` as primary `@Bean DataSource`; configure JPA to use it
- [ ] Create `TenantResolutionFilter` (Servlet filter or Spring `HandlerInterceptor`) that reads `tenant` from resolved JWT claim and sets `TenantContextHolder.setTenant(empresa)` per request; clears in `finally`
- [ ] Unit test: `TenantContextHolder` set to `t_demo` → DataSource `determineCurrentLookupKey()` returns `t_demo`
- [ ] Integration test with Testcontainers: two schemas `t_demo1`/`t_demo2`; insert to each; verify no cross-schema visibility

## Resultado esperado
Every JPA query executes within the tenant schema resolved from the JWT `tenant` claim, with no
cross-tenant leakage possible at the persistence layer.

## Criterios de aceite
- [ ] `TenantContextHolder` is set from the JWT `tenant` claim before any repository call
- [ ] `TenantAwareDataSource.determineCurrentLookupKey()` returns the current tenant on every call
- [ ] A request with `tenant=empresa_a` cannot read rows from `t_empresa_b` (enforced by schema scoping)
- [ ] `TenantContextHolder` is cleared in a `finally` block after each request (no thread-local leak)
- [ ] Integration test: two tenant schemas, isolated inserts, no cross-tenant query visibility

## Dependencias
- EVNT-001 (scaffold)
- EVNT-011 (Flyway migrations must create the schema before routing works)
- EVNT-018 (OAuth2 filter populates JWT from which tenant is extracted; interim: mocked in tests)

## Estimativa
M

## Prioridade
P3
