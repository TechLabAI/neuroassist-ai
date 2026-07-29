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
