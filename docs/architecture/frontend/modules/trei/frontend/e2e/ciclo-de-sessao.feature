# language: pt
Funcionalidade: Ciclo de vida da sessao sem perda de rascunhos (INV-4)
  Como cuidadora que encerra o treinamento
  Quero que encerrar a sessao nunca descarte propostas pendentes
  Para que nada de autorado se perca ate o paciente confirmar no CONV

  Contexto:
    Dado que a cuidadora esta autenticada no shell de treinamento com escopo "treinamento.sessao.conduzir"

  Cenario: Encerrar a sessao preserva os rascunhos pendentes
    Dado que a sessao de treinamento esta ativa com propostas pendentes
    Quando a cuidadora encerra a sessao de treinamento
    Entao o sistema exibe o aviso de retencao de rascunhos pendentes
    E o sistema nao descarta nenhuma proposta rascunho pendente

  Cenario: Sair da rota limpa os turnos mas preserva rascunhos
    Dado que a sessao de treinamento esta ativa com propostas pendentes
    Quando a cuidadora navega para fora da rota de treinamento
    Entao o sistema limpa os turnos de conversa em memoria
    E o sistema nao descarta nenhuma proposta rascunho pendente

  Cenario: O aviso de autoria esta presente em toda rota do TREI
    Quando a cuidadora navega pelas rotas do TREI
    Entao o sistema exibe o aviso de autoria "nao e o app do paciente" na rota
    E o sistema nao expoe nenhuma rota de runtime do paciente dentro da subarvore do treinamento

  Cenario: Caminho desconhecido dentro do TREI volta para a sessao
    Quando a cuidadora acessa um caminho desconhecido dentro da subarvore do treinamento
    Entao o sistema redireciona para "/treinamento/sessao"
