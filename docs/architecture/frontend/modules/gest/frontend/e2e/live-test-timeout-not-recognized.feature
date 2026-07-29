# language: pt
Funcionalidade: Tempo esgotado sem reconhecimento
  Como cuidadora, quero uma orientação clara quando o gesto não for reconhecido a tempo.

  Cenário: Nenhum sinal dentro da janela resulta em not-recognized
    Dado que existe um DispositivoEdge pareado e online
    E um rascunho de comando simples com rótulo vazio
    Quando a cuidadora aciona "Testar agora"
    Então o sistema permite que o teste ao vivo prossiga com rótulo vazio
    Quando nenhum sinal de reconhecimento chega dentro de tempoMaximoGestoMs
    Então o estado passa para "not-recognized"
    E a mensagem orienta repetir o gesto ou aumentar a sensibilidade

  Cenário: Erro de rede transitório na verificação de elegibilidade é reexecutado
    Dado que o host aciona a verificação de elegibilidade do backend
    Quando ocorre uma falha de rede transitória na chamada ao backend
    Então o sistema reexecuta com backoff antes de exibir um toast com repetição manual
