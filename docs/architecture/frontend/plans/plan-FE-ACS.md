# Plano FE — ACS (neuroassist-ai-web)

> Gerado por: `/fe-planner` (Pipeline B, Etapa B3) | 2026-08-15
> Baseline score: **41/100 — Parcial/Inicial** | Issues DONE: 0/36; PARCIAL: ~15/36; MISSING: ~21/36
> Tipo de planejamento: **REPLANEJAMENTO**
> Plano anterior: `docs/architecture/frontend/modules/acs/frontend/plans/plan-FE-ACS.md` (stub sem conteúdo — caminho externo inacessível; este documento substitui e se torna o canônico local)
> Issues canônicas: `docs/architecture/frontend/modules/acs/frontend/issues/FE-ACS-*.md` (31) + `INT-FE-ACS-*.md` (5) = **36 total**

---

## Status das prioridades

- **P1** — Lint Fix + Unused Import Cleanup — pendente (BLOQUEADOR ESTRUTURAL)
- **P2** — Access Layer: Guards RBAC Reais — pendente (BLOQUEADOR SEGURANÇA)
- **P3** — Foundation Completion: Module Contract + Descriptor + Nav Config + OnPush — pendente
- **P4** — Feature Components MVP: Editor (form+draft) + Linkage (full) + TenantSetup (steps) — pendente
- **P5** — Unit Tests: façades + guards + adapters + components — pendente
- **P6** — HTTP Adapters + Multi-empresa Header Wiring — pendente
- **P7** — E2E: Unskip + Implement 25+ Playwright Scenarios — pendente
- **P8** — UX Polish: Coverage Blocking Modal + Error Boundary + a11y — pendente
- **P9** — Go-live Gate: Observability + EVNT Contract + Final E2E Audit (40+) — pendente

---

## 0. Leitura executiva do estado atual

### Papel do módulo FE

ACS (Cuidadores e Acesso) é o núcleo de controle de acesso multi-tenant do NeuroAssist AI. Gerencia: (1) onboarding de tenant (TenantSetup), (2) roster de cuidadores com status de atividade e papel (coordenador vs. cuidadora), (3) vínculo paciente-cuidador com derivação de cobertura (ADR-005: nenhum paciente sem cuidador ativo), e (4) guards RBAC que protegem as rotas `/acs` e sub-rotas de escrita. É um módulo de segurança — guards que retornam `true` incondicionalmente são uma vulnerabilidade de acesso real, mais grave que gaps de UI.

### Estado atual observado

Código core existe e é funcional em modo mock: façades (`AcsTenantFacade`, `AcsCaregiverFacade`) com signals readonly corretos; 3 ports e 6 adapters (mock + HTTP stub) estruturados; 4 componentes com templates Tailwind completos; routes wired em `ACS_ROUTES`; providers factory `provideAcs()` integrada em `app.config.ts` do shell real. TypeScript compila sem erro.

Mas nenhum gate de qualidade passa: lint FAIL (4 erros de selector prefix), 0 testes unitários, 0 e2e rodando (25 cenários em `test.skip()`), guards retornam `true` incondicionalmente, e HTTP adapters sem header `X-Empresa-Id`.

### Continuidade da última rodada

Não há histórico de implementação anterior (BASELINE inaugural). O próximo passo é este plano — executado pela primeira vez a partir do zero.

### Diagnóstico executivo

| Dimensão | Status | Gap principal |
|---|---|---|
| Estrutural | PARCIAL | Lint FAIL (selector prefix mismatch — 4 erros) — bloqueador imediato |
| Segurança | CRÍTICO | Guards retornam `true` incondicionalmente — ausência real de RBAC |
| Funcional | PARCIAL | Façades + mock adapters OK; editor sem form validation; linkage minimal; TenantSetup step incompleto |
| Testes | CRÍTICO | Zero `.spec.ts`; nenhum gate de cobertura passou |
| E2E | CRÍTICO | 25 cenários em `test.skip()`; 0 rodando |
| Integração | LOW | HTTP adapters scaffolded; sem URL wiring real; sem `X-Empresa-Id` |
| Observabilidade | AUSENTE | Nenhuma telemetria, logging, ou performance tracking |

### Decisão de estrutura: monolito vs. sub-libs (D-mono-acs)

As issues FE-ACS-001..031 foram escritas para 10 sub-libs separadas (`libs/acs/shell-entry`, `libs/acs/data-access`, etc.). O que foi scaffolded é uma única lib monolítica `frontend/libs/acs/`. **Decisão deste plano: manter a estrutura monolítica** e mapear os arquivos dos issues para `frontend/libs/acs/src/lib/`. Justificativa: criar 10 sub-libs com lint quebrado adiciona superfície de falha; a lib monolítica tem tags `scope:acs` + `type:feature` corretas; boundaries por scope continuam enforçados; a divisão em sub-libs pode acontecer após os gates estarem verdes. Os caminhos de arquivo nos critérios de aceite refletem a estrutura real.

### Multi-empresa: interceptor @neuroassist/shared/access-http-base configurado?

**NÃO.** `@neuroassist/shared/access-http-base` é stub Wave-0 (`export const ACCESS_HTTP_BASE = '/api'`). Os 3 HTTP adapters injetam `HttpClient` e usam caminhos hardcoded (`/api/v1/acs/caregivers`). O gap é registrado em INT-FE-ACS-001 e será tratado em P6. Ação nesta wave: adicionar comentário operacional `TODO[INT-FE-ACS-001]` nos adapters + usar `ACCESS_HTTP_BASE` como prefixo para quando o interceptor real for promovido.

---

## 1. Principais achados

### Pontos fortes

- `AcsCaregiverFacade` bem estruturada: 176 linhas, signals readonly, `lastValueFrom()` + async/await, coverage derivation (coverageByPatient, coverageFor), revokeLink com tratamento de 409 (ADR-005) já presente com mensagem correta
- `AcsTenantFacade`: signals corretos; provision + loadCurrentTenant implementados
- 3 ports bem definidos (`AcsTenantPort`, `AcsCuidadorPort`, `AcsLinkPort`); mock adapters seeded com "Jamilly Amaral"
- `provideAcs()` factory alterna mock ↔ HTTP sem mudança de código no consumidor
- `ACS_ROUTES` com 4 rotas + guards wired; descriptor e shell-entry já exportados
- TypeScript compila sem erro (`tsc --noEmit` OK)

### Gaps

1. **Lint FAIL** (BLOQUEADOR): `.eslintrc.json` espera prefix `lib`; todos os 4 componentes usam prefix `acs-*` → 4 erros. Guards têm `inject` e `Router` importados mas não usados → 2 warnings
2. **Guards open gates** (BLOQUEADOR SEGURANÇA): `acsCoordinatorGuard`, `acsCaregiverWriteGuard`, `acsLinkWriteGuard` retornam `true` sem verificar sessão ou permissão — qualquer usuário acessa `/acs` e rotas de escrita
3. **Zero testes** (CRÍTICO): 0 `.spec.ts`; `nx test @neuroassist/acs` nunca rodou com sucesso
4. **E2E todos skipados** (CRÍTICO): 25 cenários em `caregiver-roster.spec.ts` são `test.skip()`; `patient-linkage.spec.ts` e `tenant-onboarding.spec.ts` também stub
5. **Módulo contract incompleto**: `acs.descriptor.ts` existe mas não exporta `MODULE_PERMISSION_MAP` nem `MODULE_FEATURE_FLAGS` (FE-ACS-001)
6. **Editor sem form validation**: `CaregiverEditorComponent` sem ReactiveFormsModule, sem validators, sem draft preservation
7. **Linkage minimal**: `PatientLinkageComponent` scaffold-only; data flow ausente
8. **OnPush ausente**: `PatientLinkageComponent` e `TenantSetupComponent` sem `ChangeDetectionStrategy.OnPush`
9. **Multi-empresa**: HTTP adapters sem `X-Empresa-Id` header

### Riscos

- `AccessDecision` e `AuthSession` são stubs Wave-0 (apenas interfaces). Os guards devem injetar um `AcsPermissionService` local que wraps a interface; retornar `false` quando `AuthSession.token === null` já fecha a vulnerabilidade para o MVP
- Coverage derivation em `AcsCaregiverFacade._refreshCoverageMap()` faz N+1 requests (`listForCaregiver` por cuidador) — aceitável em dev/mock, mas é risco de performance em produção; documentar no P9

### Aderência às regras Nx

- Tags `scope:acs`, `type:feature` presentes em `project.json` — OK
- `depConstraints` no workspace `.eslintrc.json` enforçam `scope:acs → scope:acs | scope:shared`
- `enforceBuildableLibDependency` não dispara erro (lib monolítica não tem sub-libs buildable)
- **Decisão P1**: alterar prefix de selector de `lib` → `acs` no `.eslintrc.json` local de libs/acs. Justificativa: os componentes foram scaffolded com `acs-*` selectors alinhados ao módulo; renomear para `lib-*` quebraria o namespace e violaria a identidade de módulo; a mudança é no lint config, não no código de produção

---

## 2. Estratégia geral

### Objetivo macro

Passar os gates lint (P1) e fechar vulnerabilidade de acesso (P2) imediatamente, completar o contrato de módulo e melhorias de foundation (P3), atingir o gate MVP-shippable (features completas + testes unitários, P4-P5), wiring HTTP + multi-empresa (P6), e2e (P7), UX polish + safety-critical modal (P8), e go-live gate com EVNT contract (P9).

### Ordem das prioridades

| P | Título | Motivo | Issues cobertas |
|---|---|---|---|
| P1 | Lint Fix + Unused Import Cleanup | Lint FAIL bloqueia CI; fix é 30 min; nenhum gate pode ser validado sem lint verde | FE-ACS-003 (partial) |
| P2 | Guards RBAC Reais | Vulnerabilidade de segurança: qualquer usuário acessa rotas protegidas; fix antes de qualquer feature | FE-ACS-004, FE-ACS-005, FE-ACS-007, FE-ACS-009 |
| P3 | Foundation: Module Contract + Descriptor + Nav Config + OnPush | Contrato de módulo incompleto bloqueia shell integration; OnPush faltando em 2 componentes | FE-ACS-001, FE-ACS-004, FE-ACS-006 (partial) |
| P4 | Feature Components MVP: Editor + Linkage + TenantSetup | Editor sem form validation, Linkage minimal, TenantSetup incompleto — MVP não é shippable sem eles | FE-ACS-008 (partial), FE-ACS-010, FE-ACS-012, FE-ACS-013 |
| P5 | Unit Tests: façades + guards + adapters + components | Zero testes é bloqueador de MVP; 60%+ cobertura em façades e guards | FE-ACS-020, FE-ACS-021, FE-ACS-022 |
| P6 | HTTP Adapters + Multi-empresa Header | Backend wiring; `X-Empresa-Id` obrigatório para isolamento cross-tenant | INT-FE-ACS-001, INT-FE-ACS-002, INT-FE-ACS-003 |
| P7 | E2E: 25+ Playwright Scenarios | 25 cenários em `test.skip()` — nenhuma regressão detectável; gate go-live exige 40+ | FE-ACS-027, FE-ACS-028, FE-ACS-029 |
| P8 | UX Polish: Coverage Modal + Error Boundary + a11y | Safety-critical: modal de bloqueio de cobertura (ADR-005); acessibilidade e error recovery | FE-ACS-014, FE-ACS-015, FE-ACS-016, FE-ACS-027 (partial) |
| P9 | Go-live Gate: Observability + EVNT Contract + Final E2E Audit | Gate final: 40+ e2e, 0 lint, observability hooks, contrato EVNT frozen | INT-FE-ACS-004, INT-FE-ACS-005, FE-ACS-030, FE-ACS-031 |

---

## 3. Plano por prioridade

---

### Prioridade P1 — Lint Fix + Unused Import Cleanup

Status: pendente
Issues cobertas: FE-ACS-003 (gate de lint)
Categoria: ESTRUTURAL

#### Objetivo

Fazer `npx nx lint @neuroassist/acs` passar com 0 erros e 0 warnings. Desbloqueia CI e todos os gates subsequentes que dependem de lint verde.

#### Problema identificado

`.eslintrc.json` de `libs/acs/` tem `"prefix": "lib"` para component-selector e directive-selector. Os 4 componentes scaffolded usam `acs-*` (ex.: `acs-caregiver-roster`). Resultado: 4 erros de lint. Adicionalmente, `acs.guard.ts` importa `inject` e `Router` de `@angular/core` e `@angular/router` sem usá-los (comentados nos TODOs) → 2 warnings de `no-unused-vars`.

#### Correções mandatórias

1. **`frontend/libs/acs/.eslintrc.json`**: alterar `"prefix": "lib"` para `"prefix": "acs"` em ambas as regras (`@angular-eslint/component-selector` e `@angular-eslint/directive-selector`).
2. **`frontend/libs/acs/src/lib/access/acs.guard.ts`**: remover as linhas de import `import { inject } from '@angular/core'` e `import { CanActivateFn, Router } from '@angular/router'`; substituir pela importação mínima `import { CanActivateFn } from '@angular/router'`.

#### Arquivos-alvo

- `frontend/libs/acs/.eslintrc.json`
- `frontend/libs/acs/src/lib/access/acs.guard.ts`

#### Contrato de tipo

Nenhuma mudança de tipo. Somente configuração de lint e limpeza de imports.

#### Sequência de execução

1. Editar `.eslintrc.json`: `"prefix": "lib"` → `"prefix": "acs"` (2 ocorrências: component-selector e directive-selector).
2. Editar `acs.guard.ts`: remover imports não utilizados; manter o arquivo funcional (retorna `true` até P2).
3. Executar `npx nx lint @neuroassist/acs --fix` para confirmar ausência de auto-fix pendente.
4. Executar `npx nx lint @neuroassist/acs` → verificar 0 erros, 0 warnings.

#### Generators Nx obrigatórios

Nenhum.

#### Política de testes

Nenhum teste novo nesta wave. Lint gate é o único critério.

#### Multi-empresa

Não aplicável nesta wave.

#### Critérios de aceite

```bash
npx nx lint @neuroassist/acs
# saída esperada: 0 errors, 0 warnings
```

#### Riscos

- O workspace `.eslintrc.json` pode ter uma regra global de prefix que sobrescreve o local. Verificar com `nx lint` após a mudança; se persistir erro, adicionar `"prefix": "acs"` também no override do workspace para `libs/acs/**`.

#### Instrução para o implementador

Editar apenas os 2 arquivos acima. Não tocar no código dos componentes. Confirmar que `npx nx lint @neuroassist/acs` retorna 0 erros antes de encerrar.

---

### Prioridade P2 — Access Layer: Guards RBAC Reais

Status: pendente
Issues cobertas: FE-ACS-004 (descriptor permission gates), FE-ACS-005 (bootstrap access check), FE-ACS-007 (idempotency/permissions guard), FE-ACS-009 (vinculo write guard)
Categoria: ESTRUTURAL / SEGURANÇA

#### Objetivo

Fechar a vulnerabilidade de acesso: os 3 guards de rota devem retornar `false` e redirecionar para `/` quando a sessão de usuário estiver ausente ou o usuário não tiver a permissão necessária. Eliminar o retorno incondicional `return true`.

#### Problema identificado

`acsCoordinatorGuard`, `acsCaregiverWriteGuard`, `acsLinkWriteGuard` em `frontend/libs/acs/src/lib/access/acs.guard.ts` retornam `true` sem nenhuma verificação. `@neuroassist/shared/access-decision` exporta apenas a interface `AccessDecision { can(permission: string): boolean }` (Wave-0 stub). `@neuroassist/shared/auth-session` exporta apenas `AuthSession { readonly token: string | null }` (Wave-0 stub). Não existe injeção de token DI nem implementação concreta.

#### Correções mandatórias

1. **Criar `frontend/libs/acs/src/lib/access/acs-access-tokens.ts`**: constantes de permissão ACS UI-level.
2. **Criar `frontend/libs/acs/src/lib/access/acs-permission.service.ts`**: `AcsPermissionService` implementando `AccessDecision`; injeta `AUTH_SESSION` (token DI a ser criado); retorna `can('acs.cuidador.read') = token !== null` para MVP (lógica de role real entra no P9 com BE JWT claims).
3. **Criar `frontend/libs/acs/src/lib/access/acs-auth-session.token.ts`**: `InjectionToken<AuthSession>` com factory que retorna `{ token: null }` (sem mock de session; a shell real injeta a sessão real no futuro).
4. **Atualizar `frontend/libs/acs/src/lib/access/acs.guard.ts`**: injetar `AcsPermissionService`; implementar os 3 guards com `can()` + `Router` para redirect; remover `return true`.
5. **Atualizar `frontend/libs/acs/src/lib/acs.providers.ts`**: incluir provider de `AcsPermissionService` e `ACS_AUTH_SESSION` token.
6. **Atualizar `frontend/libs/acs/src/index.ts`**: exportar `AcsPermissionService` e `ACS_AUTH_SESSION`.

#### Arquivos-alvo

```
frontend/libs/acs/src/lib/access/acs-access-tokens.ts        ← novo
frontend/libs/acs/src/lib/access/acs-permission.service.ts   ← novo
frontend/libs/acs/src/lib/access/acs-auth-session.token.ts   ← novo
frontend/libs/acs/src/lib/access/acs.guard.ts                ← modificar
frontend/libs/acs/src/lib/acs.providers.ts                   ← modificar
frontend/libs/acs/src/index.ts                               ← modificar
```

#### Contrato de tipo

```typescript
// acs-access-tokens.ts
export const ACS_PERMISSIONS = {
  READ: 'acs.cuidador.read',
  WRITE: 'acs.cuidador.write',
  LINK_WRITE: 'acs.vinculo.write',
} as const;
export type AcsPermission = typeof ACS_PERMISSIONS[keyof typeof ACS_PERMISSIONS];

// acs-auth-session.token.ts
export const ACS_AUTH_SESSION = new InjectionToken<AuthSession>('ACS_AUTH_SESSION', {
  factory: () => ({ token: null }),
});

// acs-permission.service.ts
@Injectable({ providedIn: 'root' })
export class AcsPermissionService implements AccessDecision {
  private readonly session = inject(ACS_AUTH_SESSION);
  can(permission: string): boolean {
    if (this.session.token === null) return false;
    // MVP: token presente = pode tudo no scope ACS (RBAC real via JWT claims em P9)
    return Object.values(ACS_PERMISSIONS).includes(permission as AcsPermission);
  }
}

// acs.guard.ts (após P2)
export const acsCoordinatorGuard: CanActivateFn = () => {
  const perm = inject(AcsPermissionService);
  if (!perm.can(ACS_PERMISSIONS.READ)) {
    inject(Router).navigate(['/']);
    return false;
  }
  return true;
};
export const acsCaregiverWriteGuard: CanActivateFn = () =>
  inject(AcsPermissionService).can(ACS_PERMISSIONS.WRITE)
    ? true
    : (inject(Router).navigate(['/']), false);
export const acsLinkWriteGuard: CanActivateFn = () =>
  inject(AcsPermissionService).can(ACS_PERMISSIONS.LINK_WRITE)
    ? true
    : (inject(Router).navigate(['/']), false);
```

#### Sequência de execução

1. data-access: criar `acs-access-tokens.ts`, `acs-auth-session.token.ts`, `acs-permission.service.ts`
2. access: reescrever `acs.guard.ts` com injeção de `AcsPermissionService`
3. wiring: atualizar `acs.providers.ts` e `index.ts`
4. verificar: `npx nx lint @neuroassist/acs` → 0 erros; `npx nx build @neuroassist/acs` → clean

#### Generators Nx obrigatórios

Nenhum.

#### Política de testes

Testes unitários dos guards são escritos em P5 (não P2, para não atrasar o fechamento do gate de segurança). O implementador deve garantir que `npx nx build @neuroassist/acs` é limpo após P2.

#### Multi-empresa

`AcsPermissionService` não emite HTTP. `ACS_AUTH_SESSION` não tem header. Não aplicável nesta wave.

#### Critérios de aceite

```bash
npx nx lint @neuroassist/acs
# 0 errors, 0 warnings

npx nx build @neuroassist/acs
# build clean (sem erros TypeScript)

# Verificação manual (ou teste no P5):
# acsCoordinatorGuard com token=null → retorna false e redireciona para '/'
# acsCoordinatorGuard com token='abc' → retorna true (MVP)
```

#### Riscos

- `ACS_AUTH_SESSION` token com factory `{ token: null }` significa que em dev, sem a shell injetando a sessão real, os guards retornam `false`. Isso é **correto comportamento** — o dev server precisa estar logado. Se o time de dev precisar testar sem login, usar `provideAcs({ adapter: 'mock' })` e sobrescrever o token no `app.config.ts` local de dev.

#### Instrução para o implementador

Criar os 3 novos arquivos, reescrever `acs.guard.ts`, atualizar providers e barrel. Não modificar os componentes ou façades. Confirmar `npx nx build @neuroassist/acs` limpo.

---

### Prioridade P3 — Foundation Completion: Module Contract + Descriptor + Nav Config + OnPush

Status: pendente
Issues cobertas: FE-ACS-001 (module contract), FE-ACS-004 (descriptor), FE-ACS-006 (nav config / partial)
Categoria: ESTRUTURAL

#### Objetivo

Completar o contrato de módulo exportando `MODULE_PERMISSION_MAP` e `MODULE_FEATURE_FLAGS`; completar o descriptor `ACS_MODULE_DESCRIPTOR` com `permissions` gate; criar `ACS_NAV_CONFIG`; adicionar `ChangeDetectionStrategy.OnPush` nos 2 componentes que estão sem ele.

#### Problema identificado

`frontend/libs/acs/src/lib/acs.descriptor.ts` existe e exporta `ACS_MODULE_DESCRIPTOR`, mas não exporta `MODULE_PERMISSION_MAP` nem `MODULE_FEATURE_FLAGS` (requeridos por FE-ACS-001 / SPEC-05). `ACS_NAV_CONFIG` não existe. `PatientLinkageComponent` e `TenantSetupComponent` não têm `ChangeDetectionStrategy.OnPush` (detectado no baseline).

#### Correções mandatórias

1. **Criar `frontend/libs/acs/src/lib/acs-nav-config.ts`**: constante `ACS_NAV_CONFIG` (label, icon, route, order).
2. **Atualizar `frontend/libs/acs/src/lib/acs.descriptor.ts`**: adicionar `MODULE_PERMISSION_MAP` (collapsa authorities BE → UI write keys) e `MODULE_FEATURE_FLAGS` com defaults (`coverageGuard: 'on'`, `inviteByEmail: 'off'`, `showSemCoberturaBanner: 'on'`).
3. **Atualizar `frontend/libs/acs/src/lib/feature-patient-linkage/patient-linkage.component.ts`**: adicionar `changeDetection: ChangeDetectionStrategy.OnPush`.
4. **Atualizar `frontend/libs/acs/src/lib/feature-tenant-setup/tenant-setup.component.ts`**: adicionar `changeDetection: ChangeDetectionStrategy.OnPush`.
5. **Atualizar `frontend/libs/acs/src/index.ts`**: exportar `ACS_NAV_CONFIG`, `MODULE_PERMISSION_MAP`, `MODULE_FEATURE_FLAGS`.

#### Arquivos-alvo

```
frontend/libs/acs/src/lib/acs-nav-config.ts                               ← novo
frontend/libs/acs/src/lib/acs.descriptor.ts                               ← modificar
frontend/libs/acs/src/lib/feature-patient-linkage/patient-linkage.component.ts ← modificar
frontend/libs/acs/src/lib/feature-tenant-setup/tenant-setup.component.ts  ← modificar
frontend/libs/acs/src/index.ts                                            ← modificar
```

#### Contrato de tipo

```typescript
// acs-nav-config.ts
export const ACS_NAV_CONFIG = {
  label: 'Cuidadores',
  icon: 'group',
  route: '/acs',
  showInNav: true,
  order: 3,
} as const;

// acs.descriptor.ts — adições
export const MODULE_PERMISSION_MAP = {
  'acs.cuidador.write': 'acs.cuidador.criar',
  'acs.cuidador.deactivate': 'acs.cuidador.desativar',
  'acs.vinculo.write': 'acs.vinculo.criar',
  'acs.cuidador.read': 'acs.cuidador.listar',
} as const;

export const MODULE_FEATURE_FLAGS = {
  'acs.coverageGuard': 'on',
  'acs.inviteByEmail': 'off',
  'acs.showSemCoberturaBanner': 'on',
} as const;
```

#### Sequência de execução

1. data-access: criar `acs-nav-config.ts`
2. feature-*: adicionar `OnPush` nos 2 componentes
3. wiring: atualizar `acs.descriptor.ts` com as 2 constantes novas
4. wiring: atualizar `index.ts` com novos exports
5. verificar: `npx nx lint @neuroassist/acs` → 0 erros; `npx nx build @neuroassist/acs` → clean

#### Generators Nx obrigatórios

Nenhum.

#### Política de testes

Sem novos testes nesta wave (testes de shape/valor das constantes entram em P5).

#### Multi-empresa

`ACS_NAV_CONFIG` e `MODULE_*` são constantes TS puras. Não emitem HTTP.

#### Critérios de aceite

```bash
npx nx lint @neuroassist/acs
# 0 errors, 0 warnings

npx nx build @neuroassist/acs
# build clean

# Verificação manual:
# MODULE_PERMISSION_MAP['acs.cuidador.write'] === 'acs.cuidador.criar'
# MODULE_FEATURE_FLAGS['acs.coverageGuard'] === 'on'
# ACS_NAV_CONFIG.route === '/acs'
```

#### Riscos

- Se `ACS_MODULE_DESCRIPTOR` tem um tipo `ModuleDescriptor` definido no shell, e o shape difere, o TypeScript falhará no build. Verificar o tipo importado e ajustar o shape do descriptor conforme necessário.

#### Instrução para o implementador

Criar `acs-nav-config.ts`, expandir `acs.descriptor.ts` com as 2 constantes, adicionar `OnPush` nos 2 componentes. Confirmar `build` limpo.

---

### Prioridade P4 — Feature Components MVP: Editor + Linkage + TenantSetup

Status: pendente
Issues cobertas: FE-ACS-008 (partial — roster complete), FE-ACS-010 (editor facade + form), FE-ACS-012 (linkage full), FE-ACS-013 (tenant setup steps)
Categoria: FUNCIONAL

#### Objetivo

Tornar os 4 feature components MVP-shippable: `CaregiverEditorComponent` com form validation e draft; `PatientLinkageComponent` com data flow completo; `TenantSetupComponent` com 2 steps; `CaregiverRosterComponent` com ação de deactivate funcional.

#### Problema identificado

- `CaregiverEditorComponent`: sem `ReactiveFormsModule`, sem validators, sem draft preservation no sinal; form fields presentes mas não conectados a `FormGroup`.
- `PatientLinkageComponent`: scaffold minimal; não chama `facade.linkToPatient()` ou `revokeLink()`; sem lista de cuidadores vinculados.
- `TenantSetupComponent`: form básico presente mas sem multi-step, sem call a `facade.provision()`.
- `CaregiverRosterComponent`: ação deactivate sem confirmação e sem verificação de last-coverage inline.

#### Correções mandatórias

1. **`caregiver-editor.component.ts`**:
   - Adicionar import: `ReactiveFormsModule`, `FormBuilder`, `Validators`.
   - Criar `draftForm = fb.group({ nome: ['', [Validators.required, Validators.minLength(2)]], email: ['', [Validators.required, Validators.email]], papel: ['cuidadora', Validators.required] })`.
   - Preservar draft: no `ngOnInit` (ou `effect`), restaurar form do `facade.selected()` quando `mode = 'edit'`.
   - Botão save desabilitado quando `draftForm.invalid || facade.saving()`.
   - Mostrar mensagem de erro inline por campo (`form.get('email')?.errors?.['email']`).
   - Chamar `facade.invite(draftForm.value)` no submit (novo) ou `facade.update(id, draftForm.value)` no edit.

2. **`patient-linkage.component.ts`**:
   - Injetar `AcsCaregiverFacade` (já existe).
   - No `ngOnInit`: chamar `facade.loadRoster()` para popular lista de cuidadores disponíveis.
   - Exibir lista de cuidadores ativos com botão "Vincular" por cuidador não vinculado.
   - Exibir lista de vínculos ativos com botão "Desvincular" (chamar `facade.revokeLink()`).
   - Exibir badge de cobertura: verde se `coverageFor(pacienteId).isCovered`, vermelho se não coberto.
   - `ChangeDetectionStrategy.OnPush` (já adicionado em P3).

3. **`tenant-setup.component.ts`**:
   - Substituir form simples por 2 steps: Step 1 (tipo: família|clínica), Step 2 (nome do tenant).
   - `currentStep = signal<1|2>(1)`.
   - Botão "Próximo" vai para step 2; botão "Confirmar" chama `tenantFacade.provision({ tipo, nome })`.
   - Exibir `tenantFacade.saving()` e `tenantFacade.error()`.

4. **`caregiver-roster.component.ts`**:
   - Botão "Desativar" deve chamar `facade.deactivate(id)` apenas após confirmação (dialog `confirm()` ou inline state).
   - Mostrar `facade.error()` como banner de erro inline (não `alert()`).
   - Garantir que `activeCaregivers()` signal é usado (não caregivers brutos).

#### Arquivos-alvo

```
frontend/libs/acs/src/lib/feature-caregiver-editor/caregiver-editor.component.ts  ← modificar
frontend/libs/acs/src/lib/feature-patient-linkage/patient-linkage.component.ts    ← modificar
frontend/libs/acs/src/lib/feature-tenant-setup/tenant-setup.component.ts         ← modificar
frontend/libs/acs/src/lib/feature-caregiver-roster/caregiver-roster.component.ts ← modificar
```

#### Contrato de tipo

```typescript
// CaregiverDraft já existe em acs.types.ts:
export interface CaregiverDraft { nome: string; email: string; papel: CuidadorPapel; }

// PatientLinkageComponent inputs (route param via ActivatedRoute):
// pacienteId: string (de route /acs/pacientes/:pacienteId/vinculos)

// TenantSetupComponent usa AcsTenantFacade:
// facade.provision(dto: TenantCreateDto) — dto: { nome: string; tipo: TenantTipo }
```

#### Sequência de execução

1. feature-caregiver-editor: adicionar ReactiveFormsModule + validators + draft
2. feature-patient-linkage: wiring de linkToPatient + revokeLink + lista de cuidadores
3. feature-tenant-setup: 2 steps + provision call
4. feature-caregiver-roster: confirmação de deactivate + banner de erro
5. verificar: `npx nx build @neuroassist/acs` clean; `npx nx lint @neuroassist/acs` 0 erros

#### Generators Nx obrigatórios

Nenhum.

#### Política de testes

Unit tests dos componentes entram em P5. Nesta wave: somente build + lint green.

#### Multi-empresa: @neuroassist/shared/access-http-base necessário?

Não diretamente nos componentes (componentes injetam façades, não HTTP). O wiring de header é em P6.

#### Critérios de aceite

```bash
npx nx build @neuroassist/acs
# build clean (sem erros TypeScript)

npx nx lint @neuroassist/acs
# 0 errors, 0 warnings

# Verificação manual com mock adapter:
# GET /acs → roster carregado com cuidadores da Jamilly
# POST /acs/cuidadores/novo → form valida, save funciona, retorna ao roster
# GET /acs/pacientes/:id/vinculos → lista de cuidadores + botão vincular funcional
# Tenant setup: 2 steps navegáveis, provision call dispara
```

#### Riscos

- `AcsTenantFacade.provision()` pode não existir se foi apenas scaffolded. Verificar e implementar se ausente.
- `ActivatedRoute.paramMap` para `pacienteId` em `PatientLinkageComponent` requer importação e injeção. Confirmar que `ActivatedRoute` está no imports do componente standalone.

#### Instrução para o implementador

Implementar os 4 componentes na ordem listada. Usar `ReactiveFormsModule` standalone import em `caregiver-editor.component.ts`. Confirmar build limpo antes de encerrar P4.

---

### Prioridade P5 — Unit Tests: Façades + Guards + Adapters + Components

Status: pendente
Issues cobertas: FE-ACS-020 (unit tests data-access), FE-ACS-021 (unit tests access layer), FE-ACS-022 (unit tests components)
Categoria: VALIDACAO

#### Objetivo

Sair de 0 specs para pelo menos 60% de cobertura nas façades e guards. Fazer `npx nx test @neuroassist/acs` passar com sucesso pela primeira vez.

#### Problema identificado

Zero `.spec.ts` em `frontend/libs/acs/`. O `test-setup.ts` está configurado (Vitest + @analogjs/vitest-angular) mas nunca foi invocado. Sem testes, regressões em façades e guards não são detectadas.

#### Correções mandatórias (arquivos novos)

1. **`frontend/libs/acs/src/lib/acs-caregiver.facade.spec.ts`**:
   - Cenário: `loadRoster()` com mock adapter → `caregivers()` signal atualizado com lista.
   - Cenário: `invite()` → `caregivers()` cresce em 1.
   - Cenário: `deactivate()` → cuidador marcado `isActive: false`.
   - Cenário: `revokeLink()` com 409 → `error()` signal com mensagem ADR-005.
   - Cenário: `loadRoster()` com erro de adapter → `error()` sinal não-null; `loading()` false.

2. **`frontend/libs/acs/src/lib/acs-tenant.facade.spec.ts`**:
   - Cenário: `loadCurrentTenant()` → `tenant()` signal atualizado.
   - Cenário: `provision()` → `saving()` true durante, false após.

3. **`frontend/libs/acs/src/lib/access/acs.guard.spec.ts`**:
   - Cenário: `acsCoordinatorGuard` com `token: null` → retorna `false`.
   - Cenário: `acsCoordinatorGuard` com `token: 'jwt-abc'` → retorna `true`.
   - Cenário: `acsCaregiverWriteGuard` com `token: null` → retorna `false`.
   - Cenário: `acsLinkWriteGuard` com `token: null` → retorna `false`.

4. **`frontend/libs/acs/src/lib/api/acs-cuidador-mock.adapter.spec.ts`**:
   - `list()` → retorna array com pelo menos 1 item (Jamilly).
   - `create(dto)` → retorna CuidadorDto com id não-nulo.
   - `deactivate(id)` → retorna cuidador com `status: 'inactive'`.

5. **`frontend/libs/acs/src/lib/feature-caregiver-roster/caregiver-roster.component.spec.ts`**:
   - Renderiza estado de loading quando `facade.loading() = true`.
   - Renderiza lista de cuidadores quando `facade.caregivers()` não-vazio.
   - Renderiza estado de erro quando `facade.error()` não-null.

6. **`frontend/libs/acs/src/lib/acs.descriptor.spec.ts`**:
   - `MODULE_PERMISSION_MAP['acs.cuidador.write']` === `'acs.cuidador.criar'`.
   - `MODULE_FEATURE_FLAGS['acs.coverageGuard']` === `'on'`.
   - `ACS_MODULE_DESCRIPTOR.key` === `'acs'`.

#### Arquivos-alvo

```
frontend/libs/acs/src/lib/acs-caregiver.facade.spec.ts          ← novo
frontend/libs/acs/src/lib/acs-tenant.facade.spec.ts             ← novo
frontend/libs/acs/src/lib/access/acs.guard.spec.ts              ← novo
frontend/libs/acs/src/lib/api/acs-cuidador-mock.adapter.spec.ts ← novo
frontend/libs/acs/src/lib/feature-caregiver-roster/caregiver-roster.component.spec.ts ← novo
frontend/libs/acs/src/lib/acs.descriptor.spec.ts                ← novo
```

#### Contrato de tipo

Usar `TestBed.configureTestingModule` com `provideAcs({ adapter: 'mock' })` para wiring de DI nos testes de façade. Usar `TestBed.configureTestingModule` com `provideRouter([])` para testes de guard.

#### Sequência de execução

1. data-access: `acs-caregiver.facade.spec.ts` + `acs-tenant.facade.spec.ts` + `acs-cuidador-mock.adapter.spec.ts`
2. access: `acs.guard.spec.ts`
3. feature-*: `caregiver-roster.component.spec.ts`
4. wiring: `acs.descriptor.spec.ts`
5. verificar: `npx nx test @neuroassist/acs` → PASS; cobertura ≥ 60% em façades e guards

#### Generators Nx obrigatórios

Nenhum. Os spec files são criados diretamente.

#### Política de testes

`npx nx test @neuroassist/acs` → todos os specs PASS sem `test.skip()`. Cobertura mínima 60% em `acs-caregiver.facade.ts` e `acs.guard.ts`.

#### Multi-empresa

Não aplicável em testes unitários (mock adapter, sem HTTP real).

#### Critérios de aceite

```bash
npx nx test @neuroassist/acs
# saída esperada: X specs, 0 failed, 0 skipped
# cobertura: acs-caregiver.facade.ts ≥ 60%; acs.guard.ts ≥ 80%

npx nx lint @neuroassist/acs
# 0 errors, 0 warnings (os spec files também devem passar no lint)
```

#### Riscos

- `@analogjs/vitest-angular` pode requerer configuração adicional para `TestBed`. Verificar `test-setup.ts` antes de iniciar; se a config estiver incompleta, completar primeiro.
- Signals do Angular (`signal()`) em contexto de teste requerem `TestBed.runInInjectionContext()` para inicialização.

#### Instrução para o implementador

Criar os 6 arquivos de spec na ordem listada. Usar `provideAcs({ adapter: 'mock' })` para injetar os adapters reais de mock. Confirmar `nx test @neuroassist/acs` verde antes de encerrar P5.

---

### Prioridade P6 — HTTP Adapters + Multi-empresa Header Wiring

Status: pendente
Issues cobertas: INT-FE-ACS-001 (interceptors), INT-FE-ACS-002 (backend URL wiring), INT-FE-ACS-003 (multi-empresa header)
Categoria: WIRING

#### Objetivo

Conectar os 3 HTTP adapters com os endpoints reais do `acs-service`; marcar os pontos de injeção do header `X-Empresa-Id` com TODOs operacionais para quando o interceptor shared for promovido de stub.

#### Problema identificado

`acs-cuidador-http.adapter.ts`, `acs-tenant-http.adapter.ts` e `acs-link-http.adapter.ts` injetam `HttpClient` e têm endpoints hardcoded mas não usam `ACCESS_HTTP_BASE` do shared lib. Nenhum header `X-Empresa-Id` é injetado. Os adapters compilam mas nunca foram testados contra o backend real.

#### Correções mandatórias

1. **`frontend/libs/acs/src/lib/api/acs-cuidador-http.adapter.ts`**:
   - Importar `ACCESS_HTTP_BASE` de `@neuroassist/shared/access-http-base`.
   - Alterar `private readonly base = '/api/v1/acs/caregivers'` para `private readonly base = \`${ACCESS_HTTP_BASE}/v1/acs/caregivers\``.
   - Adicionar comentário `// TODO[INT-FE-ACS-001]: X-Empresa-Id header via shared interceptor quando promovido de Wave-0 stub`.
   - Confirmar que `deactivate(id)` usa `POST ${this.base}/${id}/deactivation` (não PATCH/DELETE).

2. **`frontend/libs/acs/src/lib/api/acs-tenant-http.adapter.ts`**:
   - Mesmo padrão de `ACCESS_HTTP_BASE`.
   - Adicionar `// TODO[INT-FE-ACS-001]: POST /tenants NÃO deve carregar X-Empresa-Id (sem tenant ativo ainda — usar HttpContextToken)`.
   - Confirmar endpoints: `POST /v1/acs/tenants`, `GET /v1/acs/tenants/current`, `POST /v1/acs/tenants/:id/retry-provisioning`.

3. **`frontend/libs/acs/src/lib/api/acs-link-http.adapter.ts`**:
   - Mesmo padrão de `ACCESS_HTTP_BASE`.
   - Adicionar `// TODO[INT-FE-ACS-001]: X-Empresa-Id via shared interceptor`.
   - Confirmar endpoints: `GET /v1/acs/caregivers/:cuidadorId/links`, `POST /v1/acs/links`, `DELETE /v1/acs/links/:linkId`.

#### Arquivos-alvo

```
frontend/libs/acs/src/lib/api/acs-cuidador-http.adapter.ts ← modificar
frontend/libs/acs/src/lib/api/acs-tenant-http.adapter.ts   ← modificar
frontend/libs/acs/src/lib/api/acs-link-http.adapter.ts     ← modificar
```

#### Contrato de tipo

Nenhuma mudança de tipo de retorno. Apenas mudança de URL base.

#### Sequência de execução

1. data-access: atualizar `acs-cuidador-http.adapter.ts`
2. data-access: atualizar `acs-tenant-http.adapter.ts`
3. data-access: atualizar `acs-link-http.adapter.ts`
4. verificar: `npx nx build @neuroassist/acs` clean; `npx nx lint @neuroassist/acs` 0 erros

#### Generators Nx obrigatórios

Nenhum.

#### Política de testes

Testes de HTTP adapters (com `HttpClientTestingModule`) são opcionais nesta wave — priorizados em P9 se houver time. O critério mínimo é build limpo.

#### Multi-empresa: @neuroassist/shared/access-http-base necessário?

**SIM.** Importar `ACCESS_HTTP_BASE` dos 3 adapters. O interceptor real (`X-Empresa-Id`) está bloqueado em `@neuroassist/shared/access-http-base` Wave-0 stub — adicionar `TODO[INT-FE-ACS-001]` e não tentar implementar o interceptor nesta wave (aguardar promoção do stub).

#### Critérios de aceite

```bash
npx nx build @neuroassist/acs
# build clean

npx nx lint @neuroassist/acs
# 0 errors, 0 warnings

# Verificação manual: grep nos 3 adapters
grep -n "ACCESS_HTTP_BASE" frontend/libs/acs/src/lib/api/acs-*-http.adapter.ts
# deve mostrar import em todos os 3 arquivos

grep -n "TODO\[INT-FE-ACS-001\]" frontend/libs/acs/src/lib/api/acs-*-http.adapter.ts
# deve mostrar comentário em todos os 3 arquivos
```

#### Riscos

- `ACCESS_HTTP_BASE = '/api'` — o path resultante será `/api/v1/acs/caregivers`. Confirmar que o proxy Angular dev server aponta `/api` para o gateway correto em `proxy.conf.json` (fora do escopo desta wave, mas documentar).

#### Instrução para o implementador

Apenas atualizar os 3 adapters com `ACCESS_HTTP_BASE` e TODOs. Não implementar interceptores. Confirmar build limpo.

---

### Prioridade P7 — E2E: 25+ Playwright Scenarios

Status: pendente
Issues cobertas: FE-ACS-027 (partial — coverage scenarios), FE-ACS-028 (linkage e2e), FE-ACS-029 (editor e2e)
Categoria: VALIDACAO

#### Objetivo

Unskipar e implementar pelo menos 25 cenários Playwright que passem. Gate go-live exige 40+. Esta wave entrega os 25 obrigatórios do roster; linkage e onboarding contribuem os restantes.

#### Problema identificado

`frontend/apps/acs-e2e/src/caregiver-roster.spec.ts`: 25 cenários todos `test.skip()`. `patient-linkage.spec.ts` e `tenant-onboarding.spec.ts`: stubs sem implementação.

#### Correções mandatórias

Para cada cenário em `caregiver-roster.spec.ts`, remover `test.skip` e implementar:
- Navegação para `/acs` com sessão válida → roster visível.
- Loading state: interceptar rede → `page.getByTestId('roster-loading')` visível.
- Error state: simular falha de rede → `page.getByTestId('roster-error')` visível com botão retry.
- Coverage badge: paciente sem caregiver ativo → badge `sem-cobertura` visível.
- Invite flow: clicar "Convidar cuidador" → form visível → preencher → submit → novo cuidador no roster.
- Deactivate flow: clicar "Desativar" → confirmação → cuidador marcado inactive.
- (Demais cenários: implementar com seletores por `data-testid` nos componentes atualizados em P4.)

Para `patient-linkage.spec.ts` e `tenant-onboarding.spec.ts`: implementar pelo menos 3 cenários básicos cada (navegação, render, ação principal).

**Pré-requisito:** Adicionar `data-testid` attributes nos templates dos componentes em P4 (ou nesta wave se não foram adicionados).

#### Arquivos-alvo

```
frontend/apps/acs-e2e/src/caregiver-roster.spec.ts     ← modificar (unskip + implementar)
frontend/apps/acs-e2e/src/patient-linkage.spec.ts      ← modificar (implementar ao menos 3)
frontend/apps/acs-e2e/src/tenant-onboarding.spec.ts    ← modificar (implementar ao menos 3)
```

Possivelmente também:
```
frontend/libs/acs/src/lib/feature-caregiver-roster/caregiver-roster.component.ts ← adicionar data-testid
frontend/libs/acs/src/lib/feature-patient-linkage/patient-linkage.component.ts   ← adicionar data-testid
frontend/libs/acs/src/lib/feature-tenant-setup/tenant-setup.component.ts         ← adicionar data-testid
```

#### Contrato de tipo

Playwright `Page` API. Nenhuma mudança de tipo Angular.

#### Sequência de execução

1. feature-*: adicionar `data-testid` attributes nos 3 componentes (se não feito em P4)
2. e2e: implementar `caregiver-roster.spec.ts` (25 cenários)
3. e2e: implementar 3+ cenários em `patient-linkage.spec.ts`
4. e2e: implementar 3+ cenários em `tenant-onboarding.spec.ts`
5. verificar: `npx nx e2e acs-e2e` → ≥ 25 cenários passando, 0 `test.skip()`

#### Generators Nx obrigatórios

Nenhum.

#### Política de testes

E2E com Playwright contra o dev server (`npx nx serve neuroassist-ai-web` com adapter `mock`). Baseurl: `http://localhost:4200`.

#### Multi-empresa

E2E usa mock adapter — sem HTTP real. Não aplicável.

#### Critérios de aceite

```bash
npx nx e2e acs-e2e
# saída esperada: ≥ 25 passed, 0 failed, 0 skipped
# (de preferência: 31 passed para cobrir o alvo desta wave)
```

#### Riscos

- O dev server precisa estar rodando com `provideAcs({ adapter: 'mock' })`. Confirmar `playwright.config.ts` tem `webServer` configurado para iniciar o shell.
- Seletores por `data-testid` devem estar nos templates ANTES dos testes. Coordenar com P4.

#### Instrução para o implementador

Não criar novos spec files. Editar os 3 existentes. Cada cenário que antes era `test.skip(...)` deve virar `test(...)` com seletores e assertions reais. Confirmar 0 `test.skip()` no output de `nx e2e acs-e2e`.

---

### Prioridade P8 — UX Polish: Coverage Blocking Modal + Error Boundary + a11y

Status: pendente
Issues cobertas: FE-ACS-014 (form error handling), FE-ACS-015 (draft preservation), FE-ACS-016 (error boundary), FE-ACS-027 (coverage invariant blocking modal — safety-critical)
Categoria: FUNCIONAL / VALIDACAO

#### Objetivo

Implementar o modal de bloqueio de cobertura (safety-critical ADR-005), error recovery com retry UI, e adicionar ARIA labels nas CTAs dos 4 componentes.

#### Problema identificado

Nenhum modal de bloqueio de cobertura existe (FE-ACS-027 é safety-critical: último cuidador deactivate/unlink deve disparar modal não-dismissível). Sem error boundary: quando façades sinalizam erro, componentes não oferecem botão de retry. ARIA labels ausentes em CTAs.

#### Correções mandatórias

1. **Criar `frontend/libs/acs/src/lib/ui/coverage-block-modal.component.ts`**:
   - Standalone, OnPush.
   - Input: `@Input() type: 'deactivate' | 'cross-tenant'`.
   - Input: `@Input() pacienteNome: string`.
   - Mensagem de deactivate: `"Este paciente ficaria sem cuidador responsável — vincule ou ative outro antes."`.
   - Mensagem cross-tenant: `"Cuidador e paciente pertencem a empresas diferentes — o vínculo não é permitido."`.
   - CTA "Entendido" com `(click)` que emite `@Output() confirmed = new EventEmitter<void>()`.
   - `role="alertdialog"`, `aria-modal="true"`, `aria-labelledby`.

2. **`frontend/libs/acs/src/lib/acs-caregiver.facade.ts`**:
   - Adicionar signal: `_showCoverageModal = signal<{ type: 'deactivate' | 'cross-tenant'; pacienteNome: string } | null>(null)`.
   - `readonly coverageModal = this._showCoverageModal.asReadonly()`.
   - Em `revokeLink()`: quando 409 → `_showCoverageModal.set({ type: 'deactivate', pacienteNome: ... })` E `_error.set(...)`.
   - Em `deactivate()`: quando last-covering block → `_showCoverageModal.set(...)`.
   - Método `dismissCoverageModal()` → `_showCoverageModal.set(null)`.

3. **`caregiver-roster.component.ts`**:
   - Condicionar exibição do `CoverageBlockModalComponent` quando `facade.coverageModal() !== null`.
   - Erro com botão retry: `<button (click)="facade.loadRoster()">Tentar novamente</button>` quando `facade.error()` não-null.

4. **a11y — todos os 4 componentes**:
   - Adicionar `[attr.aria-label]` em todos os botões com apenas ícone.
   - Adicionar `role="status"` em loading states.
   - Garantir que o form de editor tem `<label>` para cada input.

#### Arquivos-alvo

```
frontend/libs/acs/src/lib/ui/coverage-block-modal.component.ts            ← novo
frontend/libs/acs/src/lib/acs-caregiver.facade.ts                         ← modificar
frontend/libs/acs/src/lib/feature-caregiver-roster/caregiver-roster.component.ts ← modificar
frontend/libs/acs/src/lib/feature-caregiver-editor/caregiver-editor.component.ts ← modificar (a11y)
frontend/libs/acs/src/lib/feature-patient-linkage/patient-linkage.component.ts    ← modificar (a11y)
frontend/libs/acs/src/lib/feature-tenant-setup/tenant-setup.component.ts         ← modificar (a11y)
```

#### Contrato de tipo

```typescript
// Signal novo em AcsCaregiverFacade
readonly coverageModal: Signal<{ type: 'deactivate' | 'cross-tenant'; pacienteNome: string } | null>;
dismissCoverageModal(): void;
```

#### Sequência de execução

1. ui: criar `coverage-block-modal.component.ts`
2. data-access: adicionar `coverageModal` signal + `dismissCoverageModal` em `acs-caregiver.facade.ts`
3. feature-caregiver-roster: wiring do modal + retry button
4. feature-*: ARIA labels nos 4 componentes
5. verificar: `npx nx build @neuroassist/acs` clean; `npx nx lint @neuroassist/acs` 0 erros

#### Generators Nx obrigatórios

Nenhum.

#### Política de testes

`npx nx test @neuroassist/acs` deve continuar passando após P8. Adicionar 1 teste em `acs-caregiver.facade.spec.ts`: `revokeLink() com 409 → coverageModal() não-null`.

#### Multi-empresa

`CoverageBlockModalComponent` é UI pura. Não emite HTTP.

#### Critérios de aceite

```bash
npx nx build @neuroassist/acs
# clean

npx nx test @neuroassist/acs
# PASS (incluindo o novo teste de modal)

npx nx lint @neuroassist/acs
# 0 errors, 0 warnings

# Verificação manual:
# revokeLink() com último vínculo ativo → CoverageBlockModalComponent visível
# Botão "Entendido" fecha o modal (dismissCoverageModal())
# form com label em cada input do editor
```

#### Riscos

- O `CoverageBlockModalComponent` deve ser um overlay, não um dialog do sistema (`alert()`). Usar CSS `position: fixed; inset: 0` com backdrop Tailwind.

#### Instrução para o implementador

Criar o modal component como standalone. Wiring exclusivo pelo signal `coverageModal`. Não usar `alert()` ou `confirm()` do browser. Confirmar ARIA attributes presentes.

---

### Prioridade P9 — Go-live Gate: Observability + EVNT Contract + Final E2E Audit (40+)

Status: pendente
Issues cobertas: INT-FE-ACS-004 (observability hooks), INT-FE-ACS-005 (EVNT coverage contract), FE-ACS-030, FE-ACS-031 (remaining e2e + final audit)
Categoria: VALIDACAO

#### Objetivo

Atingir todos os critérios do go-live gate: 40+ e2e passando, 0 violações de lint, observability hooks nas operações críticas, contrato de cobertura com EVNT frozen.

#### Problema identificado

Nenhuma telemetria ou logging nas operações críticas (invite, deactivate, revokeLink). Contrato de cobertura com EVNT não documentado. E2e count em P7 atinge ~31 — faltam ~9 para o gate de 40.

#### Correções mandatórias

1. **Observability em `acs-caregiver.facade.ts`**:
   - Importar `@neuroassist/shared/observability` (se disponível) ou usar `console.info` com estrutura `{module: 'ACS', op: string, status: string}` para rastreabilidade MVP.
   - Log em: `invite()` (start/success/error), `deactivate()` (start/block/success/error), `revokeLink()` (start/409-block/success/error).
   - Log em `loadRoster()` somente em erro (não logar cada fetch bem-sucedido).

2. **Documento de contrato EVNT**:
   - Criar `docs/architecture/shared/contratos/acs-evnt-coverage-contract.md` com:
     - Definição de `CoverageEligibilitySignal`: quando ACS considera um paciente "elegível para alarme EVNT" (ao menos 1 cuidador ativo vinculado).
     - Tipo exportado pelo `@neuroassist/acs` barrel para consumo pelo EVNT.
     - Nota de breaking change: alterações neste contrato requerem coordenação com equipe EVNT.
   - Exportar `CoverageVm` de `acs.types.ts` como contrato público (já exportado via `index.ts` — confirmar).

3. **E2E adicionais (10+ cenários para atingir 40+)**:
   - `caregiver-roster.spec.ts`: 5 cenários adicionais (pagination se aplicável, filter por papel, deactivate last-covering → modal).
   - `patient-linkage.spec.ts`: 5 cenários adicionais (cross-tenant rejection → modal, coverage re-derived após unlink).

4. **Audit final**:
   - `npx nx lint @neuroassist/acs` → 0 violations.
   - `npx nx test @neuroassist/acs` → todos passando.
   - `npx nx build @neuroassist/acs` → clean.
   - `npx nx e2e acs-e2e` → 40+ passando.

#### Arquivos-alvo

```
frontend/libs/acs/src/lib/acs-caregiver.facade.ts                     ← modificar (observability)
docs/architecture/shared/contratos/acs-evnt-coverage-contract.md      ← novo
frontend/apps/acs-e2e/src/caregiver-roster.spec.ts                    ← modificar (+ 5 cenários)
frontend/apps/acs-e2e/src/patient-linkage.spec.ts                     ← modificar (+ 5 cenários)
```

#### Contrato de tipo

```typescript
// Exportação pública via index.ts (confirmar que já está presente):
export type { CoverageVm } from './lib/acs.types';
// CoverageVm = { pacienteId: string; coveredBy: readonly string[]; isCovered: boolean }
```

#### Sequência de execução

1. data-access: observability em `acs-caregiver.facade.ts`
2. docs: criar `acs-evnt-coverage-contract.md`
3. e2e: adicionar 10+ cenários para atingir 40+
4. verificar: audit final completo (lint + test + build + e2e)

#### Generators Nx obrigatórios

Nenhum.

#### Política de testes

Gate de go-live: `npx nx e2e acs-e2e` com 40+ passando e 0 `test.skip()`.

#### Multi-empresa: @neuroassist/shared/access-http-base necessário?

N/A — P9 é observability, docs, e e2e. HTTP wiring foi em P6.

#### Critérios de aceite (Go-live Gate)

```bash
# Gate 1: Lint
npx nx lint @neuroassist/acs
# 0 errors, 0 warnings

# Gate 2: Unit Tests
npx nx test @neuroassist/acs
# PASS, ≥ 60% cobertura em façades e guards

# Gate 3: Build
npx nx build @neuroassist/acs
# clean (sem erros TypeScript)

# Gate 4: E2E (go-live threshold)
npx nx e2e acs-e2e
# ≥ 40 passed, 0 failed, 0 skipped

# Gate 5: Contrato
ls docs/architecture/shared/contratos/acs-evnt-coverage-contract.md
# arquivo existe
```

#### Riscos

- N+1 requests em `_refreshCoverageMap()` (chama `listForCaregiver` para cada cuidador ativo) → documentar no contrato EVNT como limitação conhecida; propor cache ou endpoint batch em futuro sprint.
- RBAC real (JWT claims do BE) está marcado como MVP-simplificado (`token !== null = pode tudo`). Documentar issue de evolução em `docs/architecture/frontend/issues/FE-ACS-031.md` ou novo issue FE-ACS-032.

#### Instrução para o implementador

Focar no audit final. Confirmar que todos os 4 gates passam. O documento de contrato EVNT é entregável de documentação, não de código. Registrar qualquer issue de evolução (N+1, RBAC real) como issues abertas para sprint futuro.

---

## 4. Dependências externas

| Dependência | Status | Impacto | Issues |
|---|---|---|---|
| `acs-service` (BE) | NÃO IMPLANTADO | HTTP adapters (P6) funcionam; integração real bloqueada | INT-FE-ACS-002 |
| `@neuroassist/shared/access-http-base` interceptor | STUB Wave-0 | `X-Empresa-Id` não injetado até promoção | INT-FE-ACS-001 |
| `@neuroassist/shared/auth-session` real | STUB Wave-0 | Guards usam `token !== null` como MVP | FE-ACS-007 |
| `@neuroassist/shared/empresa-context` | NÃO VERIFICADO | Necessário para `X-Empresa-Id` real | INT-FE-ACS-003 |
| EVNT coverage contract (cross-module) | AUSENTE | Definido em P9 | INT-FE-ACS-005 |
| PAC `PacientesReadClient` (read-only) | NÃO VERIFICADO | Coverage derivation pode precisar de nomes de pacientes | FE-ACS-012 |

---

## 5. Restrições arquiteturais (Nx boundaries)

| Regra | Constraint | Status |
|---|---|---|
| RULE-NX-01 | `scope:acs` não importa `scope:pac`, `scope:gest`, `scope:evnt`, `scope:hist`, `scope:dash`, `scope:edge` | A verificar após P1 |
| RULE-NX-02 | `type:ui` não importa `type:feature` ou `type:data-access` | N/A (lib monolítica; regra aplica quando sub-libs forem criadas) |
| RULE-NX-03 | `type:feature` não importa outras `type:feature` dentro de acs | N/A (monolítica) |
| RULE-NX-04 | Componentes não injetam `HttpClient` diretamente | OK (via façades → ports → adapters) |
| RULE-NX-05 | Componentes não injetam ports diretamente | OK (via façades) |
| RULE-NX-06 | Signals expostos como `asReadonly()` | OK (todas as façades) |
| RULE-NX-07 | `scope:acs` pode importar `scope:shared` | OK |

---

## 6. Referências

| Documento | Relevância |
|---|---|
| `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-01-route-map.md` | Routes, guards, breadcrumbs |
| `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-04-data-access.md` | Port/adapter/facade contracts |
| `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-05-tool-contract.md` | MODULE_KEY, PERMISSION_MAP, FEATURE_FLAGS |
| `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-06-shell-integration.md` | Descriptor, nav, shell registration |
| `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-07-error-handling.md` | Blocking modal, coverage invariant |
| `docs/architecture/frontend/modules/acs/frontend/adr/ADR-005-no-patient-without-coverage.md` | Coverage invariant rule |
| `docs/architecture/frontend/standards/nx-boundaries.md` | Tags, allowed directions |
| `docs/architecture/frontend/modules/acs/frontend/module-state.md` | Estado atual (BASELINE 2026-08-15) |
| `docs/architecture/frontend/modules/acs/frontend/issues/` | 31 FE-ACS-* + 5 INT-FE-ACS-* issues |
