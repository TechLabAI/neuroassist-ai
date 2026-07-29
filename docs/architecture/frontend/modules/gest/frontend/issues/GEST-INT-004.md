# GEST-INT-004 -- Bridge transport auth — bridgeToken provider (JWT-device provisional; mTLS/OAuth2 decision OPEN)

**Status:** ABERTA — ITEM DE ALTA PRIORIDADE (handoff M3-fe + M4-fe)
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens
**GitHub:** [#TBD]

## Outcome
`libs/gest/data-access/src/lib/bridge-token.provider.ts` implements the provisional JWT-device token provider for the localhost bridge channel. The provider is isolated so the final mTLS vs. OAuth2 `client_credentials` decision (joint with EDGE team) can be swapped without touching `GestureTestFacade`.

## Context / Problem statement
- The bridge handshake/transport auth is an **open architectural item** tracked since M2 (BE `event-contracts.md §3.3`, `api-contract.md §6.3`) and carried through M3, M3-fe, and M4.
- FE assumes a JWT-shaped device token provisionally. The isolation behind `bridgeToken` provider means the final mTLS/OAuth2 decision does not ripple into the facade.
- Source: SPEC-04 §2 (client note); cross-link.md §4.
- Coordinated with: INT-GEST-007 (BE bridge auth issue).

## Approach (execution path)
1. Create `bridge-token.provider.ts` with an `InjectionToken<BridgeTokenProvider>` and a provisional implementation that returns a JWT-device token from `shared/auth-session`.
2. `EdgeDetectionClient` injects `BridgeTokenProvider` (not the concrete impl) — the final impl can be swapped via DI.
3. Mark file with `// TODO[bridge-auth]: provisional JWT-device — replace when BE INT-GEST-007 is resolved`.
4. Document the open item in a code comment referencing `event-contracts.md §3.3` and the joint decision criteria (mTLS: needs device cert infra; OAuth2 client_credentials: needs EDGE service client ID).

## Non-goals
- Not resolving the mTLS vs. OAuth2 decision — that is a joint decision with the EDGE team.
- Not responsible for `shared/auth-session` implementation.

## Layer(s)
frontend / integration (data-access)

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-04-data-access.md` (§2)
- `docs/architecture/frontend/modules/gest/frontend/contracts/backend-api-cross-link.md` (§4)

## Architecture alignment
- InjectionToken pattern for DI swap
- Tag Nx: `scope:gest`, `type:data-access`

## Implementation checklist (by layer/file)

**Data-access**
- [ ] `libs/gest/data-access/src/lib/bridge-token.provider.ts`

**Tests**
- [ ] `npx nx test gest-data-access` PASS (provisional provider returns a token; swap test with alternate impl)
- [ ] `npx nx lint gest-data-access` PASS
- [ ] `npx nx build gest-data-access` PASS

## Acceptance criteria
- `EdgeDetectionClient` uses `BridgeTokenProvider` injection token (not concrete class)
- Provisional implementation returns a JWT-device token
- Code comment documents the open decision and references INT-GEST-007
- `npx nx build gest-data-access` PASS

## Dependencies
- GEST-FE-007 (EdgeDetectionClient injects this provider)

## Empresa-context impact
Nenhum — device token is per-device, not per-tenant.

## Milestone
Wave 4 / Hub integrations

## Labels
type:integration
layer:frontend
scope:gest
priority:P4

## Risks / Notes
- **HIGH PRIORITY OPEN ITEM** — mTLS requires device certificate infrastructure that may not exist in MVP; OAuth2 client_credentials is the simpler fallback. Escalate to EDGE team before scaffold phase.
- This issue CANNOT be fully closed until INT-GEST-007 (BE) is resolved. Mark as "implemented provisional" when DI scaffold is done; mark "closed" only when the final auth mechanism is decided and tested.

## Prioridade
P4
