# language: pt

Funcionalidade: Invariante de não-cache entre execuções de teste ao vivo
  # Fecha o gap SPEC-04 §5 identificado em M4-fe (Gate 2 coverage: 32/33 → 33/33)
  # Cobre EARS: "THE SYSTEM SHALL NOT cache or replay any recognition stream between test runs."

  Contexto:
    Dado que o cuidador está autenticado na plataforma NeuroAssist AI
    E um "DispositivoEdge" está online e pareado com o paciente

  Cenário: Execução de novo teste não reproduz sinais da execução anterior
    Dado que o cuidador iniciou e concluiu um teste ao vivo para o comando "rotina-agua"
    E o sinal "reconhecido" foi emitido pelo bridge durante a primeira execução
    E o cuidador interrompeu o teste
    Quando o cuidador inicia uma nova execução de teste ao vivo para o mesmo comando
    Então o sistema NÃO DEVE exibir o status "reconhecido" herdado da execução anterior
    E o sistema DEVE abrir uma nova subscrição ao stream do bridge (não reutilizar a anterior)
    E o estado inicial DEVE ser "aguardando" (idle)

  Cenário: Façade não reutiliza stream após stopTest() e startTest()
    Dado que o cuidador completou uma sequência startTest → stopTest
    Quando startTest é chamado novamente com o mesmo rascunho de comando
    Então o EdgeDetectionClient DEVE ter sido chamado para abrir um novo stream
    E o stream anterior DEVE estar encerrado (todos os tracks com readyState "ended")
    E nenhum sinal da execução anterior DEVE ser processado pelo GestureTestFacade

  Cenário: Sinal em atraso de execução anterior não afeta nova execução
    Dado que o cuidador encerrou uma execução de teste ao vivo
    Mas um sinal "reconhecido" chega após o encerramento (sinal atrasado)
    Quando o cuidador inicia uma nova execução de teste ao vivo
    Então o sistema NÃO DEVE processar o sinal atrasado como parte da nova execução
    E o estado da nova execução DEVE permanecer "aguardando" até que um sinal da nova subscrição chegue
