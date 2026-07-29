# INT-MIG-003 -- Contrato do chamador sincrono ACS (contract test do endpoint de provisioning)

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto
Cobrir com um contract test o contrato sincrono que ACS consome para provisionar um tenant, validando request/response/erros descritos em api-contract.md §1.1 e hub-integration.md §5.

## Spec de referencia
- SPEC-03, SPEC-10

## Escopo
- [ ] implementar `src/test/java/AcsProvisioningContractTest.java`
- [ ] contract test (WireMock ou equivalente) do lado provedor

## Criterios de aceite
- [ ] o contract test cobre o caminho de sucesso (provisionado) e o de erro (`invalid-tenant-id`)
- [ ] o contrato testado corresponde exatamente ao descrito em api-contract.md §1.1

## Dependencias
Depende de MIG-002, MIG-010.

## Estimativa
M

## Prioridade
P4
