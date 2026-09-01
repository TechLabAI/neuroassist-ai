# Architecture Decision Records — TREI (`trei-service`, local scope)

Service-level decisions for the TREI module (Treinamento Assistido por IA — authoring plane of the speech
tree). These **implement** product decisions already closed at the BA/project level (ADR-015, ADR-016,
ADR-017, ADR-018, and ADR-012 for identity — all `accepted`) and record their concrete architectural
consequence for `trei-service`. They do **not** re-open those decisions.

Numbering is **per scope** — this local sequence (001..) is independent of the shared, project-level ADRs
under `docs/architecture/backend/adr/` (hexagonal architecture, independent microservices, multi-tenancy,
edge-device auth, public API surface), which TREI inherits and references by their resolved file names.

## Index

| ADR | Title | Status | Implements (project-level, accepted) |
|---|---|---|---|
| ADR-001 | TREI como serviço novo independente (autoria fora do runtime CONV) | aceita | ADR-016 |
| ADR-002 | Autoria assistida por LLM Claude/Anthropic, fora do runtime | aceita | ADR-016 |
| ADR-003 | Dupla autorização assíncrona como gate de publicação | aceita | ADR-016 (RBAC) |
| ADR-004 | Pipeline de publicação: TREI escreve embedding, TTS cacheado e index arvore-fala | aceita | ADR-015 (index side), ADR-017 |
| ADR-005 | Autoria bilíngue independente por locale | aceita | ADR-018 |

## Relation map

- **ADR-001** fixa o boundary (serviço novo independente, único escritor do índice/áudio) que os demais
  assumem.
- **ADR-002** (LLM propõe) alimenta o estado `rascunho`; nada publica sem passar por **ADR-003**.
- **ADR-003** (dupla autorização) é o gate de domínio: só com `confirmadoCuidadora AND confirmadoPaciente`
  o pipeline de **ADR-004** dispara.
- **ADR-004** (pipeline embedding + TTS + index) é o lado de escrita que o CONV lê; carrega o invariante
  **G5** de coerência de `model_id` com o CONV.
- **ADR-005** (bilíngue) atravessa ADR-002/003/004: proposta, aprovação, embedding e áudio são resolvidos
  **por locale**; a estrutura da árvore é invariante por idioma.
- Todas referenciam o hexagonal compartilhado (`ADR-001-hexagonal-architecture`) como organização interna
  e o ADR de microserviços independentes (`ADR-002-independent-microservices`) como postura de composição;
  a identidade dupla (cuidadora/paciente) reusa o ACS (ADR-012) sem duplicá-la.

## Process

Vocabulário de status: `proposta` | `aceita` | `substituída por ADR-NNN`. Os ADRs locais aqui nascem
`aceita` porque materializam decisões de produto já fechadas (ADR-012/015/016/017/018). Números são
estáveis e nunca renumerados; uma decisão revogada mantém o número (riscado) para preservar referências
cruzadas.

## Open items carried into specs (not decisions — see doc 06 §Gaps)

- Granularidade dos flags de aprovação: por-nó vs por-locale (ADR-005 / ADR-018 sugerem por-locale) — G-A.
- Contrato do documento `arvore-fala` (campos, dims do dense_vector, valores de `status`/`tipo`, formato
  de `audio_ref`), **co-owned com o CONV** — G-B.
- Binding de `engineFeatureRef` para frases autoradas (PAC/GEST, ADR-013) — G-C.
- Contrato concreto do LLM (model id/versão, prompt, orçamento, UX de divulgação de IA) — G-D.
- Detalhes do outbox/sub-estado `publicando` (retry/backoff, idempotência embed→tts→index) — G-E.
- Janelas de retenção LGPD e cascata de apagamento (PostgreSQL + ES + áudio) — G-F.
