# CONV-007 -- Criar BindingResolutionService — resolveBinding (POST /bindings/resolve, engineFeatureRef opaco)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto

Resolve um gesto (`engineFeatureRef`) para o nó da árvore vinculado, permitindo que o canal de
gesto (GEST) seja tratado como seleção. O `engineFeatureRef` é tratado como **string opaca**
— CONV nunca parseia sua estrutura interna (ADR-013, gap G4). A resolução ocorre em dois passos:
(1) verificar se algum `conv_no_arvore_fala` do paciente tem `engine_feature_ref` matching;
(2) se não encontrado localmente, delegar ao `VocabularyPort` (PAC/GEST via Feign). Se nem um nem
outro resolver → `bound: false` (não é erro). Dependência soft: falha do VocabularyPort não falha
a navegação/toque.

## Spec de referencia

- SPEC-01 (api-surface — endpoint binding)
- SPEC-10 (integrations — VocabularyPort)
- `contracts/api-contract.md` §2.4
- `contracts/hub-integration.md` §3

## Escopo

- [ ] Criar `application/service/BindingResolutionService.java` com operação `resolveBinding(String engineFeatureRef, UUID pacienteId, String locale): BindingResponse`
- [ ] Passo 1: buscar em `conv_no_arvore_fala` da árvore do paciente onde `engine_feature_ref = :ref` (índice `ix_conv_no_engine_ref` do DDL)
- [ ] Passo 2 (se não encontrado localmente): chamar `VocabularyPort.resolveEngineFeatureRef(ref, pacienteId)`; capturar qualquer exceção de infra e retornar `bound: false`
- [ ] Resultado: se nó encontrado → `{engineFeatureRef, noId, bound: true, selecionavel: tipo==FRASE && conteudo.status==ATIVO}`; se não → `{engineFeatureRef, noId: null, bound: false}`
- [ ] Criar endpoint `POST /conv/bindings/resolve` em `ConvBindingController.java` (scope `conv.arvore.navegar`)

## Resultado esperado

`POST /api/v1/conv/bindings/resolve` retorna `bound: true` + `noId` quando ref resolúvel; `bound: false` quando desconhecido ou VocabularyPort indisponível; nunca falha com 5xx para ref desconhecido.

## Criterios de aceite

- [ ] `./gradlew :conv-service:test` BUILD SUCCESS
- [ ] Teste unitário: `engineFeatureRef` presente no `conv_no_arvore_fala` → `bound: true`, `noId` correto
- [ ] Teste unitário: ref desconhecido localmente, `VocabularyPort` retorna `Optional.empty()` → `bound: false`, `noId: null`
- [ ] Teste unitário: `VocabularyPort` lança exceção de infra → `bound: false`, `200` retornado (não 500)
- [ ] `engineFeatureRef` NUNCA é parseado (sem `split(":")`, sem regex de estrutura interna — verificável por code review)

## Dependencias

- CONV-004 (ports — VocabularyPort)
- CONV-005 (precede — reutiliza lógica de resolução de árvore)

## Estimativa

S

## Prioridade

P2
