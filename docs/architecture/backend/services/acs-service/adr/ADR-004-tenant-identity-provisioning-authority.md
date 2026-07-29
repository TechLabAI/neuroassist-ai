<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-004 — ACS as tenant and identity provisioning authority

## Status
aceita

## Contexto
O modelo multi-tenant (ADR-003 shared, schema-per-tenant) exige que exista um schema `t_<tenantId>`
antes de qualquer dado do tenant. Alguém precisa ser o dono do ciclo de vida do `Tenant` (empresa) e do
`Cuidador` — criar a empresa, disparar a criação do schema, e ser a fonte de verdade de "quais tenants
existem". As opções: (a) cada serviço cria seu próprio conceito de tenant/usuário; (b) o serviço de
migrações (MIG) também administra tenants; (c) um serviço dedicado de identidade/tenant. A opção (a)
espalha a fronteira de isolamento e não tem dono único; (b) mistura DDL (responsabilidade do MIG) com
regra de negócio de onboarding; a DDL de schema deve permanecer centralizada no MIG (ADR-003 shared),
mas a *decisão* de provisionar e o registro do tenant são regra de domínio, não migração.

## Decisão
Vamos fazer do **ACS a autoridade de provisionamento de tenant e identidade**. O ACS é o sistema de
registro do `Tenant` (diretório em `neuroassist_global`) e do `Cuidador`, e orquestra o
provisionamento: cria o `Tenant` em status `provisioning`, **solicita ao MIG** a criação do schema
`t_<tenantId>` a partir do template canônico, e só então marca o tenant `active`. O ACS decide *que* um
schema deve existir; o MIG continua dono de *como* a DDL é aplicada. Nenhum outro serviço cria tenants
ou contas de cuidador. A comunicação ACS→MIG é REST síncrona, fail-closed: sem confirmação do MIG, o
tenant não fica `active` e nenhum `Cuidador` pode ser criado contra ele.

## Consequências
**Positivas:** dono único da fronteira de isolamento; onboarding determinístico (tenant + schema +
primeiro cuidador em uma saga controlada); separação limpa entre decisão de provisionar (ACS) e DDL
(MIG), preservando o ADR-003; nenhum tenant meio-aberto (schema ausente com dados apontando para ele).
**Negativas / trade-offs:** cria uma dependência dura ACS→MIG em runtime (MIG precisa expor um endpoint
de provisionamento, não só migração de build); a saga precisa de compensação/retry se o MIG falhar; o
ACS passa a ter um papel privilegiado (raiz da árvore de tenants) acima dos demais serviços peers,
concentrando risco de isolamento. Aceito porque centralizar a autoridade de tenant é o que torna o
isolamento auditável e evita divergência de "o que é um tenant" entre serviços.
