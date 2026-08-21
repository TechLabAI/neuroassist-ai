# language: pt
Funcionalidade: Histórico — linha do tempo por período
  Como cuidador autenticado com vínculo ao paciente
  Quero revisar o histórico de eventos filtrado por período
  Para acompanhar a trilha de auditoria com rótulos legíveis

  Contexto:
    Dado que estou autenticado como cuidador com vínculo ao paciente
    E que o interceptor anexa o cabeçalho X-Empresa-Id do tenant ativo

  Cenário: Deep-link do Início abre o histórico de hoje
    Quando eu acesso a rota "/historico?period=hoje"
    Então o sistema aplica o período "hoje" e reflete o período na URL
    E a linha do tempo carrega os registros ordenados do mais recente para o mais antigo

  Cenário: Rota sem período aplica o período padrão
    Quando eu acesso a rota "/historico" sem parâmetro period
    Então o sistema aplica o período padrão "hoje"
    E a URL passa a refletir o período ativo

  Cenário: Cada linha mostra o rótulo legível e o selo de urgência
    Dado que o período "hoje" possui registros
    Quando a linha do tempo é renderizada
    Então cada linha exibe o horário, o rótulo legível do comando, o selo de urgência e a linha de confirmação
    E o sistema não exibe o código técnico factType como rótulo principal

  Cenário: Trocar de período reinicia a paginação
    Dado que estou vendo o período "hoje"
    Quando eu seleciono a aba de período "semana"
    Então o sistema reinicia a paginação para a página 0 e recarrega a linha do tempo do novo período
    E o esqueleto de carregamento aparece enquanto o novo período carrega

  Cenário: Período vazio mostra estado tranquilizador
    Dado que o período "mes" não possui eventos
    Quando a linha do tempo é carregada
    Então o sistema mostra o estado vazio "Nenhum evento neste período — ótimo sinal"
    E o sistema não mostra um erro nem uma tela em branco

  Cenário: Falha de carregamento mostra erro com nova tentativa
    Dado que a consulta da linha do tempo retorna um erro de rede
    Quando a linha do tempo tenta carregar
    Então o sistema renderiza um cartão de erro embutido com a ação de nova tentativa
    E o sistema não mostra tela em branco nem spinner infinito

  Cenário: Carregar mais anexa a próxima página
    Dado que o período possui mais resultados do que uma página
    Quando eu aciono "carregar mais"
    Então o sistema pagina os resultados e anexa a próxima página em vez de buscar todo o histórico

  Cenário: Guarda de vínculo bloqueia acesso sem vínculo
    Dado que meu vínculo cuidador-paciente não pode ser confirmado no tenant ativo
    Quando eu acesso a rota "/historico"
    Então o histGuard redireciona para "/sem-acesso" e a linha do tempo não é renderizada
