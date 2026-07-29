## [PAC-FE-018] pacCommandWriteGuard — pac.comando.write permission gate

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/access/src/lib/pac-command-write.guard.ts` implementa `pacCommandWriteGuard` que verifica se o caller possui a permissão `pac.comando.write` (mapeada a `pac:command:write` no BE); redireciona/bloqueia silenciosamente se a permissão faltar, defendendo as rotas do editor em profundidade (SPEC-01 §3, SPEC-05 §2).

**Context / Problem statement:**
- SPEC-01 §3: guard para rotas `/pac/comandos/novo` e `/pac/comandos/:id/editar`.
- SPEC-05 §2: "IF caller lacks the comando write permission, THE SYSTEM SHALL hide the novo comando entry point" — o guard é a camada de rota; a UI também esconde o botão (defense-in-depth).
- O BE enforça `pac:command:write` scope; a UI enforce apenas como UX protection, não como security boundary.
- Bloqueia: PAC-FE-002 (rotas do editor usam este guard)

**Approach (execution path):**
1. Criar `libs/pac/access/src/lib/pac-command-write.guard.ts` como `CanActivateFn`.
2. Injetar `PermissionDecisionService` de `@neuroassist/shared/access-decision`.
3. Check: `permissionDecision.hasPermission('pac.comando.write')` → `true` → allow; `false` → `UrlTree('/pac')` (redirect de volta ao catalog sem mensagem de erro explícita — o BE retornará 403 se API for chamada).
4. Unit test: permissão presente → true; ausente → redirect to `/pac`.

**Non-goals:**
- Sem fetch async para verificar permissão — usa apenas o token local do JWT.
- Sem toast/banner aqui — SPEC-07 §1 cobre o erro 403 se a chamada API passar (camada de rede).

**Layer(s):**
frontend / libs/pac/access

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-01-route-map.md` (§3)
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-05-tool-contract.md` (§2)

**Architecture alignment:**
- Functional guard (`CanActivateFn`)
- Tag Nx: `scope:pac`, `type:domain`

**Implementation checklist (by file):**

**Lib: libs/pac/access**
- [ ] `libs/pac/access/src/lib/pac-command-write.guard.ts`
- [ ] `libs/pac/access/src/lib/pac-command-write.guard.spec.ts`

**Tests**
- [ ] `npx nx test pac-access` PASS
- [ ] `npx nx lint pac-access` PASS

**Milestone:**
Wave 5 / P5 Access Guards

**Labels:**
type:feature
layer:frontend
scope:pac
priority:P5

**Acceptance criteria:**
- `npx nx test pac-access` PASS: permissão ausente → `UrlTree('/pac')`.
- `npx nx test pac-access` PASS: permissão presente → `true`.

**Dependencies:**
- PAC-FE-001 (MODULE_PERMISSION_MAP mapeia 'pac.comando.write')
- `@neuroassist/shared/access-decision`

**Data impact:**
Nenhum.

**API / Contract impact:**
Nenhum (guard local).

**Testing / Evidence required:**
- Unit: 2 cenários básicos.

**Risks / Notes:**
- Deve usar a permission key `'pac.comando.write'` (string de UI) não o scope BE diretamente; o mapeamento UI→BE é centralizado no `MODULE_PERMISSION_MAP` (PAC-FE-001).
