<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-005 — Real-Time Panel Delivery via Server-Push (SSE), Broker-Deferred

## Status
aceita

## Contexto
A razão de existir da metade "Notificações" do módulo é fazer um alarme chegar à tela do cuidador **no
instante** em que o paciente pede ajuda — um `full_screen_alarm` para `emergencia`, um aviso `discreet` para
`rotina`. Três forças moldam o como: (1) **não há broker** (`stack.messaging.enabled = false`), então não há
tópico assíncrono para publicar o alarme; (2) EVNT **já** possui o `NotificationDispatch` e seu ciclo
`queued → delivered → acknowledged → failed`, com retry fail-open (EVNT ADR-005) — DASH não deve duplicar
esse estado; e (3) um alarme de emergência **não pode** depender de um *poll* de melhor esforço do navegador.
Além disso, um cuidador pode desconectar e reconectar (fechar/reabrir a aba), e nada levantado nesse intervalo
pode ser silenciosamente perdido. As alternativas ingênuas — o painel fazer *polling* periódico de EVNT, ou
DASH manter seu próprio *inbox* durável de notificações — ou atrasam a emergência, ou recriam estado crítico
que já tem dono (contra ADR-004 local).

## Decisão
**A entrega ao navegador é server-push via SSE; o estado de despacho permanece em EVNT.** EVNT despacha
sincronamente a DASH (`POST /internal/notifications`, auth serviço-a-serviço, `dispatchId`); DASH localiza a
**sessão SSE aberta** do cuidador e **empurra** a notificação para renderização imediata, reportando
`delivered(dispatchId)` — e, no aceite do cuidador, `acknowledged(dispatchId)` — de volta a EVNT (callbacks
idempotentes por `dispatchId`). Se **não houver sessão aberta**, DASH responde "não entregue" e **não
persiste inbox** — o retry fail-open de EVNT mantém a emergência viva. Ao (re)conectar, o painel dispara
**catch-up on-connect**: DASH **puxa de EVNT** as notificações não confirmadas do cuidador e as reemite no
canal, de modo que nada levantado durante a desconexão se perca. `long-poll` é o transporte de fallback onde
SSE não estiver disponível. Os sinais de entrega são **fatos reais** — DASH nunca fabrica `delivered` para
silenciar o retry de EVNT.

## Consequências
**Positivas:** o alarme chega em segundos ao painel aberto (push, não poll); a entrega é rastreável e o
"não entregue" é **visível** (nunca silencioso), preservando a garantia de segurança de EVNT; DASH não
duplica o estado de despacho (fiel ao ADR-004 local); catch-up on-connect fecha a lacuna de reconexão sem um
inbox durável; o desenho fica broker-ready — se um broker for introduzido, EVNT→DASH e os callbacks viram
tópicos sem mudar de significado.
**Negativas / trade-offs:** entrega só-painel significa que um cuidador com a aba fechada não recebe o alarme
— canais out-of-band (SMS/push/telefone) são o conserto real e ficam **adiados** (risco R1/G1, doc 06; herdado
de EVNT ADR-005); manter uma conexão SSE por cuidador e tempestades de reconexão após restart do DASH (todos
os painéis fazem catch-up de uma vez, puxando de EVNT) exigem heartbeat limitado, backoff com jitter e um pull
de catch-up barato/idempotente (R2, doc 06); a semântica exata do catch-up (janela, deduplicação contra um
push ao vivo simultâneo) precisa ser fixada em Specs junto com EVNT (G4); o endpoint interno de despacho
precisa de auth serviço-a-serviço estrita para não permitir injeção de alarme falso (R7).
