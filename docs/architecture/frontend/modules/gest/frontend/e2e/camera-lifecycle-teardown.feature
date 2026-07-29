# language: pt
Funcionalidade: Ciclo de vida da câmera sem vazamentos
  Como paciente vulnerável,
  a webcam só pode ficar ativa durante um teste explícito e sempre deve ser encerrada ao sair.

  Contexto:
    Dado que existe um DispositivoEdge pareado e online
    E que a superfície de teste ao vivo está no estado "testing" com a webcam ativa

  Cenário: Parar o teste encerra o MediaStream e fecha o bridge
    Quando a cuidadora aciona "Parar"
    Então o sistema cancela a assinatura do stream, para toda track do MediaStream e fecha o edge bridge
    E nenhuma track do MediaStream permanece ativa

  Cenário: Sair da rota encerra a câmera
    Quando a cuidadora navega para fora da página
    Então enquanto o estado não é "testing" o sistema garante que nenhuma track do MediaStream está ativa

  Cenário: Destruição do módulo garante ausência de vazamento de câmera
    Quando o bootstrap do módulo executa "destroy"
    Então o sistema garante que nenhuma track do MediaStream permanece ativa
