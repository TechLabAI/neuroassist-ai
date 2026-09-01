# language: pt
@conv @patient-shell @seguranca
Funcionalidade: Isolamento do patient shell e reflexo de revogação de sessão
  Como responsável pela segurança
  Quero que nenhuma superfície do cuidador seja alcançável pelo paciente
  E que uma sessão revogada encerre o shell e libere a câmera

  Contexto:
    Dado um paciente com sessão ativa no patient shell em "/paciente/arvore"

  @zero-leak
  Cenário: Nenhuma rota do cuidador é alcançável a partir do patient shell
    Então as únicas rotas do patient shell são "arvore" e "confirmar"
    E não existe rota de histórico, calibração, onboarding ou login neste shell
    Quando eu tento acessar uma rota desconhecida dentro do patient shell
    Então o sistema redireciona para "/paciente/arvore"
    E o sistema não renderiza nenhuma superfície do cuidador

  @sessao-revogada
  Cenário: Sessão revogada encerra o patient shell e libera a câmera
    Dado que o canal de gesto está ativo com a câmera aberta
    Quando um POST select responde session-revoked com 401
    Então o sistema encerra o patient shell para a tela de sessão encerrada
    E o sistema libera todas as trilhas de câmera ativas
    E o sistema não redireciona para o login do cuidador

  @scope-navegar
  Cenário: Sessão sem escopo de navegação esconde toda a superfície CONV
    Dado um paciente cuja sessão não tem o escopo "conv.arvore.navegar"
    Então o sistema esconde toda a superfície CONV
    E o sistema não revela nenhuma rota do cuidador
