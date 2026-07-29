# language: pt
Funcionalidade: Confirmação concorrente de um alarme
  Como cuidadora em uma clínica com múltiplos cuidadores
  Quero que uma confirmação já feita por outra pessoa encerre o alarme
  Para que o objetivo de segurança (alguém atendeu) seja tratado como sucesso

  Contexto:
    Dado que estou autenticada como cuidadora vinculada ao paciente
    E que um alarme de emergência está ativo

  Cenário: Evento já confirmado por outro cuidador limpa o alarme como equivalente a sucesso
    Quando eu pressiono "Confirmar que atendi Márcio"
    E o POST /events/{id}/confirmation responde 409 feedback-terminal
    Então o sistema exibe "Já confirmado por" com o nome e a hora do outro cuidador
    E o alarme de emergência é limpo
