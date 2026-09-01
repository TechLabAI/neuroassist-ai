// =============================================================================
// data-model.ts — gerado por wireframe-to-angular (camada de contrato).
// Fonte: design/data-model.json (draft (BA)). NÃO editar à mão — regenerar via:
//   python main.py <projeto> --force
// =============================================================================


export type TenantTipo = 'familia' | 'clinica';
export type CuidadorPapel = 'cuidadora' | 'coordenador_clinica';
export type ComandoUrgencia = 'emergencia' | 'rotina';
export type ComandoSensibilidade = 'baixa' | 'media' | 'alta';
export type EventoFeedback = 'nao_avaliado' | 'real' | 'falso_positivo';
export type DispositivoEdgeStatus = 'online' | 'offline';

/** Unidade de cuidado — família ou clínica (multi-tenant, schema-per-empresa) */
export interface Tenant {
  id: string;  // string
  nome: string;  // string
  tipo: TenantTipo;  // enum
}

/** Pessoa monitorada pelo sistema */
export interface Paciente {
  id: string;  // string
  tenantId: string;  // ref → Tenant
  nome: string;  // string
  dispositivo: string;  // ref → DispositivoEdge
}

/** Pessoa com acesso ao painel */
export interface Cuidador {
  id: string;  // string
  tenantId: string;  // ref → Tenant
  nome: string;  // string
  email: string;  // string
  papel: CuidadorPapel;  // enum
}

/** Catálogo extensível de tipos de movimento facial (primitivos) — Emenda 2, 2026-07-27.
 * Usuário pode cadastrar novos tipos (ex.: "Leitura labial") direto na Tela 04.
 * Cadastrar o nome aqui NÃO implica suporte real do motor de detecção — ver
 * TODO[arquiteto] no Framing §6.1/§9.3. */
export interface TipoGesto {
  id: string;  // string
  nome: string;  // string
}

/** Item do catálogo de comandos do paciente — gesto (simples ou composto) mapeado a uma necessidade (LBC Emenda 1, 2026-07-27) */
export interface Comando {
  id: string;  // string
  pacienteId: string;  // ref → Paciente
  rotulo: string;  // string
  urgencia: ComandoUrgencia;  // enum
  primitivoPrincipal: string;  // ref → TipoGesto
  primitivoSecundario: string | null;  // ref → TipoGesto
  sensibilidade: ComandoSensibilidade;  // enum
  tempoMinimoGestoMs: number;  // int
  tempoMaximoGestoMs: number;  // int
}

/** Ocorrência estruturada gerada pelo edge */
export interface Evento {
  id: string;  // string
  pacienteId: string;  // ref → Paciente
  comando: string;  // ref → Comando
  timestamp: string;  // datetime
  confirmadoEm: string | null;  // datetime
  confirmadoPor: string;  // ref → Cuidador
  feedback: EventoFeedback;  // enum
}

/** Agente local que roda a detecção de gesto */
export interface DispositivoEdge {
  id: string;  // string
  pacienteId: string;  // ref → Paciente
  status: DispositivoEdgeStatus;  // enum
  ultimaSincronizacao: string;  // datetime
  versao: string;  // string
}

// =============================================================================
// Emenda 3 (2026-08-30) — módulo CONV. ADR-012/013 (accepted). Adicionado à mão
// nesta emenda; a próxima regeneração via `python main.py <projeto> --force`
// deve reproduzir estes tipos a partir de design/data-model.json (já atualizado).
// =============================================================================

export type NoArvoreFalaTipo = 'categoria' | 'frase';
export type SessaoPacienteStatus = 'ativa' | 'revogada';

// =============================================================================
// Emenda 4 (2026-08-31) — Elasticsearch/retrieval puro (ADR-015), fase de TREINAMENTO
// com LLM colaborativa (ADR-016), TTS ElevenLabs cacheado (ADR-017), bilinguismo
// EN/PT com autoria independente por idioma (ADR-018). Adicionado à mão nesta emenda.
// =============================================================================

export type Locale = 'pt' | 'en';

/** Conteúdo de um NoArvoreFala em um idioma específico — texto + áudio cacheado +
 * Dupla Autorização (ADR-016/ADR-018). Só quando confirmadoCuidadora E confirmadoPaciente
 * ficam true neste locale, o conteúdo é indexado (Elasticsearch) e sintetizado (ElevenLabs). */
export interface ConteudoLocale {
  texto: string;  // string
  audioRef: string | null;  // referência ao áudio cacheado (não o binário) — null se ainda não sintetizado
  confirmadoCuidadora: boolean;  // RBAC: treinamento.no.aprovar.cuidadora
  confirmadoPaciente: boolean;  // RBAC: treinamento.no.aprovar.paciente
}

/** Nó da árvore de falas — categoria ou frase (ADR-013). parentId nulo = raiz.
 * conteudo é por Locale (Emenda 4, ADR-018) — texto/áudio deixaram de ser campo único. */
export interface NoArvoreFala {
  id: string;  // string
  pacienteId: string;  // ref → Paciente
  parentId: string | null;  // ref → NoArvoreFala
  tipo: NoArvoreFalaTipo;  // enum
  conteudo: Record<Locale, ConteudoLocale>;  // Emenda 4 — antes era `texto: string`
  ordem: number;  // int
  engineFeatureRef: string | null;  // vocabulário unificado de gesto (commit 89f4ee5 do repo real)
  propostoPorTreinamento: boolean;  // Emenda 4 — true se originado da LLM na fase de TREINAMENTO (ADR-016), false se seed anterior à Emenda 4
}

/** Sessão de app persistente do paciente, provisionada pela cuidadora (ADR-012, Opção C). */
export interface SessaoPaciente {
  id: string;  // string
  pacienteId: string;  // ref → Paciente
  provisionadaPor: string;  // ref → Cuidador
  dispositivoId: string;  // ref → DispositivoEdge
  status: SessaoPacienteStatus;  // enum
  provisionadaEm: string;  // datetime
  revogadaEm: string | null;  // datetime
  revogadaPor: string | null;  // ref → Cuidador
}
