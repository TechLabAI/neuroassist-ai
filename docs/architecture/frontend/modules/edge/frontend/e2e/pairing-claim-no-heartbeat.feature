# language: pt
Funcionalidade: Reivindicação sem primeiro heartbeat
  Como canal crítico de segurança,
  nunca devo declarar "pareado · online" sem um primeiro heartbeat real.

  Cenário: Agente reivindica o código mas nunca envia heartbeat
    Dado que registrei o dispositivo e o agente reivindicou o código
    E que nenhum primeiro heartbeat chegou
    Quando o polling de pareamento continua
    Então o sistema mantém a fase de pareamento em "awaiting-first-heartbeat"
    E o sistema não avança a fase de pareamento para "paired"
    E o sistema não mostra "online" sem um primeiro heartbeat real
