# language: pt
Funcionalidade: Geracao server-mediated, nunca no navegador (INV-2)
  Como guarda do invariante "o runtime nunca gera texto"
  Quero que toda geracao venha do trei-service
  Para que o navegador nunca chame o modelo nem fabrique conteudo (ADR-005)

  Contexto:
    Dado que a cuidadora esta autenticada no shell de treinamento
    E que a sessao de treinamento esta ativa

  Cenario: Nenhuma requisicao sai do navegador para a Anthropic
    Quando a cuidadora conduz uma sessao completa de treinamento
    Entao o sistema envia toda requisicao do trei-service com o JWT da cuidadora pela camada de acesso compartilhada
    E o sistema nao emite nenhuma requisicao para "api.anthropic.com", ElevenLabs ou Elasticsearch

  Cenario: O bundle nao carrega SDK de IA
    Quando o grafo de dependencias do TREI e inspecionado
    Entao o sistema nao carrega nenhum cliente Anthropic, ElevenLabs ou Elasticsearch no grafo de dependencias do TREI

  Cenario: O turno do assistente so aparece apos a resposta do servico
    Quando a cuidadora envia um turno de conversa
    Entao o sistema marca o estado como assistant-thinking
    E o sistema nao acrescenta nenhum turno do assistente ate o trei-service responder
