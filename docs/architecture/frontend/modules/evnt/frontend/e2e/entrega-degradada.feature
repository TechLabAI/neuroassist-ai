# language: pt
Funcionalidade: Entrega em modo degradado com fallback de polling
  Como cuidadora
  Quero que a queda do canal live não perca nenhum alarme
  Para que a garantia de segurança se mantenha mesmo sem SSE

  Contexto:
    Dado que estou autenticada como cuidadora vinculada ao paciente
    E que o canal live de eventos está ativo

  Cenário: Queda do canal live entra em modo degradado sem perder alarme
    Quando o canal live (SSE) cai
    Então o sistema entra em modo degradado
    E passa a consultar GET /events/active a cada intervalo configurado
    E um banner de modo degradado é exibido
    Quando surge um novo evento de emergência durante o modo degradado
    Então o alarme de emergência é exibido pelo polling sem ser perdido
    Quando o canal live reconecta
    Então o sistema volta ao modo live e para o polling
