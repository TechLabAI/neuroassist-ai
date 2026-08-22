# Plano FE — Recuperação de render, shell e telas EVNT

> Status: **F1–F5 executadas e verificadas no container (22/08)** · F6 removida (virou a P2 do `plan-FE-E2E.md`) · Origem: sessão de containerização do frontend (2026-08-22)
> Evidência: `tmp/prints/home_atual.png` (estado servido em `http://localhost:14200`)
> vs. `tmp/prints/home_prototipo.png` (`frontend/design-reference/angular-prototype`).
> Decisões tomadas com o usuário: escopo = *destravar render + shell + EVNT*;
> tokens = *união do tema do protótipo com a rampa `alarm`/`error` do Estágio 0*.

---

## 1. O que o print mostra

`home_atual.png` não é "uma home feia" — é **a home sem CSS nenhum**: fonte serifada
padrão do navegador, zero layout, e os nomes das ligaduras do Material Symbols
impressos como texto literal (`emergency_home`, `check_circle`, `wifi`,
`device_unknown`, `notifications`, `history`).

O overlay de alarme do EVNT aparece no topo, fora de posição, porque
`class="fixed inset-0 z-50 …"` não resolve para nada. **O EVNT está funcionando** —
`AlarmOverlayComponent` hidratou pelo `APP_INITIALIZER` e renderizou o evento do mock.
Só não há CSS para posicioná-lo.

Conclusão: nenhum ajuste de UI no EVNT é avaliável antes das fases F1–F3.

---

## 2. Diagnóstico — quatro camadas, todas provadas

### 2.1 O Tailwind nunca rodou no build (causa raiz nº 1)

O `styles-*.css` servido pelo container continha, literalmente:

```css
@layer utilities{@tailwind utilities;}   /* diretiva v3 crua */
@theme{--bg-base:oklch(98% .004 260); …} /* @theme não compilado */
```

`@theme` e `@tailwind` são sintaxe de *entrada* do Tailwind — se aparecem na saída,
o motor não processou o arquivo. O esbuild do Angular resolveu
`@import "tailwindcss"` para `node_modules/tailwindcss/index.css` e **inlinou o pacote
verbatim**. Nenhuma classe utilitária foi gerada: `.flex{`, `.grid{`, `.rounded-xl{`
não existiam no bundle. Navegador ignora `@theme` (at-rule desconhecida) → todos os
tokens ficam indefinidos → `background: var(--color-base)` não pinta, `font-family:
var(--font-body)` cai no serif do UA. É exatamente o print.

**Causa:** `@angular/build` (Angular 21) descobre configuração PostCSS por
`.postcssrc.json` / `postcss.config.json`. O repo tem apenas **`postcss.config.js`**,
que esse builder **não lê**. O `@tailwindcss/postcss` está instalado e correto — só
nunca foi carregado.

**Prova do fix:** criado `frontend/.postcssrc.json` e reconstruída a imagem:

| | antes | depois |
|---|---|---|
| `styles-*.css` | 25.532 B | 39.109 B |
| ocorrências de `@theme` / `@tailwind` | 1 | **0** |
| `.flex{` `.grid{` `.rounded-xl{` | ausentes | **presentes** |

O `.postcssrc.json` **já está no repo e já foi validado no container** — é a única
alteração da fase F1 e está feita. (O protótipo, que renderiza certo, usa
exatamente esse arquivo: `design-reference/angular-prototype/.postcssrc.json`.)

### 2.2 Material Symbols nunca foi carregado (causa raiz nº 2)

`apps/neuroassist-ai-web/src/index.html` linka só Montserrat + Nunito Sans.
Falta o `<link>` do **Material Symbols Outlined** — daí `emergency_home` e cia.
saírem como texto. Os componentes usam `class="material-symbols-outlined"` em
todas as libs.

O standard (`design-system-tokens.md` §2) diz que os links vêm de `fonts.html`,
"injetado no `<head>` do index.html". Existem dois `fonts.html`:

| arquivo | conteúdo | Material Symbols |
|---|---|---|
| `design-reference/design-system/fonts.html` (entrega do Estágio 0) | Nunito + Inter + JetBrains Mono + Material Symbols | ✅ |
| `apps/neuroassist-ai-web/src/fonts.html` | *"GENERATED FALLBACK"* — Montserrat + Nunito Sans | ❌ |

O passo de vendorização (`/new-project` P5: "copia `design-system/fonts.html` sobre o
fallback") **não aconteceu**, e nenhum dos dois foi injetado no `index.html`, que tem
seus próprios links hardcoded.

### 2.3 Três conjuntos de tokens divergentes (causa raiz nº 3)

| # | arquivo | namespaces | quem usa |
|---|---|---|---|
| A | `apps/neuroassist-ai-web/src/theme.css` — **idêntico** a `design-reference/design-system/theme.css` (Estágio 0) | 33 `--color-*` + 18 `--bg-*` + 17 `--text-*` + 10 `--border-*` | o app |
| B | `design-reference/angular-prototype/.../theme.css` | 76 `--color-*` | as libs promovidas |
| C | `docs/architecture/frontend/standards/design-system-tokens.md` §2 (contrato canônico) | descreve **exatamente B** | o standard |

`--bg-*`, `--border-*`, `--space-*`, `--icon-*` **não são namespaces do Tailwind v4** —
não geram utilitário nenhum. `bg-surface` e `border-subtle` nunca existiriam mesmo com
o PostCSS corrigido. Pior: `--text-primary` cai no namespace `--text-*` (tamanho de
fonte), então `text-primary` viraria um `font-size`, silenciosamente.

Inventário de uso real (89 raízes de classe de cor, ~847 usos em `apps/` + `libs/`):

| situação | raízes | usos | exemplos |
|---|---|---|---|
| existe em A **e** B | 16 | 307 | `ink`, `brand`, `success-ink`, `warning-bg` |
| só em B (protótipo) | 18 | **369** | `ink-soft` (126), `ink-faint` (46), `surface` (45), `surface-2` (43), `line` (42), `danger*` (42) |
| só em A (Estágio 0) | 5 | 46 | `error-ink` (23), `error` (10), **`alarm` (6)**, **`alarm-ink` (6)** |
| em nenhum — drift real | 32 | 86 | ver §6 |

Ou seja: as libs foram promovidas do protótipo carregando a nomenclatura **B**,
enquanto o app ficou com **A**. As duas exceções relevantes são `--color-alarm*` e
`--color-error*`, que só existem em A — e o **overlay de alarme do EVNT depende delas**
(`bg-alarm`, `text-alarm-ink`).

**Decisão:** tema canônico = **B + rampa `alarm`/`error` de A**. Cobre 307+369+46 =
722 dos ~847 usos sem tocar em componente algum.

### 2.4 O shell é esqueleto Wave-0

`app/shell/{app-shell,sidebar,topbar}.component.ts` usam CSS inline artesanal
(`.sidebar { width: 240px }`), sem token, sem ícone, sem logo:

| | shell atual | protótipo |
|---|---|---|
| logo | texto "NeuroAssist AI" | `logo-white.png` na sidebar escura |
| navegação | Cuidadores · Calibração · Eventos (3, vindas de descriptors) | Status · Histórico · Calibração + Configuração inicial |
| rodapé sidebar | — | avatar + "Jamilly Amaral / Cuidadora" |
| topbar | `<select>` vazio de tenant + chip de conectividade | paciente ativo + chip de dispositivo + toggle de tema + avatar |
| tema | — | `UiStateService.toggleTheme()` |

A rota `/` já carrega `DASH_ROUTES` (HomeStatus), que é a tela do print — a home em si
existe e está correta em estrutura; o que falta é o chrome em volta e o CSS.

---

## 3. Fases

Cada fase termina verificável no container (`docker compose -f
docker/docker-compose.dev.yml up -d --build web` → `http://localhost:14200`).

### F1 — Ligar o Tailwind ✅ *(executada)*
- `frontend/.postcssrc.json` com `{"plugins":{"@tailwindcss/postcss":{}}}`.
- `postcss.config.js` **removido** (decisão do usuário) — uma fonte de verdade só.
  `design-system-tokens.md` §1 ainda cita o arquivo removido: corrigir o standard.
- **Aceite:** `grep -c "@theme\|@tailwind" dist/**/styles*.css` = 0 e `.flex{` presente.

### F2 — Fontes e ícones ✅ *(executada, com desvio do plano)*
- **Desvio:** copiar `design-system/fonts.html` cegamente teria carregado **Inter + Nunito**,
  enquanto o tema canônico declara **Montserrat + Nunito Sans + JetBrains Mono** — o app
  ficaria com fallback do sistema sem erro nenhum. As fontes foram alinhadas ao tema.
- **Material Symbols não é mais linkado do Google.** O stylesheet do Google traz, junto do
  `@font-face`, uma regra `.material-symbols-outlined { font-size: 24px }` **sem layer**;
  no Tailwind v4 CSS sem layer vence qualquer `@layer utilities`, então aquele 24px anulava
  `text-[20px]`, `text-[96px]` etc. em **todos** os 30 componentes com ícone — o ícone de
  96px do alarme saía com 24px. O `@font-face` agora é declarado em `styles.css` (CDN
  primeiro, cópia local de 4 MB em `public/assets/fonts/` como rede de segurança) e os
  defaults da classe vivem em `@layer base`, onde perdem para os utilitários — como deve ser.
- Logos (`logo.png`, `logo-white.png`) movidos para `apps/neuroassist-ai-web/public/assets/`:
  o app não pode depender de `design-reference/angular-prototype`, que será removido.
- **Aceite atingido:** nenhuma ligadura em texto; ícones no tamanho pedido pela classe.

### F3 — Tema canônico único ✅ *(executada)*
- `apps/neuroassist-ai-web/src/theme.css` ← tema do protótipo (**B**), acrescido de
  `--color-alarm`, `--color-alarm-bg`, `--color-alarm-ink`, `--color-error`,
  `--color-error-bg`, `--color-error-ink` (de **A**).
- Atualizar `design-reference/design-system/theme.css` com o mesmo conteúdo, para que
  a entrega do Estágio 0 e o app parem de divergir (senão a próxima vendorização
  reintroduz o bug).
- Drift resolvido: **88 substituições em 22 arquivos**, 32 raízes → 0 (o mapeamento de
  cada uma saiu da leitura da ocorrência, não de adivinhação; ver §6).
- **`--color-base` saiu do `@theme`.** Colisão de namespace: `--text-base` (14px) gera
  `text-base` como font-size e `--color-base` gera `text-base` como **cor**; a cor é
  emitida depois e vence, então todo `text-base` do app pintava o texto de #F7F9FC — o
  botão do overlay de alarme ficava branco sobre branco. `@utility text-base` **não**
  resolve (soma uma regra, não substitui a gerada). Como só havia 1 uso de `bg-base`
  contra 8 de `text-base`, o token virou custom property comum fora do `@theme`.
- **Pendente:** o gate que falhe quando uma classe referenciar token inexistente
  (extensão do `arch-conformance.py` FE, que o standard §3 promete mas não cobre).

### F4 — Shell no padrão do protótipo ✅ *(executada)*
- Reescrever `sidebar.component.ts` com tokens + Material Symbols + logo + rodapé de
  usuário, **mantendo** a geração de itens a partir dos descriptors dos módulos
  (é o contrato atual do app; o protótipo tem a lista hardcoded).
  - Revisar rótulos/ordem: hoje a nav mostra PAC como "Cuidadores"? Conferir
    `PAC_MODULE_DESCRIPTOR.menu.label` contra o protótipo (Status/Histórico/Calibração)
    e incluir DASH e HIST, que hoje ficam de fora da sidebar.
- `topbar.component.ts`: paciente ativo, chip de conectividade (já existe, via
  `EdgeShellIntegrationService`), toggle de tema, avatar. Trocar o `<select>` de tenant
  vazio por algo real ou removê-lo.
- `app-shell.component.ts`: grid com tokens; manter `<evnt-alarm-overlay />` acima do
  `router-outlet` (padrão ADR-005 já correto).
- Introduzir o serviço de tema (`data-theme`) — o `theme.css` já tem os dois modos.
- Feito: sidebar escura com logo, ícones e rodapé de usuário; nav gerada dos nav-configs
  (Status · Cuidadores · Histórico · Calibração · Eventos + Configuração inicial);
  topbar com paciente, chip de conectividade, toggle de tema e avatar; `UiStateService`
  novo (persiste em localStorage, tudo em try/catch); `<select>` de tenant vazio removido.
- O shell dispara `DashHomeFacade.load()` uma vez (guardado por status `idle`) para que
  paciente e cuidadora apareçam no chrome em qualquer rota, não só na home.
- **Aceite atingido** — verificado no navegador em claro e escuro.

### F5 — Telas EVNT ✅ *(executada)*
Superfícies existentes (todas renderizando):

| componente | rota / uso |
|---|---|
| `feature-alarm/alarm-overlay.component.ts` | outlet global do shell |
| `feature-routine/event-feed-page.component.ts` | `/eventos` |
| `feature-routine/event-detail-page/…` | `/eventos/:eventoId` |
| `event-list`, `confirmation-trail`, `feedback-verdict-control`, `routine-notice` | composições |
| `ui/{urgency-badge,event-timestamp,feed-skeleton,degraded-banner}` | primitivas |

O que foi feito:

- **Drift de token do EVNT:** já resolvido na varredura da F3 (`surface-border`,
  `surface-hover`, `primary*`, `surface-subtle` → 0 ocorrências).
- **Enum cru na tela:** `{{ evt.feedback }}` com `capitalize` mostrava `Nao_avaliado`
  à cuidadora. Agora há um mapa de rótulos pt-BR (`Sem revisão` / `Confirmado real` /
  `Falso positivo`) e um `data-testid="event-feedback"` para asserção exata.
- **"Ver" ilegível no tema escuro — corrigido no tema, não no componente.** A causa
  não era do EVNT: `--color-brand` (#1B2F5C, navy) não tinha override no escuro, então
  todo `text-brand` sumia sobre superfície escura. No bloco `[data-theme="dark"]` a
  rampa agora inverte de papel — `brand` clareia (#7FA8E8) e `brand-ink` escurece —
  preservando contraste tanto em `text-brand` quanto em `bg-brand + text-brand-ink`.
- **INT-FE-DASH-001 fechado:** `DASH_ACTIVE_EVENT_TOKEN` deixou de ser `signal(null)` e
  passa a projetar `EventDeliveryFacade.activeEvent` do EVNT. A projeção vive em
  scope:web porque nem DASH nem EVNT podem importar o outro (ADR-005).
  Verificado no DOM: com alarme ativo a home diz **"Evento pendente de confirmação"**;
  após a confirmação, **"Márcio está bem"**. Antes, o invariante `calm` do
  `DashHomeFacade` estava preso em `true` e a home afirmava que o paciente estava bem
  **durante** um pedido de ajuda (SPEC-01 §3).

Extra, fora do escopo declarado mas na mesma tela:

- **Chip da topbar contradizia a home** ("Sem dispositivo" vs. "Dispositivo online").
  Faltava a chamada `EdgeShellIntegrationService.startConnectivityForActivePatient()`
  (SPEC-06 §3); o shell agora a dispara via `effect` assim que o paciente ativo é
  conhecido. O chip mostra "Dispositivo online · 1.4.2".
- `playwright.config.ts` do `evnt-e2e`: `APP_URL` agora **desliga** o `webServer`.
  Com `reuseExistingServer: true` e algo escutando em :4200, a suíte rodaria contra o
  app errado sem avisar.

**Aceite atingido:**

| gate | resultado |
|---|---|
| `nx test evnt` | **67/67** (3 novos, do rótulo de veredito) |
| `nx run-many --target=test --all` | **910/910** em 7 projetos |
| `nx e2e evnt-e2e` (`APP_URL` → container) | **9/9** |
| inspeção visual das três telas | overlay, feed e detalhe, claro e escuro |

**Divergências de contrato registradas (não corrigidas — exigem decisão do dono do módulo):**

- `EventoVm` não carrega `patientId`, mas `ActiveEventVm` (DASH) exige. A projeção
  passa string vazia; o DASH só usa o null-check. Resolver acrescentando `patientId`
  a `EventoVm` ou removendo-o de `ActiveEventVm`.
- A trilha de confirmação exibe `confirmadoPor` cru (`cuid-jamilly`) para a cuidadora —
  mesma classe de defeito do enum. O VM só tem o id; falta o nome de exibição.

### ~~F6 — mock → http~~ — **removida (22/08): é a P2 do `plan-FE-E2E.md`**

A F6 propunha virar o EVNT para `provideEvntForProduction()`. Ao cruzar com
`docs/architecture/frontend/plans/plan-FE-E2E.md` (autoria do FE, 20/08) ficou claro que ela
duplica — pior — a **Prioridade P2** daquele plano:

| | F6 (removida) | P2 do plano FE-E2E |
|---|---|---|
| como se troca o modo | editando `app.config.ts` | token de DI `APP_ADAPTER_MODE` |
| perfis | um só | `mock` (default, CI a cada PR) + `integrated` (configuração Nx) |
| transporte | — | `proxy.conf.json`, porta do GWY parametrizada |
| base paths | — | normalização em PAC, EVNT, DASH e GEST |

A estratégia daquele plano é explícita: *"O modo é resolvido por token de DI, nunca por edição
de arquivo"* — que é exatamente o que a F6 faria.

E a F6 sozinha nem funcionaria: `evnt-client-http.adapter` usa `base = '/api/v1/events'`,
enquanto o GWY expõe `/api/v1/evnt/**` e reescreve para `/api/v1/**`. Virar o adapter para
`http` sem mudar o base path dá 404 — e a mudança de base path é parte da P2.

O que a F1–F5 deixou pronto para a P2 está registrado lá, em
*"Insumos já entregues por plan-fe-render-shell-evnt.md"*, junto do ponto de colisão em
`app.config.ts`.

**Nada de mock → http deve ser feito por este plano.**

## 4. Ordem e por quê

F1 → F2 → F3 são pré-requisito estrito: sem elas nada renderiza e qualquer ajuste é
feito às cegas. F4 antes de F5 porque o overlay do EVNT é avaliado *sobre* o shell —
preempção não se verifica sem uma tela por baixo.

---

## 5. Armadilhas registradas

- **Gate verde não prova render.** `nx build` passou os 8 targets com o Tailwind
  inteiramente desligado. Nenhum teste do repo olha o CSS de saída. O aceite da F1
  (grep no bundle) é o que fecha isso.
- **`postcss.config.js` é ignorado em silêncio** pelo `@angular/build` — sem warning,
  sem erro. O standard §1 aponta para o arquivo errado.
- **Namespace errado em `@theme` não falha.** `--bg-surface` simplesmente não gera
  utilitário; `--text-primary` gera o utilitário *errado* (font-size). Ambos passam
  batido no build.
- **`node_modules` local está quebrado** (falta `ng-packagr`; `nx build` falha na
  máquina, funciona no container). Rodar `npm ci --legacy-peer-deps` no host antes de
  qualquer ciclo de FE local, ou trabalhar via container.
- **Não editar `theme.css` só no app.** Se `design-reference/design-system/theme.css`
  não for atualizado junto, a próxima vendorização reintroduz o tema divergente.

---

## 6. Anexo — 32 raízes de drift (86 usos, existem em nenhum tema)

| raiz | usos | libs |
|---|---|---|
| `border` (`border-border`) | 24 | acs, app |
| `error-subtle` | 5 | acs |
| `warn-ink` | 5 | edge |
| `surface-border` | 5 | **evnt** |
| `danger-border` | 4 | edge |
| `surface-hover` | 3 | **evnt** |
| `primary` | 3 | **evnt** |
| `error-line` | 3 | hist |
| `ok-ink` | 3 | hist |
| `error-strong`, `success-subtle` | 2 cada | acs |
| `surface-3` | 2 | dash |
| `warn-bg` | 2 | edge |
| `error-surface`, `ok-surface` | 2 cada | hist |
| `success-border`, `warn-border`, `warn` | 1 cada | edge |
| `surface-subtle`, `primary-ink`, `primary-hover` | 1 cada | **evnt** |
| `error-surface-2`, `mu-surface`, `mu-ink`, `ok-line`, `er-surface`, `er-ink`, `warning-surface`, `nf-surface`, `nf-ink` | 1 cada | hist |

`hist` concentra abreviações opacas (`mu-`, `nf-`, `er-`) que provavelmente eram
`muted`, `not-found`, `error` — revisar junto com a F3.
