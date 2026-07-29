# language: pt
Funcionalidade: Gerenciamento do dispositivo (reparear / desparear / aposentar)
  Como condutor,
  quero substituir ou remover um dispositivo com falha, respeitando o estado do servidor.

  Cenário: Condutor despareia e o read-model é atualizado pelo servidor
    Dado que estou autenticado com a entitlement de condutor
    E que abri o detalhe de um dispositivo pareado
    Quando eu desparei o dispositivo com um motivo
    Então o sistema atualiza o read-model do dispositivo em vez de virar o estado local otimisticamente

  Cenário: Conflito de pareamento é tratado com atualização
    Dado que estou reparelhando um dispositivo
    Quando o backend responde "/errors/pairing-conflict"
    Então o sistema exibe um banner explicando que o paciente já tem um dispositivo ativo
    E o sistema não tenta novamente o pareamento automaticamente

  Cenário: Lock otimista recarrega o estado atual
    Quando o backend responde "/errors/optimistic-lock"
    Então o sistema atualiza o read-model do dispositivo e reapresenta o estado atual do servidor

  Cenário: Cuidadora sem entitlement não vê ações de gerenciamento
    Dado que estou autenticado como cuidadora sem a permissão "edge.device.manage"
    Quando abro o detalhe do dispositivo
    Então o sistema oculta os controles de reparear, desparear e aposentar
