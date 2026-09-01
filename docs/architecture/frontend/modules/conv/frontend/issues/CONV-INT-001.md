# CONV-INT-001 -- shared/access patient-session JWT integration (SessaoPaciente token header on all conv-service calls via GWY)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Outcome
Um `ConvAuthInterceptor` (ou configuração do interceptor de `@neuroassist/shared/access`) garante que todas as chamadas HTTP do CONV ao `conv-service` via GWY carregam o token `SessaoPaciente` no header `Authorization: Bearer`; nenhuma chamada CONV sai sem token; token de cuidador é rejeitado (403 pelo BE — defense in depth).

## Approach (execution path)
1. Verificar se `@neuroassist/shared/access` já fornece um interceptor configurável por módulo. Se sim, registrá-lo no `DATA_PROVIDERS` do CONV (CONV-FE-012).
2. Se não: criar `libs/conv/data-access/src/lib/conv-auth.interceptor.ts` que:
   - Intercepta todas as chamadas para `/api/v1/conv/*`.
   - Adiciona `Authorization: Bearer <sessaoPacienteToken>` via `@neuroassist/shared/access`.
   - Se token ausente: encerrar o patient shell (via `CONV_BOOTSTRAP.destroy()`) em vez de enviar a chamada sem auth.
3. Integrar no `DATA_PROVIDERS` (CONV-FE-012) e verificar que o `HttpClient` usado nos adapters passa pelo interceptor.

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/data-access/src/lib/conv-auth.interceptor.ts` (se não coberto por shared/access)
- [ ] teste de integração: chamada HTTP sem token → interceptado, shell encerrado
- [ ] teste de integração: token de cuidador → 403 recebido do BE, mapeado para forbidden-scope surface
- [ ] `npx nx test conv-data-access` PASS

## Acceptance criteria
- [ ] Nenhuma chamada conv-service sai sem header Authorization
- [ ] Token de cuidador em sessão de paciente → 403 mapeado para `shell-exit` (SPEC-05 §2)
- [ ] `npx nx lint conv-data-access` PASS

## Dependencies
- `@neuroassist/shared/access` (pacote de acesso cross-module)
- CONV-FE-012 (DATA_PROVIDERS — ponto de registro do interceptor)
- CONV-FE-002 (CONV_BOOTSTRAP.destroy — acionado em token ausente)

## Empresa-context impact
Nenhum — o token SessaoPaciente já carrega o `tenantId` (empresa); o interceptor não precisa saber do tenant.

## Prioridade
P4
