# language: pt
Funcionalidade: Erro de permissão de câmera com recuperação
  Como cuidadora não técnica,
  quero uma mensagem clara e uma ação de recuperação quando a webcam não puder ser acessada.

  Cenário: Permissão de câmera negada entra em estado camera-error
    Dado que a página de calibração compõe o GestureLiveTestComponent inline
    Quando a cuidadora aciona "Testar agora"
    E o getUserMedia é negado
    Então o sistema entra no estado "camera-error"
    E o sistema exibe uma mensagem de recuperação com a ação "Tentar de novo"
    E o sistema não deixa a webcam ligada

  Cenário: Câmera não é solicitada no carregamento
    Dado que a superfície de teste ao vivo acaba de carregar
    E que a cuidadora ainda não acionou "Testar agora"
    Então o sistema não solicita a câmera
