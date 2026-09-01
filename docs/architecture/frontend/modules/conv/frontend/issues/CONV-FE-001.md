# CONV-FE-001 -- conv.contract.ts + conv.permission-map.ts + conv.feature-flags.ts + CONV_MODULE_DESCRIPTOR (shell-entry barrel, patient-only)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/8 itens

## Outcome
`libs/conv/shell-entry` exporta `CONV_TOOL_KEY`, `CONV_MENU_METADATA` (patient-only, `registerInAdminShell: false`), `CONV_PERMISSION_MAP`, `CONV_FEATURE_FLAGS`, `CONV_ROUTES` e `CONV_MODULE_DESCRIPTOR` — o conjunto completo de constantes que o patient shell usa para montar o módulo CONV sem vazar qualquer superfície de cuidador.

## Approach (execution path)
1. Criar `conv.contract.ts` com `CONV_TOOL_KEY = 'conv'` e `CONV_MENU_METADATA` (`surface: 'patient-only'`, `registerInAdminShell: false`, `route: '/paciente/arvore'`, `icon: 'record_voice_over'`).
2. Criar `conv.permission-map.ts` com `CONV_PERMISSION_MAP { navegar: 'conv.arvore.navegar', selecionar: 'conv.no.selecionar' }` — nenhuma authority de admin declarada aqui.
3. Criar `conv.feature-flags.ts` com `CONV_FEATURE_FLAGS { semanticAssistEnabled, gestureChannelEnabled, ttsCacheEnabled, defaultLocale: 'pt' }`.
4. Criar `conv.nav.config.ts` com a configuração de breadcrumb do patient shell (root → active category).
5. Criar `conv.routes.ts` com as rotas `{ path: 'paciente', component: PatientShellComponent, children: [...] }` — `loadComponent`-lazy para `SpeechTreePageComponent` e `ProposalConfirmPageComponent`.
6. Criar `CONV_MODULE_DESCRIPTOR` com as constantes acima unificadas; exportar tudo pelo `src/index.ts`.

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/shell-entry/src/lib/conv.contract.ts`
- [ ] implementar `libs/conv/shell-entry/src/lib/conv.permission-map.ts`
- [ ] implementar `libs/conv/shell-entry/src/lib/conv.feature-flags.ts`
- [ ] implementar `libs/conv/shell-entry/src/lib/conv.nav.config.ts`
- [ ] implementar `libs/conv/shell-entry/src/lib/conv.routes.ts`
- [ ] implementar `libs/conv/shell-entry/src/index.ts`
- [ ] `nx lint conv-shell-entry` PASS (Nx boundary: `scope:conv` → `scope:shared` permitido, `scope:gest` PROIBIDO)
- [ ] nenhuma rota de cuidador (`/historico`, `/calibracao`, `/onboarding`) listada nas rotas do patient shell

## Acceptance criteria
- [ ] `CONV_MENU_METADATA.registerInAdminShell === false` verificável em teste unitário
- [ ] `CONV_PERMISSION_MAP` não contém nenhuma authority com prefixo diferente de `conv.*`
- [ ] `CONV_FEATURE_FLAGS.defaultLocale` é `'pt'` e o tipo é `Locale`
- [ ] `npx nx test conv-shell-entry` PASS (Jest unit)
- [ ] `npx nx lint conv-shell-entry` PASS
- [ ] `npx nx build conv-shell-entry` PASS

## Dependencies
Nenhuma (issue de fundação — é pré-requisito de todas as outras).

## Empresa-context impact
Nenhum — o contrato do módulo é empresa-agnóstico; o isolamento multi-empresa opera no `@neuroassist/shared/access` e no gateway.

## Prioridade
P1
