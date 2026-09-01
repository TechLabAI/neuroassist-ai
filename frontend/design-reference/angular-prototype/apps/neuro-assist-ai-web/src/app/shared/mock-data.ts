// =============================================================================
// mock-data.ts — gerado por wireframe-to-angular (camada de contrato).
// Fonte: design/data-model.json (draft (BA)). NÃO editar à mão — regenerar via:
//   python main.py <projeto> --force
// =============================================================================

// data-mock — instâncias ilustrativas; NUNCA baseline real.

import { Paciente, DispositivoEdge, Comando, Evento, TipoGesto, Tenant, Cuidador, NoArvoreFala, SessaoPaciente } from './data-model';

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
  { id: 'edge-marcio-01', pacienteId: 'pac-marcio', status: 'online', ultimaSincronizacao: '2026-07-27T17:40:00', versao: '1.4.2' },
  // Emenda 3 (2026-08-30) — celular do paciente como segunda "estação" (ADR-014), distinta
  // do dispositivo edge fixo acima. Mesmo padrão de identidade de dispositivo (edge.device),
  // restrito a Android/Chromium (constraint PLAT-001).
  { id: 'edge-marcio-celular', pacienteId: 'pac-marcio', status: 'online', ultimaSincronizacao: '2026-08-30T09:05:00', versao: '1.4.2' }
];

// Catálogo extensível de tipos de gesto (Emenda 2) — seed inicial, não teto.
// "Leitura labial" ilustra um tipo cadastrado pela família além dos 6 originais.
export const tiposGesto: TipoGesto[] = [
  { id: 'tg-sobrancelha', nome: 'Levantar sobrancelha' },
  { id: 'tg-piscar-duplo', nome: 'Piscar duas vezes' },
  { id: 'tg-piscar-rapido', nome: 'Piscar rápido' },
  { id: 'tg-piscar-devagar', nome: 'Piscar devagar' },
  { id: 'tg-fechar-olhos', nome: 'Fechar os olhos' },
  { id: 'tg-mover-nariz', nome: 'Mover o nariz' },
  { id: 'tg-leitura-labial', nome: 'Leitura labial' },
];

export const comandos: Comando[] = [
  { id: 'cmd-ajuda', pacienteId: 'pac-marcio', rotulo: 'Ajuda', urgencia: 'emergencia', primitivoPrincipal: 'tg-sobrancelha', primitivoSecundario: null, sensibilidade: 'media', tempoMinimoGestoMs: 400, tempoMaximoGestoMs: 1500 },
  { id: 'cmd-agua', pacienteId: 'pac-marcio', rotulo: 'água', urgencia: 'rotina', primitivoPrincipal: 'tg-sobrancelha', primitivoSecundario: 'tg-piscar-duplo', sensibilidade: 'alta', tempoMinimoGestoMs: 400, tempoMaximoGestoMs: 1800 },
  { id: 'cmd-sapo', pacienteId: 'pac-marcio', rotulo: 'Sapo', urgencia: 'rotina', primitivoPrincipal: 'tg-piscar-rapido', primitivoSecundario: 'tg-piscar-devagar', sensibilidade: 'media', tempoMinimoGestoMs: 400, tempoMaximoGestoMs: 1800 }
];

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

// =============================================================================
// Emenda 3 (2026-08-30) — módulo CONV (árvore de falas) e sessão do paciente.
// Dados ilustrativos para o protótipo, NUNCA baseline real (ADR-013/ADR-012, accepted).
// =============================================================================

// Helper só do mock — um nó "ativo" (Dupla Autorização completa) nos dois locales,
// já com áudio cacheado simulado. Representa conteúdo anterior à Emenda 4, migrado.
function conteudoAtivo(pt: string, en: string): NoArvoreFala['conteudo'] {
  return {
    pt: { texto: pt, audioRef: `audio/pt/${pt.toLowerCase().replace(/[^a-z0-9]+/g, '-')}.mp3`, confirmadoCuidadora: true, confirmadoPaciente: true },
    en: { texto: en, audioRef: `audio/en/${en.toLowerCase().replace(/[^a-z0-9]+/g, '-')}.mp3`, confirmadoCuidadora: true, confirmadoPaciente: true },
  };
}

// Árvore pequena de exemplo para Márcio Amaral: 3 categorias, 2-3 frases cada.
// engineFeatureRef nos nós-folha ilustra o mapeamento opcional gesto → nó (ADR-013 §Decisão
// complementar) — reaproveita o mesmo vocabulário de TipoGesto usado em Comando, sem tabela
// de mapeamento própria do CONV. conteudo por locale — Emenda 4 (ADR-018) — os nós seed abaixo
// já nascem "ativos" nos dois idiomas (migração ilustrativa; não é comportamento real do
// pipeline de TREINAMENTO, que sempre passa por Dupla Autorização, ver nós propostos abaixo).
export const nosArvoreFala: NoArvoreFala[] = [
  { id: 'no-cat-conforto', pacienteId: 'pac-marcio', parentId: null, tipo: 'categoria', conteudo: conteudoAtivo('Conforto', 'Comfort'), ordem: 1, engineFeatureRef: null, propostoPorTreinamento: false },
  { id: 'no-frase-agua', pacienteId: 'pac-marcio', parentId: 'no-cat-conforto', tipo: 'frase', conteudo: conteudoAtivo('Quero água', 'I want water'), ordem: 1, engineFeatureRef: 'tg-sobrancelha', propostoPorTreinamento: false },
  { id: 'no-frase-reposicionar', pacienteId: 'pac-marcio', parentId: 'no-cat-conforto', tipo: 'frase', conteudo: conteudoAtivo('Quero trocar de posição', 'I want to reposition'), ordem: 2, engineFeatureRef: null, propostoPorTreinamento: false },

  { id: 'no-cat-conversa', pacienteId: 'pac-marcio', parentId: null, tipo: 'categoria', conteudo: conteudoAtivo('Conversar', 'Talk'), ordem: 2, engineFeatureRef: null, propostoPorTreinamento: false },
  { id: 'no-frase-oi', pacienteId: 'pac-marcio', parentId: 'no-cat-conversa', tipo: 'frase', conteudo: conteudoAtivo('Oi, tudo bem?', 'Hi, how are you?'), ordem: 1, engineFeatureRef: null, propostoPorTreinamento: false },
  { id: 'no-frase-obrigado', pacienteId: 'pac-marcio', parentId: 'no-cat-conversa', tipo: 'frase', conteudo: conteudoAtivo('Obrigado', 'Thank you'), ordem: 2, engineFeatureRef: null, propostoPorTreinamento: false },
  { id: 'no-frase-cansado', pacienteId: 'pac-marcio', parentId: 'no-cat-conversa', tipo: 'frase', conteudo: conteudoAtivo('Estou cansado', "I'm tired"), ordem: 3, engineFeatureRef: null, propostoPorTreinamento: false },

  { id: 'no-cat-ajuda', pacienteId: 'pac-marcio', parentId: null, tipo: 'categoria', conteudo: conteudoAtivo('Preciso de ajuda', 'I need help'), ordem: 3, engineFeatureRef: null, propostoPorTreinamento: false },
  { id: 'no-frase-ajuda-agora', pacienteId: 'pac-marcio', parentId: 'no-cat-ajuda', tipo: 'frase', conteudo: conteudoAtivo('Preciso de ajuda agora', 'I need help now'), ordem: 1, engineFeatureRef: 'tg-sobrancelha', propostoPorTreinamento: false },
  { id: 'no-frase-nao-urgente', pacienteId: 'pac-marcio', parentId: 'no-cat-ajuda', tipo: 'frase', conteudo: conteudoAtivo('Pode ser daqui a pouco', 'It can wait a bit'), ordem: 2, engineFeatureRef: null, propostoPorTreinamento: false },

  // Propostas da fase de TREINAMENTO (Emenda 4, ADR-016) — ilustram os 3 estados de Dupla
  // Autorização usados na Tela 08/09: pendente nos dois lados, aprovado só pela cuidadora
  // (pronto para a Tela 09 do paciente), e aprovado nos dois lados mas só em PT (EN ainda
  // pendente — cobertura bilíngue parcial, ver KPI-010).
  {
    id: 'no-frase-agua-gelo', pacienteId: 'pac-marcio', parentId: 'no-cat-conforto', tipo: 'frase', ordem: 3, engineFeatureRef: null, propostoPorTreinamento: true,
    conteudo: {
      pt: { texto: 'Quero água com gelo', audioRef: null, confirmadoCuidadora: true, confirmadoPaciente: false },
      en: { texto: 'I want water with ice', audioRef: null, confirmadoCuidadora: false, confirmadoPaciente: false },
    },
  },
  {
    id: 'no-frase-chamar-danilo', pacienteId: 'pac-marcio', parentId: 'no-cat-conversa', tipo: 'frase', ordem: 4, engineFeatureRef: null, propostoPorTreinamento: true,
    conteudo: {
      pt: { texto: 'Chamar o Danilo', audioRef: null, confirmadoCuidadora: false, confirmadoPaciente: false },
      en: { texto: 'Call Danilo', audioRef: null, confirmadoCuidadora: false, confirmadoPaciente: false },
    },
  },
];

// Uma sessão ativa (celular de Márcio, provisionado por Jamilly) ilustrando o estado
// "ativa" e o rastro de auditoria — sem sessão revogada ainda, para exercitar o
// caso majoritário do protótipo (Tela 07, ActiveSessionsList).
export const sessoesPaciente: SessaoPaciente[] = [
  { id: 'sess-marcio-celular', pacienteId: 'pac-marcio', provisionadaPor: 'cuid-jamilly', dispositivoId: 'edge-marcio-celular', status: 'ativa', provisionadaEm: '2026-08-30T09:00:00', revogadaEm: null, revogadaPor: null },
];
