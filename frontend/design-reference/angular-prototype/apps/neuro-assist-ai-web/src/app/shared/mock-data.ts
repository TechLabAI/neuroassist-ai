// =============================================================================
// mock-data.ts — gerado por wireframe-to-angular (camada de contrato).
// Fonte: design/data-model.json (draft (BA)). NÃO editar à mão — regenerar via:
//   python main.py <projeto> --force
// =============================================================================

// data-mock — instâncias ilustrativas; NUNCA baseline real.

import { Paciente, DispositivoEdge, Comando, Evento, TipoGesto, Tenant, Cuidador } from './data-model';

// Tenant e Cuidador existiam só como ids soltos (ex.: 'tenant-familia-amaral' em
// Paciente.tenantId, 'cuid-jamilly' em Evento.confirmadoPor) sem coleção correspondente —
// nada resolvia o id para um registro real. Adicionados para consistência entre telas.
export const tenants: Tenant[] = [
  { id: 'tenant-familia-amaral', nome: 'Família Amaral', tipo: 'familia' }
];

export const cuidadores: Cuidador[] = [
  { id: 'cuid-jamilly', tenantId: 'tenant-familia-amaral', nome: 'Jamilly Amaral', email: 'jamilly.amaral@example.com', papel: 'cuidadora' }
];

export const pacientes: Paciente[] = [
  { id: 'pac-marcio', tenantId: 'tenant-familia-amaral', nome: 'Márcio Amaral', dispositivo: 'edge-marcio-01' }
];

export const dispositivos: DispositivoEdge[] = [
  { id: 'edge-marcio-01', pacienteId: 'pac-marcio', status: 'online', ultimaSincronizacao: '2026-07-27T17:40:00', versao: '1.4.2' }
];

// Catálogo de tipos de gesto — espelha neuroassist_poc.tipo_gesto no banco.
export const tiposGesto: TipoGesto[] = [
  { id: 'tg-piscar-duplo-001',      nome: 'Piscar duas vezes' },
  { id: 'tg-piscar-esquerdo',       nome: 'Piscar olho esquerdo' },
  { id: 'tg-piscar-direito',        nome: 'Piscar olho direito' },
  { id: 'tg-sobrancelha-dupla',     nome: 'Levantar duas sobrancelhas' },
  { id: 'tg-sobrancelha-direita',   nome: 'Levantar sobrancelha direita' },
  { id: 'tg-sobrancelha-esquerda',  nome: 'Levantar sobrancelha esquerda' },
];

// Comandos padrão — cada um usa um gesto diferente para cobrir todos os tipos no PoC.
// Alterações feitas na tela de Calibração são persistidas em localStorage e têm precedência.
export const comandosDefault: Comando[] = [
  { id: 'cmd-ajuda', pacienteId: 'pac-marcio', rotulo: 'Ajuda',  urgencia: 'emergencia', primitivoPrincipal: 'tg-piscar-duplo-001',    primitivoSecundario: null, sensibilidade: 'media', tempoMinimoGestoMs: 400, tempoMaximoGestoMs: 1500 },
  { id: 'cmd-agua',  pacienteId: 'pac-marcio', rotulo: 'Água',   urgencia: 'rotina',     primitivoPrincipal: 'tg-sobrancelha-dupla',    primitivoSecundario: null, sensibilidade: 'alta',  tempoMinimoGestoMs: 400, tempoMaximoGestoMs: 1800 },
  { id: 'cmd-sapo',  pacienteId: 'pac-marcio', rotulo: 'Sapo',   urgencia: 'rotina',     primitivoPrincipal: 'tg-piscar-esquerdo',      primitivoSecundario: null, sensibilidade: 'media', tempoMinimoGestoMs: 400, tempoMaximoGestoMs: 1800 },
];

export const comandos: Comando[] = comandosDefault;

// Coleção única de eventos (não há mais "evento_ativo" separado — Evento é uma entidade
// só, como seria numa tabela real; "evento ativo" é apenas o último evento de urgência
// emergencia ainda sem confirmadoEm, filtrado no componente Alarme sobre esta mesma
// coleção). evt-009 é esse evento pendente — ao confirmá-lo na tela de Alarme, o registro
// já existe aqui e aparece no Histórico normalmente, sem precisar de sincronização manual.
export const eventos: Evento[] = [
  { id: 'evt-001', pacienteId: 'pac-marcio', comando: 'cmd-ajuda', timestamp: '2026-07-20T08:05:00', confirmadoEm: '2026-07-20T08:06:00', confirmadoPor: 'cuid-jamilly', feedback: 'real' },
  { id: 'evt-002', pacienteId: 'pac-marcio', comando: 'cmd-agua', timestamp: '2026-07-21T12:40:00', confirmadoEm: '2026-07-21T12:41:00', confirmadoPor: 'cuid-jamilly', feedback: 'real' },
  { id: 'evt-003', pacienteId: 'pac-marcio', comando: 'cmd-sapo', timestamp: '2026-07-22T19:15:00', confirmadoEm: '2026-07-22T19:17:00', confirmadoPor: 'cuid-jamilly', feedback: 'falso_positivo' },
  { id: 'evt-004', pacienteId: 'pac-marcio', comando: 'cmd-ajuda', timestamp: '2026-07-23T07:50:00', confirmadoEm: '2026-07-23T07:51:00', confirmadoPor: 'cuid-jamilly', feedback: 'real' },
  { id: 'evt-005', pacienteId: 'pac-marcio', comando: 'cmd-agua', timestamp: '2026-07-24T15:22:00', confirmadoEm: '2026-07-24T15:23:00', confirmadoPor: 'cuid-jamilly', feedback: 'real' },
  { id: 'evt-006', pacienteId: 'pac-marcio', comando: 'cmd-sapo', timestamp: '2026-07-25T09:03:00', confirmadoEm: null, confirmadoPor: '', feedback: 'falso_positivo' },
  { id: 'evt-007', pacienteId: 'pac-marcio', comando: 'cmd-agua', timestamp: '2026-07-26T20:11:00', confirmadoEm: '2026-07-26T20:12:00', confirmadoPor: 'cuid-jamilly', feedback: 'real' },
  { id: 'evt-008', pacienteId: 'pac-marcio', comando: 'cmd-ajuda', timestamp: '2026-07-27T08:12:00', confirmadoEm: '2026-07-27T08:13:00', confirmadoPor: 'cuid-jamilly', feedback: 'real' },
  { id: 'evt-009', pacienteId: 'pac-marcio', comando: 'cmd-ajuda', timestamp: '2026-07-27T14:32:00', confirmadoEm: null, confirmadoPor: '', feedback: 'nao_avaliado' }
];
