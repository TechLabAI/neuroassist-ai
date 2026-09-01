# CONV-FE-020 -- PatientBreadcrumbComponent + tree-level breadcrumb state (libs/conv/navigation — patient paths only)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Outcome
`libs/conv/navigation` fornece `PatientBreadcrumbComponent` e `BreadcrumbStateService` que mantêm o estado de navegação `root → active category` — nunca expondo um path de cuidador, nunca expondo um nível além da categoria ativa.

## Approach (execution path)
1. Criar `BreadcrumbStateService` (`@Injectable({ providedIn: 'root' })`):
   - Signal `items: BreadcrumbItem[]` derivado de `SpeechTreeVM.path`.
   - `BreadcrumbItem { nodeId: string; label: string }`.
   - Somente paths dentro do patient shell são representados — nenhum link para `/historico`, `/calibracao` etc.
2. Criar `PatientBreadcrumbComponent` (presentacional, standalone, OnPush):
   - Input `items: BreadcrumbItem[]`.
   - Renderiza `root (Falar) › «categoria ativa»` — sem nível além da categoria.
   - Cada item clicável navega para `/paciente/arvore/:categoriaId` ou `/paciente/arvore` (root).
3. Exportar pelo `src/index.ts`.

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/navigation/src/lib/breadcrumb-state.service.ts`
- [ ] implementar `libs/conv/navigation/src/lib/patient-breadcrumb.component.ts`
- [ ] implementar `libs/conv/navigation/src/index.ts`
- [ ] teste: nenhum link de breadcrumb aponta para rota fora do patient shell

## Acceptance criteria
- [ ] Breadcrumb nunca exibe path além de `root → active category`
- [ ] Clique em item de breadcrumb navega para a rota correta dentro do patient shell
- [ ] `npx nx test conv-navigation` PASS
- [ ] `npx nx lint conv-navigation` PASS

## Dependencies
- CONV-FE-006 (SpeechTreeVM.path como fonte do estado de breadcrumb)
- CONV-FE-003 (PatientTopbarComponent — renderiza o PatientBreadcrumbComponent)

## Empresa-context impact
Nenhum.

## Prioridade
P6
