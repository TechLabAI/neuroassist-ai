<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-003 — Identidade "paciente com sessão" consumida do ACS (CONV é resource server)

## Status
aceita

> Escopo: local (`conv-service`). Implementa **ADR-012 (accepted)** — "Modelo de identidade do paciente
> com sessão persistente" (Opção C). Este ADR registra que CONV **consome**, não **possui**, essa
> identidade; não reabre a escolha do ator.

## Contexto
O ADR-012 fechou um quarto tipo de identidade — "Paciente com sessão" — como ator de primeira classe,
**modelado e gerenciado em `acs-service`**: sessão provisionada pela cuidadora, **sem expiração**,
**revogável** a qualquer momento via a **blacklist de JWT já existente** no projeto
(`shared/rbac/session-policy.md`). O RBAC é granular: o app do paciente nasce só com autoridade de
CONV/GEST-report (`conv.arvore.navegar` / `conv.no.selecionar`), sem nenhuma autoridade de cuidadora. O
celular carrega **duas identidades simultâneas e independentes** (ADR-014): token de dispositivo
(`edge.device`, do EDGE) para reportar gesto, e a sessão de paciente (deste ADR-012) para operar o CONV. É
preciso registrar o papel do CONV nesse arranjo.

## Decisão
CONV é um **resource server puro** para o ator "paciente com sessão": valida o JWT emitido pelo ACS (realm
Keycloak compartilhado), **exige os escopos** `conv.arvore.navegar` / `conv.no.selecionar` e **honra a
blacklist do ACS** em toda chamada, para que a revogação da sessão não-expirável tenha efeito imediato
(fail-closed). CONV **não** modela, emite, persiste nem revoga essa identidade — schema do ator, fluxo de
provisionamento e revogação vivem no ACS. Nenhuma autoridade de cuidadora/admin é aceita nesses tokens. A
lógica de validação/blacklist fica isolada num adapter (`AcsSessionPort` na `infrastructure`), trocável sem
tocar domínio/aplicação. Toda seleção de nó é auditável no HIST como vinda da **sessão do paciente** — não
do dispositivo, não da cuidadora.

## Consequências
**Positivas:** modelagem correta (o ator que age é o paciente); RBAC mínimo desde o início; CONV não herda
o custo de manter identidade/credencial; auditoria atribui a ação ao ator certo; ponto de troca isolado no
adapter. **Negativas / trace-offs:** CONV fica dependente **hard** do ACS (sem ACS, não há como operar) e
acoplado ao mesmo realm; a sessão sem expiração é superfície de risco herdada — só a blacklist a mitiga, e o
custo/latência de checar blacklist por request precisa ser validado em specs. Assunção a confirmar (herdada
do ADR-012): a blacklist existente suporta o ciclo "sem expiração, revogável a qualquer momento" sem
adaptação.
