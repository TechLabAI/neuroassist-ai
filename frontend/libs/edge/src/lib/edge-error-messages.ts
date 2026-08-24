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

  // 3b. Negativa genérica do Spring Security (403).
  //
  // O edge-service tem DOIS caminhos para "sem permissão": o
  // `accessDeniedHandler` do SecurityConfig responde
  // `…/errors/edge.insufficient-scope`, mas um `AuthorizationDeniedException`
  // levantado por `@PreAuthorize` cai no GlobalExceptionHandler e sai como
  // `/errors/forbidden` — tipo que esta tabela não conhecia. Sem esta entrada,
  // o 403 mais comum do serviço caía no ramo `_transient` e era anunciado à
  // cuidadora como "falha temporária de conexão": um erro de autorização
  // disfarçado de instabilidade de rede, que convida a tentar de novo para
  // sempre.
  [
    '/errors/forbidden',
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

// ---------------------------------------------------------------------------
// Normalização do ProblemDetail.type
// ---------------------------------------------------------------------------

/**
 * Reduz um `ProblemDetail.type` do edge-service à chave bare desta tabela.
 *
 * <p>As chaves acima foram escritas a partir do api-contract.md (§8), que
 * documenta a forma bare `/errors/device-retired`. O serviço em execução emite
 * outra coisa: `ProblemDetailFactory` monta
 * `https://neuroassist-ai.com/errors/edge.device-retired` — URI absoluta E com
 * prefixo `edge.`. Só `/errors/optimistic-lock` e `/errors/forbidden`, escritos
 * à mão no `GlobalExceptionHandler`, saem na forma bare.
 *
 * <p>Enquanto esta função não existia, NENHUM erro do produtor real casava com a
 * tabela: todos caíam no ramo `_transient` e a tela oferecia "tentando
 * novamente" para um 409 que jamais vai passar. A divergência é do contrato
 * (doc × código do BE) e está relatada; aqui aceitamos as duas formas em vez de
 * escolher a que nos convém — é o consumidor que tem de espelhar o produtor.
 */
export function normalizeEdgeProblemType(problemType: string): string {
  let caminho = problemType.trim();
  // URI absoluta → só o caminho.
  const marcador = caminho.indexOf('/errors/');
  if (marcador >= 0) caminho = caminho.slice(marcador);
  // `/errors/edge.device-retired` → `/errors/device-retired`
  return caminho.replace('/errors/edge.', '/errors/');
}

/**
 * Resolve an error config from a ProblemDetail.type value.
 * Returns null for suppressed types (patient-not-paired → not-paired state).
 * Returns the transient config for any unknown / 5xx type.
 */
export function resolveEdgeError(problemType: string): EdgeErrorConfig | null {
  const chave = normalizeEdgeProblemType(problemType);
  if (EDGE_ERROR_MESSAGES.has(chave)) {
    return EDGE_ERROR_MESSAGES.get(chave) ?? null;
  }
  // Unknown type → treat as transient (5xx / timeout / network)
  return EDGE_ERROR_MESSAGES.get('_transient') ?? null;
}

// ---------------------------------------------------------------------------
// Ponte entre o erro cru do HttpClient e a tabela de cópia
// ---------------------------------------------------------------------------

/** Forma mínima de um HttpErrorResponse — sem importar @angular/common/http aqui. */
interface ErroHttpParcial {
  readonly status?: number;
  readonly error?: unknown;
}

/** Extrai o `type` do corpo ProblemDetail, quando houver. */
function tipoDoProblema(err: unknown): string | null {
  const corpo = (err as ErroHttpParcial | null)?.error;
  if (corpo && typeof corpo === 'object' && typeof (corpo as { type?: unknown }).type === 'string') {
    return (corpo as { type: string }).type;
  }
  return null;
}

/**
 * Config de erro EDGE a partir do erro cru devolvido pelo HttpClient.
 * Sem corpo ProblemDetail (queda de rede, HTML de proxy, timeout) → transiente.
 */
export function edgeErrorConfigFromHttp(err: unknown): EdgeErrorConfig | null {
  const tipo = tipoDoProblema(err);
  if (tipo === null) return EDGE_ERROR_MESSAGES.get('_transient') ?? null;
  return resolveEdgeError(tipo);
}

/**
 * Mensagem pt-BR pronta para a tela, a partir do erro cru do HttpClient.
 *
 * <p>Existe porque as facades faziam `String(err)`, e `String(HttpErrorResponse)`
 * é literalmente `"[object Object]"` — foi isso que a página de dispositivo
 * mostrou à cuidadora durante toda a vida do caminho HTTP. Nunca devolver o erro
 * cru: além de ilegível, um corpo de erro pode carregar identificadores que não
 * têm o que fazer na tela.
 */
export function edgeErrorMessage(err: unknown, fallbackTransiente?: string): string {
  const config = edgeErrorConfigFromHttp(err);

  // Sem ProblemDetail (rede caída, timeout, HTML de proxy) a tabela devolve a
  // cópia `_transient`, que diz "tentando novamente…". Quem chama depois de
  // esgotar as tentativas passa aqui a sua própria frase de desfecho: anunciar
  // uma retentativa que não vai acontecer é outra forma de mentir para a tela.
  if (fallbackTransiente && config?.kind === 'transient') return fallbackTransiente;

  return (
    config?.message ??
    EDGE_ERROR_MESSAGES.get('_transient')?.message ??
    'Falha ao comunicar com o serviço de dispositivos.'
  );
}

/** Erro de conflito (409) — a tela deve recarregar o read-model, não repetir a escrita. */
export function isEdgeConflict(err: unknown): boolean {
  return edgeErrorConfigFromHttp(err)?.kind === 'conflict';
}

/**
 * A falha merece nova tentativa automática?
 *
 * <p>Só falhas transientes: rede caída (status 0), timeout (408), throttling
 * (429) e 5xx. Repetir um 400/401/403/404/409 três vezes multiplica por três uma
 * requisição que já se sabe condenada — e no caso do 409 de despareamento
 * chegava a tentar três escritas sobre um estado que o servidor acabou de dizer
 * que mudou.
 */
export function isEdgeTransient(err: unknown): boolean {
  const status = (err as ErroHttpParcial | null)?.status ?? 0;
  return status === 0 || status === 408 || status === 429 || status >= 500;
}

/**
 * Este erro deve aparecer como AVISO passageiro?
 *
 * <p>Quem decide é a tabela do SPEC-07, que já declara a `surface` de cada erro
 * — e não o ponto de chamada. Sem esta pergunta, as facades mostravam aviso para
 * TUDO, e um 400 de validação aparecia duas vezes: no banner ao lado do campo,
 * que é o lugar dele, e num aviso no canto da tela. Repetir a mesma frase em
 * dois lugares faz a pessoa procurar dois problemas.
 *
 * <p>Erro transiente é caso à parte: a superfície dele é aviso E ele merece
 * "Tentar novamente", porque repetir pode de fato adiantar.
 */
export function deveAvisarPorToast(err: unknown): boolean {
  return edgeErrorConfigFromHttp(err)?.surface === 'toast';
}
