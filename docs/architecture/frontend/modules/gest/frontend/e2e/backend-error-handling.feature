# language: pt
Funcionalidade: Tratamento de erros do backend (ProblemDetail) no host da feature
  Erros REST do backend são mapeados para mensagens user-facing em pt-BR, sem dados biométricos.

  Cenário: Erro de validação exibe banner inline
    Quando o backend responde "/errors/validation"
    Então o sistema exibe um banner inline pedindo para conferir os dados do comando

  Cenário: Tenant mismatch exibe modal de reautenticação
    Quando o backend responde "/errors/tenant-mismatch"
    Então o sistema exibe um modal bloqueante solicitando reautenticação

  Cenário: Escopo insuficiente exibe toast
    Quando o backend responde "/errors/insufficient-scope"
    Então o sistema exibe um toast informando que a ação não é permitida

  Cenário: Primitivo desconhecido exibe banner de reconfiguração
    Quando o backend responde "/errors/unknown-primitive"
    Então o sistema exibe um banner pedindo para reconfigurar o comando

  Cenário: Nenhum dado biométrico em erro ou log
    Quando qualquer erro é exibido ou registrado
    Então o sistema não inclui vídeo, imagem, landmark ou confidence bruto na mensagem de erro ou log
