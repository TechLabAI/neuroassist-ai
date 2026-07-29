<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-003 — Multi-tenancy

## Status
aceita

## Contexto
Cada empresa (família ou clínica) é um tenant que monitora pacientes com dados de saúde
sensíveis. A LGPD exige isolamento forte e auditabilidade por tenant. As opções usuais são:
discriminador por linha (coluna `tenantId`), schema-per-tenant, ou database-per-tenant. Row-level
concentra risco de vazamento cross-tenant por bug de query; database-per-tenant é caro
operacionalmente para o volume do MVP.

## Decisão
Vamos usar **schema-per-tenant** numa única instância PostgreSQL: um schema global
(`neuroassist_global`) para dados cross-tenant (registro de tenants, referência de plataforma) e
um schema `t_<tenantId>` por empresa. O isolamento é **estrutural** (fronteira de schema), não
apenas lógico. A evolução de schema é centralizada no serviço de migrações (MIG), que aplica um
template canônico idêntico a cada schema de tenant e roda migrações globais antes das de tenant.

## Consequências
**Positivas:** isolamento forte por fronteira de schema (mitiga vazamento cross-tenant); auditoria
por tenant natural; provisionamento determinístico via template; sem sharding/multi-DB no MVP.
**Negativas / trade-offs:** "migrate all tenants" cresce linearmente com o nº de empresas; roles
de banco precisam distinguir DDL (MIG) de DML (serviços); onboarding depende de um passo de
provisionamento; reavaliar particionamento/arquivamento quando o volume crescer.
