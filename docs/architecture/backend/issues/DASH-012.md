# DASH-012 -- Implement long-poll fallback: GET /panel/notifications/poll (waitSeconds<=30, cursor)

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto

Em ambientes onde SSE e bloqueado (proxies, redes corporativas), o painel usa long-poll como
fallback. O endpoint suspende a request por ate `waitSeconds` (max 30) e retorna quando houver
notificacoes novas ou timeout. O `cursor` e opaco, gerado pelo DASH, e permite retomada sem
duplicatas. Este endpoint NAO e o canal principal; e somente o fallback.

## Spec de referencia

- SPEC-01 (GET /panel/notifications/poll, query: waitSeconds<=30, cursor; 200 { notifications[], cursor })
- SPEC-03 (long-poll como fallback de transporte onde SSE e indisponivel)

## Escopo

- [ ] `api/PanelController.pollNotifications(waitSeconds, cursor)`: valida `waitSeconds <= 30`, suspende com `DeferredResult<PollResponse>` ou `Callable`, aguarda notificacoes via uma fila in-memory por (caregiverId, cursor) ou retorna vazio apos timeout
- [ ] `cursor` opaco (timestamp + caregiverId hash), gerado na primeira chamada; ausente = inicio de sessao de poll
- [ ] Array `notifications` retornado pode ser vazio (timeout): resposta `200` com lista vazia e novo cursor
- [ ] Teste: mock com 1 notificacao em fila → retorna em < waitSeconds; sem notificacao → retorna apos `waitSeconds` com lista vazia

## Resultado esperado

`GET /panel/notifications/poll` retorna notificacoes pendentes ou lista vazia apos timeout, com cursor para proxima chamada.

## Criterios de aceite

- [ ] `waitSeconds > 30` → `400 Bad Request`
- [ ] Sem notificacoes durante `waitSeconds` → `200 { notifications: [], cursor: "<novo>" }` apos timeout
- [ ] Com notificacao disponivel → retorna imediatamente com `notifications: [...]` e cursor avancado
- [ ] `cursor` opaco presente em toda resposta (incluindo timeout)

## Dependencias

- DASH-004 (PanelSessionRegistry ou fila auxiliar)
- DASH-021 (auth /panel/**)

## Estimativa

S

## Prioridade

P3
