# SPEC-08 — E2E Scenarios — DASH (Frontend)

> Gherkin scenarios (pt-BR) that exercise the DASH panel end-to-end. Each maps 1:1 to a `.feature` file in
> `../e2e/`. These `Cenario:` blocks are the coverage counterpart of the `THE SYSTEM SHALL` rules in
> SPEC-01/03/04/05/07 (EARS coverage gate). No EARS section here (SPEC-08 lists scenarios).

## Feature files

| File | Covers | Spec sources |
|---|---|---|
| `home-status-calm.feature` | default landing, auth redirect, summary loading/ok/error, calm honesty, degraded device section | SPEC-01, SPEC-03, SPEC-07 |
| `notification-delivery.feature` | SSE connect + catch-up, discreet notification append, mark-read revert, long-poll fallback, acknowledge idempotent | SPEC-04, SPEC-03 |
| `calibration-writethrough.feature` | calibration write-through to PAC, validation error, upstream-rejected relay, event confirm | SPEC-04, SPEC-07 |

## Scenario inventory

### home-status-calm.feature
- Painel Status é a tela inicial padrão do cuidador autenticado.
- Visitante não autenticado é redirecionado para a entrada.
- Home carrega o resumo e mostra o estado calmo "está bem".
- Falha ao carregar o resumo mostra erro com botão de tentar novamente.
- Emergência não confirmada impede o estado calmo na Home.
- Seção de dispositivo degradada mostra "desconhecido" mantendo a Home calma.

### notification-delivery.feature
- Ao conectar, o painel abre o stream e recupera notificações não reconhecidas.
- Notificação de rotina chega pelo stream e é anexada à central.
- Marcar notificação como lida reverte silenciosamente ao falhar o acknowledge.
- Reconhecer o mesmo dispatchId duas vezes é idempotente.
- Sem stream disponível, a entrega usa o long-poll de fallback.

### calibration-writethrough.feature
- Editar calibração encaminha para o PAC e retorna o resultado autoritativo.
- Calibração inválida mostra erro de campo sem reenviar.
- PAC rejeita a calibração e a mensagem do serviço dono é repassada.
- Confirmar um evento encaminha o veredito para o EVNT.
