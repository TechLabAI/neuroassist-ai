# language: pt
Funcionalidade: Alarme de emergência preempta qualquer tela
  Como cuidadora responsável por Márcio
  Quero que um evento de emergência apareça como alarme full-screen em qualquer tela
  Para que uma emergência nunca passe despercebida

  Contexto:
    Dado que estou autenticada como cuidadora vinculada ao paciente
    E que o canal live de eventos está ativo

  Cenário: Alarme de emergência preempta a tela ativa
    Dado que estou na tela de calibração
    Quando o evnt-service entrega um evento de urgência "emergencia"
    Então o AlarmOutlet global exibe o alarme full-screen sobre a rota ativa
    E a única ação disponível é "Confirmar que atendi Márcio"
    E a URL da rota não muda

  Cenário: Recarregar durante um alarme ativo re-exibe o alarme
    Dado que existe um evento de emergência ativo não confirmado
    Quando eu recarrego o painel
    Então o sistema hidrata o evento ativo via GET /events/active antes de abrir o stream
    E o alarme de emergência é re-exibido imediatamente
