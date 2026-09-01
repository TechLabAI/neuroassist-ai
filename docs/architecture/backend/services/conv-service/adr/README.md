# Architecture Decision Records — CONV (`conv-service`, local scope)

Service-level decisions for the CONV module (Conversação por Árvore de Falas). These **implement**
product decisions already closed at the BA/project level (ADR-012..ADR-018, all `accepted`) and record
their concrete architectural consequence for `conv-service`. They do **not** re-open those decisions.

Numbering is **per scope** — this local sequence (001..) is independent of the shared, project-level ADRs
under `docs/architecture/backend/adr/` (hexagonal, independent microservices, multi-tenancy, edge-device
auth, public API surface), which CONV inherits and references.

## Index

| ADR | Title | Status | Implements (project-level, accepted) |
|---|---|---|---|
| ADR-001 | CONV como serviço novo independente (não extensão de PAC) | aceita | ADR-013 |
| ADR-002 | Busca semântica retrieval-only sobre Elasticsearch, reusando SearchPort/IndexPort | aceita | ADR-015 |
| ADR-003 | Identidade "paciente com sessão" consumida do ACS (CONV é resource server) | aceita | ADR-012 |
| ADR-004 | Playback de áudio cacheado por referência e fallbacks de degradação | aceita | ADR-017 (supersede parcial do ADR-013) |
| ADR-005 | Conteúdo bilíngue por locale com estrutura da árvore invariante | aceita | ADR-018 |

## Relation map
- **ADR-001** fixa o boundary (single independent service, sem tocar PAC) que os demais assumem.
- **ADR-002** (retrieval) e **ADR-004** (áudio) são as duas dependências **soft** do runtime — ambas
  degradam (estático / Web Speech) sem parar a comunicação (OPS-003 / OPS-004).
- **ADR-003** (identidade) é a única dependência **hard** (fail-closed): sem sessão válida do ACS não há
  operação; a revogação imediata reusa a blacklist existente.
- **ADR-005** (bilíngue) atravessa ADR-002 e ADR-004: busca e áudio são resolvidos **por locale ativo**;
  a estrutura da árvore é invariante por idioma.
- Todas referenciam o hexagonal compartilhado como organização interna e o ADR de microserviços
  independentes como postura de composição.

## Process
Vocabulário de status: `proposta` | `aceita` | `substituída por ADR-NNN`. Os ADRs locais aqui nascem
`aceita` porque materializam decisões de produto já fechadas (ADR-012..018). Números são estáveis e nunca
renumerados; uma decisão revogada mantém o número (riscado) para preservar referências cruzadas.

## Open items carried into specs (not decisions — see doc 06 §Gaps)
- Assinatura kNN de `SearchPort` (aceita bloco `knn` ou precisa de método kNN-aware? — extensão aditiva).
- `conv_no_conteudo`: JSON-column vs tabela normalizada (este conjunto propõe normalizada).
- Regra de fallback de locale (nó ativo em PT / pendente em EN com sessão em EN) — TODO[arquiteto] ADR-018.
- Contrato consumível do `engineFeatureRef` (PAC/GEST) — TODO[arquiteto] ADR-013.
- Contrato de consistência CONV↔TREI (quem escreve o índice/áudio e quando).
