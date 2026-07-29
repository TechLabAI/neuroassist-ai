# INT-GEST-006 -- Integration contract with PAC — eligibility check endpoint consumed synchronously

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Contexto
O servico PAC chama `POST /api/v1/gest/eligibility:check` sincronamente ao construir um `Comando` de urgencia `emergencia`. GEST e a autoridade de elegibilidade — PAC nao toma decisoes de seguranca localmente. O contrato de request/response e definido em `api-contract.md §3`. PAC deve tratar `422 unknown-primitive` como erro de programacao (primitivo invalido no Comando) e `503` do GEST como indisponibilidade temporaria.

## Spec de referencia
- SPEC-03, api-contract.md §3, integration-contracts.md (consumido por PAC)

## Escopo
- [ ] validar request/response shape de `POST /eligibility:check` com PAC team (primitiveIds sao UUIDs do catalogo GEST; resposta tem `eligible`, `urgencia`, `results[]`)
- [ ] documentar SLA do endpoint (read-only, sem transacao de escrita — target < 100ms p99 colocalizados)
- [ ] validar que PAC nao pode bypass: criar Comando de emergencia sem chamar GEST deve ser detectado por ArchUnit ou contrato arquitetural

## Resultado esperado
PAC team tem contrato estavel para consumir `eligibility:check`; GEST garante que o endpoint e idempostente e read-only.

## Criterios de aceite
- [ ] `POST /eligibility:check` com primitivos SUPPORTED retorna `eligible: true` em < 100ms (teste de performance simples)
- [ ] `POST /eligibility:check` e read-only (nenhuma mutacao em BD — verificado por contagem antes/depois)
- [ ] chamadas repetidas com mesmos inputs retornam mesma resposta (idempotente)

## Dependencias
GEST-008 (EligibilityService implementado).

## Estimativa
S

## Prioridade
P2
