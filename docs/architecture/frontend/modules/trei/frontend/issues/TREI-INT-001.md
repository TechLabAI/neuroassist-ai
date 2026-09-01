## [TREI-INT-001] Integration com ACS (@neuroassist/shared/access) — JWT caregiver session + caregiverSessionGuard delegation

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/trei/access` delega correctamente a verificação de sessão JWT ao `CaregiverSessionService` de `@neuroassist/shared/access` — sem reimplementar JWT parsing ou auth state no módulo TREI.

**Context / Problem statement:**
- TREI não implementa login nem gestão de sessão — delega inteiramente ao shared access layer (SPEC-01 §3, ADR-012).
- `caregiverSessionGuard` e `treiGuard`/`treiProporGuard` usam `CaregiverSessionService.hasActiveSession()` e `CaregiverSessionService.hasScope(scope)` — estas são as únicas APIs do shared/access que TREI toca directamente.
- A revogação de sessão (401 session-revoked) é tratada pelo shared HTTP interceptor antes de chegar ao código TREI — TREI apenas reage ao `AppError.code === 'SESSION_REVOKED'` no error mapper.
- A integração afecta: guards (TREI-FE-022/023), error mapper (TREI-FE-012), e bootstrap context (TREI-FE-005).

**Approach (execution path):**
1. Verificar que `@neuroassist/shared/access` exporta `CaregiverSessionService` com `hasActiveSession(): boolean` e `hasScope(scope: string): boolean`.
2. Garantir que `libs/trei/access` declara `@neuroassist/shared/access` como peer dep (não como dep directa do feature lib).
3. Confirmar que o shared HTTP interceptor (de `@neuroassist/shared/access`) está registado no root do app e captura todos os 401 antes dos adapters TREI.
4. Documentar no `libs/trei/access/README.md` a dependência de integração e o contrato de revogação.

**Non-goals:**
- Não implementar `CaregiverSessionService` (pertence ao shared/access).
- Não duplicar lógica de JWT parsing.

**Layer(s):**
frontend / libs/trei/access · libs/trei/data-access

**Bounded context:**
trei → shared/access

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-01-route-map.md` (§3)

**Architecture alignment:**
- `scope:trei` → `scope:shared` (permitido por nx-boundaries.md)
- Tag Nx: `scope:trei`, `type:domain`

**Implementation checklist (by file)**

**libs/trei/access**
- [ ] `caregiver-session.guard.ts` usa `CaregiverSessionService.hasActiveSession()`
- [ ] `trei.guard.ts` + `trei-propor.guard.ts` usa `CaregiverSessionService.hasScope()`
- [ ] `libs/trei/access/README.md` documenta integração com shared/access

**Tests**
- [ ] `nx test trei-access` PASS com mock de `CaregiverSessionService`
- [ ] `nx lint trei-access` PASS — sem import de módulos de auth fora de `scope:shared`

**Milestone:**
Wave 1 / Foundation (integração crítica)

**Labels:**
type:integration
layer:frontend
scope:trei
priority:P1

**Acceptance criteria:**
- `npx nx test trei-access` PASS
- `npx nx lint trei-access` PASS — sem violação de boundary
- Guards funcionam correctamente com mock de sessão activa e inactiva

**Dependencies:**
- TREI-FE-022/023 (os guards que usam esta integração)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum HTTP (JWT local)

**Testing / Evidence required:**
- Teste de integração: guard com `CaregiverSessionService` spy

**Risks / Notes:**
- Se `CaregiverSessionService` não existir em `@neuroassist/shared/access` com esta interface exacta, levantar com o squad do shared antes de implementar os guards.
