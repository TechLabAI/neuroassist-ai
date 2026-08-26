/**
 * proxy.conf.js — Angular/Nx dev-server proxy for neuroassist-ai-web.
 *
 * Routes API traffic to the Spring Cloud Gateway so the Angular dev-server
 * acts as a same-origin proxy. CORS is handled at the server level; preflight
 * requests are resolved automatically by the proxy.
 *
 * Port parametrization:
 *   GWY_PORT environment variable (default: 18080).
 *   Changed once already (8080 → 18080) — never hard-code.
 *
 * Prefixes (hypothesis (a), ADR-008 / E2E-014):
 *   /api/v1/**  → all 7 microservices via GWY (uniform surface)
 *   /oauth2/**  → ACS token / refresh / jwks (permitAll in GWY SecurityConfig)
 *
 * Usage:
 *   nx serve neuroassist-ai-web -c integrated
 *   GWY_PORT=9090 nx serve neuroassist-ai-web -c integrated
 */

const GWY_PORT = process.env['GWY_PORT'] ?? '18080';
const GWY_TARGET = `http://localhost:${GWY_PORT}`;

module.exports = {
  '/api/v1': {
    target: GWY_TARGET,
    secure: false,
    changeOrigin: true,
    logLevel: 'warn',
  },
  '/oauth2': {
    target: GWY_TARGET,
    secure: false,
    changeOrigin: true,
    logLevel: 'warn',
  },
};
