# CONV-FE-004 -- patient-tool-descriptors.ts — register CONV in patient shell ONLY, not caregiver (apps/neuroassist-ai-web)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Outcome
`apps/neuroassist-ai-web` contém `patient-tool-descriptors.ts` com `PATIENT_TOOL_DESCRIPTORS` listando CONV com lazy load via `import('@neuroassist/conv')`, e as rotas do patient shell montadas sob `/paciente` com `patientSessionGuard`. O `tool-descriptors.ts` do caregiver shell NÃO lista CONV.

## Approach (execution path)
1. Criar `apps/neuroassist-ai-web/src/app/patient/patient-tool-descriptors.ts` com `PATIENT_TOOL_DESCRIPTORS: ToolDescriptor[]` — entry `{ key: 'conv', load: () => import('@neuroassist/conv').then(m => m.CONV_MODULE_DESCRIPTOR), surface: 'patient-only' }`.
2. Criar `apps/neuroassist-ai-web/src/app/patient/patient.routes.ts` com a rota `/paciente` apontando para `PatientShellComponent`, guarded por `patientSessionGuard`.
3. Verificar que o `tool-descriptors.ts` existente (caregiver) não foi alterado para incluir CONV.
4. Adicionar `{ path: 'paciente', loadChildren: ... }` no `app.routes.ts` principal.

## Implementation checklist (by layer/file)
- [ ] implementar `apps/neuroassist-ai-web/src/app/patient/patient-tool-descriptors.ts`
- [ ] implementar `apps/neuroassist-ai-web/src/app/patient/patient.routes.ts`
- [ ] verificar que o caregiver `tool-descriptors.ts` não lista CONV
- [ ] `nx lint neuroassist-ai-web` PASS

## Acceptance criteria
- [ ] `PATIENT_TOOL_DESCRIPTORS` contém exatamente um entry com `key: 'conv'` e `surface: 'patient-only'`
- [ ] Caregiver `tool-descriptors.ts` não referencia `conv` nem `/paciente`
- [ ] Rota `/paciente` está protegida por `patientSessionGuard`
- [ ] `npx nx test neuroassist-ai-web` PASS
- [ ] `npx nx build neuroassist-ai-web` PASS

## Dependencies
- CONV-FE-001 (CONV_MODULE_DESCRIPTOR, CONV_ROUTES)
- CONV-FE-003 (PatientShellComponent)
- CONV-FE-018 (patientSessionGuard)

## Empresa-context impact
Nenhum — o descriptor do patient shell é empresa-agnóstico; o tenant é determinado pelo JWT do paciente.

## Prioridade
P1
