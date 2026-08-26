# ADR-009 — Onboarding de empresa é operação de plataforma, e a empresa nasce com quem a opera

**Status:** Aceito — 2026-08-23
**Contexto:** E2E-029 (C-48, C-49); complementa [ADR-003 (multi-tenancy)](ADR-003-multi-tenancy.md)
e [ADR-004 (tenant identity & provisioning authority)](../services/acs-service/adr/ADR-004-tenant-identity-provisioning-authority.md)

---

## Contexto

Depois que E2E-016 fez o provisionamento criar o schema de verdade, dois problemas ficaram
visíveis ao tentar usar uma empresa recém-criada.

**1. A empresa nascia inalcançável.** Todo token pertence a um cuidador e todo cuidador pertence a
um tenant. Uma empresa sem nenhum cuidador não tem ator possível dentro dela. Criar o primeiro por
fora respondia `403 acs.tenant.mismatch` — corretamente, porque quem chamava pertencia a outra
empresa. O schema ficava perfeito e inútil. O seed de desenvolvimento mascarava isso inserindo
linhas direto no banco, que é por que seis sessões de E2E nunca esbarraram nisto.

**2. `acs.tenant.provisionar`, `.read` e `.reprocessar` estavam no papel `COORDENADOR_CLINICA`.**
O coordenador da clínica A podia criar e reprocessar a clínica B. Isso nunca foi o modelo
pretendido; era o único caminho que existia.

## Decisão

**O primeiro coordenador faz parte de criar a empresa.** `POST /api/v1/acs/tenants` passa a exigir
`primeiroCoordenador` e o cria atomicamente com o tenant.

**Onboarding é operação de plataforma, não de clínica.** As três autoridades `acs.tenant.*` saem
do papel de coordenador e passam ao cliente de serviço `platform-ops`, que obtém um token **sem
claim de tenant** — criar uma empresa acontece antes de essa empresa existir.

## Alternativas descartadas

| Alternativa | Por que não |
|---|---|
| Papel de plataforma acima do tenant (`ADMIN_PLATAFORMA`) | Cria um papel humano que enxerga todas as empresas; `cuidador.tenant_id` é NOT NULL, então exigiria schema novo, claim de tenant nulo e mudança em todo filtro de tenant dos resource servers |
| Endpoint de bootstrap com token de convite | Um segredo novo para emitir, entregar, expirar e proteger, para resolver um evento que acontece uma vez por empresa |
| Relaxar a comparação de tenant em `CaregiverService.create` | Abriria criação cross-tenant em **toda** chamada para atender um caso único. É a proteção mais importante do serviço |

## Consequências

**"Empresa sem ninguém dentro" deixa de ser representável.** É a garantia mais forte disponível:
não depende de ninguém lembrar de um segundo passo.

**A comparação de tenant do JWT continua intacta.** O caminho de bootstrap não a relaxa — troca-a
por uma invariante mais estreita: `seedFirstCoordinator` recusa se o tenant já tiver qualquer
cuidador (`TenantAlreadySeededException`). Roda uma vez por empresa e nunca mais.

**Token sem tenant é exceção declarada por cliente,** `tenantless: true` em
`acs.service-clients`. Não é flexibilização global: serviços cujas chamadas roteiam o
`search_path` pelo claim continuam obrigados a informar o tenant, e um cliente tenant-less que
peça token escopado é recusado — configuração errada não vira privilégio silencioso.

**`findById` e `retryProvisioning` deixam de exigir contexto de tenant.** A tabela `tenant` é o
registro global, não dado de empresa; nenhum `search_path` se aplica a ela. Exigir contexto ali só
seria satisfazível por um cuidador de alguma empresa não relacionada — exatamente o poder que este
ADR remove. Mesma exceção que `createTenant` já documentava.

**O segredo do `platform-ops` passa a ser credencial de produção.** Quem o tem cria empresas. O
default de desenvolvimento (`dev-service-secret`) nunca pode ir para produção, e a rotação é troca
de env var + restart, como nos demais clientes de serviço.

## Pendente

- **Trilha de auditoria de quem semeou o primeiro coordenador de cada empresa.** O fato existe nos
  logs; ainda não é audit fact no HIST. Depende de [E2E-019](../issues/E2E-019.md), que é onde o
  ACS ganha produção de fatos.
