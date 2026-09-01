## [TREI-FE-003] TREI_FEATURE_FLAGS (tool.feature-flags.ts)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/trei/shell-entry/src/lib/trei.feature-flags.ts` declara `TREI_FEATURE_FLAGS` com as quatro flags (`treinamentoEnabled`, `streamingConversation`, `voiceInputEnabled`, `defaultLocale`) e seus defaults para o MVP.

**Context / Problem statement:**
- TREI é um módulo retrofit; o kill-switch `treinamentoEnabled` é obrigatório para rollout seguro — quando `off`, o menu item e as rotas não são registradas (SPEC-05 §3).
- `streamingConversation: false` reflete ADR-005: o MVP usa request/response; SSE é deferred até o BE expor a capability.
- `voiceInputEnabled: false` está explicitamente fora do MVP (doc 01 §5).
- `defaultLocale: 'pt'` é semeado do JWT locale da cuidadora (SPEC-06 §3).
- Bloqueia: TREI-FE-010 (`TrainingSessionFacade.switchLocale` usa `defaultLocale`), TREI-FE-005 (bootstrap seed locale), INT-FE-TREI-004 (kill-switch no shell).

**Approach (execution path):**
1. Criar `libs/trei/shell-entry/src/lib/trei.feature-flags.ts`.
2. Importar `Locale` de `@neuroassist/trei/data-access` (ou do barrel local quando definido em TREI-FE-009).
3. Exportar `TREI_FEATURE_FLAGS = { treinamentoEnabled: true, streamingConversation: false, voiceInputEnabled: false, defaultLocale: 'pt' as Locale } as const`.
4. Documentar cada flag com o rationale em JSDoc (ADR-ref, doc-ref).

**Non-goals:**
- Não implementar o `FeatureFlagService` — esse é de `@neuroassist/shared/feature-flags`.
- Não ler as flags do servidor em runtime (MVP é estático).

**Layer(s):**
frontend / libs/trei/shell-entry

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-05-tool-contract.md` (§3)
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/generator-config.yml` (feature_flags)

**Architecture alignment:**
- `as const` — imutável; o `FeatureFlagService` sobrescreve em runtime se necessário
- Tag Nx: `scope:trei`, `type:domain`

**Implementation checklist (by file)**

**libs/trei/shell-entry**
- [ ] `libs/trei/shell-entry/src/lib/trei.feature-flags.ts`
- [ ] `treinamentoEnabled: true` (on por default para dev)
- [ ] `streamingConversation: false` (off — ADR-005 deferred)
- [ ] `voiceInputEnabled: false` (off — MVP out-of-scope)
- [ ] `defaultLocale: 'pt'` (locale padrão da cuidadora)

**Tests**
- [ ] `nx lint trei-shell-entry` PASS
- [ ] `nx build trei-shell-entry` PASS

**Milestone:**
Wave 1 / Foundation

**Labels:**
type:feature
layer:frontend
scope:trei
priority:P1

**Acceptance criteria:**
- `npx nx lint trei-shell-entry` PASS
- `npx nx build trei-shell-entry` PASS
- `TREI_FEATURE_FLAGS.streamingConversation === false` (SSE deferred)
- `TREI_FEATURE_FLAGS.voiceInputEnabled === false` (voice deferred)
- Tipo de `defaultLocale` restrito a `Locale` (`'pt' | 'en'`), não `string`

**Dependencies:**
- TREI-FE-001 (mesma lib)
- TREI-FE-009 (tipo `Locale` — pode ser forward-declared localmente se necessário)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- `nx lint` clean
- Revisão de código: presença dos 4 flags com defaults corretos

**Risks / Notes:**
- Ordem de definição do tipo `Locale`: se TREI-FE-009 ainda não existir, declarar `type Locale = 'pt' | 'en'` localmente e refatorar quando TREI-FE-009 for mergeado.
