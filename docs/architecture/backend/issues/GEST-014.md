# GEST-014 -- Wire multi-tenant SchemaRoutingDataSource for gest-service + 503-on-unresolved-schema guard

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto
Todo acesso ao BD do `gest-service` usa schema-per-tenant. O `SchemaRoutingDataSource` le o tenant do `TenantContextHolder` (populado pelo `TenantContextFilter` em GEST-018) e emite `SET search_path = t_<tenantId>` antes de cada connection. Se o schema nao existir (MIG ainda nao aplicou para aquele tenant), o servico deve retornar `503` em vez de executar queries cross-schema ou expor erros de BD raw.

## Spec de referencia
- SPEC-04, hub-integration.md §3, setup-local.md §3

## Escopo
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/config/TenantSchemaConfig.java` (bean `SchemaRoutingDataSource` que aplica `SET search_path` via `TenantContextHolder`)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/config/DataSourceConfig.java` (desabilita Flyway local, registra `SchemaRoutingDataSource` como primary `DataSource`)
- [ ] adicionar `TenantSchemaHealthIndicator` que verifica se o schema do tenant corrente existe em `/actuator/health`
- [ ] implementar guard: se `TenantContextHolder` vazio, retornar `503` antes de abrir conexao (anotacao `@PreAuthorize` ou filtro)
- [ ] testes de integracao: request com tenant schema existente funciona; request com tenant sem schema retorna 503

## Resultado esperado
Requests de tenant com schema aplicado funcionam normalmente; requests de tenant sem schema retornam `503` com ProblemDetail, sem expor detalhes internos de BD.

## Criterios de aceite
- [ ] request com JWT de tenant `t_empresa_test` (schema existente) retorna `200` para GET /tipos-gesto
- [ ] request com JWT de tenant `t_schema_missing` retorna `503 Service Unavailable` com ProblemDetail
- [ ] `SET search_path` e aplicado antes de cada query (verificado em log de BD)
- [ ] Flyway nao roda no startup do `gest-service` (nenhum log de migration no boot)

## Dependencias
GEST-001, GEST-013 (migration gerada para criar o schema nos testes).

## Estimativa
M

## Prioridade
P3
