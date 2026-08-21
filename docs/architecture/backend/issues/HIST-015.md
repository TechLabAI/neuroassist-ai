# HIST-015 -- TenantContextHolder + schema-pinning filter (JWT tenant claim → t_<empresa>)

## Contexto

Isolamento multi-tenant schema-per-empresa: o JWT `tenant` claim é a única fonte de
verdade para o schema `t_<empresa>` a usar em cada request (SPEC-06, api-contract §1.1).
Body/query/path com `tenantId`/`empresaId` diferente do claim → 403 tenant-mismatch.
A store de schema deve ser thread-safe (ThreadLocal ou request scope).

## Spec de referencia

- SPEC-06 (tenant isolation — tenant claim authoritative)
- SPEC-04 (schema-per-empresa: `t_<empresa>`)
- `contracts/api-contract.md §1.1 EARS — tenant scoping`

## Escopo

**`com.neuroassist.hist.infrastructure.multitenancy`:**
- `TenantContextHolder` (ThreadLocal wrapper): `setCurrentTenant(String)`, `getCurrentTenant(): String`, `clear()`.
- `TenantContextFilter` (`OncePerRequestFilter`): extrai `tenant` claim do JWT (via `SecurityContextHolder`), chama `TenantContextHolder.setCurrentTenant(t_<tenant>)`. Executa **após** `SecurityFilter`.
- `HistCurrentTenantIdentifierResolver` (`CurrentTenantIdentifierResolver`): retorna `TenantContextHolder.getCurrentTenant()` para o Hibernate multi-tenancy.
- `HistMultiTenantConnectionProvider` (`MultiTenantConnectionProvider`): `getConnection(tenantIdentifier)` → seta `SET search_path TO <tenantIdentifier>, public`.
- Interceptor de request body: se body contém `tenantId`/`empresaId` diferente do claim → lança `TenantMismatchException` → 403 `/errors/tenant-mismatch`.

## Resultado esperado

Teste de integração: request com `tenant=empresa_a` no JWT acessa só schema `t_empresa_a`;
body com `tenantId != JWT.tenant` resulta em 403; cleanup de ThreadLocal após response.

## Criterios de aceite

- [ ] `TenantContextHolder.getCurrentTenant()` retorna `t_<empresa>` derivado do JWT `tenant` claim.
- [ ] Request com JWT `tenant=empresaA` acessa somente registros do schema `t_empresaA`.
- [ ] Request com body `tenantId=empresaB` e JWT `tenant=empresaA` resulta em `403 /errors/tenant-mismatch`.
- [ ] `TenantContextHolder.clear()` é chamado no finally do filter (sem vazamento de contexto entre requests).
- [ ] `search_path` é setado corretamente para o schema do tenant em cada conexão.

## Dependencias

- HIST-001 (Spring Security já configurado)
- HIST-014 (JWT parsing disponível via SecurityContext)
- HIST-009, HIST-010 (entities/adapters usam o schema resolvido)

## Estimativa

M

## Prioridade

P5
