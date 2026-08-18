# language: pt
Funcionalidade: Entrega de notificações ao cuidador
  Como cuidadora autenticada
  Quero receber notificações em tempo real no painel
  Para não perder um pedido de ajuda do paciente

  Contexto:
    Dado que a cuidadora tem uma sessão válida
    E que o painel está montado

  Cenario: Ao conectar o painel abre o stream e recupera notificações não reconhecidas
    Quando o painel conecta
    Então o sistema abre o canal SSE /panel/stream
    E puxa o catch-up de GET /panel/notifications/unacknowledged

  Cenario: Notificação de rotina chega pelo stream e é anexada à central
    Dado que o canal /panel/stream está aberto
    Quando um evento notification de tier discreet chega pelo stream
    Então o sistema anexa a notificação à lista recente sem refazer o fetch

  Cenario: Marcar notificação como lida reverte ao falhar o acknowledge
    Dado que existe uma notificação não lida na central
    Quando a cuidadora marca a notificação como lida e o acknowledge falha
    Então o sistema atualiza o flag read imediatamente e depois reverte o flag read sem mostrar erro

  Cenario: Reconhecer o mesmo dispatchId duas vezes é idempotente
    Dado que existe uma notificação com um dispatchId
    Quando a cuidadora reconhece o mesmo dispatchId duas vezes
    Então o sistema trata o acknowledge repetido do mesmo dispatchId como sucesso

  Cenario: Sem stream disponível a entrega usa o long-poll de fallback
    Dado que o canal SSE está indisponível
    Quando o painel busca notificações
    Então o sistema usa o fallback GET /panel/notifications/poll para a entrega
