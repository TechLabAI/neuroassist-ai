# GEST-INT-005 -- PAC shell-page composition slot — pac-comando-editor-page hosts <gest-live-test>

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens
**GitHub:** [#TBD]

## Outcome
`apps/web/src/app/pac/pac-comando-editor-page.component.ts` renders `<gest-live-test [commandDraft]="form.draftSignal()" (testCompleted)="onVerdict($event)">` via `GestShellIntegrationService.resolveLiveTestSlot()`, preserving the `scope:pac` → `scope:gest` boundary.

## Context / Problem statement
- This is the primary production composition point. The PAC command editor page (calibração) embeds the live test inline. The host page owns the command draft and the verdict callback.
- The component is fetched lazily via `GestShellIntegrationService`, not via a direct import.
- Source: SPEC-01 §2, SPEC-02 §4, SPEC-06 §5.
- Coordinates with: PAC module backlog (PAC must expose the host page; GEST provides the slot).

## Approach (execution path)
1. In `pac-comando-editor-page.component.ts` (lives in `scope:web`), inject `GestShellIntegrationService`.
2. In `ngOnInit`, call `service.resolveLiveTestSlot()` and store the component type.
3. Render dynamically: `@defer { <ng-container *ngComponentOutlet="liveTestSlot; inputs: {commandDraft: form.draftSignal()}"/> }` or equivalent.
4. Wire `(testCompleted)` output to `onVerdict()` handler in the PAC host page.
5. Ensure no `import` of `scope:gest` in this file — only `scope:web` imports `GestShellIntegrationService` via the `gest/integration` lib.

## Non-goals
- Not responsible for PAC's own command form (that is PAC module territory).
- Not responsible for implementing `GestShellIntegrationService` (GEST-INT-001).

## Layer(s)
frontend / integration (shell composition)

## Bounded context
gest (composition side: scope:web)

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-06-shell-integration.md` (§5)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-01-route-map.md` (§2)

## Architecture alignment
- `scope:web` page (the only scope that may compose `scope:gest`)
- Dynamic component outlet, no static import of `scope:gest`
- Tag Nx: `scope:web`, `type:feature`

## Implementation checklist (by layer/file)

**Shell / web app**
- [ ] `apps/web/src/app/pac/pac-comando-editor-page.component.ts`

**Tests**
- [ ] `npx nx lint web` PASS (no direct scope:gest import in scope:pac code)
- [ ] `npx nx build web` PASS

## Acceptance criteria
- `pac-comando-editor-page.component.ts` has NO direct import from `@neuroassist/gest`
- `GestureLiveTestComponent` rendered inside the page at runtime
- `(testCompleted)` output wired to host handler
- `npx nx lint web` PASS

## Dependencies
- GEST-INT-001 (GestShellIntegrationService)
- GEST-FE-011 (GestureLiveTestComponent exists)

## Empresa-context impact
Nenhum — composition is tenant-agnostic; tenant context flows via auth.

## Milestone
Wave 4 / Hub integrations

## Labels
type:integration
layer:frontend
scope:gest
priority:P4

## Risks / Notes
- PAC module must have its command editor page scaffolded before this integration can be wired — coordinate with PAC backlog timeline

## Prioridade
P4
