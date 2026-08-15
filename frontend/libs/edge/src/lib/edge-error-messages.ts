// =============================================================================
// edge-error-messages.ts — EDGE pt-BR error copy table (INT-FE-EDGE-006)
// Source: SPEC-07 §1 + api-contract.md §8 ProblemDetail.type values
//
// Registered at module bootstrap (edgeBootstrap.init) when SharedErrorModelService
// becomes available. For now exposed as a typed constant for error interceptor use.
//
// Map key: ProblemDetail.type value from BE api-contract §8
// Map value: EdgeErrorConfig (message + surface + kind) — null means suppress dialog
// =============================================================================

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type EdgeErrorKind =
  | 'validation'
  | 'unauthorized'
  | 'forbidden'
  | 'notFound'
  | 'conflict'
  | 'transient';

export type EdgeErrorSurface = 'banner' | 'toast' | 'modal' | 'inline';

export interface EdgeErrorConfig {
  /** Category for routing to the appropriate error handler (SPEC-07 §1). */
  readonly kind: EdgeErrorKind;
  /** User-facing pt-BR copy (copy-frozen — matches SPEC-07 §1 table exactly). */
  readonly message: string;
  /** Presentation surface (banner | toast | modal | inline). */
  readonly surface: EdgeErrorSurface;
  /**
   * When true, the error MUST NOT surface as a dialog.
   * Used for /errors/patient-not-paired which maps to derivedState='not-paired' (SPEC-07 §2).
   */
  readonly suppressDialog: boolean;
}

// ---------------------------------------------------------------------------
// EDGE_ERROR_MESSAGES — 12 entries matching SPEC-07 §1 table
// null value = suppress all dialog display (patient-not-paired → chip not-paired state)
// ---------------------------------------------------------------------------

export const EDGE_ERROR_MESSAGES: ReadonlyMap<string, EdgeErrorConfig | null> = new Map<
  string,
  EdgeErrorConfig | null
>([
  // 1. Validation error (400)
  [
    '/errors/validation',
    {
      kind: 'validation',
      message: 'Verifique os dados do dispositivo antes de continuar.',
      surface: 'inline',
      suppressDialog: false,
    },
  ],

  // 2. Invalid/expired enrollment token (401)
  [
    '/errors/enrollment-invalid',
    {
      kind: 'unauthorized',
      message: 'Código de pareamento inválido ou expirado. Gere um novo código.',
      surface: 'banner',
      suppressDialog: false,
    },
  ],

  // 3. Missing management permission (403)
  [
    '/errors/insufficient-scope',
    {
      kind: 'forbidden',
      message: 'Você não tem permissão para gerenciar dispositivos.',
      surface: 'toast',
      suppressDialog: false,
    },
  ],

  // 4. Session identity mismatch (403)
  [
    '/errors/identity-mismatch',
    {
      kind: 'forbidden',
      message: 'Sessão inválida para esta ação. Entre novamente.',
      surface: 'modal',
      suppressDialog: false,
    },
  ],

  // 5. Device not found for this empresa (404)
  [
    '/errors/device-not-found',
    {
      kind: 'notFound',
      message: 'Dispositivo não encontrado para esta empresa.',
      surface: 'banner',
      suppressDialog: false,
    },
  ],

  // 6. Patient has no paired device (404) — SUPPRESSED: chip shows not-paired, no dialog (SPEC-07 §2)
  ['/errors/patient-not-paired', null],

  // 7. Device already activated with another code (409)
  [
    '/errors/already-enrolled',
    {
      kind: 'conflict',
      message: 'Este dispositivo já foi ativado. Use outro código.',
      surface: 'banner',
      suppressDialog: false,
    },
  ],

  // 8. Patient already has an active device (409)
  [
    '/errors/pairing-conflict',
    {
      kind: 'conflict',
      message:
        'Este paciente já tem um dispositivo ativo. Despareie o atual antes de parear um novo.',
      surface: 'banner',
      suppressDialog: false,
    },
  ],

  // 9. Device not in pairable state (409) — refresh read-model
  [
    '/errors/illegal-pairing-transition',
    {
      kind: 'conflict',
      message: 'O dispositivo não está pareado. Recarregando o estado atual.',
      surface: 'toast',
      suppressDialog: false,
    },
  ],

  // 10. Device retired — cannot be used (409) — SPEC-07 amendment (FE-EDGE-027 clause)
  [
    '/errors/device-retired',
    {
      kind: 'conflict',
      message: 'Este dispositivo foi aposentado e não pode ser usado. Registre um novo.',
      surface: 'banner',
      suppressDialog: false,
    },
  ],

  // 11. Optimistic-lock conflict — stale write prevented (409)
  [
    '/errors/optimistic-lock',
    {
      kind: 'conflict',
      message: 'O estado do dispositivo mudou. Recarregando…',
      surface: 'toast',
      suppressDialog: false,
    },
  ],

  // 12. Transient network / 5xx / timeout on an action — auto-retry with backoff (SPEC-07 §3)
  [
    '_transient',
    {
      kind: 'transient',
      message: 'Falha temporária de conexão. Tentando novamente…',
      surface: 'toast',
      suppressDialog: false,
    },
  ],
]);

/**
 * Resolve an error config from a ProblemDetail.type value.
 * Returns null for suppressed types (patient-not-paired → not-paired state).
 * Returns the transient config for any unknown / 5xx type.
 */
export function resolveEdgeError(problemType: string): EdgeErrorConfig | null {
  if (EDGE_ERROR_MESSAGES.has(problemType)) {
    return EDGE_ERROR_MESSAGES.get(problemType) ?? null;
  }
  // Unknown type → treat as transient (5xx / timeout / network)
  return EDGE_ERROR_MESSAGES.get('_transient') ?? null;
}
