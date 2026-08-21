## [FE-HIST-006] hist data-access: FeedbackClient (POST feedback para evnt-service)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`FeedbackClient` em `libs/hist/data-access` encapsula o POST de veredicto de feedback ao `evnt-service`, com switch via `feedbackTarget` flag.

**Context / Problem statement:**
- SPEC-04 §2.4: `POST /api/v1/evnt/events/{eventId}/feedback { verdict }`.
- `feedbackTarget` flag (default `'evnt'`): se `'evnt'` → base evnt-service; se `'hist'` → base hist-service proxy. DTO idêntico.
- Non-optimistic: item verdict é setado apenas em sucesso (SPEC-03 §4).
- 409 already-terminal: reconciliar item ao veredicto do backend (SPEC-06 §1).
- Bloqueia: FE-HIST-008 (método `markFeedback` da Facade).

**Approach (execution path):**
1. `FeedbackClient` em `libs/hist/data-access`:
   - `markFeedback(eventId: string, verdict: Verdict): Observable<void>`
   - Resolve base_path via `HIST_FEATURE_FLAGS.feedbackTarget`.
2. Lança `HttpErrorResponse` para tratamento na Facade (409 → erro específico, 5xx → genérico).
3. Injetar `HIST_FEATURE_FLAGS` via token — testável com override.

**Non-goals:**
- Não implementar Facade — FE-HIST-008.
- Não implementar UI de error state — FE-HIST-019.

**Layer(s):**
frontend / data-access

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-04-api-integration.md` (§2.4)
- `docs/architecture/frontend/modules/hist/contracts/backend-api-cross-link.md` (§3)

**Architecture alignment:**
- Tag Nx: `scope:hist`, `type:data-access`

**Implementation checklist (by file):**
- 🔲 `libs/hist/data-access/src/lib/feedback.client.ts`
- 🔲 `libs/hist/data-access/src/lib/feedback.models.ts` (Verdict type)
- 🔲 `libs/hist/data-access/src/lib/feedback.client.spec.ts`
- 🔲 Atualizar `src/index.ts`
- 🔲 `nx test hist-data-access` PASS
- 🔲 `nx lint hist-data-access` PASS

**Milestone:** Wave P2 / Data Access

**Labels:**
type:feature · layer:frontend · scope:hist · priority:P2

**Acceptance criteria:**
- `feedbackTarget='evnt'` → POST vai para `/api/v1/evnt/events/{id}/feedback`.
- `feedbackTarget='hist'` → POST vai para proxy hist-service (DTO idêntico).
- Unit test cobre 200/201, 409 e 5xx.

**Dependencies:**
- FE-HIST-002 (flags token), INT-FE-HIST-005

**API / Contract impact:**
- POST /api/v1/evnt/events/{eventId}/feedback (evnt-service, G1).

**Risks / Notes:**
- G1 aberto: confirmar endpoint exato do evnt-service antes de wiring final.
