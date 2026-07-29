# Feature Architect Workflow (NeuroAssist AI)

> Pipeline `fe-feature-architect-*` -- adicionar feature nova em módulo existente.
> Delta-only, ADR-gate, naming canônico.

Goal: estender módulos existentes (adicionar feature, função ou widget) sem violar ADRs
aceitos nem duplicar trabalho já feito.

Quem usa: pipeline `fe-feature-architect-*` (4 etapas) -- `discover` → `spec-delta`
→ `backlog` → `scaffold`. Implementação dos commands em
`.claude/commands/fe-feature-architect-*.md`.

---

## 1. Quando usar cada pipeline

| Caso | Use |
|---|---|
| Módulo **novo** (criar do zero) | Setup manual de SPECs do módulo + `/fe-feature-architect-discover` |
| Adicionar feature em módulo existente | `fe-feature-architect-discover/spec-delta/backlog/scaffold` |
| A feature tem tela no protótipo Angular do Estágio 0 | adotar a tela-semente de `frontend/design-reference/` no `scaffold`/`implement` |
| Adicionar resource isolado em data-access (sem feature visível) | `fe-feature-architect-discover/spec-delta/backlog/scaffold` (escopo reduzido) |
| Refatoração interna sem alterar contratos | issue manual + `/fe-validator` → `/fe-planner` → `/fe-implement` |

---

## 2. Princípio: delta-only

A pipeline de extensão **não reescreve documentação**. Atua exclusivamente em modo delta:

- SPECs são **anexados/modificados em seções específicas** (nunca sobrescritos).
- `generator-config.yml` do módulo recebe **entradas novas** em `features:` e `data_access.resources:`.
- Issues novas continuam numeração `<PROJECT_PREFIX>-<MODULE>-FE-NNN` existente.
- ADRs aceitos **nunca são alterados** -- se a feature exigir mudança, criar ADR-novo com status `proposed`.

Razão: preservar histórico, auditabilidade e contratos com BE.

---

## 3. ADR-conformance gate (bloqueante)

Toda execução de `discover` produz `conformance-report.md` com classificação por ADR:

- ✅ **Aderente** -- pipeline prossegue sem restrição
- ⚠️ **Em risco** -- pipeline prossegue mas exige stub de ADR-novo (`proposed`) em `spec-delta`
- ❌ **Violador** -- pipeline **bloqueia** até decisão explícita do usuário:
  - opção 1: ajustar a feature para não violar
  - opção 2: aceitar criação de ADR-novo `proposed` que documenta a exceção

ADRs canônicos que tipicamente bloqueiam:
- ADR-001 (FE) Angular standalone + signals + OnPush -- proposta de NgRx ou BehaviorSubject como source-of-truth = bloqueio
- ADR-002 (FE) Tax-map Nx -- proposta de criar lib fora de `libs/<modulo>/{feature,data-access,ui,access}` = bloqueio ou ADR-novo
- ADR-003 (FE) Design tokens -- uso de cor/spacing hardcoded em vez de `@neuroassist/shared/ui-tokens` = refatoração
- ADR-004 (FE) RBAC via AccessDecisionService -- permissão inline em UI = bloqueio
- ADR-001 (shared) Multi-empresa -- HTTP escapando do interceptor `@neuroassist/shared/access-http-base` = bloqueio

---

## 4. Naming de feature-slug

- **kebab-case** (só minúsculas + hífen)
- **3-30 caracteres**
- **sem prefixo de módulo** (já é contexto): `cadastro-cliente`, não `cad-cadastro-cliente`
- **descritivo** (verbo+substantivo ou substantivo composto): `cadastro-cliente`, `relatorio-mensal`, `aprovacao-em-massa`
- **estável**: o slug aparece em path, nome de pasta, nome de feature-lib, label de issue. Mudar depois é custoso.

Exemplos válidos: `cadastro-cliente`, `aprovacao-em-massa`, `relatorio-mensal`, `wizard-onboarding`.
Exemplos inválidos: `Cliente`, `cadastro_cliente`, `cadcli`, `cad-cadastro-cliente`.

---

## 5. Estrutura de output gerada

Por feature, a pipeline cria:

```
docs/architecture/frontend/
├── libs/<modulo>/features/<feature-slug>/
│   ├── conformance-report.md                 # discover
│   └── spec-delta.md                         # spec-delta (manifesto)
├── adr/ADR-00N-<topic>.md                    # só se conformance-report exigiu (proposed)
├── libs/<modulo>/SPEC-01..08.md              # modificados em modo delta
├── libs/<modulo>/generator-config.yml        # modificado
├── issues/<PROJECT_PREFIX>-<MODULE>-FE-NNN..NNN+K.md       # criados
├── issues/<PROJECT_PREFIX>-<MODULE>-INT-NNN.md             # criado se houver integração BE nova
├── libs/<modulo>/issues-map.md               # atualizado (P1-P9 + by feature)
└── plans/plan-<MODULO>-<feature-slug>.md     # mini-plan da feature
```

E em código:

```
frontend/libs/<modulo>/feature-<feature-slug>/                              # criado por scaffold
frontend/libs/<modulo>/data-access/src/lib/<resource>.api.service.ts        # criado se resource novo
frontend/apps/neuroassist-ai-web-e2e/src/specs/<modulo>/<feature-slug>-*.spec.ts   # criado por scaffold
```

---

## 6. Issue template -- campos extras

Issues da pipeline de extensão reusam o template canônico, mais 5 campos:

- `**Feature:**` -- slug da feature
- `**Related issues:**` -- relações (related/blocker/blocked-by/superseded), extraídas do conformance-report
- `**Conformance:**` -- link para conformance-report.md
- `**Spec sources:**` -- links para SPECs com âncoras de seção
- `**Prototype source:**` -- link para `prototypes/<modulo>/<feature-slug>/` (se aplicável)
- `**BE dependency:**` -- `<PROJECT_PREFIX>-<MODULE>-NNN` da issue BE que entrega o endpoint, ou "endpoint já existe"

---

## 7. Granularidade recomendada (extensão)

Total típico: **3-10 issues funcionais + 0-2 INT = 3-12 total**.

| Onda | Issues | Quando incluir |
|---|---|---|
| P1 | 0-1 | só se permission-map / feature-flags do módulo mudou |
| P2 | 0-1 | só se houver resource novo |
| P3 | 1-3 | feature-lib + components |
| P4 | 0-1 | só se houver integração BE nova (<PROJECT_PREFIX>-<M>-INT) |
| P5 | 0-1 | guard customizado |
| P6 | 0-1 | mudança em breadcrumb/nav |
| P7 | 0-1 | UX polish (pode entrar como acceptance da P3) |
| P8 | 0-1 | telemetry events novos |
| P9 | 1 | Playwright specs (sempre) |

Se passar de 12 issues, **quebrar em sub-features** e rodar a pipeline para cada.

---

## 8. Gates de qualidade pós-scaffold

```bash
npx nx lint <project>      # boundaries + standalone + OnPush + tax-map
npx nx test <project>      # Jest unit
npx nx build <project>     # TypeScript compila
npx nx affected -t lint,test --base=HEAD~1   # sem regressão
```

Os 4 verdes antes de finalizar. Se algum falhar:
- `nx lint` -- verificar `generator-config.yml`; corrigir e regerar com `--force` se preciso
- `nx test` -- adicionar mocks/stubs mínimos no setup-jest da lib gerada
- `nx build` -- erro de TypeScript; revisar tipos de DTOs/VMs gerados

**Não** rodar e2e aqui -- responsabilidade do `fe-implement` na onda P9.

---

## 9. Pipeline pós-architect

Ao final do `scaffold`, a feature está registrada no Nx workspace e pronta para
`fe-implement` da P2/P3:

```
/fe-validator <<PROJECT_PREFIX>-<MODULE>-FE-NNN>          # consolida estado
/fe-planner   <<PROJECT_PREFIX>-<MODULE>-FE-NNN>          # produz plan-<ID>.md
/fe-implement <<PROJECT_PREFIX>-<MODULE>-FE-NNN> fase-<N>  # executa fase a fase
```

---

## 10. NÃO fazer

- não usar `fe-feature-architect-*` para criar módulo do zero (criar manualmente os SPECs primeiro)
- não reescrever SPECs do zero (só anexar/modificar)
- não alterar status de ADR existente para `accepted` -- promoção é decisão humana fora da pipeline
- não prosseguir com `spec-delta` se conformance-report = ❌ Violador sem decisão explícita
- não reusar números de `<PROJECT_PREFIX>-<MODULE>-FE-NNN` já existentes
- não gerar código TS/Angular fora do scaffold
- não manter `sessionStorage`, `localStorage`, blob download, permissão inline em código adaptado de protótipo
