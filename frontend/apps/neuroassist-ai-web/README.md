# neuroassist-ai-web

Angular shell application for the NeuroAssist AI platform.

## Development profiles

The app supports two profiles. Profile selection is build-time (Nx configuration),
not runtime — no environment variables are read inside the browser bundle.

### `mock` (default)

All 7 module adapters use in-memory mock data. No backend or Docker required.
This is the default for `nx serve` and is the profile used in PR CI checks.

```bash
# Start (mock — default)
npx nx serve neuroassist-ai-web

# Build (mock)
npx nx build neuroassist-ai-web
```

The 44 Playwright E2E tests (edge-e2e, gest-e2e, pac-e2e) run hermetically in
this profile and must never require a backend to pass.

### `integrated`

All 7 module adapters are switched to HTTP and talk to the Spring Cloud Gateway
via the Angular dev-server proxy (`proxy.conf.js`).

**Requires:** the full backend stack running on the host.

```bash
# Start the backend stack first
docker compose -f docker/docker-compose.dev.yml up -d

# Start the app in integrated mode
npx nx serve neuroassist-ai-web -c integrated

# Override GWY port (default: 18080)
GWY_PORT=9090 npx nx serve neuroassist-ai-web -c integrated
```

#### Proxy config

`proxy.conf.js` routes two prefixes to the Spring Cloud Gateway:

| Prefix | Destination | Covers |
|---|---|---|
| `/api/v1/**` | `http://localhost:${GWY_PORT:-18080}` | all 7 microservices (ADR-008) |
| `/oauth2/**` | `http://localhost:${GWY_PORT:-18080}` | ACS token / refresh / jwks |

The port is read from `GWY_PORT` environment variable (default `18080`).
It changed from `8080` once already — never hard-code it.

#### Adapter mode token

`APP_ADAPTER_MODE` (`InjectionToken<'mock' | 'http'>`) is injectable anywhere in the DI
tree and reflects the build-time mode. In the `integrated` configuration,
`adapter-mode-env.ts` is replaced with `adapter-mode-env.integrated.ts` via
Nx `fileReplacements`, which sets `CURRENT_ADAPTER_MODE = 'http'`.

#### GEST base URL

In `integrated` mode, `GEST_SERVICE_BASE_URL` is overridden to `/api/v1/gest`
so `eligibility` and other calls route through the GWY proxy rather than
directly to port `8084`. `EDGE_BRIDGE_WS_URL` is NOT overridden — the local
edge-agent WebSocket bridge is always localhost-only and never goes through GWY.

## Nx commands

```bash
# Run unit tests (all libs)
npx nx run-many --target=test --all

# Lint (all libs)
npx nx run-many --target=lint --all

# E2E (mock profile — no backend required)
npx nx e2e edge-e2e
npx nx e2e gest-e2e
npx nx e2e pac-e2e
```

## Architecture references

- `docs/architecture/frontend/plans/plan-FE-E2E.md` — E2E integration plan
- `docs/architecture/frontend/standards/nx-boundaries.md` — Nx tags and boundaries
- `docs/architecture/shared/blueprint-core.md` — App + modules + shared mental model
- `backend/services/neuroassist-ai-api-gateway/src/main/resources/application.yml` — GWY routes
