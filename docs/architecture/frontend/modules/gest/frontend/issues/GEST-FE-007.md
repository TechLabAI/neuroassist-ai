# GEST-FE-007 -- EdgeDetectionClient — openStream/mockStream/close + contractVersion negotiation + degraded fallback

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens
**GitHub:** [#TBD]

## Outcome
`libs/gest/data-access/src/lib/edge-detection.client.ts` implements `EdgeDetectionClient` with `openStream(draft)`, `mockStream(draft)`, and `close()`. The client negotiates `contractVersion` on channel open and transparently switches to `mockStream` (degraded, `reason: 'version-mismatch'`) if incompatible.

## Context / Problem statement
- The edge bridge is a localhost WS/SSE channel (FE ADR-005). The client wraps it as `Observable<EdgeRecognitionSignal>` so the facade can subscribe without knowing the transport.
- Transport auth is a JWT-shaped device token provisionally, isolated behind a `bridgeToken` provider (GEST-INT-004) so the final mTLS/OAuth2 decision does not ripple into the facade.
- Source: SPEC-04 §2.
- Bloqueia: GEST-FE-008 (facade depends on EdgeDetectionClient)

## Approach (execution path)
1. Create `libs/gest/data-access/src/lib/edge-detection.client.ts` implementing `EdgeDetectionClient` interface.
2. `openStream(draft)`: open WS/SSE connection, send `contractVersion` handshake. On version mismatch, return `mockStream(draft)` transparently.
3. `mockStream(draft)`: return an `Observable<EdgeRecognitionSignal>` with `degraded: true` signals (honest; may emit not_recognized, never fake recognized).
4. `close()`: unsubscribe + close the channel.
5. Create `libs/gest/data-access/src/lib/bridge-token.provider.ts` (provisional JWT-device token provider, see GEST-INT-004).

## Non-goals
- Not responsible for `MediaStream` acquisition (that is the facade, GEST-FE-008).
- Not responsible for finalizing bridge transport auth (GEST-INT-004 tracks the open item).

## Layer(s)
frontend / data-access

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-04-data-access.md` (§2)

## Architecture alignment
- `Injectable`, provided in `GestureTestFacade` scope
- Tag Nx: `scope:gest`, `type:data-access`

## Implementation checklist (by layer/file)

**Data-access**
- [ ] `libs/gest/data-access/src/lib/edge-detection.client.ts`
- [ ] `libs/gest/data-access/src/lib/bridge-token.provider.ts`

**Tests**
- [ ] `npx nx test gest-data-access` PASS (unit: version-mismatch → mockStream; close() stops observable)
- [ ] `npx nx lint gest-data-access` PASS
- [ ] `npx nx build gest-data-access` PASS

## Acceptance criteria
- `openStream` returns `mockStream` on version mismatch with reason `version-mismatch`
- `close()` completes the observable
- `mockStream` signals always have `degraded: true`, never `signalKind: 'recognized'` with `degraded: false`
- `npx nx test gest-data-access` PASS

## Dependencies
- GEST-INT-003 (EdgeRecognitionSignal type from shared/contracts)
- GEST-INT-004 (bridgeToken provider)

## Empresa-context impact
Nenhum — localhost bridge is per-device, not per-tenant.

## Milestone
Wave 2 / Data access

## Labels
type:feature
layer:frontend
scope:gest
priority:P2

## Risks / Notes
- Bridge address (localhost port) must be configurable for test environments; hardcoded localhost in unit tests is fine (mock transport)
- `OPEN: bridge transport auth` — mTLS vs OAuth2 decision tracked in GEST-INT-004 / BE INT-GEST-007

## Prioridade
P2
