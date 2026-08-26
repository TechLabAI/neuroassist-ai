# GEST-INT-008 -- Guarda de drift dos limiares apontando para o BROWSER (hoje guarda o runtime aposentado)

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens
**GitHub:** [#TBD]

## Outcome
O CI passa a comparar os limiares do **motor do produto** (browser) com os canônicos do
gest-service. A divergência atual é corrigida e fica impossível reintroduzi-la em silêncio.

## Context / Problem statement
- `check_edge_agent_drift.py` roda no `ci-backend.yml` e compara `DetectionThresholdTable` com
  `backend/edge_agent_core.py` — o agente **Python que a `ADR-007` tirou do caminho do produto**.
- O motor que de fato roda (`libs/gest/.../gesture-detector.ts`) **não é comparado com nada**, e
  diverge do canônico:

  | | `earClose` | `blinkMinFrames` | `doubleBlinkWindowMs` | `briRaiseOn` |
  |---|---|---|---|---|
  | backend `media` | 0.22 | 2 | 2000 | 0.30 |
  | browser | 0.20 | 3 | 1800 | 0.40 |

- É a armadilha "um guarda que não guarda nada" catalogada no `CLAUDE.md`: gate verde apoiado no
  lado errado da divergência. O guarda passa **porque** vigia o arquivo que ninguém mais executa.

## Approach (execution path)
1. Estender (ou substituir) o script para ler `LIMIARES_PADRAO` do detector TypeScript e comparar
   com a faixa `media` de `DetectionThresholdTable.defaults()`.
2. Rodar no `ci-frontend.yml` também — é código de frontend que ele passa a guardar.
3. Decidir o destino do trecho que vigia `edge_agent_core.py`: reapontar ou aposentar junto com o
   agente. Manter os dois "por segurança" recria a ambiguidade que a ADR-007 fechou.
4. Corrigir a divergência (parte de GEST-FE-029) e confirmar que o guarda **reprova** se ela voltar.

## Non-goals
- Mudar os valores canônicos do backend.

## Layer(s)
infra / CI

## Bounded context
gest

## Service execution spec(s)
- `ADR-008` — pendências P2 e P3
- `frontend-spec/SPEC-09-estacao-paciente.md` §4 (I5)

## Architecture alignment
- O script continua sem dependência de runtime Node — lê o TS como texto, como já faz com o Python

## Implementation checklist (by layer/file)

**Scripts + CI**
- [ ] `backend/scripts/check_edge_agent_drift.py` — passa a ler o detector do browser
- [ ] `.github/workflows/ci-frontend.yml` — executa o guarda
- [ ] decisão registrada sobre `edge_agent_core.py` (reapontar ou aposentar)
- [ ] prova por mutação: alterar um limiar do browser **reprova** o CI

## Acceptance
- Alterar `LIMIARES_PADRAO` sem alterar o backend reprova — verificado, não presumido.
- Nenhum guarda ativo aponta exclusivamente para runtime fora do produto.

> Nota: os workflows de CI estão **desativados** desde 25/08/2026. Esta issue só cumpre seu papel
> quando forem religados (`gh workflow enable`) — do contrário o guarda existe e não roda, que é
> uma variação do mesmo problema que ela vem corrigir.
