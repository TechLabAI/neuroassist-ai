# CONV-FE-018 -- patientSessionGuard — active SessaoPaciente JWT check, shell teardown on revoke (no caregiver redirect)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Outcome
`patientSessionGuard` em `libs/conv/access` verifica a presença de `SessaoPaciente` JWT ativo antes de qualquer render do patient shell; em ausência ou revogação, encerra o patient shell (chama `CONV_BOOTSTRAP.destroy()`) e navega para a tela `session-ended` — nunca para login de cuidador.

## Approach (execution path)
1. Criar `patient-session.guard.ts` como `CanActivateFn` Angular 21.
2. Injetar `@neuroassist/shared/access` para verificar presença de `SessaoPaciente` JWT com `status === 'ativa'`.
3. Se JWT ausente: `CONV_BOOTSTRAP.destroy()` + redirecionar para `/session-ended` (tela de sessão encerrada — nunca `/login`).
4. Se JWT presente: retornar `true`.
5. Escutar respostas `401 session-revoked` de qualquer chamada `conv-service` (via `ConvShellIntegrationService`): acionar o mesmo destroy + redirect.
6. Exportar pelo `src/index.ts`.

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/access/src/lib/patient-session.guard.ts`
- [ ] implementar `libs/conv/access/src/index.ts`
- [ ] teste: JWT ausente → `destroy()` chamado, redireciona para `/session-ended`, não para `/login`
- [ ] teste: `401 session-revoked` → teardown acionado

## Acceptance criteria
- [ ] Guard nunca redireciona para nenhuma rota de cuidador ou `/login`
- [ ] `CONV_BOOTSTRAP.destroy()` é chamado antes do redirect em caso de sessão inválida
- [ ] Camera tracks liberados em qualquer caminho de exit da sessão
- [ ] `npx nx test conv-access` PASS
- [ ] `npx nx lint conv-access` PASS

## Dependencies
- CONV-FE-002 (CONV_BOOTSTRAP.destroy)
- INT-FE-CONV-001 (shared/access — SessaoPaciente JWT)

## Empresa-context impact
Nenhum — o guard é empresa-agnóstico; a empresa é determinada pelo tenant no JWT.

## Prioridade
P5
