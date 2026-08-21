## [FE-HIST-016] hist access: histGuard (vínculo caregiver↔patient, fail-closed)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`histGuard` em `libs/hist/access` — guarda de rota que verifica vínculo caregiver↔patient para o tenant ativo (ACS-resolvido) com semântica fail-closed.

**Context / Problem statement:**
- SPEC-01 §3: `histGuard` verifica vínculo para `X-Empresa-Id` tenant ativo. Falha → redirect `/sem-acesso?reason=vinculo-ausente`.
- Fail-closed: vínculo não verificável → negar acesso (nunca mostrar lista vazia).
- `X-Empresa-Id` provém do interceptor `access-http-base`, não de input do usuário.
- FE-GAP-09/10 (FE Specs-Validate): cenários de 403/401 precisam ser cobertos.
- SPEC-01 §3: `authGuard` (do `@neuroassist/shared/access`) é pré-requisito; `histGuard` assume sessão autenticada.

**Approach (execution path):**
1. Criar `libs/hist/access` com `project.json` Nx (tags `scope:hist, type:domain`).
2. `histGuard`: `CanActivateFn` que consulta ACS via serviço compartilhado.
3. Se ACS retornar vínculo presente → `true`.
4. Se ACS retornar ausente ou erro (503, timeout) → `router.navigate(['/sem-acesso'], { queryParams: { reason: 'vinculo-ausente' } })`; retorna `false`.
5. Registrar guard em `hist.routes.ts` (FE-HIST-001).
6. Unit tests: vínculo presente, ausente, timeout/503.

**Non-goals:**
- Não verificar entitlement server-side — responsabilidade do ACS.

**Layer(s):**
frontend / access

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-01-route-map.md` (§3)

**Architecture alignment:**
- Tag Nx: `scope:hist`, `type:domain`

**Implementation checklist (by file):**
- 🔲 `libs/hist/access/src/lib/hist.guard.ts`
- 🔲 `libs/hist/access/src/lib/hist.guard.spec.ts`
- 🔲 `libs/hist/access/src/index.ts`
- 🔲 `libs/hist/access/project.json`
- 🔲 `nx test hist-access` PASS
- 🔲 `nx lint hist-access` PASS

**Milestone:** Wave P4 / Access Guards

**Labels:**
type:feature · layer:frontend · scope:hist · priority:P4

**Acceptance criteria:**
- Vínculo ausente → redirect `/sem-acesso?reason=vinculo-ausente`.
- ACS timeout → fail-closed (redirect, não lista vazia).
- `authGuard` precede `histGuard` na cadeia de guards.

**Dependencies:**
- FE-HIST-001, FE-HIST-002

**Risks / Notes:**
- FE-GAP-09: cenário de 403 da API (mid-session vínculo revogado) deve ser coberto também por FE-HIST-017 (error mapping).
