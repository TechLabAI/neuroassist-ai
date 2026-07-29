# INT-ACS-005 -- EVNT CaregiverRoutingClient: validar consumo do directory endpoint de routing

**Status:** INTACT -- gerado 2026-07-29
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Contexto

EVNT consome `GET /api/v1/acs/directory/patients/{pid}/caregivers` para rotear alarmes.
Esta issue valida a integração end-to-end: EVNT chama ACS directory, ACS retorna routing
targets corretos, EVNT trata `responsibleCaregivers: []` como routing-failure alert.

## Spec de referência

- SPEC-10 §3.2 (directory surface — EVNT routing targets)
- `contracts/api-contract.md §6.2`

## Escopo

- [ ] confirmar que `evnt-service` tem `CaregiverRoutingClient` apontando para
      `GET /api/v1/acs/directory/patients/{pid}/caregivers` (verificar `evnt-service` contracts)
- [ ] smoke test integração: provisionar tenant + cuidador + vínculo + chamar EVNT endpoint
      que aciona routing — verificar que o cuidador aparece como routing target
- [ ] validar que EVNT trata array vazio como routing-failure (não dropa o alarme silenciosamente)
- [ ] garantir que o header `X-Empresa-Id` é propagado na chamada EVNT → ACS

## Critérios de aceite

- [ ] Cuidador vinculado ao paciente → aparece em `responsibleCaregivers` ao criar evento EVNT
- [ ] Array vazio → EVNT registra routing-failure (não descarta o evento)
- [ ] Revocação de link → cuidador ausente no próximo lookup

## Dependências

- ACS-018 (DirectoryController)
- EVNT M5 scaffold

## Estimativa

M

## Prioridade

P5
