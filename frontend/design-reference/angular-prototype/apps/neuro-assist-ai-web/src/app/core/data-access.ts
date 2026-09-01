// =============================================================================
// core/data-access.ts — gerado por wireframe-to-angular (camada de contrato).
// Fonte: design/data-model.json (draft (BA)). NÃO editar à mão — regenerar via:
//   python main.py <projeto> --force
// =============================================================================
import { Injectable, signal, inject } from '@angular/core';
import { Comando, DispositivoEdge, Evento, Paciente, TipoGesto, Tenant, Cuidador, NoArvoreFala, SessaoPaciente, Locale } from '../shared/data-model';
import { pacientes, dispositivos, comandos, eventos, tiposGesto, tenants, cuidadores, nosArvoreFala, sessoesPaciente } from '../shared/mock-data';

// Arquitetura hexagonal de frontend — para cada coleção/agregado:
//   Port (interface)  <-  MockAdapter (Estágio 0)  |  HttpAdapter (fe-autopilot do cliente)
//   Facade (camada de aplicação) — a UI injeta o Facade; NUNCA o mock nem HTTP direto.
// O fe-autopilot implementa os HttpAdapters (API do backend) + a lógica dos casos de uso.
// Ver architecture/frontend-architecture-plan.md.

export abstract class PacientesPort { abstract list(): Promise<Paciente[]>; }
@Injectable()
export class PacientesMockAdapter extends PacientesPort { async list(): Promise<Paciente[]> { return pacientes; } }
@Injectable({ providedIn: 'root' })
export class PacientesFacade {
  private readonly port = inject(PacientesPort);
  readonly items = signal<Paciente[]>([]);
  readonly loading = signal(false);
  constructor() { void this.load(); }
  async load(): Promise<void> { this.loading.set(true); this.items.set(await this.port.list()); this.loading.set(false); }
  // TODO[fe-autopilot]: casos de uso reais (filtros, comandos, paginação) sobre a porta
}

export abstract class DispositivosPort { abstract list(): Promise<DispositivoEdge[]>; }
@Injectable()
export class DispositivosMockAdapter extends DispositivosPort { async list(): Promise<DispositivoEdge[]> { return dispositivos; } }
@Injectable({ providedIn: 'root' })
export class DispositivosFacade {
  private readonly port = inject(DispositivosPort);
  readonly items = signal<DispositivoEdge[]>([]);
  readonly loading = signal(false);
  constructor() { void this.load(); }
  async load(): Promise<void> { this.loading.set(true); this.items.set(await this.port.list()); this.loading.set(false); }
  // TODO[fe-autopilot]: casos de uso reais (filtros, comandos, paginação) sobre a porta
}

export abstract class ComandosPort { abstract list(): Promise<Comando[]>; }
@Injectable()
export class ComandosMockAdapter extends ComandosPort { async list(): Promise<Comando[]> { return comandos; } }
@Injectable({ providedIn: 'root' })
export class ComandosFacade {
  private readonly port = inject(ComandosPort);
  readonly items = signal<Comando[]>([]);
  readonly loading = signal(false);
  constructor() { void this.load(); }
  async load(): Promise<void> { this.loading.set(true); this.items.set(await this.port.list()); this.loading.set(false); }
  // TODO[fe-autopilot]: casos de uso reais (filtros, comandos, paginação) sobre a porta
}

export abstract class TiposGestoPort { abstract list(): Promise<TipoGesto[]>; }
@Injectable()
export class TiposGestoMockAdapter extends TiposGestoPort { async list(): Promise<TipoGesto[]> { return tiposGesto; } }
@Injectable({ providedIn: 'root' })
export class TiposGestoFacade {
  private readonly port = inject(TiposGestoPort);
  readonly items = signal<TipoGesto[]>([]);
  readonly loading = signal(false);
  constructor() { void this.load(); }
  async load(): Promise<void> { this.loading.set(true); this.items.set(await this.port.list()); this.loading.set(false); }
  /** Cadastra um novo tipo de gesto no catálogo (ex.: "Leitura labial") e retorna o id gerado.
   * TODO[arquiteto]: cadastrar o nome aqui não implica suporte real do motor de detecção —
   * ver Framing §6.1/§9.3. */
  adicionar(nome: string): string {
    const id = `tg-${nome.trim().toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, '-') || Date.now()}`;
    this.items.update(list => [...list, { id, nome: nome.trim() }]);
    return id;
  }
  // TODO[fe-autopilot]: casos de uso reais (filtros, comandos, paginação) sobre a porta
}

export abstract class TenantsPort { abstract list(): Promise<Tenant[]>; }
@Injectable()
export class TenantsMockAdapter extends TenantsPort { async list(): Promise<Tenant[]> { return tenants; } }
@Injectable({ providedIn: 'root' })
export class TenantsFacade {
  private readonly port = inject(TenantsPort);
  readonly items = signal<Tenant[]>([]);
  readonly loading = signal(false);
  constructor() { void this.load(); }
  async load(): Promise<void> { this.loading.set(true); this.items.set(await this.port.list()); this.loading.set(false); }
  // TODO[fe-autopilot]: casos de uso reais (filtros, comandos, paginação) sobre a porta
}

export abstract class CuidadoresPort { abstract list(): Promise<Cuidador[]>; }
@Injectable()
export class CuidadoresMockAdapter extends CuidadoresPort { async list(): Promise<Cuidador[]> { return cuidadores; } }
@Injectable({ providedIn: 'root' })
export class CuidadoresFacade {
  private readonly port = inject(CuidadoresPort);
  readonly items = signal<Cuidador[]>([]);
  readonly loading = signal(false);
  constructor() { void this.load(); }
  async load(): Promise<void> { this.loading.set(true); this.items.set(await this.port.list()); this.loading.set(false); }
  // TODO[fe-autopilot]: casos de uso reais (filtros, comandos, paginação) sobre a porta
}

export abstract class EventosPort { abstract list(): Promise<Evento[]>; }
@Injectable()
export class EventosMockAdapter extends EventosPort { async list(): Promise<Evento[]> { return eventos; } }
@Injectable({ providedIn: 'root' })
export class EventosFacade {
  private readonly port = inject(EventosPort);
  readonly items = signal<Evento[]>([]);
  readonly loading = signal(false);
  constructor() { void this.load(); }
  async load(): Promise<void> { this.loading.set(true); this.items.set(await this.port.list()); this.loading.set(false); }
  // TODO[fe-autopilot]: casos de uso reais (filtros, comandos, paginação) sobre a porta
}

// =============================================================================
// Emenda 3 (2026-08-30) — módulo CONV. ADR-012/013 (accepted). No produto real, CONV é
// um módulo/serviço Nx próprio (ADR-013) — estas facades ficam neste core/data-access.ts
// só enquanto o módulo vive dentro do protótipo único; o arquiteto promove para
// `frontend/libs/conv` seguindo o mesmo padrão hexagonal (Port/MockAdapter/Facade →
// HttpAdapter) já usado pelos módulos existentes.
// =============================================================================

export abstract class NoArvoreFalaPort { abstract list(): Promise<NoArvoreFala[]>; }
@Injectable()
export class NoArvoreFalaMockAdapter extends NoArvoreFalaPort { async list(): Promise<NoArvoreFala[]> { return nosArvoreFala; } }
@Injectable({ providedIn: 'root' })
export class NoArvoreFalaFacade {
  private readonly port = inject(NoArvoreFalaPort);
  readonly items = signal<NoArvoreFala[]>([]);
  readonly loading = signal(false);
  constructor() { void this.load(); }
  async load(): Promise<void> { this.loading.set(true); this.items.set(await this.port.list()); this.loading.set(false); }

  // Emenda 4 (2026-08-31) — Fase de TREINAMENTO (ADR-016) e Dupla Autorização (ADR-018).
  // Casos de uso simulados no protótipo (sem chamada real à API Claude/Anthropic nem
  // pipeline de indexação/TTS — ver TODO[arquiteto] nos ADRs 015/016/017).

  /** Um nó/locale só está "ativo" (disponível no runtime retrieval puro do CONV, ver
   * ADR-015) quando as duas confirmações (cuidadora + paciente) estão feitas nesse locale. */
  estaAtivo(no: NoArvoreFala, locale: Locale): boolean {
    const c = no.conteudo[locale];
    return c.confirmadoCuidadora && c.confirmadoPaciente;
  }

  /** Cria uma proposta nova vinda da conversa de TREINAMENTO com a LLM (ADR-016) —
   * nasce em rascunho, sem nenhuma confirmação, sem áudio cacheado. */
  propor(pacienteId: string, parentId: string | null, textoPt: string, textoEn: string): string {
    const id = `no-treino-${Date.now()}`;
    this.items.update(list => [...list, {
      id, pacienteId, parentId, tipo: 'frase', ordem: list.length + 1, engineFeatureRef: null, propostoPorTreinamento: true,
      conteudo: {
        pt: { texto: textoPt, audioRef: null, confirmadoCuidadora: false, confirmadoPaciente: false },
        en: { texto: textoEn, audioRef: null, confirmadoCuidadora: false, confirmadoPaciente: false },
      },
    }]);
    return id;
  }

  /** Confirmação da cuidadora (Tela 08, shell DASH) — RBAC treinamento.no.aprovar.cuidadora. */
  confirmarCuidadora(id: string, locale: Locale): void {
    this.items.update(list => list.map(n =>
      n.id === id ? { ...n, conteudo: { ...n.conteudo, [locale]: { ...n.conteudo[locale], confirmadoCuidadora: true } } } : n,
    ));
  }

  /** Confirmação do paciente (Tela 09, patient-shell) — RBAC treinamento.no.aprovar.paciente.
   * Assíncrona em relação à confirmação da cuidadora (sem handoff de tela, ver ADR-016).
   * Ao completar a Dupla Autorização, dispara (simulado) indexação ES + síntese ElevenLabs
   * — no protótipo, só preenche audioRef com um caminho ilustrativo. */
  confirmarPaciente(id: string, locale: Locale): void {
    this.items.update(list => list.map(n => {
      if (n.id !== id) return n;
      const conteudoLocale = { ...n.conteudo[locale], confirmadoPaciente: true };
      if (conteudoLocale.confirmadoCuidadora) {
        conteudoLocale.audioRef = `audio/${locale}/${conteudoLocale.texto.toLowerCase().replace(/[^a-z0-9]+/g, '-')}.mp3`;
      }
      return { ...n, conteudo: { ...n.conteudo, [locale]: conteudoLocale } };
    }));
  }

  /** Recusa a proposta para este locale — nó permanece em rascunho nos demais locales. */
  recusar(id: string, locale: Locale): void {
    this.items.update(list => list.map(n =>
      n.id === id ? { ...n, conteudo: { ...n.conteudo, [locale]: { texto: n.conteudo[locale].texto, audioRef: null, confirmadoCuidadora: false, confirmadoPaciente: false } } } : n,
    ));
  }
  // TODO[fe-autopilot]: casos de uso reais (filtros, comandos, paginação) sobre a porta
}

export abstract class SessaoPacientePort { abstract list(): Promise<SessaoPaciente[]>; }
@Injectable()
export class SessaoPacienteMockAdapter extends SessaoPacientePort { async list(): Promise<SessaoPaciente[]> { return sessoesPaciente; } }
@Injectable({ providedIn: 'root' })
export class SessaoPacienteFacade {
  private readonly port = inject(SessaoPacientePort);
  readonly items = signal<SessaoPaciente[]>([]);
  readonly loading = signal(false);
  constructor() { void this.load(); }
  async load(): Promise<void> { this.loading.set(true); this.items.set(await this.port.list()); this.loading.set(false); }
  /** Provisiona uma nova sessão de paciente (ADR-012, Opção C — sem expiração, revogável).
   * TODO[arquiteto]: persistência real via o ator "Paciente com sessão" em acs-service. */
  provisionar(pacienteId: string, dispositivoId: string, provisionadaPor: string): void {
    const id = `sess-${pacienteId}-${Date.now()}`;
    this.items.update(list => [...list, {
      id, pacienteId, dispositivoId, provisionadaPor,
      status: 'ativa', provisionadaEm: new Date().toISOString(), revogadaEm: null, revogadaPor: null,
    }]);
  }
  /** Revoga uma sessão ativa (constraint SEC-001 — mecanismo obrigatório, reaproveita
   * blacklist de JWT já existente no projeto real, ver rbac/session-policy.md). */
  revogar(id: string, revogadaPor: string): void {
    this.items.update(list => list.map(s =>
      s.id === id ? { ...s, status: 'revogada' as const, revogadaEm: new Date().toISOString(), revogadaPor } : s,
    ));
  }
  // TODO[fe-autopilot]: casos de uso reais (filtros, paginação) sobre a porta
}

/** Providers Port->MockAdapter (Estágio 0). O fe-autopilot substitui por HttpAdapters. */
export const DATA_PROVIDERS = [
  { provide: PacientesPort, useClass: PacientesMockAdapter },
  { provide: DispositivosPort, useClass: DispositivosMockAdapter },
  { provide: ComandosPort, useClass: ComandosMockAdapter },
  { provide: TiposGestoPort, useClass: TiposGestoMockAdapter },
  { provide: TenantsPort, useClass: TenantsMockAdapter },
  { provide: CuidadoresPort, useClass: CuidadoresMockAdapter },
  { provide: EventosPort, useClass: EventosMockAdapter },
  { provide: NoArvoreFalaPort, useClass: NoArvoreFalaMockAdapter },
  { provide: SessaoPacientePort, useClass: SessaoPacienteMockAdapter },
];
