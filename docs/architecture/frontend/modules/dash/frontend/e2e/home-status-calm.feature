# language: pt
Funcionalidade: Home Status e honestidade do estado calmo
  Como cuidadora autenticada
  Quero abrir o painel e saber em segundos se o paciente está bem
  Para agir rápido quando houver um pedido de ajuda

  Contexto:
    Dado que o serviço dash-service está disponível na porta 8090

  Cenario: Painel Status é a tela inicial padrão do cuidador autenticado
    Dado que a cuidadora tem uma sessão válida
    Quando ela abre o painel na rota raiz
    Então o sistema renderiza a Home Status como landing padrão do painel

  Cenario: Visitante não autenticado é redirecionado para a entrada
    Dado que não existe sessão de cuidadora válida
    Quando um visitante requisita a rota de Status
    Então o sistema redireciona para a entrada preservando o caminho de retorno

  Cenario: Home carrega o resumo e mostra o estado calmo
    Dado que a cuidadora tem uma sessão válida
    E que o GET /panel/summary responde com o resumo do paciente
    Quando ela entra na Home
    Então o sistema chama GET /panel/summary e renderiza dados novos sem cache
    E o StatusCard mostra o estado calmo "está bem"

  Cenario: Falha ao carregar o resumo mostra erro com tentar novamente
    Dado que a cuidadora tem uma sessão válida
    E que o GET /panel/summary responde 503 upstream indisponível
    Quando ela entra na Home
    Então o StatusCard mostra uma mensagem não técnica de erro com botão de tentar novamente
    E a estrutura do shell permanece intacta

  Cenario: Emergência não confirmada impede o estado calmo na Home
    Dado que a cuidadora está na Home
    E que o EVNT reporta um activeEvent de emergência não confirmada
    Quando a Home projeta o estado calm
    Então o sistema mantém calm falso e não renderiza o estado calmo "está bem"

  Cenario: Seção de dispositivo degradada mostra desconhecido mantendo a Home calma
    Dado que a cuidadora está na Home
    E que o resumo marca a seção device como degradada
    Quando a Home renderiza o paciente
    Então o sistema mostra o chip de dispositivo "desconhecido" e mantém a Home calma
