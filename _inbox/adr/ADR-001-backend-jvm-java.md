# ADR-001 — Backend em Java 21 + Spring Boot

> **Data:** 2026-07-27
> **Status:** draft (BA)
> **Decisor:** Danilo Amaral (condutor técnico)

---

## Contexto

O framework techlab-architecture padroniza stack JVM (Java 21 + Spring Boot, arquitetura hexagonal) para todo backend gerado via new-project.py. Essa decisão foi confirmada no pré-voo (Pergunta 0) antes mesmo do LBC, sem avaliação comparativa de alternativas — é herança de plataforma, não escolha deste projeto.

---

## Opções consideradas

### Opção 1 — Java 21 + Spring Boot (padrão do framework)

- **Vantagens:**
  - Compatibilidade total com new-project.py e pipeline /be-architect-*
  - Consistência com o restante do portfólio TechLab
- **Desvantagens:**
  - Nenhuma alternativa avaliada — decisão de plataforma, não deste projeto
- **Custo:** baixo (já é o padrão gerado)

TODO[arquiteto]: investigar alternativas antes de fechar ADR — decisão herdada do pré-voo, nunca comparada com outras stacks.

---

## Decisão

Escolhida: **Java 21 + Spring Boot, arquitetura hexagonal (ver ADR-004), seguindo o padrão do framework sem desvio.**.

---

## Justificativa

Padrão techlab-architecture confirmado no pré-voo; divergir quebraria a geração automatizada (TECH-001).

---

## Como reverter se a decisão se mostrar errada

Trocar de stack exigiria reescrever todo o backend e o pipeline de geração automatizada — alto custo, não recomendado sem motivo técnico forte.

---

## Sinais que indicariam que está errada

- Necessidade de integração nativa com biblioteca de visão computacional que só existe fora da JVM (mas a detecção roda no edge, não no backend — risco baixo)
- Performance da JVM inadequada para volume de eventos (improvável dado o baixo volume de 1 paciente)
