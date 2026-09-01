# CONV-FE-025 -- Playwright e2e: falar-frase.feature + isolamento-patient-shell.feature (golden path + zero caregiver-surface leakage)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Outcome
`apps/conv-e2e` contém specs Playwright para os dois cenários de maior risco de regressão: (a) golden path Tela 06 (navegar categoria → selecionar frase → falar áudio cacheado, com paridade de canal gesto/toque); (b) zero leakage de superfície de cuidador a partir do patient shell, incluindo session revoke + camera release.

## Approach (execution path)
1. Criar `apps/conv-e2e/src/falar-frase.spec.ts` transpilando `falar-frase.feature` (3 cenários):
   - Navegar categoria raiz → frase → falar áudio cacheado (toque).
   - Paridade de canal: mesmo `selectedNodeId` por gesto ou toque.
   - Ativação de categoria apenas desce um nível (não chama `/select`).
2. Criar `apps/conv-e2e/src/isolamento-patient-shell.spec.ts` transpilando `isolamento-patient-shell.feature` (2 cenários):
   - Nenhuma rota de cuidador (`/historico`, `/calibracao`, `/onboarding`, `/login`) acessível.
   - Sessão revogada: shell encerrado + câmera liberada (verificar que `MediaStream.getTracks()` está vazia).
3. Usar `MockInputChannelAdapter` (CONV-FE-004) injetado em `apps/neuroassist-ai-web` para simular gestos em e2e.
4. Verificar em CI: `npx nx e2e conv-e2e --project conv`.

## Implementation checklist (by layer/file)
- [ ] implementar `apps/conv-e2e/src/falar-frase.spec.ts`
- [ ] implementar `apps/conv-e2e/src/isolamento-patient-shell.spec.ts`
- [ ] MockInputChannelAdapter disponível para injeção em e2e (CONV-FE-004 / INT-FE-CONV-003)
- [ ] CI: `npx nx e2e conv-e2e` PASS (Playwright headless)
- [ ] test.fixme() com mensagem explícita para quaisquer cenários ainda não buildáveis

## Acceptance criteria
- [ ] `falar-frase.spec.ts` cobre os 3 cenários de `falar-frase.feature`
- [ ] `isolamento-patient-shell.spec.ts` verifica que nenhuma rota de cuidador é navegável
- [ ] Sessão revogada: `MediaStream.getTracks().length === 0` verificado no spec
- [ ] `npx nx e2e conv-e2e` PASS ou `test.fixme()` com mensagem explícita (nunca `describe` vazio)

## Dependencies
- CONV-FE-001 a CONV-FE-019 (todas as features que os cenários testam)
- INT-FE-CONV-003 (MockInputChannelAdapter para simular gestos)
- CONV-FE-028 (G-4 — garantia de que os stubs usam `test.fixme()`)

## Empresa-context impact
Nenhum.

## Prioridade
P9
