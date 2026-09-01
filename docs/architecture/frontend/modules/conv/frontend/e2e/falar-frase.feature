# language: pt
@conv @tela06 @golden-path
Funcionalidade: Falar uma frase na árvore de falas (Tela 06)
  Como paciente com controle motor reduzido
  Quero navegar categoria → frase e ouvir a frase na minha voz
  Para me comunicar sem que alguém adivinhe por mim

  Contexto:
    Dado um paciente com sessão ativa e escopo "conv.arvore.navegar" e "conv.no.selecionar"
    E a árvore de falas do paciente carregada no idioma ativo "pt"

  @canal-toque
  Cenário: Selecionar e falar uma frase com áudio cacheado por toque
    Dado que estou no nível raiz da árvore em "/paciente/arvore"
    Quando eu ativo a categoria "Preciso de algo" por toque
    Então o sistema desce um nível sem chamar select
    Quando eu ativo a frase "Água com gelo" por toque
    Então o sistema chama POST select com via "toque" e um clientEventId
    E o playbackMode retornado é "audio-cacheado"
    E o sistema reproduz o áudio cacheado da frase

  @paridade-de-canal
  Cenário: Paridade de canal — o gesto reconhecido alcança o mesmo nó e a mesma fala
    Dado que estou no nível da categoria "Preciso de algo"
    Quando o canal de gesto reconhece o engineFeatureRef "gest:blink-double"
    E o sistema resolve o binding para o nó da frase "Água com gelo" com bound verdadeiro
    Então o selectedNodeId é o mesmo nó alcançado por toque
    E a fala reproduzida é idêntica à do canal de toque
    E nenhuma lógica de navegação ou playback ramifica pelo canal ativo

  @categoria
  Cenário: Ativar uma categoria apenas desce um nível
    Dado que estou no nível raiz da árvore
    Quando eu ativo a categoria "Falar" por toque
    Então o sistema carrega o próximo nível pela árvore
    E o sistema não chama POST select para a categoria
