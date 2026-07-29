# language: pt
Funcionalidade: Pareamento do dispositivo edge (caminho feliz)
  Como condutor técnico no onboarding,
  quero parear o dispositivo edge ao paciente e confirmar que está genuinamente online.

  Contexto:
    Dado que estou autenticado com a entitlement de condutor
    E que estou na etapa 3 "Dispositivo edge" do onboarding

  Cenário: Registrar, reivindicar código e confirmar primeiro heartbeat
    Quando eu registro o dispositivo
    Então o sistema exibe o token de pareamento exatamente uma vez
    E o sistema inicia o polling pelo primeiro heartbeat
    Quando o agente reivindica o código e o primeiro heartbeat chega fresco
    Então o sistema avança a fase de pareamento para "paired" somente após um primeiro heartbeat real
    E o sistema mostra "Dispositivo pareado · online · versão X"
    E o resultado emitido tem derivedState "online"

  Cenário: Token de pareamento é mostrado apenas uma vez
    Quando eu registro o dispositivo
    Então o sistema exibe o token de pareamento exatamente uma vez
    E o token de pareamento não é exibido novamente após a navegação
