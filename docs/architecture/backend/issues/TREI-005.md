# TREI-005 -- SessaoTreinamentoService — startSession + requestProposal

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/8 itens

## Contexto

Implementa o golden path de autoria assistida: (1) caregiver inicia sessão LLM para um
paciente da sua `empresa` → TREI resolve via PAC, cria `trei_sessao_treinamento` e
registra o `llmModelId` Anthropic usado; (2) caregiver envia instrução → TREI chama
Anthropic via `LlmAuthoringPort` scoped à sessão ativa, persiste CADA candidato em
`trei_proposta_llm` ANTES de retornar ao caller, e sinaliza `aiDisclosed: true`.
Uma sessão tem `localeAutoria` fixo (ADR-018). Falha na API Anthropic → 503, sem
proposta parcial persistida.

## Spec de referencia

- SPEC-02 (authoring-orchestration — fluxo completo startSession + requestProposal)
- SPEC-06 (security — scope treinamento.sessao.conduzir + treinamento.no.propor)
- integration-contracts.md §1 (Anthropic API: sessão-scoped, ANTHROPIC_MODEL_ID recorded)

## Escopo

- [ ] criar `src/main/java/com/neuroassist/trei/application/service/SessaoTreinamentoService.java` — interface com `startSession` + `requestProposal`
- [ ] criar `src/main/java/com/neuroassist/trei/application/service/SessaoTreinamentoServiceImpl.java` — implementação usando `PacPatientPort` para resolução do paciente, `LlmAuthoringPort` para proposta, `TreiSessaoTreinamentoRepository` para persistência
- [ ] `startSession`: validar `pacienteId` via `PacPatientPort.resolvePatient` → 404 se ausente; criar `TreiSessaoTreinamento` com `localeAutoria`, `llmModelId = ANTHROPIC_MODEL_ID` configurado, estado `ativa`
- [ ] `requestProposal`: verificar sessão ativa; chamar `LlmAuthoringPort.propose` com a instrução; persistir cada candidato como `TreiPropostaLlm(estado=PROPOSTA)` em transação antes de retornar; retornar lista + `aiDisclosed=true`
- [ ] se `LlmAuthoringPort` lança exceção → propagar como `LlmUnavailableException` (mapeada para 503) sem deixar linha `trei_proposta_llm` parcial
- [ ] flag `aiDisclosed` sempre `true` nas respostas (ElevenLabs Use Policy, ADR-017)
- [ ] `@Transactional` somente na camada `application.service` (ArchUnit RULE-03)
- [ ] criar `src/test/java/com/neuroassist/trei/application/service/SessaoTreinamentoServiceTest.java` — mocks via Mockito: PAC retorna paciente, Anthropic retorna propostas, verificar que `TreiPropostaLlm` foi persistido antes de retornar

## Resultado esperado

`startSession` cria sessão ativa com llmModelId; `requestProposal` persiste propostas e
retorna candidatos com `aiDisclosed=true`; falha Anthropic deixa nenhuma linha parcial.

## Criterios de aceite

- [ ] `./gradlew :trei-service:test --tests "*SessaoTreinamentoServiceTest*"` sai 0
- [ ] Teste verifica que `TreiPropostaLlmRepository.save` é chamado antes de `requestProposal` retornar (ordem de operações)
- [ ] Teste verifica que `LlmUnavailableException` é lançada (sem save de proposta) quando `LlmAuthoringPort` falha
- [ ] Nenhum import de classe de `infrastructure.*` em `SessaoTreinamentoServiceImpl`

## Dependencias

- TREI-001 (entidades)
- TREI-003 (ports)
- TREI-008 (repositories — pode ser feito em paralelo, mas testes precisam dos stubs)

## Estimativa

L

## Prioridade

P2
