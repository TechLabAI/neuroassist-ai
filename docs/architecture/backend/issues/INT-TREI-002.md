# INT-TREI-002 -- INT: TREI -> PAC (patient resolution + engine_feature_ref)

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto

TREI chama PAC ao iniciar uma sessão de autoria: verifica que o `pacienteId` pertence
à `empresa` do caller. A resposta de PAC é usada apenas para validação (404 se ausente).
Ao materializar um nó com `engineFeatureRef`, TREI passa o valor opaco ao PAC via
`VocabularyPort` — TREI nunca parseia o formato interno do ref (gap G4 herdado do CONV).
Autenticação TREI→PAC: `client_credentials` OAuth2 via ACS realm.

## Spec de referencia

- hub-integration.md §2 (PAC: PacPatientPort + VocabularyPort, opaque engineFeatureRef)
- api-contract.md §2.1 (startSession — 404 patient-not-found)
- api-contract.md §2.3 (materializeNode — engineFeatureRef passthrough)

## Escopo

- [ ] confirmar endpoint PAC para resolução de paciente por `(pacienteId, empresa)` — documentar em `integration-contracts.md` TREI se houver divergência com o hub-integration.md
- [ ] configurar `PacPatientAdapter` e `PacVocabularyAdapter` (Feign) com `client_credentials` TREI→PAC
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/pac/PacPatientAdapter.java` — implementa `PacPatientPort`; Feign client para `pac-service`
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/pac/PacVocabularyAdapter.java` — implementa `VocabularyPort`; passthrough opaco do `engineFeatureRef` sem parse
- [ ] criar `src/test/java/com/neuroassist/trei/integration/PacIntegrationTest.java` — WireMock de PAC: paciente encontrado → session criada; paciente não encontrado → 404; `engineFeatureRef` passado sem modificação

## Resultado esperado

`SessaoTreinamentoService.startSession` valida paciente via PAC real; 404 em paciente
inexistente; `engineFeatureRef` tratado como string opaca.

## Criterios de aceite

- [ ] `./gradlew :trei-service:test --tests "*PacIntegrationTest*"` sai 0
- [ ] Teste verifica que `pacienteId` de empresa diferente → 404 `patient-not-found`
- [ ] `PacVocabularyAdapter` NÃO parseia o conteúdo de `engineFeatureRef` (apenas passthrough)

## Dependencias

- TREI-005 (SessaoTreinamentoService — usa PacPatientPort)
- TREI-007 (NoAutoriaService — usa VocabularyPort para engineFeatureRef)

## Estimativa

M

## Prioridade

P4
