# SPEC-08 — E2E Scenarios — EVNT (Frontend)

> Module: **EVNT — Eventos e Alarmes** · Playwright specs live in `apps/evnt-e2e`.
> Each scenario below becomes a `.feature` in `e2e/`. Gherkin only — **no EARS section** (per D-446).
> Every `Cenario` shares domain nouns (alarme, emergência, confirmar, rotina, degradado, evento) with the
> `THE SYSTEM SHALL` rules it covers, so the EARS coverage matcher casa.

## Feature files

| `.feature` | Covers SPEC EARS | Golden intent |
|---|---|---|
| `alarme-emergencia-preempcao.feature` | SPEC-01 (AlarmOutlet), SPEC-03, SPEC-06 | emergency pre-empts any screen |
| `confirmar-atendimento.feature` | SPEC-04, SPEC-07 | confirm clears alarm only after 200 |
| `confirmacao-concorrente.feature` | SPEC-07 (409) | already-confirmed by another caregiver |
| `notificacao-rotina.feature` | SPEC-03, SPEC-05 | discreet routine notice, dismissible |
| `entrega-degradada.feature` | SPEC-04, SPEC-06, SPEC-07 | stream drop → degraded polling, no lost alarm |

## Scenario inventory

### `alarme-emergencia-preempcao.feature`
- Cenário: Alarme de emergência preempta a tela ativa
- Cenário: Recarregar durante um alarme ativo re-exibe o alarme (hidratação)

### `confirmar-atendimento.feature`
- Cenário: Confirmar atendimento limpa o alarme após sucesso
- Cenário: Falha de rede ao confirmar mantém o alarme ativo com retry inline

### `confirmacao-concorrente.feature`
- Cenário: Evento já confirmado por outro cuidador limpa o alarme como equivalente a sucesso

### `notificacao-rotina.feature`
- Cenário: Notificação de rotina aparece de forma discreta e é dispensável

### `entrega-degradada.feature`
- Cenário: Queda do canal live entra em modo degradado sem perder alarme
