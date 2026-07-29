# language: pt
Funcionalidade: Notificação de rotina discreta
  Como cuidadora
  Quero receber eventos de rotina de forma discreta e dispensável
  Para que eventos não urgentes não interrompam meu trabalho

  Contexto:
    Dado que estou autenticada como cuidadora vinculada ao paciente
    E que o canal live de eventos está ativo

  Cenário: Notificação de rotina aparece de forma discreta e é dispensável
    Quando o evnt-service entrega um evento de urgência "rotina"
    Então uma notificação de rotina discreta é exibida sem bloquear a tela
    E nenhum alarme full-screen é exibido
    Quando eu dispenso a notificação de rotina
    Então a notificação é removida da fila de rotina sem chamada ao servidor
