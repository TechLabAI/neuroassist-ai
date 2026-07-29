# language: pt
Funcionalidade: Teste ao vivo de comando composto (dois primitivos)
  Como cuidadora, ao configurar um comando composto por dois primitivos,
  quero um teste em duas etapas com feedback por etapa.

  Contexto:
    Dado que a página de calibração compõe o GestureLiveTestComponent inline
    E que existe um DispositivoEdge pareado e online
    E um rascunho de comando composto com primitivo principal e primitivo secundário

  Cenário: Comando composto reconhecido em sequência
    Quando a cuidadora aciona "Testar agora"
    E o edge bridge emite signalKind "recognized" para a etapa principal
    Então o estado permanece "testing" com step "secundario"
    Quando o edge bridge emite signalKind "recognized" para a etapa secundaria
    Então o sistema exige que ambos os primitivos principal e secundario sejam reconhecidos antes de definir o estado "recognized"
    E o estado passa para "recognized"

  Cenário: Comando composto não reconhecido por tempo esgotado na segunda etapa
    Quando a cuidadora aciona "Testar agora"
    E o edge bridge emite signalKind "recognized" para a etapa principal
    E nenhum sinal de reconhecimento chega dentro de tempoMaximoGestoMs
    Então o estado passa para "not-recognized"
