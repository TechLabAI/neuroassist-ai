# CONV-INT-002 -- shared/error-model ProblemDetail → AppError mapping (all CONV BE error codes, patient-safe copy, never raw HTTP status)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Outcome
O `ConvErrorMapper` (CONV-FE-023) estende (ou implementa) o contrato de `@neuroassist/shared/error-model` para os 9 códigos BE do CONV (api-contract §3), garantindo que nenhum `ProblemDetail` cru chegue à superfície do paciente e que cada código gere o `AppError` correto com a i18n key e a surface (`inline | toast | banner | shell-exit`).

## Approach (execution path)
1. Verificar o contrato de `AppError` em `@neuroassist/shared/error-model`: como é estruturado, quais campos aceita (`code`, `message`, `surface`, `i18nKey`, ...).
2. Implementar o mapeamento em `conv-error-mapper.ts` (CONV-FE-023) seguindo o contrato de `shared/error-model`.
3. Registrar o mapper como provider no `DATA_PROVIDERS` ou no interceptor do CONV.
4. Testar todos os 9 códigos de SPEC-07 §1 com mocks de `ProblemDetail`.

## Implementation checklist (by layer/file)
- [ ] `conv-error-mapper.ts` usa os tipos de `@neuroassist/shared/error-model` (não redefine)
- [ ] todos os 9 BE type suffixes de SPEC-07 §1 têm um mapping testado
- [ ] `npx nx test conv-data-access` PASS (cobertura de todos os códigos)

## Acceptance criteria
- [ ] Nenhum campo de `ProblemDetail` (title, detail, status) exposto diretamente na UI
- [ ] `retrieval-unavailable` → sem AppError criado (degradação silenciosa)
- [ ] `npx nx lint conv-data-access` PASS

## Dependencies
- `@neuroassist/shared/error-model` (tipos de AppError)
- CONV-FE-023 (conv-error-mapper.ts — implementação)
- CONV-INT-001 (interceptor — captura erros HTTP e delega ao mapper)

## Empresa-context impact
Nenhum.

## Prioridade
P4
