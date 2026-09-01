# CONV-FE-003 -- PatientShellComponent + PatientTopbarComponent (restricted patient shell — topbar only, no caregiver sidebar)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Outcome
`libs/conv/patient-shell` entrega o shell restrito do paciente: `PatientShellComponent` (smart, standalone, OnPush) com `RouterOutlet` para `{ arvore, confirmar }` e `PatientTopbarComponent` (presentacional) — sem sidebar, sem menu de admin, sem nenhuma rota de cuidador renderizável.

## Approach (execution path)
1. Promover `shell/patient-shell.component.ts` do protótipo `design-reference/` para `libs/conv/patient-shell/` (ajustando imports para `@neuroassist/conv/*`).
2. Implementar `PatientShellComponent`: lê `ConvLocaleFacade.locale` e `PatientSessionFacade.status` via signals; fornece `RouterOutlet`; não importa `sidebar.component.ts` nem qualquer lib de cuidador.
3. Implementar `PatientTopbarComponent` (presentacional): inputs `locale: Locale`, `patientName: string`; output `localeSwitchRequested: EventEmitter<Locale>`; exibe breadcrumb e switcher de idioma.
4. Validar que o template HTML não contém nenhum link para `/historico`, `/calibracao`, `/onboarding` ou login.
5. Exportar pelo `src/index.ts` da lib.

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/patient-shell/src/lib/patient-shell.component.ts`
- [ ] implementar `libs/conv/patient-shell/src/lib/patient-shell.component.html`
- [ ] implementar `libs/conv/patient-shell/src/lib/patient-topbar.component.ts`
- [ ] implementar `libs/conv/patient-shell/src/index.ts`
- [ ] teste: nenhum elemento de rota de cuidador renderizável a partir do `PatientShellComponent`
- [ ] `nx lint conv-patient-shell` PASS (Nx boundary: `scope:conv`, sem imports de `scope:gest`)

## Acceptance criteria
- [ ] `PatientShellComponent` não importa nenhum componente de sidebar de cuidador
- [ ] `PatientTopbarComponent` aceita `locale` e `patientName` como inputs e emite `localeSwitchRequested`
- [ ] Nenhuma rota de cuidador está no template ou na árvore de imports
- [ ] `npx nx test conv-patient-shell` PASS
- [ ] `npx nx lint conv-patient-shell` PASS
- [ ] `npx nx build conv-patient-shell` PASS

## Dependencies
- CONV-FE-001 (CONV_ROUTES para o RouterOutlet)
- CONV-FE-018 (patientSessionGuard)
- CONV-FE-020 (PatientBreadcrumbComponent)
- INT-FE-CONV-001 (ConvLocaleFacade semeado a partir do JWT)

## Empresa-context impact
Nenhum.

## Prioridade
P1
