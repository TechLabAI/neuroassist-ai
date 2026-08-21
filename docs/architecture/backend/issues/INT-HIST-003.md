# INT-HIST-003 -- Multi-tenant schema isolation integration test

## Contexto

Verifica que registros de tenant `empresa_a` são invisíveis para `empresa_b` — o coração
do isolamento schema-per-empresa. Usa dois schemas Testcontainers provisionados pelo
Flyway de teste, e dois JWTs com `tenant` claims distintos.

## Spec de referencia

- SPEC-04 (schema-per-empresa: nenhum dado HIST fora de `t_<empresa>`)
- SPEC-06 (tenant isolation — tenant claim authoritative; cross-tenant → 404, nunca 403)
- `contracts/api-contract.md §1.1 EARS — tenant scoping`

## Escopo

**`src/test/kotlin/com/neuroassist/hist/integration/multitenancy/MultiTenantIsolationTest`:**
- Setup: Testcontainers PostgreSQL com dois schemas `t_empresa_a` e `t_empresa_b`; Flyway aplica migrations nos dois.
- Inserir 3 records no tenant `empresa_a` via `POST /audit-facts` com JWT `tenant=empresa_a`.
- `GET /audit-facts/{id-empresa_a}` com JWT `tenant=empresa_b` → `404` (sem vazamento de existência).
- `GET /history` com JWT `tenant=empresa_b` → 200 com lista vazia (não retorna records de `empresa_a`).
- `POST /audit-facts` com body `tenantId=empresa_b` e JWT `tenant=empresa_a` → `403 /errors/tenant-mismatch`.
- Verificar `SET search_path` no log via captura de SQL (QueryLog ou p6spy).

## Resultado esperado

Zero cross-tenant leak em qualquer cenário. `tenant-mismatch` retorna 403.

## Criterios de aceite

- [ ] Record inserido em `t_empresa_a` não aparece em query com JWT `tenant=empresa_b`.
- [ ] `GET /audit-facts/{id}` cross-tenant retorna `404`, não `403` (sem revelar existência).
- [ ] Body com `tenantId != JWT.tenant` retorna `403 /errors/tenant-mismatch`.
- [ ] `search_path` correto visível em logs/QueryLog para cada request.
- [ ] Testes rodam com dois schemas independentes no mesmo Testcontainers.

## Dependencias

- HIST-015 (TenantContextHolder + schema-pinning)
- HIST-009, HIST-010, HIST-011 (persistência + migrations)
- HIST-014 (JWT mock com `tenant` claim configurável)

## Estimativa

M

## Prioridade

P5
