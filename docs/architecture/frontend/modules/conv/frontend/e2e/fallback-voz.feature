# language: pt
@conv @tela06 @voz @fallback
Funcionalidade: Voz nunca em silêncio — cadeia de fallback e idioma pendente
  Como paciente
  Quero que toda seleção de frase produza alguma fala ou texto visível
  Para que a comunicação nunca pare, mesmo sem áudio cacheado

  Contexto:
    Dado um paciente com sessão ativa e escopo "conv.no.selecionar"
    E a árvore de falas do paciente carregada no idioma ativo "en"

  @web-speech
  Cenário: Áudio ausente cai para Web Speech
    Quando eu ativo a frase "I want water with ice" por toque
    E o POST select retorna audioRef ausente com playbackMode "web-speech" e degraded verdadeiro
    Então o sistema fala o texto da frase via Web Speech
    E o indicador de playback mostra a fonte "web-speech"

  @texto-proeminente
  Cenário: Web Speech indisponível cai para texto proeminente
    Dado que a Web Speech API está indisponível no dispositivo
    Quando eu ativo a frase "I want water with ice" por toque
    E o áudio cacheado está ausente
    Então o sistema renderiza o texto da frase de forma proeminente em tela cheia
    E a seleção nunca fica em silêncio

  @idioma-pendente
  Cenário: Nó pendente no idioma ativo nunca fala o outro idioma
    Dado um nó cujo conteúdo no idioma ativo "en" tem contentStatus "pending_locale"
    Então o sistema exibe o nó desabilitado e esmaecido com a nota de idioma pendente
    E o nó não é selecionável
    Quando eu tento ativar esse nó pendente
    Então o sistema responde content-pending-locale e mantém o nó desabilitado
    E o sistema não substitui o texto ou áudio pelo outro idioma
