# DASH-013 -- Implement PUT /panel/patients/{id}/commands/{cmd}/calibration: write-through to PAC with error relay

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto

O cuidador pode editar a calibracao de um comando diretamente do painel. O DASH age como
proxy: valida o payload, verifica o escopo, encaminha ao PAC (fonte autoritativa de calibracao,
ADR-006), e retorna o resultado do PAC — incluindo a validacao de negocio e o ProblemDetail
em caso de rejeicao. O DASH NAO persiste nenhum valor de calibracao.

## Spec de referencia

- SPEC-01 (PUT /panel/patients/{id}/commands/{cmd}/calibration, 200 echo PAC, 422 bad payload, 404 cmd/patient, relay PAC error)
- SPEC-02 (write-through only: nao persistir localmente, ADR-006)

## Escopo

- [ ] `api/PanelController.updateCalibration(patientId, commandId, @RequestBody CalibrationRequest)`: valida campos obrigatorios (`sensibilidade`, `minGestureMs`, `maxGestureMs`), verifica escopo, chama `CommandCatalogPort.updateCalibration()`
- [ ] `CommandCatalogPort.updateCalibration()` encaminha ao PAC inalterado; retorna resultado do PAC
- [ ] PAC rejeita (422/409/etc.) → relay exato do ProblemDetail do PAC (mesmo status code)
- [ ] `commandId` nao pertence ao `patientId` (PAC responde 404) → retornar `404`
- [ ] Nenhuma escrita local apos chamada ao PAC (write-through puro)

## Resultado esperado

Calibracao submetida pelo cuidador e persistida pelo PAC, com resultado retornado diretamente ao cliente.

## Criterios de aceite

- [ ] `sensibilidade` invalido → `422` antes de chamar PAC
- [ ] `minGestureMs >= maxGestureMs` → `422` antes de chamar PAC
- [ ] PAC aceita → retorna `200` com a calibracao atualizada (echo do PAC)
- [ ] PAC rejeita com `409` → relay `409` com ProblemDetail original
- [ ] `commandId` fora do escopo → `403`

## Dependencias

- DASH-002 (CommandCatalogPort)
- DASH-017 (PacRestAdapter)
- DASH-016 (AcsRestAdapter: scope)

## Estimativa

S

## Prioridade

P4
