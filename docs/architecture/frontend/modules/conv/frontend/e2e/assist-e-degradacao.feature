# language: pt
@conv @tela06 @assist @degradacao
Funcionalidade: Busca semântica assistida e degradação silenciosa
  Como paciente
  Quero uma sugestão da frase mais próxima quando disponível
  Mas quero continuar navegando manualmente se a busca cair

  Contexto:
    Dado um paciente com sessão ativa e escopo "conv.arvore.navegar"
    E a árvore de falas do paciente carregada no idioma ativo "en"

  @match
  Cenário: A busca assistida retorna a frase mais próxima e o paciente confirma
    Quando eu aciono a busca assistida
    E o conv-service retorna matches com retrieval "semantic" e degraded falso
    Então o sistema renderiza os matches como vieram, sem reordenar
    Quando eu escolho o match "I want water with ice"
    Então o selectedNodeId passa a ser o nó do match

  @es-down
  Cenário: Elasticsearch indisponível degrada em silêncio para navegação manual
    Quando eu aciono a busca assistida
    E o conv-service responde degraded verdadeiro com retrieval "static" e matches vazio
    Então o sistema mantém a navegação manual da árvore disponível
    E o sistema não exibe nenhuma superfície de erro
