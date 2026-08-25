# GEST-FE-029 -- Limiares vêm do perfil publicado, não de constantes de código

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens
**GitHub:** [#TBD]

## Outcome
`GestureDetector` recebe `DetectionThresholds` por parâmetro. A sensibilidade que a cuidadora
escolhe na calibração passa a mudar o comportamento do motor — hoje não muda nada.

## Context / Problem statement
- O gest-service publica `DetectionThresholdTable` com três faixas validadas para serem coerentes
  entre si (`alta` detecta mais que `media`, que detecta mais que `baixa`).
- O detector do browser tem os limiares como **constantes de módulo** e nunca recebe
  `sensibilidade`. Divergem, medidos em 2026-08-25:

  | | `earClose` | `blinkMinFrames` | `doubleBlinkWindowMs` | `briRaiseOn` |
  |---|---|---|---|---|
  | backend `media` (canônico) | 0.22 | 2 | 2000 | 0.30 |
  | browser (fixo) | 0.20 | 3 | 1800 | 0.40 |

- **Consequência clínica:** a coordenadora escolhe "alta" para um paciente com movimento fraco e o
  motor continua exigindo o mesmo esforço. O produto mente sobre uma configuração de acessibilidade.
- O guarda de drift do CI vigia `edge_agent_core.py` — o agente Python que a ADR-007 removeu do
  produto. Guarda verde apoiado no lado errado (ver GEST-INT-008).

## Approach (execution path)
1. Tipo `DetectionThresholds` em `shared/contracts`, espelhando o record do backend campo a campo:
   `earClose, earOpen, earOtherMax, blinkMinFrames, doubleBlinkWindowMs, briRaiseOn, briRaiseOff,
   briOtherMax, browMinFrames`.
2. `GestureDetector` recebe os limiares no construtor; as constantes viram `LIMIARES_PADRAO`.
3. O default **só** vale quando o perfil não pôde ser lido, e nesse caso a tela declara
   (`origem: INDISPONIVEL`).
4. Alinhar `LIMIARES_PADRAO` com a faixa `media` do backend — hoje divergem, e o default é o que
   roda quando tudo mais falha.
5. O teste ao vivo (calibração) passa a usar a faixa do rascunho, para que testar e produzir
   coincidam.

## Non-goals
- Mudar os valores canônicos do backend.

## Layer(s)
frontend / feature lib (motor)

## Bounded context
gest

## Service execution spec(s)
- `frontend-spec/SPEC-09-estacao-paciente.md` §2.2, §4 (I5)
- `ADR-008` D3

## Architecture alignment
- `GestureDetector` continua classe pura, sem Angular
- Tag Nx: `scope:gest`

## Implementation checklist (by layer/file)

**Contracts + motor**
- [ ] `libs/shared/contracts/src/lib/detection-thresholds.ts`
- [ ] `libs/gest/src/lib/detection/gesture-detector.ts` — limiares por parâmetro
- [ ] `libs/gest/src/lib/detection/gesture-detector.spec.ts` — faixa alta dispara antes da baixa
- [ ] `libs/gest/src/lib/api/edge-detection-browser.adapter.ts` — repassar a faixa
- [ ] alinhar `LIMIARES_PADRAO` com `media` do backend

## Acceptance
- Mesma sequência de quadros: `alta` reconhece onde `baixa` não reconhece. Este teste é o que prova
  que a configuração deixou de ser decorativa — sem ele a issue não está pronta.
- Nenhum limiar continua fixo no caminho da estação.
