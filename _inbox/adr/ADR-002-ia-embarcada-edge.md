# ADR-002 — IA embarcada no dispositivo edge, não como serviço cloud dedicado

> **Data:** 2026-07-27
> **Status:** draft (BA)
> **Decisor:** Danilo Amaral (condutor técnico)

---

## Contexto

Muitos projetos do framework rodam IA como serviço dedicado no backend cloud. Neste produto, a detecção de gesto (visão computacional) precisa rodar localmente no dispositivo do paciente — não há chamada de IA ao backend.

---

## Opções consideradas

### Opção 1 — IA 100% embarcada no edge (GestureDetectionEngine local)

- **Vantagens:**
  - Nenhum vídeo sai do dispositivo (privacidade)
  - Funciona offline (alarme não depende de internet)
  - Baixa latência (sem round-trip de rede)
- **Desvantagens:**
  - Atualização de modelo exige deploy no próprio dispositivo edge, não é centralizada
  - Sem reuso fácil de infraestrutura de IA compartilhada entre pacientes
- **Custo:** médio — exige empacotamento do motor de detecção para rodar localmente

### Opção 2 — IA como serviço cloud dedicado, recebendo stream de vídeo

- **Vantagens:**
  - Mais fácil atualizar o modelo centralmente
- **Desvantagens:**
  - Viola TECH-002 (nenhum vídeo pode sair do edge)
  - Aumenta latência (round-trip de rede)
  - Depende de conectividade — viola OPS-001 (alarme deve funcionar offline)
- **Custo:** alto — violaria duas constraints não-negociáveis

---

## Decisão

Escolhida: **IA embarcada no edge (Opção 1).**.

---

## Justificativa

TECH-002 e OPS-001 são não-negociáveis; a Opção 2 violaria as duas diretamente.

---

## Como reverter se a decisão se mostrar errada

Migrar para IA cloud exigiria relaxar TECH-002 e OPS-001 — mudança de escopo de produto e de privacidade, não apenas técnica.

---

## Sinais que indicariam que está errada

- Dispositivo edge sem capacidade computacional suficiente para rodar o modelo localmente respeitando TECH-003 (<200ms)
