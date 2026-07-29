# language: pt
Funcionalidade: Tratamento de erros do backend
  Como frontend,
  devo mapear cada ProblemDetail do edge-service para a superfície correta, sem vazar dados sensíveis.

  Cenário: Erro de validação exibe banner inline
    Quando o backend responde "/errors/validation"
    Então o sistema exibe um banner inline pedindo para o operador conferir os dados do dispositivo

  Cenário: Escopo insuficiente exibe toast
    Quando o backend responde "/errors/insufficient-scope"
    Então o sistema exibe um toast informando que a ação no dispositivo não é permitida

  Cenário: Dispositivo aposentado exibe banner
    Quando o backend responde "/errors/device-retired"
    Então o sistema exibe um banner informando que o dispositivo foi aposentado e não pode ser usado

  Cenário: Paciente sem dispositivo pareado não gera diálogo de erro
    Quando o backend responde "/errors/patient-not-paired" no caminho de liveness
    Então o sistema renderiza o chip de conectividade como "not-paired"
    E o sistema não gera um diálogo de erro

  Cenário: Nenhum dado sensível aparece em erros
    Quando qualquer erro do backend é exibido
    Então o sistema não inclui nenhum segredo de credencial, vídeo, imagem ou landmark na mensagem de erro ou no log
