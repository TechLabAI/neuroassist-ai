# ADR-008 — Evento referencia Comando dinamicamente, não um enum fixo de tipo

> **Data:** 2026-07-27
> **Status:** draft (BA)
> **Decisor:** Danilo Amaral (condutor técnico)

---

## Contexto

A v1 do produto tinha um único tipo de evento (`HELP_REQUESTED` fixo). A Emenda 1 do LBC expandiu o núcleo do produto para um catálogo de N comandos por paciente — Evento precisa referenciar qual comando foi disparado, não um enum fechado.

---

## Opções consideradas

### Opção 1 — Evento.comando como referência a Comando (implementada e validada no protótipo)

- **Vantagens:**
  - Suporta catálogo extensível de comandos sem alterar schema a cada novo comando
  - Urgência e rótulo do evento vêm sempre do Comando referenciado, sem duplicação
- **Desvantagens:**
  - Migração de dados necessária se já existisse produção com o enum antigo (não é o caso — MVP ainda não foi ao ar)
- **Custo:** baixo — mudança já implementada no protótipo

TODO[arquiteto]: investigar se o schema definitivo do backend deve manter Comando como tabela própria ou desnormalizar rótulo/urgência direto no Evento por performance — decisão de implementação, não avaliada aqui.

---

## Decisão

Escolhida: **`Evento.comando` é uma referência (FK) a `Comando`, substituindo o antigo `Evento.tipo` enum fixo em `HELP_REQUESTED`.**.

---

## Justificativa

LBC Emenda 1 §7.2; já validado funcionando no protótipo com 3 comandos reais (Ajuda/água/Sapo).

---

## Como reverter se a decisão se mostrar errada

Baixo-médio custo — é uma migração de coluna (enum → FK) direta em banco relacional, caso precise reverter.

---

## Sinais que indicariam que está errada

- Nenhum — já validado no protótipo end-to-end (Home, Histórico, Alarme, Calibração todos leem/escrevem Evento.comando consistentemente)
