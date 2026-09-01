## [TREI-FE-002] TREI_PERMISSION_MAP (tool.permission-map.ts)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/trei/shell-entry/src/lib/trei.permission-map.ts` declara `TREI_PERMISSION_MAP` com as quatro permissões da cuidadora (`conduzir`, `propor`, `aprovar`, `descartar`) verbatim do `api-contract.md §2` — e explicitamente sem `treinamento.no.aprovar.paciente` (INV-1).

**Context / Problem statement:**
- Os scopes do JWT (ACS) são o contrato entre o BE e o FE para RBAC. O `TREI_PERMISSION_MAP` é a fonte única de verdade destes scopes no FE; guards e componentes o importam, não redeclaram strings (SPEC-05 §2).
- A ausência explícita de `treinamento.no.aprovar.paciente` é uma invariante estrutural (INV-1, ADR-007): o comentário `// DELIBERATELY ABSENT` faz parte do código e deve permanecer.
- Bloqueia: TREI-FE-022/023 (guards), TREI-FE-010/011 (facades que checam permissão), TREI-FE-005 (bootstrap assert).

**Approach (execution path):**
1. Criar `libs/trei/shell-entry/src/lib/trei.permission-map.ts`.
2. Exportar `TREI_PERMISSION_MAP = { conduzir: 'treinamento.sessao.conduzir', propor: 'treinamento.no.propor', aprovar: 'treinamento.no.aprovar.cuidadora', descartar: 'treinamento.no.descartar' } as const`.
3. Adicionar comentário `// NOTE: 'treinamento.no.aprovar.paciente' is DELIBERATELY ABSENT — patient side is CONV's (INV-1, ADR-007)`.
4. Garantir que o tipo inferido é `const` (não `Record<string,string>`).

**Non-goals:**
- Não implementar a lógica de verificação de permissão aqui (responsabilidade dos guards e do shared/access).
- Não listar permissões de outros módulos.

**Layer(s):**
frontend / libs/trei/shell-entry

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-05-tool-contract.md` (§2)
- `docs/architecture/backend/services/trei-service/contracts/api-contract.md` (§2) — fonte de verdade dos scopes

**Architecture alignment:**
- `as const` para type-safety máximo
- Tag Nx: `scope:trei`, `type:domain`

**Implementation checklist (by file)**

**libs/trei/shell-entry**
- [ ] `libs/trei/shell-entry/src/lib/trei.permission-map.ts`
- [ ] Comentário `// DELIBERATELY ABSENT` presente

**Tests**
- [ ] `nx lint trei-shell-entry` PASS
- [ ] Teste de snapshot: `TREI_PERMISSION_MAP.aprovar === 'treinamento.no.aprovar.cuidadora'`
- [ ] Teste de ausência: `'treinamento.no.aprovar.paciente'` não aparece nos values do map

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
- O string `'treinamento.no.aprovar.paciente'` não ocorre em nenhum arquivo de `libs/trei/**` (grep -r verificável)
- Teste unitário de snapshot passa (4 keys, 4 valores verbatim do contrato)

**Dependencies:**
- TREI-FE-001 (mesma lib, mesmo arquivo de barrel)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum (constantes; os scopes correspondem a `api-contract.md §2`)

**Testing / Evidence required:**
- Resultado de `grep -r 'aprovar.paciente' libs/trei/` vazio
- `nx lint` clean

**Risks / Notes:**
- Se o `api-contract.md` for atualizado com um novo scope, este arquivo deve ser o único lugar no FE a mudar.
