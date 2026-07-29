# SPEC-04 — Data model + migrations (none)

> The gateway owns no schema and is not a migration target. This SPEC makes that explicit
> so downstream generators do not provision persistence for it. See
> `contracts/data-model.md` and `05-persistence-model.md`. EARS per standard.

## Objetivo
Registrar formalmente a **ausência de persistência** do gateway e as consequências para o
pipeline de geração (migração, scaffold).

## Escopo
- **In:** declaração de zero schema, zero migração, estado apenas efêmero.
- **Out:** qualquer DDL, datasource, pool de conexão.

## Decisões técnicas
- Nenhum `spring-boot-starter-data-*`, nenhum datasource auto-configurado.
- `be-migration-generator.py` contribui **zero** arquivos para este serviço (não há blocos
  ```sql``` em `data-model.md`).
- Estado efêmero: buckets de rate-limit (in-memory), cache JWK, config de rota.

## Implementação
- Excluir auto-configuração de datasource (ex.: sem dependência JDBC/R2DBC no build do
  gateway; se presente transitivamente, `spring.autoconfigure.exclude` de
  DataSourceAutoConfiguration).
- Smoke test de arquitetura: falhar o contexto se um `DataSource` bean existir.

## Casos de borda
- Dependência transitiva puxando JDBC → build/scaffold deve excluir a auto-config.

## Critérios de aceite (EARS)
- THE SYSTEM SHALL NOT auto-configure or open any database connection.
- THE SYSTEM SHALL contribute zero Flyway migrations to MIG.
- IF a `DataSource` bean is present in the application context, THEN THE SYSTEM SHALL be
  considered defective (asserted by a smoke test).
