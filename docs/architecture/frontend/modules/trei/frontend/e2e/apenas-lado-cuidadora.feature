# language: pt
Funcionalidade: TREI escreve apenas o lado cuidadora da aprovacao (INV-1)
  Como guarda da consideracao de duas chaves
  Quero que nenhuma rota do TREI escreva o lado paciente
  Para que o consentimento do paciente permaneca exclusivo do CONV (ADR-007)

  Contexto:
    Dado que a cuidadora esta autenticada no shell de treinamento

  Cenario: O indicador do lado paciente e somente leitura
    Dado que existe um no rascunho materializado
    Quando a cuidadora ve o cartao da proposta
    Entao o sistema expoe o campo paciente aprovado como somente leitura
    E o sistema nao oferece nenhum controle que escreva o lado paciente da proposta

  Cenario: Nenhuma chamada ao endpoint de aprovacao do paciente
    Dado que a cuidadora aprova o lado cuidadora de um no
    Quando o sistema envia a aprovacao
    Entao o sistema nao chama o endpoint de aprovacao do paciente "POST /api/v1/trei/nos/{noId}/aprovacoes/paciente"
    E o sistema chama apenas "POST /api/v1/trei/nos/{noId}/aprovacoes/cuidadora"

  Cenario: Sem escopo de aprovacao o botao aprovar fica desabilitado
    Dado que a sessao da cuidadora nao tem escopo "treinamento.no.aprovar.cuidadora"
    Quando a cuidadora ve os cartoes de proposta
    Entao o sistema desabilita a acao Aprovar em cada cartao de proposta
