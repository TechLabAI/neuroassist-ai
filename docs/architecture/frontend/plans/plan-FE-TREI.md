# Plano FE — TREI (neuroassist-ai-web)

> **Módulo:** TREI · **Shell:** caregiver (`neuroassist-ai-web`) · **Surface:** `/treinamento`
> **Tipo de planejamento:** REPLANEJAMENTO
> **Score baseline:** 30/100 (Inicial) · **Issues:** 0/37 DONE (31 FE + 6 INT)
> **Gerado:** 2026-09-02 · **Gerado por:** /fe-planner (Pipeline B — Implement)

---

## Status das prioridades

- P1 — pendente (ESLint Config + Foundation)
- P2 — pendente (Data Access Layer)
- P3 — pendente (Route Guards — G1 crítico)
- P4 — pendente (Chat UI — Tela 08)
- P5 — pendente (Proposal UI — Approval Surface)
- P6 — pendente (UI Primitives)
- P7 — pendente (UX Polish + CONV Integration)
- P8 — pendente (Observability)
- P9 — pendente (E2E Hardening)

---

## 0. Leitura executiva do estado atual

### Papel do módulo FE

TREI é o módulo de **autoria interativa da árvore de falas** do paciente. A cuidadora conversa com um LLM (Claude/Anthropic via `trei-service`) e recebe propostas de novas frases/categorias. Apenas conteúdo dual-aprovado (cuidadora + paciente assíncrona) é publicado ao módulo CONV. TREI vive exclusivamente no shell da cuidadora — nunca é visível ao paciente.

### Estado atual observado

- **Build:** `nx build trei` → PASS
- **Tests:** `nx test trei` → 0 suites, 0 testes (passWithNoTests: true)
- **Lint:** `nx lint trei` → **FAIL** — `scope:trei` ausente no `.eslintrc.json`; todos os arquivos ignorados
- **Estrutura:** Monolito único `libs/trei/`; tags `scope:trei` + `type:feature` corretas no `project.json`; sublibsmodule (8 sublibs previstas) ainda não criadas
- **Componentes:** 2 smart components de ~25 esperados; data layer ~50% (facades OK, HTTP adapters em stub com TODO)
- **Guards:** 0% — G1 crítico sem proteção

### Continuidade da última rodada

Nenhuma rodada de implementação anterior. Este é o primeiro plano de execução (baseline 2026-09-02). O plano de ondas do arquiteto (`docs/architecture/frontend/modules/trei/frontend/plans/plan-FE-TREI.md`) foi referência estrutural mas não é o plano de execução canônico.

### Diagnóstico executivo

O gate de lint está falhando por ausência de configuração no arquivo de workspace — não por problema no código da lib. Este bloqueio impede o conformance check e possivelmente o CI. É o primeiro item a resolver. Após o desbloqueio do lint, a sequência natural é: foundation do shell (P1) → data layer com mock ativo (P2) → guards de rota antes da UI complexa (P3) → decomposição de componentes em dois waves (P4: chat; P5: proposal) → UI primitivos (P6) → polish + observability → E2E.

### Multi-empresa: `@neuroassist/shared/access-http-base` configurado?

**Parcialmente.** Declarado na spec; os HTTP adapters existem mas têm TODO pendente para ativação quando o `trei-service` for deployado. O interceptor `X-Empresa-Id` será injetado via `@neuroassist/shared/access-http-base` no momento da ativação. Durante P1-P8, os mock adapters contornam esta dependência — `isMock: true` flag presente no `trei.providers.ts`. Nenhum HTTP real é emitido até três-service estar online.

---

## 1. Principais achados

### Pontos fortes

- Build verde: `nx build trei` compila sem erro
- Data layer 50% presente: 4 ports, 4 mock adapters, 4 HTTP adapters (stub), 2 facades signal-based
- Descriptor completo: `TREI_MODULE_DESCRIPTOR`, `TREI_PERMISSION_MAP`, `TREI_FEATURE_FLAGS` existem em `trei.descriptor.ts`
- Tags Nx corretas no `project.json`: `scope:trei`, `type:feature`
- 5 feature files E2E definidos com cenários completos (sem steps)
- Invariante INV-1 estruturalmente declarada (sem `confirmadoPaciente` nos adapters)

### Gaps

| # | Gap | Severidade | Prioridade |
|---|---|---|---|
| G-LINT | `scope:trei` ausente no `.eslintrc.json` (gate bloqueante) | CRÍTICO | P1 |
| G1 | `treiProporGuard` não implementado — acesso a `/proposta` sem scope `treinamento.propor` | CRÍTICO | P3 |
| G2 | Guards de rota zerados — rotas TREI abertas sem autenticação de scope | CRÍTICO | P3 |
| G3 | ~23 componentes presentacionais faltando (apenas 2/~25 existem) | ALTO | P4-P5 |
| G4 | `patient-not-found` banner ausente — sessão pode ser iniciada sem paciente válido | MÉDIO | P7 |
| G5 | `correlationId` exposto potencialmente na UI (TreiErrorMapper não implementado) | ALTO | P2 |

### Riscos

| # | Risco | Mitigação |
|---|---|---|
| R1 | `trei-service` não online → HTTP adapters inativos | Manter MockAdapter ativo via `isMock: true`; switch em providers quando service disponível |
| R2 | Sublib split cria regressão no build se feita durante desenvolvimento ativo | Diferir split para P6 após MVP funcional com guards |
| R3 | G5 violado acidentalmente (`correlationId` renderizado na UI) | Implementar `TreiErrorMapper` em P2; validar com `expect(body).not.toContainText(correlationId)` no E2E P9 |
| R4 | `hasScope()` em `@neuroassist/shared/access` pode não ter a assinatura exata esperada | Verificar antes de implementar guards em P3 |

### Aderência às regras Nx (boundaries, tax-map, naming)

- **RULE-NX-01 (scope isolation):** Parcial — `scope:trei` existe no `project.json` mas não no `.eslintrc.json` (corrigir em P1)
- **RULE-NX-02 (type hierarchy):** `type:feature` correto para a lib de entrada; sublibs futuras terão `type:data-access`, `type:ui`, `type:domain` conforme split
- **RULE-NX-03 (dependency direction):** `scope:trei` deve depender apenas de `scope:shared` (nunca de `scope:gest`, `scope:pac`, etc.)
- **RULE-NX-04 (buildable libs):** lib atual não-buildable para dev; OK para monolito de entrada
- **Naming:** `@neuroassist/trei` correto conforme `npmScope: @neuroassist`

---

## 2. Estratégia geral

### Objetivo macro

Elevar o módulo TREI de 30/100 (Inicial) para 85/100 (Maduro) em 9 prioridades sequenciais, garantindo: gate de lint verde, foundation do shell completa, data layer com mock adapter funcional, guards de rota com G1 resolvido, MVP de UI demonstrável, e E2E com todos os 5 gaps G1..G5 cobertos.

### Decisão: Sublibsmodule Split

**Decisão: DIFERIR para P6 (após MVP + guards).** Rationale:
- O monolito atual tem build verde e tags corretas
- O split das 8 sublibs é um refactoring puramente arquitetural sem valor funcional imediato
- Fazer o split agora atrasaria P1-P5 sem benefício observável
- Em P6, após MVP funcional e guards ativos, o split será feito como wave dedicada de "architecture hardening"
- A separação lógica por pastas (`access/`, `api/`, `feature-training-session/`, `feature-proposal-review/`) já existe e orienta o split futuro

### Decisão: Dependência do `trei-service`

**Decisão: MANTER MockAdapter ativo através de P8; ativar HTTP em P9/produção.** Rationale:
- O `trei.providers.ts` já tem seleção por feature flag (`isMock: true`)
- Todos os componentes consomem apenas as facades (nunca adapters diretamente)
- Quando `trei-service` for deployado, basta alterar `provideTrei()` em `trei.providers.ts` — zero mudanças nos componentes
- HTTP adapters serão completados (sem TODO) em P2, mas permanecerão desabilitados via feature flag até o service estar disponível

### Decisão: Ordem Guards vs Componentes

**Decisão: Guards (P3) ANTES da decomposição de componentes (P4-P5).** Rationale:
- G1 (`treiProporGuard`) é gap de segurança crítico — rotas abertas sem scope check
- Guards dependem apenas de `TREI_PERMISSION_MAP` (P1) e `@neuroassist/shared/access`
- Implementar guards antes dos componentes garante que todas as rotas criadas nas waves P4-P5 já nascem protegidas
- Inversão desta ordem criaria rotas abertas temporariamente no branch

### Ordem das prioridades

| P | Título | Motivo | Issues cobertas |
|---|---|---|---|
| P1 | ESLint Config + Foundation Shell | Gate bloqueante + base de integração com shell | LINT, FE-001..006, INT-001, INT-004 |
| P2 | Data Access Layer | Tipos, facades e error mapper antes da UI | FE-007..012, INT-002, INT-003 |
| P3 | Route Guards (G1 crítico) | Segurança antes da decomposição de UI | FE-022, FE-023 |
| P4 | Chat UI — Training Session | Feature principal (Tela 08) | FE-013..017 |
| P5 | Proposal UI — Approval Surface | Feature review + INV-1 | FE-018..020 |
| P6 | UI Primitives + Sublib Split | Banner + StatusChip + refactor arquitetural | FE-021 |
| P7 | UX Polish + CONV Integration | G4 banner + optimistic approval + G5 link | FE-024, FE-025, INT-005 |
| P8 | Observability | Telemetry + shared/ui-* tokens | FE-026, INT-006 |
| P9 | E2E Hardening | Steps para todos os 5 cenários + G1..G5 | FE-027..031 |

---

## 3. Plano por prioridade

---

### Prioridade 1 — ESLint Config + Foundation Shell

**Status:** pendente
**Issues cobertas:** LINT (quick win), TREI-FE-001, TREI-FE-002, TREI-FE-003, TREI-FE-004, TREI-FE-005, TREI-FE-006, TREI-INT-001, TREI-INT-004
**Categoria:** ESTRUTURAL

#### Objetivo

Desbloquear o gate de lint (remoção do bloqueio que ignora todos os arquivos `libs/trei/**/*.ts`) e completar o wiring do módulo TREI no shell da cuidadora — sidebar item "Treinamento" visível, kill-switch funcional, bootstrap do módulo conectado.

#### Problemas identificados

1. `.eslintrc.json` não tem `"scope:trei"` no array `onlyDependOnLibsWithTags` de `scope:web`, nem depConstraint para `"sourceTag": "scope:trei"` — todos os arquivos do módulo são ignorados pelo lint
2. `TreiRootComponent` não foi criado — a shell não tem um componente raiz para a rota `/treinamento`
3. `TreiShellIntegrationService` existe como stub em `trei-nav-config.ts` mas não está registrado no shell (`module-descriptors.ts`)
4. `TREI_MENU_METADATA` declarado no descriptor mas não integrado ao `neuroassist-ai-web` app-shell

#### Correções mandatórias

1. **Atualizar `frontend/.eslintrc.json`:**
   - Adicionar `"scope:trei"` ao array `onlyDependOnLibsWithTags` de `sourceTag: "scope:web"`
   - Adicionar bloco depConstraint: `{ "sourceTag": "scope:trei", "onlyDependOnLibsWithTags": ["scope:trei", "scope:shared"] }`

2. **Criar `TreiRootComponent`** em `frontend/libs/trei/src/lib/trei-root.component.ts` — componente standalone, `OnPush`, `<router-outlet>` + `TreiAuthoringBannerComponent` (placeholder em P1, real em P6)

3. **Registrar TREI no shell:** atualizar `frontend/apps/neuroassist-ai-web/src/app/shell/module-descriptors.ts` adicionando o `TREI_MODULE_DESCRIPTOR` ao array de descritores ativos

4. **Completar `trei.bootstrap.ts`:** garantir `init()` registra providers, `destroy()` limpa state — consumido pelo `ModuleRegistry` do shell

5. **Verificar `TREI_TOOL_KEY`, `TREI_MENU_METADATA`, `TREI_PERMISSION_MAP`, `TREI_FEATURE_FLAGS`** já declarados em `trei.descriptor.ts` — confirmar campos sem `undefined`; corrigir se necessário

6. **Verificar `TREI_NAV_CONFIG`** em `trei-nav-config.ts` — confirmar breadcrumb e sidebar entry completos

#### Arquivos-alvo

```
frontend/.eslintrc.json                                               ← LINT FIX (item 1)
frontend/libs/trei/src/lib/trei-root.component.ts                    ← novo
frontend/libs/trei/src/lib/trei.descriptor.ts                        ← completar campos
frontend/libs/trei/src/lib/trei.bootstrap.ts                         ← completar init/destroy
frontend/libs/trei/src/lib/trei-nav-config.ts                        ← completar nav entry
frontend/libs/trei/src/index.ts                                       ← exportar TreiRootComponent
frontend/apps/neuroassist-ai-web/src/app/shell/module-descriptors.ts ← registrar TREI_MODULE_DESCRIPTOR
```

#### Contrato de tipo

```typescript
// trei.descriptor.ts — campos obrigatórios sem undefined
export const TREI_TOOL_KEY = 'treinamento' as const;
export const TREI_MENU_METADATA: MenuMetadata = {
  key: TREI_TOOL_KEY,
  label: 'Treinamento',
  icon: 'neurology',
  route: '/treinamento',
  requiredScope: 'treinamento.sessao',
};
export const TREI_PERMISSION_MAP: Record<string, string[]> = {
  'treinamento.sessao': ['CAREGIVER', 'ADMIN'],
  'treinamento.propor': ['CAREGIVER', 'ADMIN'],
};
export const TREI_FEATURE_FLAGS = {
  useMockAdapter: true, // switch para false quando trei-service deployado
} as const;

// trei-root.component.ts
@Component({
  selector: 'trei-root',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<router-outlet />`,
  imports: [RouterOutlet],
})
export class TreiRootComponent {}
```

#### Sequência de execução

1. Atualizar `.eslintrc.json` (lint fix imediato)
2. Completar `trei.descriptor.ts` (campos sem undefined)
3. Criar `TreiRootComponent`
4. Completar `trei.bootstrap.ts`
5. Completar `trei-nav-config.ts`
6. Atualizar barrel `index.ts`
7. Registrar no shell `module-descriptors.ts`
8. Verificar `TREI_ROUTES` aponta para `TreiRootComponent`

#### Generators Nx obrigatórios

Nenhum — todos os arquivos já existem ou são criados manualmente.

#### Política de testes

Criar `frontend/libs/trei/src/lib/trei-root.component.spec.ts` — teste unitário mínimo: componente renderiza, `router-outlet` presente.

#### Multi-empresa

Não aplicável em P1 — nenhum HTTP emitido. Mock adapters apenas.

#### Critérios de aceite

```bash
cd frontend
npx nx lint trei           # deve PASS — zero erros, zero arquivos ignorados
npx nx build neuroassist-ai-web  # deve PASS
npx nx test trei           # deve PASS (mínimo: TreiRootComponent spec)
# Manual: sidebar item "Treinamento" visível no shell da cuidadora (dev server)
# Manual: kill-switch em TREI_FEATURE_FLAGS.useMockAdapter = false não quebra o build
```

#### Riscos

- O `module-descriptors.ts` pode não existir ainda — se ausente, criar a convenção conforme outros módulos ativos (GEST, PAC, ACS)
- A assinatura de `TreiShellIntegrationService.init()` pode divergir da interface do ModuleRegistry — verificar antes de implementar

#### Instrução para implementador

Implementar na ordem exata da sequência acima. O lint fix (item 1) é a única alteração fora de `libs/trei/` — fazer primeiro e confirmar `nx lint trei` passa antes de continuar. Não criar novos arquivos que não estejam na lista de arquivos-alvo. Não implementar guards, componentes de chat ou HTTP adapters nesta prioridade.

---

### Prioridade 2 — Data Access Layer

**Status:** pendente
**Issues cobertas:** TREI-FE-009, TREI-FE-007, TREI-FE-008, TREI-FE-012, TREI-FE-010, TREI-FE-011, TREI-INT-002, TREI-INT-003
**Categoria:** FUNCIONAL

#### Objetivo

Completar o data layer do TREI com tipos TypeScript precisos, HTTP adapters implementados (mas desabilitados via feature flag), facades signal-based robustas, e `TreiErrorMapper` que nunca expõe `correlationId` na UI (G5).

#### Problemas identificados

1. `trei.types.ts` tem tipos baseline mas faltam view models completos (SessionVM, TurnVM, ProposalNodeVM, ApprovalStatusVM) — FE-009
2. HTTP adapters existem com `// TODO:` em todos os métodos — FE-007, FE-008
3. `TreiErrorMapper` não existe — G5 não está implementado — FE-012
4. `TrainingSessionFacade` e `ProposalReviewFacade` existem mas podem ter contratos incompletos versus a spec SPEC-03 — FE-010, FE-011
5. `@neuroassist/shared/error-model` não integrado ao módulo — INT-003

#### Correções mandatórias

1. **Completar `drei.types.ts`** com todos os DTOs de request/response e view models da SPEC-03
2. **Implementar métodos dos HTTP adapters** (remover `// TODO:`) — usar `@neuroassist/shared/access-http-base` para `HttpClient` com interceptor `X-Empresa-Id`; manter `isMock: true` no `TREI_FEATURE_FLAGS` — adapters serão chamados somente quando flag for `false`
3. **Criar `TreiErrorMapper`** em `frontend/libs/trei/src/lib/api/trei-error.mapper.ts` — mapeia `ProblemDetail` → `AppError`; nunca coloca `correlationId` em campo de texto visível; loga `correlationId` somente via `observability.debug()`
4. **Revisar e completar `TrainingSessionFacade`** — signals: `sessions$`, `activeTurn$`, `isLoading$`, `error$`; methods: `startSession()`, `submitTurn()`, `endSession()`
5. **Revisar e completar `ProposalReviewFacade`** — signals: `proposals$`, `isLoading$`, `error$`; idempotência G3: `approveNode()` não lança erro em re-aprovação (retorna estado atual sem HTTP extra)
6. **Integrar `@neuroassist/shared/error-model`** nos adapters e mapper — INT-003
7. **Adicionar `X-Correlation-Id` echo** via `@neuroassist/shared/access-http-base` — INT-002

#### Arquivos-alvo

```
frontend/libs/trei/src/lib/trei.types.ts                                   ← completar DTOs + view models
frontend/libs/trei/src/lib/api/training-session-http.adapter.ts            ← implementar métodos (sem TODO)
frontend/libs/trei/src/lib/api/proposal-turn-http.adapter.ts               ← implementar métodos
frontend/libs/trei/src/lib/api/draft-node-http.adapter.ts                  ← implementar métodos
frontend/libs/trei/src/lib/api/caregiver-approval-http.adapter.ts          ← implementar métodos
frontend/libs/trei/src/lib/api/trei-error.mapper.ts                        ← novo
frontend/libs/trei/src/lib/trei.facade.ts                                  ← completar signals/methods
frontend/libs/trei/src/lib/trei-proposal.facade.ts                         ← completar + G3 idempotência
frontend/libs/trei/src/index.ts                                             ← exportar TreiErrorMapper
```

#### Contrato de tipo

```typescript
// trei.types.ts — view models (resumo)
export interface SessionVM { id: string; patientId: string; status: SessionStatus; locale: Locale; turns: TurnVM[]; }
export interface TurnVM { id: string; role: 'user' | 'assistant'; content: string; createdAt: string; }
export interface ProposalNodeVM { id: string; text: string; category: string; subEstado: NodeSubEstado; approvedCaregiver: boolean; approvedPatient: boolean; }
export type SessionStatus = 'ACTIVE' | 'ENDED' | 'ERROR';
export type NodeSubEstado = 'PENDING' | 'APPROVED_CAREGIVER' | 'APPROVED_PATIENT' | 'PUBLISHED' | 'REJECTED';
export type Locale = 'pt-BR' | 'en-US' | 'es-ES';

// trei-error.mapper.ts
export function mapProblemDetailToAppError(detail: ProblemDetail): AppError {
  // correlationId NUNCA vai para fields.message ou fields.display
  return { code: detail.type, message: detail.title, detail: detail.detail ?? '' };
}

// trei.facade.ts (TrainingSessionFacade) — signals obrigatórios
readonly sessions = signal<SessionVM[]>([]);
readonly activeTurn = signal<TurnVM | null>(null);
readonly isLoading = signal(false);
readonly error = signal<AppError | null>(null);
readonly isMock = signal(TREI_FEATURE_FLAGS.useMockAdapter);
```

#### Sequência de execução

1. Completar `trei.types.ts` (todos os DTOs e view models de SPEC-03)
2. Criar `TreiErrorMapper` (G5 — crítico)
3. Completar HTTP adapters (remover TODO, implementar corpo dos métodos)
4. Completar `TrainingSessionFacade` (signals + methods)
5. Completar `ProposalReviewFacade` (G3 idempotência)
6. Verificar que `providers.ts` seleciona adapter correto via feature flag

#### Generators Nx obrigatórios

Nenhum novo projeto. Todos os arquivos são alterações em `libs/trei/`.

#### Política de testes

- `frontend/libs/trei/src/lib/api/trei-error.mapper.spec.ts` — obrigatório: verificar que `correlationId` nunca aparece em `AppError.message` ou `AppError.detail`
- `frontend/libs/trei/src/lib/trei.facade.spec.ts` — obrigatório: `startSession()` atualiza `sessions` signal
- `frontend/libs/trei/src/lib/trei-proposal.facade.spec.ts` — obrigatório: re-aprovação via `approveNode()` não lança erro (G3)
- Verificar que `grep -r 'aprovacoes/paciente' libs/trei/` retorna vazio (INV-1)

#### Multi-empresa

HTTP adapters usam `@neuroassist/shared/access-http-base` para `HttpClient` — o interceptor injeta `X-Empresa-Id` automaticamente. O sinal `empresaId` é consumido via `EmpresaContext` de `@neuroassist/shared/empresa-context`. Adapters ficam desabilitados (via feature flag) até `trei-service` estar disponível — multi-empresa será validado no P9 E2E contra o backend real.

#### Critérios de aceite

```bash
cd frontend
npx nx test trei                                         # PASS
grep -r 'aprovacoes/paciente' libs/trei/                 # vazio (INV-1 garantida)
grep -r 'correlationId' libs/trei/src/lib/api/           # somente em TreiErrorMapper (debug log)
npx nx lint trei                                         # PASS
npx nx build trei                                        # PASS
```

#### Riscos

- `@neuroassist/shared/error-model` pode não exportar `AppError` com a shape esperada — verificar `libs/shared/error-model/src/index.ts` antes de implementar `TreiErrorMapper`
- `@neuroassist/shared/access-http-base` pode requerer configuração adicional de provider — verificar implementação em módulos já integrados (ACS, EVNT)

#### Instrução para implementador

Começar pelo `TreiErrorMapper` (G5) e pelos view models (`trei.types.ts`) — eles são pré-requisito dos adapters e facades. Não ativar os HTTP adapters via feature flag. O `TREI_FEATURE_FLAGS.useMockAdapter` deve permanecer `true`. Não criar componentes.

---

### Prioridade 3 — Route Guards (G1 crítico)

**Status:** pendente
**Issues cobertas:** TREI-FE-022, TREI-FE-023
**Categoria:** ESTRUTURAL

#### Objetivo

Implementar os três guards de rota TREI que protegem as superfícies `/treinamento/sessao` e `/treinamento/proposta`, resolvendo o gap crítico G1 (acesso à review surface sem scope `treinamento.propor`).

#### Problemas identificados

1. `caregiverSessionGuard` — não implementado; rota de sessão acessível sem autenticação de caregiver
2. `treiGuard` — não implementado; todo o módulo `/treinamento` acessível sem scope `treinamento.sessao`
3. `treiProporGuard` — não implementado — **G1 crítico**: review surface acessível sem scope `treinamento.propor`; deve redirecionar para `/treinamento/sessao` (não bloquear, não 403)

#### Correções mandatórias

1. **Criar `caregiverSessionGuard`** em `frontend/libs/trei/src/lib/access/caregiver-session.guard.ts` — verifica se há sessão ativa de caregiver via `@neuroassist/shared/auth-session`; redireciona para `/login` se ausente
2. **Criar `treiGuard`** em `frontend/libs/trei/src/lib/access/trei.guard.ts` (substituir stub atual) — verifica scope `treinamento.sessao` via `@neuroassist/shared/access`; redireciona para `/dashboard` se scope ausente
3. **Criar `treiProporGuard`** em `frontend/libs/trei/src/lib/access/trei-propor.guard.ts` — verifica scope `treinamento.propor`; redireciona para `/treinamento/sessao` (nunca 403, nunca bloquear); é adicionado como `canActivate` na rota `/treinamento/proposta`
4. **Atualizar `trei.routes.ts`** para aplicar os 3 guards nas rotas corretas

#### Arquivos-alvo

```
frontend/libs/trei/src/lib/access/caregiver-session.guard.ts    ← novo
frontend/libs/trei/src/lib/access/trei.guard.ts                 ← substituir stub
frontend/libs/trei/src/lib/access/trei-propor.guard.ts          ← novo
frontend/libs/trei/src/lib/trei.routes.ts                       ← aplicar guards
frontend/libs/trei/src/index.ts                                  ← exportar guards
```

#### Contrato de tipo

```typescript
// caregiver-session.guard.ts
export const caregiverSessionGuard: CanActivateFn = (route, state) => {
  const authSession = inject(AuthSessionService);
  const router = inject(Router);
  return authSession.isAuthenticated()
    ? true
    : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

// trei.guard.ts
export const treiGuard: CanActivateFn = (route, state) => {
  const access = inject(AccessDecisionService);
  const router = inject(Router);
  return access.hasScope('treinamento.sessao')
    ? true
    : router.createUrlTree(['/dashboard']);
};

// trei-propor.guard.ts — G1: redirect, não bloqueia
export const treiProporGuard: CanActivateFn = (route, state) => {
  const access = inject(AccessDecisionService);
  const router = inject(Router);
  return access.hasScope('treinamento.propor')
    ? true
    : router.createUrlTree(['/treinamento/sessao']); // G1: vai para sessão, não para 403
};

// trei.routes.ts — aplicar guards
export const TREI_ROUTES: Routes = [
  {
    path: '',
    component: TreiRootComponent,
    canActivate: [caregiverSessionGuard, treiGuard],
    children: [
      { path: 'sessao', component: TrainingSessionPageComponent },
      { path: 'proposta', component: ProposalReviewPageComponent, canActivate: [treiProporGuard] },
      { path: '', redirectTo: 'sessao', pathMatch: 'full' },
    ],
  },
];
```

#### Sequência de execução

1. Verificar assinatura de `AccessDecisionService.hasScope()` em `@neuroassist/shared/access-decision`
2. Verificar assinatura de `AuthSessionService` em `@neuroassist/shared/auth-session`
3. Criar `caregiverSessionGuard`
4. Criar `treiGuard` (substituir stub)
5. Criar `treiProporGuard` (G1)
6. Atualizar `trei.routes.ts`
7. Exportar no barrel

#### Generators Nx obrigatórios

Nenhum.

#### Política de testes

- `caregiver-session.guard.spec.ts` — obrigatório: retorna `true` quando autenticado; redireciona para `/login` quando não autenticado
- `trei.guard.spec.ts` — obrigatório: retorna `true` com scope; redireciona para `/dashboard` sem scope
- `trei-propor.guard.spec.ts` — **obrigatório (G1)**: sem scope `treinamento.propor` redireciona para `/treinamento/sessao` (não 403, não `/dashboard`)

#### Multi-empresa

Não aplicável — guards verificam apenas JWT claims (scope), não empresa context.

#### Critérios de aceite

```bash
cd frontend
npx nx test trei                # PASS — incluindo specs dos 3 guards
npx nx lint trei                # PASS
npx nx build neuroassist-ai-web # PASS
# Verificar manualmente: acesso a /treinamento/proposta sem scope treinamento.propor
# redireciona para /treinamento/sessao (G1 validado)
```

#### Riscos

- `AccessDecisionService` pode ter nome diferente em `@neuroassist/shared/access-decision` — verificar exports do lib antes de implementar
- `hasScope()` pode ser síncrono ou retornar Observable — adaptar guard para ambos os casos (`toObservable` se necessário)

#### Instrução para implementador

Verificar PRIMEIRO as assinaturas dos shared services antes de escrever qualquer guard. O comportamento de `treiProporGuard` deve ser redirect para `/treinamento/sessao` — nunca `false`, nunca 403, nunca redirect para `/dashboard`. Este é o invariante G1.

---

### Prioridade 4 — Chat UI (Training Session — Tela 08)

**Status:** pendente
**Issues cobertas:** TREI-FE-013, TREI-FE-014, TREI-FE-015, TREI-FE-016, TREI-FE-017
**Categoria:** FUNCIONAL

#### Objetivo

Decompor o `training-session-page.component.ts` atual (componente monolítico) em hierarquia de componentes standalone conforme SPEC-02, cobrindo o fluxo completo de sessão de treino: locale tabs → chat conversation → composer → error panels.

#### Problemas identificados

1. `TrainingSessionPageComponent` existe mas é monolítico — toda a lógica inlined em ~200 LOC
2. `ChatConversationPanelComponent`, `ChatBubbleComponent`, `AssistantThinkingIndicatorComponent` não existem como arquivos separados
3. `ChatComposerComponent` não existe como componente separado
4. `ConversationErrorPanelComponent`, `EmptySessionState`, `EndSessionButton`, `SessionEndedNotice` não existem
5. `LocaleTabsComponent` não existe como componente separado

#### Correções mandatórias

1. Refatorar `TrainingSessionPageComponent` para ser smart container — lê signals da `TrainingSessionFacade`, delega rendering para componentes presentacionais
2. Criar `ChatConversationPanelComponent` + `ChatBubbleComponent` + `AssistantThinkingIndicatorComponent`
3. Criar `ChatComposerComponent`
4. Criar `ConversationErrorPanelComponent` + `EmptySessionStateComponent` + `EndSessionButtonComponent` + `SessionEndedNoticeComponent`
5. Criar `LocaleTabsComponent`
6. Todos componentes: standalone, `OnPush`, sem lógica de negócio (apenas Input/Output + signals)

#### Arquivos-alvo

```
frontend/libs/trei/src/lib/feature-training-session/
  training-session-page.component.ts        ← refatorar (smart container)
  chat-conversation-panel.component.ts      ← novo
  chat-bubble.component.ts                  ← novo
  assistant-thinking-indicator.component.ts ← novo
  chat-composer.component.ts                ← novo
  conversation-error-panel.component.ts     ← novo
  empty-session-state.component.ts          ← novo
  end-session-button.component.ts           ← novo
  session-ended-notice.component.ts         ← novo
  locale-tabs.component.ts                  ← novo
  index.ts                                  ← barrel de feature
```

#### Contrato de tipo

```typescript
// chat-conversation-panel.component.ts
@Input({ required: true }) turns!: TurnVM[];
@Input() isThinking = false;
@Output() scrollEnd = new EventEmitter<void>();

// chat-bubble.component.ts
@Input({ required: true }) turn!: TurnVM;

// chat-composer.component.ts
@Input() disabled = false;
@Output() submitTurn = new EventEmitter<string>();

// locale-tabs.component.ts
@Input({ required: true }) locales!: Locale[];
@Input({ required: true }) activeLocale!: Locale;
@Output() localeChange = new EventEmitter<Locale>();

// training-session-page.component.ts (smart) — injeta facade via inject()
private facade = inject(TrainingSessionFacade);
readonly turns = this.facade.activeTurns;     // computed signal
readonly isLoading = this.facade.isLoading;
readonly error = this.facade.error;
```

#### Sequência de execução

1. Criar componentes leaf primeiro: `ChatBubble`, `AssistantThinkingIndicator`, `EndSessionButton`, `SessionEndedNotice`
2. Criar `ChatConversationPanel` (agrega bubble + indicator)
3. Criar `ChatComposer`
4. Criar `LocaleTabs`
5. Criar error panels: `ConversationErrorPanel`, `EmptySessionState`
6. Refatorar `TrainingSessionPageComponent` para usar todos os componentes acima
7. Escrever specs unitárias

#### Generators Nx obrigatórios

Nenhum novo projeto — tudo dentro de `libs/trei`.

#### Política de testes

Spec unitário obrigatório para cada componente: renderização mínima, Inputs corretos, Outputs emitidos. Usar `TestBed` com `importTestingModule` de `RouterTestingModule` apenas se necessário.

#### Multi-empresa

Não aplicável — componentes presentacionais sem HTTP.

#### Critérios de aceite

```bash
cd frontend
npx nx test trei                # PASS — specs dos novos componentes incluídas
npx nx lint trei                # PASS
npx nx build trei               # PASS
# Verificar que nenhum componente contém injeção de HttpClient ou adapters diretamente
```

#### Riscos

- Componente monolítico existente pode ter lógica que precisa ser preservada ao refatorar — ler o arquivo antes de partir para a extração
- `OnPush` pode causar issues de detecção se signals não forem usados corretamente — preferir `computed()` e `toSignal()` para derivações

#### Instrução para implementador

Ler `training-session-page.component.ts` atual ANTES de criar qualquer novo componente. Extrair template por partes e criar arquivos separados. O smart component deve usar somente `inject(TrainingSessionFacade)` — nenhuma injeção de adapter ou HttpClient. Todos os componentes presentacionais são dumb (apenas Input/Output).

---

### Prioridade 5 — Proposal UI (Approval Surface)

**Status:** pendente
**Issues cobertas:** TREI-FE-018, TREI-FE-019, TREI-FE-020
**Categoria:** FUNCIONAL

#### Objetivo

Criar a superfície de revisão e aprovação de propostas — `ProposalReviewPageComponent` e seus subcomponentes — com enforcement estrutural de INV-1 (nenhum componente escreve `confirmadoPaciente`).

#### Problemas identificados

1. `ProposalReviewPageComponent` existe como stub vazio
2. `ProposalListComponent`, `ProposedNodeCardComponent`, `DualConfirmationComponent` não existem — INV-1 não enforced
3. `ProposalEditDialogComponent`, `ApprovalSummaryBarComponent`, `ProposalEmptyStateComponent` não existem
4. G2 (deep-link para proposta específica): `ProposalReviewPageComponent` deve suportar `proposalId` como query param

#### Correções mandatórias

1. Refatorar `ProposalReviewPageComponent` — smart container que consome `ProposalReviewFacade`; suporta `proposalId` query param (G2)
2. Criar `ProposalListComponent` — lista de propostas com paginação; `@Output() selectProposal`
3. Criar `ProposedNodeCardComponent` — card de nó proposto; exibe `subEstado`; `@Output() approve`, `@Output() reject`; INV-1: sem botão/output para `confirmadoPaciente`
4. Criar `DualConfirmationComponent` — mostra estado de aprovação dual (caregiver ✓ / paciente pendente); read-only para paciente
5. Criar `ProposalEditDialogComponent` — edição de texto do nó proposto antes da aprovação final
6. Criar `ApprovalSummaryBarComponent` — barra de status de aprovações pendentes
7. Criar `ProposalEmptyStateComponent` — estado vazio quando não há propostas

#### Arquivos-alvo

```
frontend/libs/trei/src/lib/feature-proposal-review/
  proposal-review-page.component.ts     ← refatorar (smart + G2)
  proposal-list.component.ts            ← novo
  proposed-node-card.component.ts       ← novo (INV-1 enforced)
  dual-confirmation.component.ts        ← novo
  proposal-edit-dialog.component.ts     ← novo
  approval-summary-bar.component.ts     ← novo
  proposal-empty-state.component.ts     ← novo
  index.ts                              ← barrel de feature
```

#### Contrato de tipo

```typescript
// proposed-node-card.component.ts — INV-1: sem output confirmadoPaciente
@Input({ required: true }) node!: ProposalNodeVM;
@Output() approve = new EventEmitter<string>();   // nodeId
@Output() reject = new EventEmitter<string>();    // nodeId
// PROIBIDO: @Output() confirmadoPaciente — INV-1

// proposal-review-page.component.ts (smart)
private facade = inject(ProposalReviewFacade);
private route = inject(ActivatedRoute);
readonly proposals = this.facade.proposals;
readonly proposalId = toSignal(this.route.queryParamMap.pipe(map(p => p.get('proposalId')))); // G2
```

#### Sequência de execução

1. Criar `ProposalEmptyStateComponent` e `ApprovalSummaryBarComponent` (sem dependências)
2. Criar `DualConfirmationComponent`
3. Criar `ProposedNodeCardComponent` (sem `confirmadoPaciente` — INV-1)
4. Criar `ProposalListComponent`
5. Criar `ProposalEditDialogComponent`
6. Refatorar `ProposalReviewPageComponent` (G2 + smart container)
7. Specs unitárias para todos

#### Generators Nx obrigatórios

Nenhum.

#### Política de testes

- Spec de `ProposedNodeCardComponent` — **obrigatório INV-1**: verificar que nenhum Output se chama `confirmadoPaciente`; verificar que `approve` e `reject` emitem `nodeId` correto
- Spec de `ProposalReviewPageComponent` — G2: `proposalId` query param é lido e passado para facade

#### Multi-empresa

Não aplicável — componentes presentacionais sem HTTP.

#### Critérios de aceite

```bash
cd frontend
npx nx test trei                                        # PASS
grep -r 'confirmadoPaciente' libs/trei/                 # vazio (INV-1)
npx nx lint trei                                        # PASS
npx nx build trei                                       # PASS
```

#### Instrução para implementador

Verificar INV-1 explicitamente: `grep -r 'confirmadoPaciente' libs/trei/` deve retornar vazio. Documentar este check no spec de `ProposedNodeCardComponent`.

---

### Prioridade 6 — UI Primitives + Architecture Hardening

**Status:** pendente
**Issues cobertas:** TREI-FE-021
**Categoria:** ESTRUTURAL

#### Objetivo

Criar os componentes UI primitivos de nível de módulo (`TreiAuthoringBannerComponent`, `StatusChipComponent`) e iniciar o plano de split das 8 sublibs conforme `generator-config.yml` — sem executar o split nesta prioridade, apenas documentar e preparar.

#### Problemas identificados

1. `TreiAuthoringBannerComponent` inlined no `training-session-page` — violação de INV-6 (banner em todas as rotas TREI)
2. `StatusChipComponent` não existe como componente separado reutilizável
3. Sublibsmodule split ainda não iniciado

#### Correções mandatórias

1. Criar `TreiAuthoringBannerComponent` em `frontend/libs/trei/src/lib/ui/trei-authoring-banner.component.ts` — banner visível em todas as rotas TREI (declarado no `TreiRootComponent`)
2. Criar `StatusChipComponent` em `frontend/libs/trei/src/lib/ui/status-chip.component.ts` — chip reutilizável para `SessionStatus` e `NodeSubEstado`; sem CSS hardcoded (usa tokens de `@neuroassist/shared/ui-tokens`)
3. Remover instância inlined de banner do `training-session-page`
4. Documentar decisão de split em ADR ou nota no `module-state.md` (sem executar o split)

#### Arquivos-alvo

```
frontend/libs/trei/src/lib/ui/
  trei-authoring-banner.component.ts   ← novo
  status-chip.component.ts             ← novo
  index.ts                             ← barrel de ui
frontend/libs/trei/src/lib/trei-root.component.ts  ← adicionar banner ao template
frontend/libs/trei/src/index.ts        ← exportar novos componentes
```

#### Critérios de aceite

```bash
cd frontend
npx nx test trei    # PASS
npx nx lint trei    # PASS
npx nx build trei   # PASS
# Verificar: StatusChipComponent não tem cor hardcoded (CSS custom properties apenas)
```

#### Instrução para implementador

`StatusChipComponent` usa somente CSS custom properties de `@neuroassist/shared/ui-tokens` — zero `color: #xxx` hardcoded. O banner é adicionado ao template do `TreiRootComponent` para aparecer em todas as rotas filhas.

---

### Prioridade 7 — UX Polish + CONV Integration

**Status:** pendente
**Issues cobertas:** TREI-FE-024, TREI-FE-025, TREI-INT-005
**Categoria:** FUNCIONAL

#### Objetivo

Completar os comportamentos de UX avançados: spinner otimista por card de proposta, banner `patient-not-found` (G4), rollback de aprovação, e exibição de `embedModelId` de status (G5 link com CONV).

#### Correções mandatórias

1. **Per-card optimistic approval (FE-024):** `ProposedNodeCardComponent` emite estado otimista imediatamente ao clicar "Aprovar" — spinner no card; rollback automático se backend retornar erro
2. **patient-not-found banner (FE-025, G4):** `TrainingSessionPageComponent` detecta erro `PATIENT_NOT_FOUND` via `TrainingSessionFacade.error` signal — exibe banner sem criar sessão; usa `ConversationErrorPanelComponent`
3. **embedModelId display (INT-005, G5 link):** mostrar estado de readiness do embedding model na UI de forma não-técnica (ex.: "Motor de IA disponível / indisponível"); consumir status via `TrainingSessionFacade`

#### Critérios de aceite

```bash
cd frontend
npx nx test trei    # PASS — incluindo spec de rollback (G3/P7)
npx nx lint trei    # PASS
# G4: com mock retornando PATIENT_NOT_FOUND, banner exibe sem iniciar sessão
# G5 (correlationId): grep na UI html deve retornar vazio para 'correlationId'
```

---

### Prioridade 8 — Observability

**Status:** pendente
**Issues cobertas:** TREI-FE-026, TREI-INT-006
**Categoria:** ESTRUTURAL

#### Objetivo

Emitir 3 eventos de telemetria (`session-started`, `turn-submitted`, `node-approved`) via `@neuroassist/shared/observability` sem PHI nos payloads, e integrar tokens visuais de `@neuroassist/shared/ui-tokens`.

#### Correções mandatórias

1. Criar `TreiTelemetryService` em `frontend/libs/trei/src/lib/trei-telemetry.service.ts` — emite eventos via `ObservabilityService` de `@neuroassist/shared/observability`
2. Verificar que nenhum payload contém `patientId` em texto plano — SEC-002
3. Aplicar `ui-tokens` para quaisquer cores/espaçamentos hardcoded restantes

#### Critérios de aceite

```bash
cd frontend
npx nx test trei    # PASS
npx nx lint trei    # PASS
# grep nos payloads de telemetry: sem 'patientId' em plaintext (SEC-002)
```

---

### Prioridade 9 — E2E Hardening

**Status:** pendente
**Issues cobertas:** TREI-FE-027, TREI-FE-028, TREI-FE-029, TREI-FE-030, TREI-FE-031
**Categoria:** VALIDACAO

#### Objetivo

Implementar os steps Playwright/Cucumber para os 5 feature files existentes, cobrindo todos os gaps G1..G5 com cenários automatizados verificáveis.

#### Feature files existentes (sem steps)

| Issue | Feature | Gaps cobertos |
|---|---|---|
| TREI-FE-027 | `ciclo-de-sessao.feature` | — |
| TREI-FE-028 | `autoria-e-aprovacao.feature` | G3 |
| TREI-FE-029 | `apenas-lado-cuidadora.feature` | G1, G2, INV-1 |
| TREI-FE-030 | `erros-e-degradacao.feature` | G4, G5 |
| TREI-FE-031 | `independencia-de-locale.feature` | — |

#### Correções mandatórias

1. Implementar steps Playwright para todos os 5 features
2. `FE-029`: verificar que acesso a `/treinamento/proposta` sem scope `treinamento.propor` redireciona para `/treinamento/sessao` (G1); verificar deep-link por `proposalId` query param (G2); verificar INV-1 (`confirmadoPaciente` nunca visível)
3. `FE-030`: verificar que `correlationId` nunca aparece no DOM (G5); verificar banner `patient-not-found` (G4)

#### Critérios de aceite

```bash
cd frontend
npx nx e2e gest-e2e    # base de exemplo (adaptar para trei-e2e quando configurado)
# Todos os 5 feature files com steps implementados
# G1..G5 todos com cenário e2e passing
```

---

## 4. Dependências externas

| Dependência | Tipo | Impacto | Status |
|---|---|---|---|
| `trei-service` (backend, porta 8091) | HTTP | Bloqueia ativação dos HTTP adapters | Não deployado — mock ativo |
| `@neuroassist/shared/access-decision` | Lib Nx | Bloqueia implementação dos guards (P3) | Disponível (verificar `hasScope()`) |
| `@neuroassist/shared/auth-session` | Lib Nx | Bloqueia `caregiverSessionGuard` (P3) | Disponível (verificar `isAuthenticated()`) |
| `@neuroassist/shared/error-model` | Lib Nx | Bloqueia `TreiErrorMapper` (P2) | Disponível (verificar `AppError` shape) |
| `@neuroassist/shared/access-http-base` | Lib Nx | Bloqueia HTTP adapters reais (P2 impl, P9 ativação) | Disponível (verificar `HttpClient` com interceptor) |
| `@neuroassist/shared/observability` | Lib Nx | Bloqueia telemetria (P8) | Verificar disponibilidade antes de P8 |
| `@neuroassist/shared/ui-tokens` | Lib Nx | Bloqueia `StatusChipComponent` (P6) | Verificar tokens disponíveis |
| `neuroassist-ai-web` shell `module-descriptors.ts` | App Nx | Bloqueia smoke test do shell (P1) | Verificar se interface existe |

---

## 5. Restrições arquiteturais (Nx boundaries)

| Regra | Descrição | Estado atual |
|---|---|---|
| RULE-NX-01 | `scope:trei` só depende de `scope:shared` | Pendente correção `.eslintrc.json` (P1) |
| RULE-NX-02 | `scope:web` pode depender de `scope:trei` | Pendente adição no `.eslintrc.json` (P1) |
| RULE-NX-03 | `scope:trei` não depende de `scope:gest`, `scope:pac`, `scope:evnt` | Enforced via depConstraint pós-P1 |
| RULE-NX-04 | Sublibs futuras: `type:data-access` só de `scope:shared`; `type:feature` de `scope:trei` + `scope:shared` | Aplicável pós-P6 split |

**Decisão registrada:** Split das 8 sublibs diferido para P6. O monolito `libs/trei/` com tag `scope:trei` é aceitável até MVP + guards completos.

---

## 6. Referências

| Documento | Relevância |
|---|---|
| `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-01-route-map.md` | Hierarquia de rotas e guards |
| `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-02-component-hierarchy.md` | Hierarquia de componentes (P4-P5) |
| `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-03-view-models.md` | Contratos de tipo para P2 |
| `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-04-data-access.md` | Adapters e facades |
| `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-06-shell-integration.md` | Wiring com shell (P1) |
| `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-07-error-handling.md` | G5 + TreiErrorMapper (P2) |
| `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-08-e2e-scenarios.md` | Cenários e2e (P9) |
| `docs/architecture/frontend/modules/trei/frontend/frontend-spec/generator-config.yml` | Target structure das 8 sublibs (referência P6) |
| `docs/architecture/frontend/modules/trei/frontend/plans/plan-FE-TREI.md` | Plano de ondas original do arquiteto (referência histórica) |
| `docs/architecture/frontend/standards/nx-boundaries.md` | Tags, allowed directions, ESLint config |
| `docs/architecture/shared/multi-empresa.md` | Interceptor X-Empresa-Id, HttpBase |
