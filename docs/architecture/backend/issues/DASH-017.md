# DASH-017 -- Implement PacRestAdapter (CommandCatalogPort): catalog read + calibration write-through forwarding

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto

O `CommandCatalogPort` consome o PAC para (1) leitura do catalogo de comandos por paciente e
calibracao atual e (2) write-through de edits de calibracao. O adapter e puro proxy: nao
transforma a logica de negocio, apenas converte os tipos de dominio em chamadas HTTP e volta.
PAC e hard-dependency para overview e calibracao.

## Spec de referencia

- SPEC-10 (`CommandCatalogPort` upstream: `pac-service`, `PacRestAdapter`)

## Escopo

- [ ] `infrastructure/adapter/PacRestAdapter implements CommandCatalogPort` usando Feign (`PacClient`)
- [ ] `getCommandCatalog(patientId)`: `GET /patients/{patientId}/commands` no PAC → lista de `CommandView` (label, urgencia, sensibilidade, minGestureMs, maxGestureMs)
- [ ] `updateCalibration(patientId, commandId, CalibrationRequest)`: `PUT /patients/{patientId}/commands/{commandId}/calibration` → retorna o resultado do PAC (autoritative)
- [ ] PAC indisponivel ou retorna 5xx → throw `UpstreamUnavailableException`; PAC retorna 4xx (validacao/conflito) → throw `UpstreamRejectedException` preservando status e ProblemDetail original

## Resultado esperado

Catalogo de comandos e calibracao servidos diretamente do PAC; erros do PAC repassados fiel ao caller.

## Criterios de aceite

- [ ] `getCommandCatalog` retorna lista correta de comandos do PAC
- [ ] `updateCalibration` encaminha payload identico ao PAC
- [ ] PAC retorna `422` → `UpstreamRejectedException` com status `422` e body original
- [ ] PAC indisponivel → `UpstreamUnavailableException`
- [ ] WireMock test cobrindo: catalog ok, calibration ok, PAC rejeita, PAC down

## Dependencias

- DASH-001 (infraestrutura)
- DASH-002 (CommandCatalogPort interface)

## Estimativa

S

## Prioridade

P5
