# language: pt
Funcionalidade: Composição do chip de conectividade no shell
  Como plataforma,
  o chip de conectividade deve estar sempre visível e ser composto sem violar as fronteiras nx.

  Cenário: A topbar consome o read-model de conectividade em qualquer rota autenticada
    Dado que estou autenticado em uma rota qualquer
    Quando a topbar do shell solicita a conectividade
    Então o sistema fornece o read-model DeviceConnectivityVM derivado através do EdgeShellIntegrationService
    E o sistema não exige um import direto de scope:edge nas internas do shell

  Cenário: Clique no chip navega para o detalhe do dispositivo
    Quando a cuidadora aciona o chip de conectividade da topbar
    Então o sistema navega para a rota de detalhe do dispositivo do dispositivo ativo

  Cenário: EDGE fica fora do menu de navegação
    Então o sistema omite EDGE do menu de navegação do shell
    E o sistema mostra a conectividade apenas através do chip sempre visível da topbar

  Cenário: Sessão não autenticada é redirecionada
    Dado que a sessão não está autenticada
    Quando qualquer rota "/edge" é acessada
    Então o sistema redireciona para a rota de login
    E o sistema não monta o componente de dispositivo ou de pareamento

  Cenário: Bootstrap inicia e encerra o polling sem vazar timer
    Quando o bootstrap init do módulo executa
    Então o sistema resolve o dispositivo do paciente ativo e inicia o polling de conectividade pausado enquanto a aba está oculta
    Quando o bootstrap destroy do módulo executa
    Então o sistema encerra o timer de polling de conectividade para que nenhum intervalo vaze
