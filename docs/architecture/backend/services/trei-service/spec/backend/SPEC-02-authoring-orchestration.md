# SPEC-02 — Authoring Session Orchestration (LLM proposals)

> EARS acceptance criteria (D-057). Contracts:
> [`../../contracts/integration-contracts.md`](../../contracts/integration-contracts.md) §1,
> [`../../contracts/api-contract.md`](../../contracts/api-contract.md) §2.1–2.3.

## Objetivo

Orquestrar a sessão de autoria assistida por LLM: a cuidadora inicia a sessão, conduz turnos de proposta
com a Claude/Anthropic, e materializa propostas em nós `rascunho`. A LLM **apenas propõe** — nunca publica
(ADR-016, TECH-007).

## Escopo

- **In:** `ConduzirSessaoTreinamento`, `RegistrarPropostaLlm`, `MaterializarProposta`; persistência de
  `trei_sessao_treinamento` e `trei_proposta_llm`; disclosure de IA.
- **Out:** aprovação/gate (SPEC-03), pipeline de publicação (SPEC-12).

## Decisões técnicas

- Chamada Anthropic via `LlmAuthoringPort`, request-scoped à sessão; `ANTHROPIC_MODEL_ID` gravado na
  sessão (traceabilidade, SEC-002).
- Prompt/response guardados **por referência** (`prompt_ref`, `response_ref`); nunca inline.
- Registro obrigatório: cada candidato vira `trei_proposta_llm` **antes** de retornar ao humano.

## Implementação

- `RegistrarPropostaLlm`: monta o prompt (system prompt restringe a proposta a vocabulário de necessidade
  cotidiana no locale da sessão), chama a porta, persiste candidatos, retorna com `aiDisclosed: true`.
- `MaterializarProposta`: cria/localiza o nó em `conv_no_arvore_fala`, cria `conv_no_conteudo`
  (`status='pendente'`, flags `false`) e `trei_conteudo_autoria` (`sub_estado='rascunho'`); marca a
  proposta `materializada`.

## Casos de borda

- Anthropic indisponível → `503 llm-unavailable`, sem proposta parcial.
- Texto clínico na saída da LLM → flag + exigir edição para o registro cotidiano (SEC-002; PHI ADR-017).
- Materializar o mesmo nó em 2º locale → reusa `noId`, adiciona só a linha de conteúdo do locale (ADR-018).

## Critérios de aceite (EARS)

- WHEN a proposal turn runs, THE SYSTEM SHALL call `LlmAuthoringPort` scoped to the session and SHALL
  persist every candidate as `trei_proposta_llm` before returning it.
- THE SYSTEM SHALL NEVER embed, synthesize, index, or publish LLM output at proposal time.
- THE SYSTEM SHALL record the concrete `ANTHROPIC_MODEL_ID` on the session.
- IF the Anthropic API is unreachable, THEN THE SYSTEM SHALL fail with `503` and persist no partial proposal.
- WHEN a draft is materialized, THE SYSTEM SHALL create the content row with `status='pendente'` and both
  approval flags `false`.
