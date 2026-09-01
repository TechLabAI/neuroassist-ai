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
| `conv-arvore` *(Emenda 3; busca semântica/bilíngue Emenda 4, 2026-08-31)* | Conversar | `/paciente/conversar` | `ConvArvoreComponent` | §5.1–5.5 — Tela 06 |
| `celular-ativacao` *(Emenda 3, 2026-08-30)* | Ativar celular do paciente | `/celular-paciente` | `CelularAtivacaoComponent` | §5.1–5.5 — Tela 07 |
| `treinamento` *(Emenda 4, 2026-08-31)* | Treinamento | `/treinamento` | `TreinamentoComponent` | §5.1–5.5 — Tela 08 |
| `confirmacao-treinamento` *(Emenda 4, 2026-08-31)* | Confirmar frases novas | `/paciente/confirmar-treinamento` | `ConfirmacaoTreinamentoComponent` | §5.1–5.5 — Tela 09 |

> **Módulo CONV (Emenda 3, ADR-013 accepted).** As telas de CONV/TREI acima e o `patient-shell.component.ts`
> (shell restrito para a sessão do paciente, ver decisão de wireframe em `wireframe.md` §3.2.B
> adendo) vivem hoje dentro deste app único, junto com os outros módulos — igual ao restante do
> protótipo. No **produto real**, CONV é um módulo Nx próprio (`frontend/libs/conv`), seguindo o
> mesmo padrão dos módulos existentes (`frontend/libs/edge`, `frontend/libs/pac`, etc.). Este
> código é ponto de partida de design, não o código de produção — cabe ao arquiteto promovê-lo
> para a lib real ao integrar a Emenda 3.
>
> **Módulo TREI (Emenda 4, ADR-015/016/017/018 accepted).** `treinamento` e `confirmacao-treinamento`
> simulam a fase de TREINAMENTO — conversa com LLM **scriptada** no protótipo (sem chamada real
> de API Claude/Anthropic), Dupla Autorização (`confirmadoCuidadora`/`confirmadoPaciente` por
> `Locale` em `NoArvoreFala.conteudo`, ver `shared/data-model.ts`), e retrieval puro na Tela 06
> (só nós com Dupla Autorização completa aparecem na navegação do paciente — Elasticsearch e
> ElevenLabs são simulados, sem indexação/síntese real). No **produto real**, TREI é um módulo
> Nx próprio, assim como CONV — cabe ao arquiteto promovê-lo ao integrar a Emenda 4.

## Próximos passos

O arquivo `prototype-map.json` na raiz do workspace contém metadados de todas as telas (rota Angular, seletor, caminhos de arquivo, referência ao wireframe, objetivo e componentes). Na **fase de arquitetura**, o agente de frontend lê esse JSON para:

- Substituir os `MOCK` por chamadas reais a serviços/APIs.
- Implementar os componentes listados em `componentes` usando a biblioteca DS de `app/ui/`.
- Refinar os utilitários Tailwind sobre os tokens do `theme.css`.
- Adicionar testes unitários e e2e.
- **Promover o módulo CONV** (`screens/conv-arvore`, `screens/celular-ativacao`, `shell/patient-shell.component.ts`, as facades `NoArvoreFalaFacade`/`SessaoPacienteFacade` em `core/data-access.ts`) para `frontend/libs/conv` no repositório do produto, e desenhar o schema real do ator "Paciente com sessão" em `acs-service` (ver ADR-012/013/014, todos `accepted`).
- **Promover o módulo TREI** (`screens/treinamento`, `screens/confirmacao-treinamento`) para `frontend/libs/trei`; substituir o roteiro scriptado por chamada real à API Claude/Anthropic; implementar o pipeline assíncrono real de indexação (Elasticsearch, embedding self-hosted) e síntese (ElevenLabs, cache em storage de arquivo) disparado quando `confirmadoCuidadora AND confirmadoPaciente` viram `true` no mesmo `Locale` (ver ADR-015/016/017, todos `accepted`).
- **i18n da UI administrativa** (DASH/onboarding/etc.) — escolher mecanismo (`@angular/localize` vs. `ngx-translate`/`transloco`); hoje só o CONV/TREI têm seletor de idioma no protótipo (ver ADR-018, TODO[arquiteto]).
