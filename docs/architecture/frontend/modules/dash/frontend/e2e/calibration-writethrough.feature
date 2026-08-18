# language: pt
Funcionalidade: Ações write-through do cuidador
  Como cuidadora autenticada
  Quero editar calibração e confirmar eventos pelo painel
  Para ajustar a sensibilidade e dar veredito, sem o painel virar fonte de verdade

  Contexto:
    Dado que a cuidadora tem uma sessão válida

  Cenario: Editar calibração encaminha para o PAC e retorna o resultado autoritativo
    Dado que a cuidadora abre a calibração de um comando do paciente
    Quando ela salva uma edição de calibração
    Então o sistema faz PUT em /panel/patients/{patientId}/commands/{commandId}/calibration
    E não armazena nenhum valor de calibração localmente

  Cenario: Calibração inválida mostra erro de campo sem reenviar
    Dado que a cuidadora abre a calibração de um comando do paciente
    Quando ela salva uma calibração inválida e o backend responde 422 validação
    Então o sistema mostra um erro de campo inline e não reenvia automaticamente

  Cenario: PAC rejeita a calibração e a mensagem do serviço dono é repassada
    Dado que a cuidadora salva uma edição de calibração
    Quando o PAC rejeita a calibração como upstream-rejected
    Então o sistema mostra um toast com a mensagem repassada do serviço dono e não armazena o valor localmente

  Cenario: Confirmar um evento encaminha o veredito para o EVNT
    Dado que existe um evento não avaliado do paciente
    Quando a cuidadora confirma o evento com o veredito real
    Então o sistema encaminha o veredito de confirmação para o EVNT e retorna o resultado do EVNT
