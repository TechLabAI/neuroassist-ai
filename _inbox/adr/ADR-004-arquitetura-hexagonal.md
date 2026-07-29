# ADR-004 — Arquitetura hexagonal (ports & adapters)

> **Data:** 2026-07-27
> **Status:** draft (BA)
> **Decisor:** Danilo Amaral (condutor técnico)

---

## Contexto

Padrão do framework para todo backend gerado. O protótipo frontend já segue o mesmo princípio (Port/MockAdapter/Facade em core/data-access.ts), preparado para o fe-autopilot trocar por HttpAdapters.

---

## Opções consideradas

### Opção 1 — Hexagonal (ports & adapters)

- **Vantagens:**
  - Consistência com o portfólio TechLab
  - Facilita troca futura de IA embarcada por serviço cloud sem reescrever o domínio
  - Frontend já segue o mesmo princípio no protótipo
- **Desvantagens:**
  - Overhead de abstração para um domínio pequeno (1 paciente)
- **Custo:** baixo (já é o padrão gerado)

TODO[arquiteto]: investigar alternativas antes de fechar ADR — decisão de plataforma, não comparada a outras arquiteturas.

---

## Decisão

Escolhida: **Arquitetura hexagonal no backend, espelhando o padrão Port/Adapter/Facade já usado no protótipo frontend.**.

---

## Justificativa

Consistência com o framework; facilita evolução futura (ex: multi-paciente, múltiplas clínicas) sem reescrever regras de negócio.

---

## Como reverter se a decisão se mostrar errada

Baixo custo — a violação de camadas fica isolada em adapters, não no domínio.

---

## Sinais que indicariam que está errada

- Complexidade de portas/adaptadores desproporcional ao tamanho real do domínio (1 paciente) — sinal fraco, não esperado no curto prazo
