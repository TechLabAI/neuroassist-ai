# ADR-006 — Arquitetura híbrida edge + cloud

> **Data:** 2026-07-27
> **Status:** draft (BA)
> **Decisor:** Danilo Amaral (condutor técnico)

---

## Contexto

Tensão registrada no LBC §12/§13 entre a visão original 100% offline (desktop JavaFX) e o pivot de negócio para SaaS multi-tenant.

---

## Opções consideradas

### Opção 1 — Híbrida: detecção+alarme no edge, sincronização de eventos estruturados com o cloud quando há conectividade

- **Vantagens:**
  - Preserva privacidade (TECH-002) e alarme offline (OPS-001)
  - Viabiliza dashboard SaaS multi-tenant
- **Desvantagens:**
  - Complexidade de duas camadas deployáveis independentes
  - Exige fila local de retry (outbox) no edge
- **Custo:** médio

### Opção 2 — Tudo na nuvem (vídeo em streaming)

- **Vantagens:**
  - Arquitetura mais simples de um só lado
- **Desvantagens:**
  - Viola TECH-002 e OPS-001 diretamente
- **Custo:** alto — inviável dadas as constraints

### Opção 3 — Tudo local, sem cloud

- **Vantagens:**
  - Mais simples, sem infraestrutura cloud
- **Desvantagens:**
  - Sem dashboard SaaS multi-tenant nem histórico compartilhado — contradiz o pivot de negócio do LBC
- **Custo:** alto — contradiz objetivo de negócio

---

## Decisão

Escolhida: **Arquitetura híbrida edge + cloud (Opção 1).**.

---

## Justificativa

Única opção compatível simultaneamente com TECH-002, OPS-001 e a necessidade de dashboard SaaS do LBC §6.1.

---

## Como reverter se a decisão se mostrar errada

Alto custo — é a espinha dorsal da arquitetura; reverter significa re-arquitetar o produto inteiro.

---

## Sinais que indicariam que está errada

- Fila local de retry (outbox) saturando sem drenagem sistemática — considerar broker assíncrono (RabbitMQ), já citado como TODO no Framing §11.2
