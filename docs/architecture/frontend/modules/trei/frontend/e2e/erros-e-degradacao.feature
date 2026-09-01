# language: pt
Funcionalidade: Erros e degradacao do assistente e das aprovacoes
  Como cuidadora diante de falhas do trei-service
  Quero erros retentaveis e primeiros-classe, nunca telas em branco
  Para que eu continue aprovando o que ja existe (SPEC-07)

  Contexto:
    Dado que a cuidadora esta autenticada no shell de treinamento
    E que a sessao de treinamento esta ativa

  Cenario: Falha do assistente mostra banner retentavel e mantem propostas
    Quando o backend responde "llm-unavailable" com status 503 em um turno de conversa
    Entao o sistema mostra um banner de conversa retentavel
    E o sistema mantem as propostas existentes visiveis e aprovaveis

  Cenario: Edicao invalida mostra erro de campo inline
    Quando o backend responde "invalid-request" com status 400 em uma edicao
    Entao o sistema mostra um erro de campo inline
    E o sistema nao submete a proposta

  Cenario: Aprovacao otimista que falha faz rollback e re-confirmacao
    Dado que a cuidadora aprova um no de forma otimista
    Quando a requisicao de aprovacao da cuidadora falha apos a atualizacao otimista
    Entao o sistema desfaz o flag da cuidadora
    E o sistema busca novamente o no
    E o sistema pede a re-confirmacao

  Cenario: Descartar uma fala ja publicada mostra conflito
    Quando o backend responde "already-published" com status 409 em uma acao de rascunho
    Entao o sistema mostra um toast
    E o sistema busca novamente o no afetado

  Cenario: Escopo proibido mostra toast de permissao sem retry automatico
    Quando o backend responde "forbidden-scope" com status 403
    Entao o sistema mostra um toast de permissao
    E o sistema nao repete a acao automaticamente

  Cenario: Sessao revogada encerra fail-closed
    Quando o backend responde "session-revoked" com status 401
    Entao o sistema encerra a sessao fail-closed
    E o sistema encaminha a cuidadora para a re-autenticacao

  Cenario: Sem escopo de conduzir a superficie do treinamento fica oculta
    Dado que a sessao da cuidadora nao tem escopo "treinamento.sessao.conduzir"
    Quando a cuidadora tenta abrir a superficie de treinamento
    Entao o sistema redireciona para o estado proibido da cuidadora
    E o sistema nao renderiza a sessao de treinamento
