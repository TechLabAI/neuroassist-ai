# language: pt
@conv @tela09 @proposta @fixme
Funcionalidade: Confirmar novas frases propostas (Tela 09)
  Como paciente
  Quero confirmar, ajustar ou recusar frases que a cuidadora aprovou
  Para decidir o que passa a fazer parte da minha árvore de falas

  # @fixme — depende de um endpoint de propostas no conv-service ainda ausente
  # na api-contract v1 (list-pending / confirm / adjust / reject). Ver
  # frontend-spec/contracts/backend-api-cross-link.md §Proposals. Enquanto o
  # contrato BE não existe, estes cenários rodam sobre o MockAdapter.

  Contexto:
    Dado um paciente com sessão ativa no idioma ativo "pt"
    E a lista de propostas pendentes com confirmadoCuidadora verdadeiro e confirmadoPaciente falso

  @confirmar
  Cenário: Confirmar uma proposta de forma otimista
    Quando eu confirmo a proposta "Quero água com gelo"
    Então o sistema remove a proposta da lista pendente imediatamente
    E o status da confirmação fica "saving" durante a escrita
    E o sistema reconcilia a proposta no ack do servidor

  @ajustar
  Cenário: Ajustar escolhe uma variante curta sem texto livre
    Quando eu escolho ajustar a proposta "Quero água com gelo"
    Então o sistema oferece apenas variantes curtas
    E o sistema não apresenta nenhum campo de texto livre
    Quando eu escolho a variante "Quero água gelada"
    Então o sistema envia a variante escolhida

  @rollback
  Cenário: Falha na escrita re-insere o nó pendente
    Quando eu confirmo a proposta "Quero água com gelo"
    E a escrita da confirmação falha
    Então o sistema re-insere o nó na lista pendente
    E o sistema mostra uma mensagem não bloqueante
    E o nó permanece pendente até uma escrita bem-sucedida
