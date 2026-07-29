# language: pt
Funcionalidade: Conectividade derivada da frescura do heartbeat
  Como conscience de conectividade da plataforma,
  a superfície deve dizer a verdade sobre se o dispositivo está sendo observado agora.

  Contexto:
    Dado que existe um dispositivo pareado reportando heartbeats
    E que estou autenticado em uma rota qualquer

  Cenário: Heartbeat fresco mantém o chip online
    Dado que a idade relativa ao servidor do heartbeat está abaixo do limiar de staleness
    Quando a conectividade é derivada
    Então o sistema define o derivedState como "online"

  Cenário: Heartbeat sem sinal recente degrada para stale
    Dado que a idade relativa ao servidor do heartbeat está entre o limiar de staleness e a janela de offline
    Quando a conectividade é derivada
    Então o sistema define o derivedState como "stale"

  Cenário: Heartbeat ultrapassa a janela de offline
    Dado que a idade relativa ao servidor do heartbeat atinge a janela de offline
    Quando a conectividade é derivada
    Então o sistema define o derivedState como "offline"

  Cenário: O chip nunca permanece online após a janela expirar
    Dado que o dispositivo parou de reportar
    Quando a idade relativa ao servidor do heartbeat ultrapassa a janela de offline
    Então o sistema deriva a conectividade a partir da idade relativa ao servidor
    E o sistema não renderiza a flag status persistida como verdade atual
    E o sistema não mostra "online"
