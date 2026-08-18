# INT-DASH-003 -- DASH -> PAC calibration write-through: forward edit + relay PAC ProblemDetail on rejection

## Contexto

O DASH encaminha edits de calibracao ao PAC via write-through. Este issue valida o contrato de
chamada DASH→PAC: payload encaminhado sem transformacao, resposta autoritativa do PAC repassada
ao cuidador (incluindo ProblemDetail em caso de rejeicao). Nenhum valor de calibracao armazenado
localmente pelo DASH.

## Spec de referencia

- SPEC-10 (`CommandCatalogPort.updateCalibration`, upstream: `pac-service`)
- `contracts/integration-contracts.md §2` (PAC write-through: relay ProblemDetail)

## Escopo

- WireMock stub PAC aceitando calibracao (200 com calibracao atualizada) → verificar que DASH retorna 200 com body do PAC inalterado
- WireMock stub PAC rejeitando com 422 + ProblemDetail → verificar que DASH retorna 422 com o mesmo ProblemDetail (sem wrap)
- WireMock stub PAC down → DASH retorna 503 `upstream-unavailable`
- Verificar que DASH nao armazenou nenhum valor de calibracao localmente (nenhuma escrita em memoria ou banco)

## Resultado esperado

Calibracao editada persiste no PAC; o relay do DASH e transparente (body e status identicos).

## Criterios de aceite

- [ ] PAC aceita → DASH retorna `200` com body do PAC (echo fiel)
- [ ] PAC retorna `422` → DASH retorna `422` com ProblemDetail original (nao 503, nao 500)
- [ ] PAC down → DASH retorna `503`
- [ ] DASH nao persiste nenhum valor de calibracao (verificar ausencia de escrita em qualquer store)

## Dependencias

- DASH-013 (PUT calibration endpoint)
- DASH-017 (PacRestAdapter)

## Estimativa

S

## Prioridade

P4
