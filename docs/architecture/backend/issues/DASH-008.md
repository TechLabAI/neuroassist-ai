# DASH-008 -- Implement GET /panel/patients/{patientId}/overview: per-patient composition (PAC + EDGE)

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto

A tela de overview por paciente exibe o catalogo de comandos com calibracao atual (PAC) e o
status do dispositivo (EDGE). PAC e hard-dependency para esta tela (sem catalogo a tela nao tem
significado). EDGE soft-degrada. O DASH valida que o `patientId` esta dentro do escopo do
caregiver autenticado antes de chamar os upstreams.

## Spec de referencia

- SPEC-01 (GET /panel/patients/{patientId}/overview, 200 PatientOverview, 403 out-of-scope, 503 PAC down)
- SPEC-02 (composicao: PAC = catalogo + calibracao, EDGE = liveness)

## Escopo

- [ ] `application/PanelCompositionService.composePatientOverview(caregiverId, patientId)`: resolve escopo (IdentityDirectoryPort), valida que `patientId` esta no escopo → 403 caso contrario; chama CommandCatalogPort (catalogo + calibracao) e DeviceLivenessPort em paralelo
- [ ] PAC indisponivel → `503 upstream-unavailable` (nao ha degrade util sem catalogo)
- [ ] EDGE indisponivel → `device.status = "unknown"`, `device.degraded = true` (soft-degrade)
- [ ] `api/PanelController.getPatientOverview(patientId)`: valida scope, delega ao service, retorna `PatientOverview`

## Resultado esperado

`GET /panel/patients/{id}/overview` retorna catalogo de comandos + status de dispositivo.
PAC down → 503. EDGE down → device degradado, corpo valido.

## Criterios de aceite

- [ ] `patientId` fora do escopo do JWT → `403 Forbidden` (sem revelar existencia do paciente)
- [ ] PAC indisponivel → `503` com ProblemDetail `upstream-unavailable`
- [ ] EDGE indisponivel → `device.degraded = true`, status = "unknown", retorno `200`
- [ ] `commands[]` retorna label, urgencia, sensibilidade, minGestureMs, maxGestureMs de cada comando

## Dependencias

- DASH-002 (ports)
- DASH-003 (PatientOverview, DeviceStatus)
- DASH-016 (AcsRestAdapter: scope)
- DASH-017 (PacRestAdapter)

## Estimativa

M

## Prioridade

P3
