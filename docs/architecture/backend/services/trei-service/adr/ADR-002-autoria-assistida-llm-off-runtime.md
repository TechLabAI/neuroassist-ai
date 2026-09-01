<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-002 — Autoria assistida por LLM Claude/Anthropic, fora do runtime

## Status

aceita — materializa ADR-016 (BA/projeto, `accepted`).

## Contexto

A fase de TREINAMENTO usa um LLM para **propor** categorias/frases novas da árvore, em conversa com a
cuidadora e o paciente. O runtime do CONV precisa permanecer retrieval puro (TECH-007) — nenhum LLM no
caminho crítico de comunicação diária do paciente. Precisamos fixar o provedor de LLM e o seu escopo de
uso.

## Decisão

Vamos usar a **API Claude/Anthropic** como o LLM de autoria, invocado **exclusivamente** dentro de
sessões de TREINAMENTO do `trei-service` (uso ocasional, não contínuo — custo baixo e previsível, boa
qualidade de conversa em pt-BR). O LLM **apenas propõe** conteúdo (`SpeechNodeProposed`), nunca publica: a
saída entra no estado `rascunho` e só avança mediante aprovação humana (ver ADR-003). Nenhuma chamada de
LLM ocorre em qualquer caminho de leitura que o CONV possa compartilhar. O contrato concreto (model
id/versão, prompt/system-prompt, orçamento de tokens, controles de custo e a UX de divulgação de IA ao
usuário — exigida pela Use Policy da ElevenLabs) é **deferido para a camada de specs**; esta ADR fixa o
provedor e o escopo, não o modelo.

## Consequências

- **Positivas:** geração fica quarentenada na fase de autoria; runtime imune a alucinação e a
  dependências externas; provedor único e previsível.
- **Negativas / trade-offs:** dependência de API externa (soft — sua ausência bloqueia novas propostas,
  não aprovações/publicações já em curso); necessidade de guarda de sensibilidade no boundary (não enviar
  texto clínico ao LLM — SEC-002); model id/versão ainda em aberto (gap G-D em doc 06).
- **Reversão:** trocar de provedor de LLM é uma troca de adapter (`LlmAuthoringPort`), sem impacto no
  domínio.
