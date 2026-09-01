# CONV-FE-019 -- convNavegarGuard — conv.arvore.navegar scope check, forbidden → árvore forbidden state (never caregiver route)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Outcome
`convNavegarGuard` verifica que o JWT do paciente contém o scope `conv.arvore.navegar` antes de renderizar qualquer rota da árvore; em ausência do scope, redireciona para o estado `forbidden` da árvore (nunca expõe rota de cuidador).

## Approach (execution path)
1. Criar `conv-navegar.guard.ts` como `CanActivateFn`.
2. Verificar que o JWT (via `@neuroassist/shared/access`) contém `conv.arvore.navegar` nos scopes.
3. Se scope ausente: redirecionar para `/paciente/arvore?forbidden=true` (estado forbidden de `SpeechTreePageComponent` — CONV-FE-021) — nunca para uma rota de cuidador.
4. Notar que `conv.no.selecionar` NÃO é verificado neste guard — gates a ação de seleção dentro do `SpeechTreePageComponent` (não bloqueia a navegação).

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/access/src/lib/conv-navegar.guard.ts`
- [ ] teste: JWT sem `conv.arvore.navegar` → redireciona para forbidden state (não para `/login`, não para caregiver)
- [ ] teste: `conv.no.selecionar` ausente NÃO bloqueia a navegação (guard passa)
- [ ] `nx lint conv-access` PASS

## Acceptance criteria
- [ ] Guard redireciona para forbidden state dentro do patient shell — nunca expõe caregiver route
- [ ] Scope `conv.no.selecionar` não é verificado no guard de navegação
- [ ] `npx nx test conv-access` PASS

## Dependencies
- CONV-FE-018 (patientSessionGuard — rodado antes deste guard na cadeia de guards)
- CONV-FE-021 (forbidden state da speech tree)
- INT-FE-CONV-001 (shared/access — leitura de scopes do JWT)

## Empresa-context impact
Nenhum.

## Prioridade
P5
