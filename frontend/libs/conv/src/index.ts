// =============================================================================
// @neuroassist/conv — public API barrel
// Surface: patient-only (registerInAdminShell: false — CONV-FE-001).
// Imported by scope:web app.routes.ts and app.config.ts.
// scope:gest, scope:pac, scope:acs MUST NOT import from here directly
// (nx-boundary: scope:conv imports only scope:conv + scope:shared).
// =============================================================================

// Routes (scope:web app.routes.ts registers CONV_ROUTES under /paciente)
export { CONV_ROUTES } from './lib/conv.routes';

// Providers factory (scope:web app.config.ts)
export { provideConv } from './lib/conv.providers';
export type { ConvProviderConfig } from './lib/conv.providers';

// Facade (scope:web may inject directly if needed; prefer route composition)
export { ConvFacade } from './lib/conv.facade';

// Port tokens (scope:web may override adapters in app.config.ts)
export { NO_ARVORE_FALA_PORT } from './lib/api/no-arvore-fala.port';
export type { NoArvoreFalaPort } from './lib/api/no-arvore-fala.port';
export { PROPOSAL_PORT } from './lib/api/proposal.port';
export type { ProposalPort } from './lib/api/proposal.port';

// Types (scope:web composition pages)
export type { NoArvoreFala, Locale, ConteudoLocale, NoArvoreFalaTipo } from './lib/conv.types';

// Patient shell (scope:web composes INPUT_CHANNEL_PORT at /paciente level)
export { PatientShellComponent } from './lib/patient-shell/patient-shell.component';
export { PatientTopbarComponent } from './lib/patient-shell/patient-topbar.component';

// Screens (scope:web may reference for testing; loadComponent is the normal path)
export { ConvArvoreComponent } from './lib/feature-conv-arvore/conv-arvore.component';
export { ConfirmacaoTreinamentoComponent } from './lib/feature-confirmacao-treinamento/confirmacao-treinamento.component';
