# ADR-007 — Tema claro como padrão de UI (exceção ao Design System canônico dark-only)

> **Data:** 2026-07-27
> **Status:** draft (BA)
> **Decisor:** Danilo Amaral (condutor técnico)

---

## Contexto

O Design System canônico do framework techlab-architecture é dark-only por padrão em todos os projetos gerados. Durante a Fase 0.3 (UX), identificou-se que a persona deste produto (cuidadora não-técnica em ambiente doméstico, uso periférico e esporádico) se encaixa melhor num tema calmo e claro por padrão ("guardião silencioso"), com o escuro como toggle opcional — não o contrário.

---

## Opções consideradas

### Opção 1 — Tema claro como padrão, com toggle para escuro

- **Vantagens:**
  - Alinhado à persona doméstica não-técnica (UI Concept §5)
  - Reduz ansiedade visual no uso cotidiano, reservando intensidade só para o alarme real
- **Desvantagens:**
  - Diverge do Design System canônico dark-only do framework
  - Hoje não existe mecanismo oficial de exceção — o gerador wireframe-to-angular ignora design/design-system/theme.css e sempre aplica o tema dark-only canônico, exigindo patch manual a cada regeneração
- **Custo:** médio — exige manutenção manual do theme.css a cada vez que o protótipo é regenerado, até o framework suportar exceção de tema por projeto

### Opção 2 — Dark-only (padrão do framework, sem exceção)

- **Vantagens:**
  - Sem manutenção manual, sempre consistente com o gerador
- **Desvantagens:**
  - Não alinhado à persona doméstica; UI Concept §5 recomenda calma/claro como padrão
- **Custo:** baixo técnico, alto em adequação de produto

---

## Decisão

Escolhida: **Opção 1 — tema claro como padrão, documentado como exceção em theme.css e no wireframe §3.1.**.

---

## Justificativa

UI Concept §5 (cromática sóbria/calma) e a persona real (Jamilly Amaral, cuidadora doméstica sem perfil de usuária de dashboard técnico).

---

## Como reverter se a decisão se mostrar errada

Esforço médio — reverter para dark-only exigiria descartar os tokens de tema claro já validados no protótipo (~150 linhas de tokens costurados).

---

## Sinais que indicariam que está errada

- Se o framework padronizar um mecanismo oficial de exceção de tema por projeto, esta implementação manual deve migrar para o mecanismo oficial, evitando que --force apague o tema claro a cada regeneração (já aconteceu 2x durante a Fase 0.3 deste projeto)
