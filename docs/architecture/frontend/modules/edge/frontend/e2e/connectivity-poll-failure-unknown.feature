# language: pt
Funcionalidade: Falha de polling entra em estado desconhecido
  Como superfície honesta,
  nunca devo assumir "online" quando o polling de conectividade falhou.

  Cenário: Poll de liveness falha e a conectividade fica desconhecida
    Dado que existe um dispositivo pareado
    Quando um poll de liveness falha com erro de rede
    Então o sistema define o derivedState como "unknown"
    E o sistema não define o derivedState como "online"
    E o sistema tenta novamente no próximo tick de polling

  Cenário: Poll de liveness pausa com a aba oculta
    Dado que a aba do navegador está oculta
    Quando o polling de conectividade está ativo
    Então o sistema pausa o polling enquanto a aba está oculta

  Cenário: 304 Not Modified continua envelhecendo o estado
    Dado que um poll de liveness retorna "304 Not Modified"
    Então o sistema mantém o último snapshot do dispositivo
    E o sistema continua envelhecendo o estado de conectividade derivado
