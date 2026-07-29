# NeuroAssist AI — Angular Prototype

Workspace gerado automaticamente pelo `wireframe-to-angular` a partir do `wireframe.md`.

## Início rápido

```bash
npm install
npm audit fix          # corrige CVEs auto-fixáveis nas deps de build
npx nx serve neuro-assist-ai-web
```

Acesse `http://localhost:4200` após o servidor iniciar.

> **Sobre vulnerabilidades de npm:** as CVEs reportadas por `npm audit` estão em
> **devDependencies** (build tooling do Angular/Nx) e **não afetam o bundle de produção**.
> O `package.json` inclui uma seção `overrides` que força versões corrigidas das deps
> transitivas mais críticas (`tar`, `semver`, `micromatch`, `path-to-regexp`).
> Execute `npm audit fix` após o install para aplicar as correções restantes.

## Estrutura de estilos (Tailwind v4 + Design System)

Sem SCSS. Os tokens do design system vivem em `theme.css` (bloco `@theme`, oklch — ver `plans/design-system/FORMAT.md §2`) e o Tailwind v4 os transforma em utilitários (`--color-brand` → `bg-brand`; `--radius-card` → `rounded-card`; `--font-display` → `font-display`).

1. **Tokens** (`apps/<proj>-web/src/theme.css`) — espelho de `design/design-system/theme.css`. `styles.css` faz `@import "tailwindcss"; @import "./theme.css"`.
2. **Biblioteca DS** (`apps/<proj>-web/src/app/ui/`) — componentes standalone token-driven: `ds-button`, `ds-badge`, `ds-card`, `ds-kpi`, `ds-input` (fiéis a `plans/design-system/ds/components.jsx`). Componentes curados à mão (`tab-bar`, `modal`) convivem aqui e **não** são sobrescritos pela geração.
3. **Shell e Telas** (`app/shell/`, `app/screens/<id>/`) — componentes standalone com utilitários Tailwind inline; as telas compõem a partir da biblioteca DS.

## Telas

| ID | Label | Rota | Componente | Wireframe ref |
|---|---|---|---|---|
| `entrada` | Entrada | `/entrada` | `EntradaComponent` | §5.1–5.5 |
| `home` | Home | `/home` | `HomeComponent` | §5.1–5.5 |
| `alarme` | Alarme | `/alarme` | `AlarmeComponent` | §5.1–5.5 |
| `historico` | Historico | `/historico` | `HistoricoComponent` | §5.1–5.5 |
| `calibracao` | Calibracao | `/calibracao` | `CalibracaoComponent` | §5.1–5.5 |
| `onboarding` | Onboarding | `/onboarding` | `OnboardingComponent` | §5.1–5.5 |

## Próximos passos

O arquivo `prototype-map.json` na raiz do workspace contém metadados de todas as telas (rota Angular, seletor, caminhos de arquivo, referência ao wireframe, objetivo e componentes). Na **fase de arquitetura**, o agente de frontend lê esse JSON para:

- Substituir os `MOCK` por chamadas reais a serviços/APIs.
- Implementar os componentes listados em `componentes` usando a biblioteca DS de `app/ui/`.
- Refinar os utilitários Tailwind sobre os tokens do `theme.css`.
- Adicionar testes unitários e e2e.
