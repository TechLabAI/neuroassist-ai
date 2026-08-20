# language: pt
Funcionalidade: Histórico — registro de feedback (veredito) não-otimista
  Como cuidador revisando o histórico
  Quero marcar um evento como real ou falso positivo
  Para que o veredito só apareça após a confirmação do backend (trilha de auditoria confiável)

  Contexto:
    Dado que estou autenticado como cuidador com vínculo ao paciente
    E que a linha do tempo do período "hoje" está carregada com registros

  Cenário: Marcar veredito com sucesso mostra estado terminal
    Dado que um item está com marcação não avaliada
    Quando eu confirmo o veredito "real" no item
    Então o sistema mostra um indicador de progresso por item enquanto registra
    E o sistema define o veredito do item somente após o backend confirmar (não-otimista)
    E o item exibe o chip terminal "✓ Marcado real" com texto e ícone
    E a ação de feedback fica desabilitada para o item

  Cenário: Falha na escrita de feedback mantém o item não avaliado
    Dado que um item está com marcação não avaliada
    Quando a escrita de feedback falha com erro de rede
    Então o sistema coloca o item no estado mark-error e o mantém não avaliado
    E o sistema oferece uma nova tentativa por item
    E o item nunca é exibido como marcado

  Cenário: Conflito 409 reconcilia o item com o veredito do backend
    Dado que um item já foi marcado por outro cuidador
    Quando a escrita de feedback retorna 409 já terminal
    Então o sistema reconcilia o item com o veredito do backend
    E o sistema mostra "Já marcado por {nome} às {hora}" sem sobrescrita pelo cliente

  Cenário: Selo de verificação da cadeia visível para coordenador
    Dado que estou autenticado como coordenador com escopo hist:read:all
    Quando a linha do tempo é renderizada
    Então o sistema mostra o selo de verificação da cadeia refletindo o resultado intact, broken ou empty

  Cenário: Selo de verificação da cadeia oculto para cuidador comum
    Dado que estou autenticado como cuidador com escopo hist:read apenas
    Quando a linha do tempo é renderizada
    Então o sistema oculta o selo de verificação da cadeia
