# Pole Position split — FE-E2E (fe)

> Gerado por `pole-position-split.py` a partir de `docs\architecture\frontend\plans\plan-FE-E2E.md`.
> Terminais: 2 (pedidos: 2). Ownership de arquivo disjunto.
> O planner enriquece a narrativa; o split (arquivos por terminal) é determinístico.

## ⚠️ Avisos

- 4 file(s) are targeted by >1 unit; those units were coalesced into the same stream to keep ownership disjoint.
- some units declare no file targets — their split is by unit only (ownership not verifiable). Add 'Alvos de alteração' paths to the plan.

## Setup de worktrees (humano, uma vez)

```bash
git worktree add ../fe-e2e-t1 -b stream/fe-e2e-t1
git worktree add ../fe-e2e-t2 -b stream/fe-e2e-t2
```

## Streams

| Terminal | Branch | Unidades | Arquivos (owned) |
|---|---|---|---|
| t1 | `stream/fe-e2e-t1` | P1, P2, P6, P7 | 45 |
| t2 | `stream/fe-e2e-t2` | P3, P4, P5 | 6 |

### Terminal t1 — `stream/fe-e2e-t1`

**Unidades:** P1, P2, P6, P7

**Arquivos sob sua responsabilidade (não tocar fora desta lista):**

- `-e2e/src/backend-error-handling.spec.ts`
- `.github/workflows/e2e-integrated.yml`
- `INT-FE-HIST-001..006`
- `app.config.ts`
- `application.yml`
- `apps/evnt-e2e/playwright.config.ts`
- `backend/services/neuroassist-ai-api-gateway/src/main/resources/application.yml`
- `dash-notifications-http.adapter.ts`
- `docker/docker-compose.dev.yml`
- `docs/architecture/backend/issues/E2E-001..013.md`
- `docs/architecture/backend/plans/plan-e2e-integration.md`
- `docs/architecture/frontend/issues/FE-E2E-001..017.md`
- `docs/architecture/frontend/issues/INT-FE-HIST-001..006.md`
- `docs/architecture/frontend/standards/access-layer-contracts.md`
- `docs/architecture/frontend/standards/nx-boundaries.md`
- `docs/architecture/shared/blueprint-core.md`
- `evnt-client-http.adapter.ts`
- `evnt.providers.ts`
- `frontend/apps/acs-e2e/src/tenant-onboarding.spec.ts`
- `frontend/apps/dash-e2e/src/notification-delivery.spec.ts`
- `frontend/apps/neuroassist-ai-web/README.md`
- `frontend/apps/neuroassist-ai-web/project.json`
- `frontend/apps/neuroassist-ai-web/proxy.conf.json`
- `frontend/apps/neuroassist-ai-web/src/app/app.config.ts`
- `frontend/apps/neuroassist-ai-web/src/app/config/adapter-mode.ts`
- `frontend/apps/neuroassist-ai-web/src/app/shell/app-shell.component.ts`
- `frontend/libs/dash/src/lib/api/dash-notifications-http.adapter.ts`
- `frontend/libs/evnt/src/lib/api/evnt-client-http.adapter.ts`
- `frontend/libs/gest/src/lib/api/eligibility-http.adapter.spec.ts`
- `frontend/libs/shared/access-http-base/src/index.ts`
- `frontend/libs/shared/access-http-base/src/lib/access-http-base.ts`
- `frontend/libs/shared/access-http-base/src/lib/access-http.interceptor.ts`
- `frontend/libs/shared/auth-session/src/index.ts`
- `frontend/libs/shared/auth-session/src/lib/auth-session.service.ts`
- `frontend/libs/shared/auth-session/src/lib/auth-session.ts`
- `frontend/nginx.conf`
- `frontend/playwright-report/index.html`
- `gest-e2e/camera-lifecycle-teardown.spec.ts`
- `libs/shared/access-http-base/src/lib/access-http-base.ts`
- `libs/shared/auth-session/src/lib/auth-session.ts`
- `pac-comando-editor-page.component.ts`
- `page.route`
- `plan-fe-render-shell-evnt.md`
- `postcss.config.js`
- `proxy.conf.json`

**Prompt de kick-off:**

```
Você implementa o terminal t1 do split pole-position de FE-E2E.
Branch: stream/fe-e2e-t1. Nunca trocar de branch.
Plano-fonte: docs\architecture\frontend\plans\plan-FE-E2E.md
Suas unidades: P1, P2, P6, P7.
Toque SOMENTE os arquivos listados acima. Se precisar de um arquivo
de outro terminal, pare e sinalize — não edite fora do seu ownership.
Siga o plano para suas unidades; rode os testes locais antes de marcar pronto.
```

### Terminal t2 — `stream/fe-e2e-t2`

**Unidades:** P3, P4, P5

**Arquivos sob sua responsabilidade (não tocar fora desta lista):**

- `-e2e/playwright.config.ts`
- `docker/seed-e2e.sql`
- `frontend/e2e-support/global-setup.ts`
- `frontend/e2e-support/seed.ts`
- `playwright.config.ts`
- `test.skip`

**Prompt de kick-off:**

```
Você implementa o terminal t2 do split pole-position de FE-E2E.
Branch: stream/fe-e2e-t2. Nunca trocar de branch.
Plano-fonte: docs\architecture\frontend\plans\plan-FE-E2E.md
Suas unidades: P3, P4, P5.
Toque SOMENTE os arquivos listados acima. Se precisar de um arquivo
de outro terminal, pare e sinalize — não edite fora do seu ownership.
Siga o plano para suas unidades; rode os testes locais antes de marcar pronto.
```

## Ordem de merge

Merge em `dev` na ordem dos terminais (t1, t2, ...). Cada um é file-disjunto, então a ordem é livre; conflitos de merge não devem ocorrer por design. Resolva dependências de unidade (ex.: P2 depende de P1) sincronizando via `dev` antes do gate do terminal dependente.

