# TREI-010 -- LlmAuthoringPort adapter — AnthropicAdapter

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto

Implementa `LlmAuthoringPort` usando o SDK Anthropic (Java). A chamada é
request-scoped à sessão ativa: o `sessaoId` e `llmModelId` da sessão acompanham o
prompt enviado. O system prompt constrainge o LLM a propor texto de necessidades
cotidianas (nunca clínico). `ANTHROPIC_MODEL_ID` é configurável via env e registrado
na sessão para rastreabilidade (TECH-007). Falha → `LlmUnavailableException` (503);
nenhuma linha parcial de `trei_proposta_llm` é persistida (garantido em TREI-005).

## Spec de referencia

- SPEC-02 (authoring-orchestration — chamada ao LLM, persist antes de retornar)
- integration-contracts.md §1 (Anthropic API: session-scoped, model recorded, SEC-002 PHI)

## Escopo

- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/llm/AnthropicAdapter.java` — implementa `LlmAuthoringPort`; usa Anthropic SDK; system prompt força registro de necessidades cotidianas; extrai candidatos da resposta como `List<PropostaLlm>`
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/llm/AnthropicConfig.java` — lê `ANTHROPIC_API_KEY` e `ANTHROPIC_MODEL_ID` do environment; injeta como beans
- [ ] sistema de prompt: constraints de register (cotidiano, não clínico); instrução de formato (JSON ou structured output para parse determinístico); locale derivado da sessão
- [ ] se `ANTHROPIC_API_KEY` ausente ou Anthropic inacessível → `LlmUnavailableException` (sem retry aqui — retry é responsabilidade do caller ou do usuário tentar de novo)
- [ ] criar `src/test/java/com/neuroassist/trei/infrastructure/llm/AnthropicAdapterTest.java` — mock do Anthropic SDK client para verificar: chamada scoped à sessão, candidatos extraídos corretamente, exceção lançada em falha

## Resultado esperado

Adapter funcional com o Anthropic SDK; propostas retornadas como lista de candidatos;
falha de rede → `LlmUnavailableException` limpa.

## Criterios de aceite

- [ ] `./gradlew :trei-service:test --tests "*AnthropicAdapterTest*"` sai 0
- [ ] Teste verifica que `ANTHROPIC_MODEL_ID` é lido do env e passado no request (rastreabilidade TECH-007)
- [ ] Teste verifica que falha de conexão → `LlmUnavailableException` (não NullPointerException ou genérico)
- [ ] Nenhum texto clínico/diagnóstico está hardcoded no system prompt (inspecionar manualmente)

## Dependencias

- TREI-003 (LlmAuthoringPort interface)

## Estimativa

M

## Prioridade

P4
