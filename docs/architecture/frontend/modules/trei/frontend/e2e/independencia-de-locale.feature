# language: pt
Funcionalidade: Independencia de locale na autoria e aprovacao
  Como cuidadora que autora conteudo bilingue EN/PT
  Quero que cada locale seja proposto e aprovado de forma independente
  Para que aprovar um idioma nunca altere o outro (ADR-018)

  Contexto:
    Dado que a cuidadora esta autenticada no shell de treinamento com escopo "treinamento.no.aprovar.cuidadora"
    E que a sessao de treinamento esta ativa

  Cenario: Trocar o locale ativo re-projeta as propostas
    Dado que o locale ativo e "pt"
    Quando a cuidadora troca o locale ativo para "en"
    Entao o sistema re-projeta a lista de propostas e o resumo de aprovacao para o locale ativo "en"
    E o sistema nao altera os flags de aprovacao do outro locale "pt"

  Cenario: Aprovar em um locale nao vira o outro locale
    Dado que existe um no rascunho aprovado pela cuidadora no locale "pt"
    Quando a cuidadora troca o locale ativo para "en"
    Entao o mesmo no aparece com cuidadora aguardando no locale "en"
    Quando a cuidadora aprova o no no locale "en"
    Entao o sistema escreve apenas o lado cuidadora do locale "en"
    E o lado do locale "pt" permanece inalterado

  Cenario: Resumo de aprovacao e calculado por locale ativo
    Dado que ha propostas em estados diferentes por locale
    Quando o locale ativo e "pt"
    Entao o sistema calcula o resumo de aprovacao a partir da projecao do locale ativo apenas
