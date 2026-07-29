# ADR-009 — Catálogo de tipos de gesto (TipoGesto) extensível via UI

> **Data:** 2026-07-27
> **Status:** draft (BA)
> **Decisor:** Danilo Amaral (condutor técnico)

---

## Contexto

A Emenda 2 do LBC expandiu o vocabulário de primitivos faciais de uma lista fechada de 6 valores para um catálogo extensível, editável pela cuidadora/condutor técnico na tela de Calibração (ex.: cadastrar "Leitura labial").

---

## Opções consideradas

### Opção 1 — TipoGesto como entidade própria (id + nome), referenciada por Comando.primitivoPrincipal/Secundario; UI permite cadastro inline

- **Vantagens:**
  - Família pode registrar um movimento residual não previsto originalmente, sem depender de release
  - Já implementado e validado no protótipo
- **Desvantagens:**
  - Cria a possibilidade de cadastrar um tipo que o motor de detecção real ainda não suporta — ver TECH-005 e ADR relacionado ao guardrail necessário
- **Custo:** baixo — é uma tabela de lookup adicional

---

## Decisão

Escolhida: **`TipoGesto` como entidade própria extensível via UI.**.

---

## Justificativa

LBC Emenda 2 §7.2/§12.

---

## Como reverter se a decisão se mostrar errada

Baixo custo — é uma tabela de lookup; reverter para enum fixo é uma migração simples se necessário.

---

## Sinais que indicariam que está errada

- CRÍTICO: cadastrar um TipoGesto na UI não implica suporte real do motor de detecção (TECH-005). Hoje o protótipo não modela um estado "pendente de validação" — o tipo nasce direto como disponível. TODO[arquiteto]: decidir se isso precisa de um campo de status antes de ir para produção real.
