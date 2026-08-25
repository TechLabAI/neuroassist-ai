# GEST-FE-033 -- Playwright e2e da estação — os oito cenários da SPEC-09 §5

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens
**GitHub:** [#TBD]

## Outcome
`apps/gest-e2e` cobre a estação de ponta a ponta: caminho feliz, idempotência, aba oculta, perfil
indisponível, fila offline, guard de dispositivo e a invariante de privacidade.

## Context / Problem statement
- A estação é o único lugar do produto onde ninguém está olhando. Um defeito aqui não é reportado
  por usuário — só um teste encontra.
- Lição registrada nesta sessão: **teste verde não prova que ele pega o defeito.** Cada cenário
  abaixo precisa ser conferido por mutação — quebrar a produção de propósito e ver o vermelho.
- Lição correlata: o spec unitário do `GestureLiveTestComponent` **substitui o template** por um de
  teste, então template não é coberto lá. Estes cenários e2e são o que cobre a tela de verdade.

## Approach (execution path)
1. `estacao-happy-path.spec.ts` — E1, E2: pareado → `vigiando` → gesto → relato com o
   `engineFeatureRef` certo.
2. `estacao-idempotencia.spec.ts` — E3: gesto repetido no cooldown → **um** relato.
3. `estacao-aba-oculta.spec.ts` — E4: ocultar → `parada` visível **e** heartbeat continua.
4. `estacao-perfil-indisponivel.spec.ts` — E5: `origem: INDISPONIVEL` → `degradada` com motivo.
5. `estacao-fila-offline.spec.ts` — E6: rede cai/volta → relato chega uma vez.
6. `estacao-guard.spec.ts` — E7: sessão de usuário recusada.
7. `estacao-privacidade.spec.ts` — E8: nenhuma requisição carrega quadro, landmark ou campo extra.

## Non-goals
- Cenários de calibração (já cobertos em `live-test-*.spec.ts`).

## Layer(s)
e2e

## Bounded context
gest

## Service execution spec(s)
- `frontend-spec/SPEC-09-estacao-paciente.md` §5

## Architecture alignment
- `addInitScript` **antes** do `goto` — os stores leem `sessionStorage` no construtor
- Ganchos de E2E fechados por `isDevMode()`, como o restante do GEST

## Implementation checklist (by layer/file)

**E2E**
- [ ] `apps/gest-e2e/src/estacao-happy-path.spec.ts`
- [ ] `apps/gest-e2e/src/estacao-idempotencia.spec.ts` + `estacao-aba-oculta.spec.ts`
- [ ] `apps/gest-e2e/src/estacao-perfil-indisponivel.spec.ts` + `estacao-fila-offline.spec.ts`
- [ ] `apps/gest-e2e/src/estacao-guard.spec.ts` + `estacao-privacidade.spec.ts`
- [ ] fixtures de sessão de dispositivo e de perfil

## Acceptance
- Os oito cenários rodam de verdade — nenhum `test.fixme`, nenhum pulado sem motivo declarado.
- Cada um foi conferido por mutação e o resultado dessa conferência está registrado na issue.
