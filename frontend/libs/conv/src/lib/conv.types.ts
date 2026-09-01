// =============================================================================
// conv.types.ts — domain types for the CONV module.
// Promoted from: design-reference/.../shared/data-model.ts (Emenda 3/4).
// Kept in libs/conv/src/lib/conv.types.ts so scope:conv owns its own type
// surface; types shared with scope:web (e.g. locale) are re-exported from here.
// =============================================================================

export type NoArvoreFalaTipo = 'categoria' | 'frase';
export type Locale = 'pt' | 'en';

/** Content of a speech-tree node in one locale — text + cached TTS ref +
 *  Dual-Authorization flags (ADR-016/ADR-018).
 *  A locale content is "active" (searchable, playable) only when both
 *  confirmadoCuidadora AND confirmadoPaciente are true (ADR-015). */
export interface ConteudoLocale {
  /** Phrase text in this locale. */
  texto: string;
  /** Path to pre-synthesized ElevenLabs audio (ADR-017).
   *  null = not yet synthesized — fallback to Web Speech API (OPS-004). */
  audioRef: string | null;
  /** Caregiver approval — RBAC: treinamento.no.aprovar.cuidadora */
  confirmadoCuidadora: boolean;
  /** Patient approval — RBAC: treinamento.no.aprovar.paciente */
  confirmadoPaciente: boolean;
}

/** Speech-tree node — category or phrase (ADR-013).
 *  parentId null = root category.
 *  conteudo is per-locale (Emenda 4, ADR-018). */
export interface NoArvoreFala {
  id: string;
  pacienteId: string;
  parentId: string | null;
  tipo: NoArvoreFalaTipo;
  /** Locale-keyed content map. */
  conteudo: Record<Locale, ConteudoLocale>;
  /** Display order within the parent level. */
  ordem: number;
  /** Optional gesture vocabulary reference (ADR-013 §Decisão complementar).
   *  Reuses the TipoGesto id vocabulary from GEST/PAC — no separate mapping table. */
  engineFeatureRef: string | null;
  /** true if this node originated from the TREINAMENTO LLM phase (ADR-016).
   *  false for seed content pre-Emenda 4. */
  propostoPorTreinamento: boolean;
}
