// =============================================================================
// tool-descriptors.ts — shell module registry (static fallback)
// Modules are added progressively via /fe-architect-promote of each module.
// The shell reads MODULE_DESCRIPTORS to build nav items (modules with showInNav:true)
// and to locate lazy-loaded feature modules.
// =============================================================================

export interface ModuleDescriptor {
  readonly key: string;
  readonly label: string;
  readonly icon: string;
  readonly shellRoute: string;
  readonly showInNav: boolean;
  readonly requiredPermission: string;
}

// ---------------------------------------------------------------------------
// GEST — Detecção de Gesto (Edge)
// showInNav:false — embedded feature; no sidebar entry.
// Thin host at /gest for dev/e2e only.
// ---------------------------------------------------------------------------
const GEST_DESCRIPTOR: ModuleDescriptor = {
  key: 'gest',
  label: 'Teste de Gesto',
  icon: 'gesture',
  shellRoute: '/gest',
  showInNav: false,
  requiredPermission: 'gest:live-test:execute',
};

// ---------------------------------------------------------------------------
// PAC — Pacientes e Catálogo de Comandos (Calibração)
// showInNav:true — primary caregiver surface (tune icon, /pac route).
// ADR-005: syncState banner shown in catalog for all commands (pending-sync).
// ---------------------------------------------------------------------------
const PAC_DESCRIPTOR: ModuleDescriptor = {
  key: 'pac',
  label: 'Calibração',
  icon: 'tune',
  shellRoute: '/pac',
  showInNav: true,
  requiredPermission: 'pac.comando.read',
};

// ---------------------------------------------------------------------------
// EDGE — Integração de Dispositivo Edge
// showInNav:false — composed surface (topbar chip + onboarding step-3).
// Thin host at /edge for dev/e2e; chip target at /edge/device.
// ADR-005: connectivity derived from heartbeat freshness (never raw status flag).
// ---------------------------------------------------------------------------
const EDGE_DESCRIPTOR: ModuleDescriptor = {
  key: 'edge',
  label: 'Dispositivo',
  icon: 'wifi',
  shellRoute: '/edge',
  showInNav: false,
  requiredPermission: 'edge.device.read',
};

// ---------------------------------------------------------------------------
// ACS — Cuidadores e Acesso
// showInNav:true — coordinator-gated surface (Cuidadores nav entry).
// Owns the onboarding wizard shell (scope:web /onboarding page).
// ---------------------------------------------------------------------------
const ACS_DESCRIPTOR: ModuleDescriptor = {
  key: 'acs',
  label: 'Cuidadores',
  icon: 'manage_accounts',
  shellRoute: '/acs',
  showInNav: true,
  requiredPermission: 'acs.cuidador.read',
};

// Modules added progressively via /fe-architect-promote
// (only modules with showInNav:true appear in the sidebar)
export const MODULE_DESCRIPTORS: ModuleDescriptor[] = [
  GEST_DESCRIPTOR,
  PAC_DESCRIPTOR,
  EDGE_DESCRIPTOR,
  ACS_DESCRIPTOR,
];

/** Convenience: sidebar nav items (modules that contribute a nav entry). */
export const NAV_DESCRIPTORS: ModuleDescriptor[] = MODULE_DESCRIPTORS.filter(
  (m) => m.showInNav,
);
