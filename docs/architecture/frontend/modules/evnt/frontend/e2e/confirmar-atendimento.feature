# language: pt
Funcionalidade: Confirmar atendimento de um alarme de emergência
  Como cuidadora
  Quero confirmar que atendi Márcio
  Para que o alarme só seja limpo depois do registro (invariante de segurança)

  Contexto:
    Dado que estou autenticada como cuidadora vinculada ao paciente
    E que um alarme de emergência está ativo

  Cenário: Confirmar atendimento limpa o alarme após sucesso
    Quando eu pressiono "Confirmar que atendi Márcio"
    E o POST /events/{id}/confirmation responde 200
    Então o alarme de emergência é limpo
    E permaneço na rota em que eu estava

  Cenário: Falha de rede ao confirmar mantém o alarme ativo com retry inline
    Quando eu pressiono "Confirmar que atendi Márcio"
    E o POST /events/{id}/confirmation falha por erro de rede
    Então o alarme de emergência permanece ativo
    E uma mensagem inline de retry é exibida
    E nenhum toast substitui a mensagem inline
