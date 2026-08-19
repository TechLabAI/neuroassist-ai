# @neuroassist/hist

> Modulo de Historico e Auditoria do NeuroAssist AI.
> Exibe a linha do tempo de eventos detectados com marcacao de veredito nao-otimista.

---

## Estado do modulo

| Dimensao | Status | Detalhe |
|---|---|---|
| **Score de maturidade** | 85/100 (Maduro) | Atualizado pos-P2 (2026-08-19) |
| **Lint** | PASS | 0 errors, 0 warnings (P1 DONE) |
| **Unit tests** | PASS | 32/32 testes PASS (P2 DONE) |
| **Build** | PASS | neuroassist-ai-web compila com HIST como lazy chunk |
| **E2E** | SKIP | 13/13 cenarios definidos e skipped (P4 pendente) |
| **HTTP integration** | BLOQUEADO | Aguardando hist-service + evnt-service (P5 deferred) |

---

## Responsabilidades

- Exibir linha do tempo imutavel de eventos detectados (AuditRecord)
- Filtro por periodo: hoje / semana / mes
- Paginacao incremental ("Carregar mais")
- Marcacao de veredito nao-otimista: real / falso_positivo
- Reconciliacao de conflito 409 (atualizacao concorrente)
- Verificacao de integridade da cadeia de auditoria (ADR-006, permissao `hist:read:all`)

---

## Estrutura

```
frontend/libs/hist/
├── src/
│   ├── index.ts                               # Public API (provideHist, HistoryFacade, tipos)
│   └── lib/
│       ├── feature-timeline/
│       │   └── history-timeline.component.ts  # Standalone, OnPush — entrada da rota /historico
│       ├── api/
│       │   ├── hist-client.port.ts            # HistClientPort: list(), get(), verify()
│       │   ├── hist-client-mock.adapter.ts    # Mock funcional (dev/test)
│       │   ├── hist-client-http.adapter.ts    # Stub — ativar em INT-FE-HIST-001
│       │   ├── hist-feedback.port.ts          # HistFeedbackPort: markFeedback()
│       │   ├── hist-feedback-mock.adapter.ts  # Mock funcional (dev/test)
│       │   └── hist-feedback-http.adapter.ts  # Stub — ativar em INT-FE-HIST-002
│       ├── access/
│       │   ├── hist-access-tokens.ts          # Tokens DI: HIST_AUTH_SESSION, HIST_ACCESS_DECISION
│       │   └── hist.guard.ts                  # authGuard, histGuard (Wave-0 bypass ativo)
│       ├── hist.facade.ts                     # HistoryFacade — signals, todos os metodos
│       ├── hist.types.ts                      # Tipos de dominio + DTOs da API
│       ├── hist.descriptor.ts                 # Descriptor, permissions, feature flags
│       ├── hist.providers.ts                  # provideHist() com toggle de adapter
│       ├── hist.routes.ts                     # HIST_ROUTES: path '', loadComponent
│       └── hist-nav-config.ts                 # Configuracao de menu do shell
├── project.json                               # Nx: tags scope:hist, type:feature
└── .eslintrc.json                             # Component selector prefix: lib-*
```

---

## Como usar

### Registrar no shell (app.routes.ts)

```typescript
import { HIST_ROUTES } from '@neuroassist/hist';

export const appRoutes: Routes = [
  {
    path: 'historico',
    loadChildren: () => import('@neuroassist/hist').then(m => m.HIST_ROUTES),
  },
];
```

### Prover o modulo (app.config.ts)

```typescript
import { provideHist } from '@neuroassist/hist';

export const appConfig: ApplicationConfig = {
  providers: [
    provideHist({ adapter: 'mock' }),  // 'http' quando hist-service deployado (INT-FE-HIST-001)
  ],
};
```

---

## Comandos

```bash
# Lint
cd frontend && npx nx lint hist
# Saida esperada: 0 errors, 0 warnings

# Unit tests
cd frontend && npx nx test hist
# Saida esperada: 32/32 PASS (Test Suites: 4, Tests: 32)

# Build (verifica que o modulo compila com o shell)
cd frontend && npx nx build neuroassist-ai-web
# Saida esperada: exit 0, hist como lazy chunk

# E2E (pendente P4)
cd frontend && npx nx e2e hist-e2e
# Status atual: 13/13 cenarios .skip (assertions a implementar em P4)
```

---

## Dependencias de backend (status)

| Servico | Endpoint | Status | Issue |
|---|---|---|---|
| hist-service | `GET /api/v1/hist/history` | BLOQUEADO | INT-FE-HIST-001 |
| hist-service | `GET /api/v1/hist/history/:id` | BLOQUEADO | INT-FE-HIST-001 |
| hist-service | `GET /api/v1/hist/history/verify` | BLOQUEADO | INT-FE-HIST-001 |
| evnt-service | `POST /api/v1/evnt/events/:id/feedback` | BLOQUEADO | INT-FE-HIST-002 |

**Multi-empresa:** `X-Empresa-Id` via `@neuroassist/shared/access-http-base` — padrao documentado, wiring deferred ate INT-FE-HIST-001.

---

## Issues ativas

| Issue | Titulo | Status |
|---|---|---|
| [HIST-FE-001](../../docs/architecture/frontend/issues/HIST-FE-001.md) | Lint: selector + imports | DONE |
| [HIST-FE-002](../../docs/architecture/frontend/issues/HIST-FE-002.md) | Unit tests scaffold | DONE |
| [HIST-FE-003](../../docs/architecture/frontend/issues/HIST-FE-003.md) | E2E: 13 cenarios Playwright | PENDENTE (P4) |
| [HIST-FE-004](../../docs/architecture/frontend/issues/HIST-FE-004.md) | HTTP adapter hist-service | BLOQUEADO (P5) |
| [HIST-FE-005](../../docs/architecture/frontend/issues/HIST-FE-005.md) | Feedback adapter evnt-service | BLOQUEADO (P5) |
| [HIST-FE-006](../../docs/architecture/frontend/issues/HIST-FE-006.md) | Module docs formais | DONE |
| [INT-FE-HIST-001](../../docs/architecture/frontend/issues/INT-FE-HIST-001.md) | Integracao hist-service | BLOQUEADO |
| [INT-FE-HIST-002](../../docs/architecture/frontend/issues/INT-FE-HIST-002.md) | Integracao evnt-service feedback | BLOQUEADO |

---

## Notas arquiteturais

- **Adapter toggle:** `provideHist({ adapter: 'mock' | 'http' })` controla qual adapter e injetado. Dev/test usam `mock`; producao usara `http` apos INT-FE-HIST-001.
- **Non-optimistic feedback:** `markFeedback()` na facade marca o veredito apenas apos confirmacao do backend. Conflito 409 reconcilia o item com o estado retornado pelo servidor.
- **Wave-0 bypass:** `histGuard` e `authGuard` estao em modo bypass (Wave-0). Verificacao real de `AccessDecisionService.can('hist:read:all')` sera ativada junto com o backend.
- **Chain verification:** `HistClientPort.verify()` esta implementado no port e nos adapters, mas ainda nao exposto na UI do `HistoryTimelineComponent`. Sera exposto em iteracao futura para coordenadores com `hist:read:all`.
- **Nx tags:** `scope:hist`, `type:feature`. Apenas pode importar de `scope:hist` ou `scope:shared`.
