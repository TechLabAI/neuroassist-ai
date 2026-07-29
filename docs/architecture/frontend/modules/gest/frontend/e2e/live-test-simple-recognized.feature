# language: pt
Funcionalidade: Teste ao vivo de comando simples reconhecido
  Como cuidadora familiar, ao configurar um comando simples,
  quero testar ao vivo e receber um veredito de reconhecimento em linguagem clara.

  Contexto:
    Dado que a cuidadora tem a permissão "gest.live-test.run"
    E que a página de calibração do PAC compõe o GestureLiveTestComponent inline
    E que existe um DispositivoEdge pareado e online
    E que a feature flag "edgeLiveBridge" está ligada

  Cenário: Comando simples reconhecido pelo stream real do edge
    Dado um rascunho de comando simples com rótulo "água"
    Quando a cuidadora aciona "Testar agora"
    Então o sistema adquire a webcam via getUserMedia e assina o stream de reconhecimento para o commandDraftRef
    Quando o edge bridge emite signalKind "recognized" para o comando simples
    Então o estado passa para "recognized"
    E o sistema emite um GestureTestOutcome com recognized verdadeiro
    E a mensagem exibida é em linguagem humana pt-BR
    E o sistema não exibe confidence nem número bruto na tela

  Cenário: Controle "Testar agora" oculto sem permissão
    Dado que a cuidadora não possui a permissão "gest.live-test.run"
    Então o sistema oculta o controle "Testar agora"
