# CONV-FE-023 -- ProblemDetail → patient copy (toast/banner/inline/shell-exit surface mapping, i18n EN/PT, SPEC-07 §1 table, ADR-018)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Outcome
`conv-error-mapper.ts` mapeia cada código BE (`ProblemDetail.type` suffix) para o `AppError` correto com a surface e i18n key definidas em SPEC-07 §1; os arquivos `conv.pt.json` e `conv.en.json` contêm as cópias de paciente (calmas, sem jargão, sem código HTTP) em PT-BR e EN-US (ADR-018).

## Approach (execution path)
1. Criar `libs/conv/data-access/src/lib/conv-error-mapper.ts`:
   - Mapeamento BE type suffix → `{ surface, i18nKey, action }` conforme SPEC-07 §1 tabela.
   - `content-pending-locale` → `inline` (não abre nada visível além do nó disabled).
   - `unauthenticated | session-revoked | forbidden-scope` → `shell-exit` (acionado pelo guard/service, nunca modal).
   - `node-not-found` → `toast` + reload level.
   - `unsupported-locale` → `banner` + revert locale.
   - `invalid-request` → `toast` (assist input only).
   - `retrieval-unavailable` → sem surface (silent).
   - Nunca modal na superfície do paciente (SPEC-07 §4).
2. Criar `apps/neuroassist-ai-web/src/assets/i18n/conv.pt.json` com as cópias PT-BR para todas as keys de erro.
3. Criar `apps/neuroassist-ai-web/src/assets/i18n/conv.en.json` com as cópias EN-US correspondentes.
4. Criar `libs/conv/ui/src/lib/session-ended.component.ts` (presentacional): tela de "Sessão encerrada" para shell-exit; sem opção de re-login do cuidador.

## Implementation checklist (by layer/file)
- [ ] implementar `libs/conv/data-access/src/lib/conv-error-mapper.ts`
- [ ] implementar `libs/conv/ui/src/lib/session-ended.component.ts`
- [ ] implementar `apps/neuroassist-ai-web/src/assets/i18n/conv.pt.json`
- [ ] implementar `apps/neuroassist-ai-web/src/assets/i18n/conv.en.json`
- [ ] teste: `retrieval-unavailable` → nenhuma surface (sem toast, sem banner)
- [ ] teste: `session-revoked` → surface é `shell-exit`, nunca `toast` ou `modal`

## Acceptance criteria
- [ ] Nenhum código HTTP visível nas mensagens de paciente
- [ ] Nenhum modal renderizado na superfície do paciente (invariante SPEC-07 §4)
- [ ] Todas as i18n keys de SPEC-07 §1 têm valores em `conv.pt.json` e `conv.en.json`
- [ ] `npx nx test conv-data-access` PASS
- [ ] `npx nx lint conv-data-access` PASS
- [ ] `npx nx build neuroassist-ai-web` PASS (assets carregados)

## Dependencies
- INT-FE-CONV-002 (shared/error-model ProblemDetail → AppError base mapping)
- CONV-FE-018 (patientSessionGuard — usa shell-exit para session errors)

## Empresa-context impact
Nenhum — cópias de paciente são independentes de empresa.

## Prioridade
P7
