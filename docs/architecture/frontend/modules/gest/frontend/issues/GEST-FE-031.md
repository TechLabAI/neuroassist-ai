# GEST-FE-031 -- Heartbeat, Wake Lock e o estado "parada" — detecção parada nunca em silêncio

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens
**GitHub:** [#TBD]

## Outcome
Uma estação que parou de detectar é visível — na tela em segundos, e no painel da cuidadora em até
45 s. Wake Lock evita o sleep de tela; `visibilitychange` produz um estado `parada` de primeira
classe; o heartbeat continua batendo em todos os estados.

## Context / Problem statement
- **`requestAnimationFrame` não dispara em aba oculta.** A detecção não degrada: **para.** Hoje
  para em silêncio, e a `ADR-007` chama isso de "o pior modo de falha possível para este produto".
- O controle compensatório do servidor já existe e não está sendo usado: o `edge-service` deriva
  conectividade do frescor do heartbeat (limiar 45 s). Medido em 2026-08-25: **16 dispositivos
  registrados, zero heartbeats**. Não falta o controle — falta quem emita.
- Contrato lido no produtor (`HeartbeatController` + `HeartbeatRequest`): `deviceId` vem do `sub`
  do JWT; corpo com outro `deviceId` leva **403 `edge.identity-mismatch`**; **campo não declarado
  no corpo lança** (`@JsonAnySetter`, invariante de privacidade nº 6) — não acrescentar telemetria.
- A resposta traz `profileDrift` e `currentProfileVersion`: é assim que a estação descobre que há
  perfil novo.

## Approach (execution path)
1. Heartbeat a cada 15 s (três batidas cabem na janela de 45 s) com `agentVersion` e
   `runningProfileVersion`.
2. **O heartbeat continua em `parada` e `degradada`** — é o que distingue "parada" de "morta".
   Parar de bater esconde justamente o problema que ele existe para revelar.
3. `navigator.wakeLock.request('screen')` ao entrar em `vigiando`; reaquisição após
   `visibilitychange`, porque o lock é perdido ao ocultar. Ausência da API não é erro: é degradação
   declarada.
4. `visibilitychange` → estado `parada` ocupando a tela, com o horário em que parou.
5. Watchdog visível: indicador que prova que um quadro foi processado há pouco (o `onFrame` do
   detector já emite o batimento).
6. `profileDrift: true` na resposta → pedir recarga do perfil (GEST-FE-028).

## Non-goals
- Impedir que a aba seja ocultada (impossível). O objetivo é **tornar visível**, não evitar.
- `MediaStreamTrackProcessor` em Worker — só Chromium; registrado na ADR-008 como caminho futuro.

## Layer(s)
frontend / feature lib + data-access

## Bounded context
gest

## Service execution spec(s)
- `frontend-spec/SPEC-09-estacao-paciente.md` §2.4, §3, §4 (I2, I3)
- `ADR-008` D5 · `EDGE ADR-005`

## Architecture alignment
- Timer com `takeUntilDestroyed`; sem `setInterval` órfão
- Tag Nx: `scope:gest`

## Implementation checklist (by layer/file)

**Data access + feature**
- [ ] `libs/gest/src/lib/estacao/heartbeat.port.ts` + adapter HTTP
- [ ] `libs/gest/src/lib/estacao/heartbeat.service.ts` — 15 s, em todos os estados
- [ ] `libs/gest/src/lib/estacao/wake-lock.service.ts` — com reaquisição
- [ ] `libs/gest/src/lib/estacao/estacao.component.ts` — estado `parada` em tela cheia
- [ ] specs: aba oculta → `parada`; heartbeat NÃO para
- [ ] `profileDrift` → recarga do perfil

## Acceptance
- Ocultar a aba leva a `parada` visível **e** o heartbeat segue batendo.
- Encerrar a estação para o heartbeat; o painel mostra offline em até 45 s.
- Nenhum campo além de `agentVersion`, `runningProfileVersion` e `deviceId` no corpo.
