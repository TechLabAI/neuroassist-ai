# ADR-005 — Frontend em Angular 21 + Nx 22 + Tailwind v4 + Angular CDK

> **Data:** 2026-07-27
> **Status:** draft (BA)
> **Decisor:** Danilo Amaral (condutor técnico)

---

## Contexto

Padrão do framework, já implementado integralmente no protótipo da Fase 0.3: 6 telas (Entrada, Home, Alarme, Histórico, Calibração, Onboarding), build limpo, tema claro/escuro funcional.

---

## Opções consideradas

### Opção 1 — Angular 21 + Nx 22 + Tailwind v4 + Angular CDK (padrão do framework)

- **Vantagens:**
  - Já validado no protótipo funcionando
  - CDK cobre overlay/dialog/menu sem SCSS customizado
  - Consistência com o restante do portfólio
- **Desvantagens:**
  - Nenhuma alternativa avaliada — decisão de plataforma
- **Custo:** baixo (protótipo já entregue)

TODO[arquiteto]: investigar alternativas antes de fechar ADR — decisão de plataforma, não comparada a outros frameworks frontend.

---

## Decisão

Escolhida: **Angular 21 + Nx 22 + Tailwind v4 + Angular CDK, sem desvio do padrão.**.

---

## Justificativa

Padrão techlab-architecture; protótipo da Fase 0.3 já valida a stack funcionando de ponta a ponta.

---

## Como reverter se a decisão se mostrar errada

Baixo custo — é a camada de apresentação; lógica de domínio no backend não é afetada por troca de frontend.

---

## Sinais que indicariam que está errada

- Nenhum sinal de erro identificado — protótipo com build limpo em todas as telas
