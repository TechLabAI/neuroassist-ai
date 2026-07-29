# ADR-010 — Evento é entidade única; "evento ativo" é uma query, não uma tabela separada

> **Data:** 2026-07-27
> **Status:** draft (BA)
> **Decisor:** Danilo Amaral (condutor técnico)

---

## Contexto

Durante a Fase 0.3 (protótipo), uma primeira versão modelou "evento_ativo" como uma coleção mock separada de "eventos" (histórico). Isso causou um bug real detectado em revisão: confirmar o alarme atualizava apenas a coleção separada, e a tela de Histórico nunca refletia a confirmação, porque liam fontes de dados diferentes para o mesmo conceito.

---

## Opções consideradas

### Opção 1 — Evento como tabela/coleção única; "evento ativo" = evento mais recente com urgência emergência e sem confirmadoEm, filtrado em tempo de leitura (corrigido, estado atual do protótipo)

- **Vantagens:**
  - Uma escrita (confirmar) é vista por todas as telas imediatamente
  - Elimina a classe inteira de bug de dessincronização entre coleções
- **Desvantagens:**
  - Nenhum identificado
- **Custo:** baixo — já corrigido no protótipo

### Opção 2 — Tabela/coleção separada para evento ativo (estado inicial com bug, descartado)

- **Vantagens:**
  - Nenhum — foi o estado que causou o bug
- **Desvantagens:**
  - Confirmar o alarme não refletia no histórico
  - Duas fontes de verdade para o mesmo conceito
- **Custo:** alto — bug de consistência de dados

---

## Decisão

Escolhida: **Opção 1 — Evento é entidade única no schema real; "evento ativo" nunca deve virar uma tabela própria no backend.**.

---

## Justificativa

Corrigido nesta própria fase após detecção do bug; registrado aqui explicitamente para o arquiteto não reintroduzir a separação no schema real do backend.

---

## Como reverter se a decisão se mostrar errada

Não aplicável — já é a correção; não deve ser revertido.

---

## Sinais que indicariam que está errada

- Se qualquer tela mostrar um evento confirmado sem refletir em outra tela (ex: confirmar no Alarme mas Histórico não atualizar), é sinal de que essa separação foi reintroduzida por engano no backend real.
