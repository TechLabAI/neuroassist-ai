// =============================================================================
// core/data-access.ts — gerado por wireframe-to-angular (camada de contrato).
// Fonte: design/data-model.json (draft (BA)). NÃO editar à mão — regenerar via:
//   python main.py <projeto> --force
// =============================================================================
import { Injectable, signal, inject } from '@angular/core';
import { Comando, DispositivoEdge, Evento, Paciente, TipoGesto, Tenant, Cuidador } from '../shared/data-model';
import { pacientes, dispositivos, comandosDefault, eventos, tiposGesto, tenants, cuidadores } from '../shared/mock-data';

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

const COMANDOS_STORAGE_KEY = 'neuroassist_poc_comandos';

export abstract class ComandosPort { abstract list(): Promise<Comando[]>; }
@Injectable()
export class ComandosMockAdapter extends ComandosPort {
  async list(): Promise<Comando[]> {
    try {
      const raw = localStorage.getItem(COMANDOS_STORAGE_KEY);
      if (raw) return JSON.parse(raw) as Comando[];
    } catch { /* ignora JSON inválido e usa default */ }
    return comandosDefault;
  }
}
@Injectable({ providedIn: 'root' })
export class ComandosFacade {
  private readonly port = inject(ComandosPort);
  readonly items = signal<Comando[]>([]);
  readonly loading = signal(false);
  constructor() { void this.load(); }
  async load(): Promise<void> { this.loading.set(true); this.items.set(await this.port.list()); this.loading.set(false); }
  /** Persiste o estado atual dos comandos em localStorage (PoC — substitui escrita no BD). */
  persistir(): void {
    try { localStorage.setItem(COMANDOS_STORAGE_KEY, JSON.stringify(this.items())); } catch { /* ignora */ }
  }
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

  // `silent: true` suprime os updates em loading (uso: polling de background)
  async load(opts?: { silent?: boolean }): Promise<void> {
    if (!opts?.silent) this.loading.set(true);
    const incoming = await this.port.list();
    this.mergeIncoming(incoming);
    if (!opts?.silent) this.loading.set(false);
  }

  // Adiciona apenas eventos com IDs ainda não presentes na lista local.
  // Eventos confirmados localmente são preservados — nunca removidos pelo polling.
  private mergeIncoming(incoming: Evento[]): void {
    const current    = this.items();
    const currentIds = new Set(current.map(e => e.id));
    const novos      = incoming.filter(e => !currentIds.has(e.id));
    if (novos.length > 0) this.items.update(list => [...list, ...novos]);
  }
  // TODO[fe-autopilot]: casos de uso reais (filtros, comandos, paginação) sobre a porta
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
];
