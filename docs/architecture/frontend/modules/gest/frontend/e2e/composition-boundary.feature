# language: pt
Funcionalidade: Composição no scope:web e disciplina de fronteira
  O live-test é uma feature embutida, composta no shell scope:web, sem rota de navegação própria.

  Cenário: Feature renderizada inline sem rota de navegação dedicada
    Dado que a cuidadora navega para uma página que compõe a feature de teste ao vivo
    Então o sistema renderiza o GestureLiveTestComponent inline sem uma rota de navegação GEST dedicada
    E o sistema omite o GEST do menu de navegação do shell

  Cenário: Composição resolve o slot sem import direto de scope:gest
    Dado uma página host em scope:web
    Quando a página host solicita o slot de teste ao vivo
    Então o sistema resolve o GestureLiveTestComponent através do GestShellIntegrationService sem exigir um import direto de scope:gest do PAC ou ACS

  Cenário: Feature composta não contribui breadcrumb
    Dado que a feature de teste ao vivo está composta dentro de uma página host do PAC ou onboarding
    Então enquanto composta o sistema não contribui breadcrumb, deixando a trilha com a página host

  Cenário: Sessão não autenticada no host thin redireciona para login
    Dado que a sessão não está autenticada
    Quando a rota thin-host "/gest" é acessada
    Então o sistema redireciona para a rota de login e não monta o componente de teste ao vivo

  Cenário: Bootstrap init negocia contractVersion sem abrir a câmera
    Quando o bootstrap do módulo executa "init"
    Então o sistema negocia a versão do contrato de detecção e não abre a câmera
    E o sistema expõe o GEST_MODULE_DESCRIPTOR pelo barrel para registro no shell
