# INT-CONV-004 -- Decisão de domínio — endpoints de Proposals (Tela 09) pertencem a TREI, não a CONV: FE ProposalPort aponta para trei-service

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Contexto

A specs-validate do FE identificou que os endpoints de Proposals (Tela 09 — fluxo de confirmação
de treinamento: list-pending/confirm/adjust/reject) estão **ausentes do api-contract v1 do CONV**
e o FE usa `ProposalPort` com `MockAdapter` temporariamente.

**Análise de domínio:**
- `list-pending/confirm/adjust/reject` são operações sobre **propostas de conteúdo autorizadas pelo
  LLM** (autoria assistida por IA)
- O ator que confirma é a cuidadora/paciente — autoridade de **TREI**, não do runtime de comunicação
- O CONV é estritamente o runtime de comunicação (paciente fala); TREI é a fase de treinamento/autoria
- **Decisão:** estes endpoints pertencem semanticamente ao **TREI** e serão especificados no
  `trei-service` api-contract

**Impacto:** CONV não recebe novos endpoints. FE deve redirecionar `ProposalPort` para
`trei-service` quando o backlog do TREI gerar os contratos correspondentes.

## Spec de referencia

- SPEC-01 (api-surface — ausência de endpoints de proposals no CONV)
- `contracts/api-contract.md` (sem referência a proposals — correto)

## Escopo

- [ ] Documentar a decisão em `contracts/api-contract.md` §7 (se não existir, adicionar §7 "Out of Scope"): "Endpoints de Proposals (list-pending/confirm/adjust/reject) são responsabilidade do `trei-service` — veja `TREI api-contract` quando disponível"
- [ ] Notificar o backlog do FE CONV: `ProposalPort` deve permanecer em `MockAdapter` até que o TREI backlog gere o contrato de Proposals
- [ ] Criar card de dependência FE→TREI no issues do FE (INT-CONV-004 é a origem da decisão)

## Resultado esperado

Decisão documentada; CONV api-contract sem endpoints de proposals; FE ciente de que ProposalPort depende do TREI.

## Criterios de aceite

- [ ] `contracts/api-contract.md` §7 (ou seção "Out of Scope") menciona explicitamente que Proposals → TREI
- [ ] Nenhuma issue do CONV backlog (CONV-001..CONV-022) adiciona endpoints de proposals ao conv-service
- [ ] FE CONV backlog rastreia `ProposalPort MockAdapter` como bloqueio dependente do TREI

## Dependencias

Nenhuma.

## Estimativa

S

## Prioridade

P2
