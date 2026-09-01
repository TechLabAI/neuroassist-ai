# language: pt
Funcionalidade: Autoria de fala e aprovacao do lado cuidadora (caminho ouro)
  Como cuidadora familiar no shell de treinamento
  Quero conversar com o assistente, materializar uma proposta e aprovar meu lado
  Para que a fala fique aguardando a confirmacao do paciente sem publicar sozinha

  Contexto:
    Dado que a cuidadora esta autenticada no shell de treinamento com escopo "treinamento.sessao.conduzir"
    E que o locale ativo e "pt"

  Cenario: Abrir treinamento redireciona para a rota de sessao
    Quando a cuidadora abre a rota "/treinamento"
    Entao o sistema redireciona para "/treinamento/sessao" como rota de treinamento padrao
    E o sistema exibe o aviso de autoria "nao e o app do paciente" na rota

  Cenario: Estado vazio oferece iniciar treinamento
    Dado que nenhuma sessao de treinamento foi iniciada
    Quando a cuidadora ve a tela de sessao
    Entao o sistema apresenta o estado vazio com o botao "Iniciar treinamento"

  Cenario: Iniciar uma sessao de treinamento
    Quando a cuidadora inicia uma sessao de treinamento para o paciente
    Entao o sistema chama "POST /api/v1/trei/sessoes" com pacienteId e localeAutoria
    E o sistema guarda o sessaoId, o llmModelId e a divulgacao de IA retornados

  Cenario: Enviar um turno de conversa gera propostas do assistente
    Dado que a sessao de treinamento esta ativa
    Quando a cuidadora envia um turno de conversa com uma orientacao
    Entao o sistema marca o estado como assistant-thinking
    E o sistema nao acrescenta nenhum turno do assistente ate o trei-service responder
    E quando o trei-service responde o sistema renderiza as propostas retornadas como propostas nao materializadas

  Cenario: Materializar uma proposta em um no rascunho
    Dado que existe uma proposta do assistente para o locale ativo
    Quando a cuidadora materializa a proposta
    Entao o sistema chama "POST /api/v1/trei/nos"
    E o sistema marca o cartao como pendente ate um noId ser retornado

  Cenario: Aprovar o lado cuidadora mantem a publicacao pendente
    Dado que existe um no rascunho materializado para o locale ativo
    Quando a cuidadora aprova o no para o locale ativo
    Entao o sistema chama "POST /api/v1/trei/nos/{noId}/aprovacoes/cuidadora" apenas com o locale
    E o sistema nao envia nenhum campo do lado paciente
    E o cartao mostra o indicador cuidadora confirmado e paciente aguardando
    E a publicacao permanece nao disparada
