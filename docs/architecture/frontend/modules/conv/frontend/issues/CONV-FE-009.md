# CONV-FE-009 -- BindingPort + Http/MockAdapter (POST /bindings/resolve, opaque engineFeatureRef, bound:false = no-op, SPEC-04 §3)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Outcome
`BindingPort` e seus adapters implementam `POST /api/v1/conv/bindings/resolve`, passando `engineFeatureRef` como string opaca para o backend (nunca parseada no FE) e tratando `bound: false` como no-op sem nenhuma superfície de erro.

## Approach (execution path)
1. Definir `BindingPort` com operação `resolve(engineFeatureRef: string, locale: Locale): Observable<BindingResponseDto>`.
2. Implementar `BindingHttpAdapter`: `POST /api/v1/conv/bindings/resolve` com body `{ engineFeatureRef, locale }`; `engineFeatureRef` passado opaco (string), nunca inspecionado.
3. Implementar `BindingMockAdapter`: retornar cenários `bound: true` (com `noId` e `selecionavel: true`) e `bound: false`.
4. `ConvShellIntegrationService` (CONV-FE-005) consome `BindingPort`: se `bound: false` → no-op silencioso; se `bound: true && selecionavel: true` → chamar `SpeechTreeFacade.selectNode(noId, 'gesture')`.

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/data-access/src/lib/binding.port.ts`
- [ ] implementar `libs/conv/data-access/src/lib/binding.http-adapter.ts`
- [ ] implementar `libs/conv/data-access/src/lib/binding.mock-adapter.ts`
- [ ] teste: `bound: false` não dispara `SpeechTreeFacade.selectNode`

## Acceptance criteria
- [ ] `engineFeatureRef` é passado como string opaca — nenhuma inspeção do conteúdo no FE
- [ ] `bound: false` é silencioso — nenhum toast, nenhum log de erro
- [ ] `bound: true && selecionavel: true` → `SpeechTreeFacade.selectNode(noId)` é chamado
- [ ] `npx nx test conv-data-access` PASS

## Dependencies
- CONV-FE-012 (BindingRequestDto, BindingResponseDto, DATA_PROVIDERS)
- CONV-FE-005 (ConvShellIntegrationService — consumidor do BindingPort)

## Empresa-context impact
Nenhum.

## Prioridade
P2
