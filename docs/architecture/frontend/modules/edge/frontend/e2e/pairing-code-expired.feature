# language: pt
Funcionalidade: Código de pareamento expirado
  Como sistema honesto de pareamento,
  não devo travar aguardando um código que já expirou.

  Cenário: TTL do token de enrollment expira antes da reivindicação
    Dado que registrei o dispositivo e recebi um token de pareamento
    Quando o TTL do token de enrollment expira antes de uma reivindicação
    Então o sistema define a fase de pareamento como "expired"
    E o sistema exige um novo registro para gerar um novo código

  Cenário: Backend rejeita um código inválido ou expirado
    Dado que informei um código de pareamento inválido ou expirado
    Quando o backend responde "/errors/enrollment-invalid"
    Então o sistema exibe um banner de pareamento pedindo para gerar um novo código
    E o sistema não tenta novamente o pareamento automaticamente
