# CONV-FE-028 -- G-4 scaffold spec: fe-nx-register-module.py MUST emit test.fixme() with explicit message for all 14 CONV e2e scenarios (not empty describe)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Outcome
O scaffold de `apps/conv-e2e` gerado por `fe-nx-register-module.py` (ou pelo promote) emite `test.fixme('Scaffold pending — implement after CONV-FE-NNN', ...)` com mensagem explícita para cada um dos 14 cenários e2e do CONV, em vez de `describe('', () => {})` vazio — que produziria suites Playwright falsamente verdes.

## Approach (execution path)
1. Identificar os 14 cenários e2e de CONV (5 feature files × média de ~3 cenários: `falar-frase` 3, `fallback-voz` 3, `assist-e-degradacao` 2, `isolamento-patient-shell` 2, `confirmar-proposta` 3, + G-3 locale-switch 1 = 14 cenários).
2. Verificar que `scripts/fe-nx-register-module.py` (modo `--specs-only` ou padrão) gera para cada cenário:
   ```ts
   test.fixme('Scaffold pending — implement CONV-FE-025: falar-frase golden path', async ({ page }) => { /* TODO */ });
   ```
   e nunca `describe('feature', () => {})` vazio.
3. Se o gerador ainda não suporta `test.fixme()` com mensagem: registrar o déficit como aprendizado e criar os stubs manualmente com `test.fixme()` + mensagem explícita referenciando a issue CONV-FE-NNN correspondente.
4. Registrar o pitfall no Learning Layer (sinal `rework_pattern` — correlaciona com o déficit D-603 já indexado).

## Implementation checklist (by layer/file)
- [ ] implementar `apps/conv-e2e/src/conv-scaffold-register.spec.ts` (stub verificador)
- [ ] verificar que TODOS os `.spec.ts` do `apps/conv-e2e` têm `test.fixme()` com mensagem OU implementação real
- [ ] nenhum `describe` vazio em `apps/conv-e2e/src/`

## Acceptance criteria
- [ ] `npx nx e2e conv-e2e --list` mostra 14 testes (não 0)
- [ ] Nenhum teste passa silenciosamente sem implementação — todos são `fixme` ou implementados
- [ ] Mensagens de `test.fixme()` referenciam a issue CONV-FE-NNN que vai implementar o cenário
- [ ] `npx nx lint conv-e2e` PASS

## Dependencies
- CONV-FE-025 a CONV-FE-027 (issues que implementarão os cenários reais)
- Learning Layer (sinal `rework_pattern` de empty-describe já indexado — trace `tr_a8429fa2a45f`)

## Empresa-context impact
Nenhum.

## Prioridade
P9
