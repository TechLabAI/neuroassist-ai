import { TestBed, ComponentFixture } from '@angular/core/testing';
import { CommandRowComponent } from './command-row.component';
import { UrgencyBadgeComponent, UrgenciaType } from './urgency-badge.component';
import { SensitivitySelectorComponent, SensibilidadeType } from './sensitivity-selector.component';
import { SyncStatusBadgeComponent, SyncStateDisplay } from './sync-status-badge.component';
import { ComandoRowVM } from '../pac-comando.facade';

// =============================================================================
// PAC-FE-019 — UI components spec
// Covers: UrgencyBadgeComponent, SensitivitySelectorComponent,
//         SyncStatusBadgeComponent, CommandRowComponent
//
// Test strategy: createComponent(<Child>) + fixture.componentRef.setInput()
// This matches the pattern used by evnt/EventListComponent — @Input() decorator
// inputs support setInput(); the ts-jest JIT environment does NOT support
// setInput() for input() signal-based inputs (known limitation).
//
// Acceptance criteria:
//   - SensitivitySelectorComponent with sensibilidade='baixa' renders 'Baixa',
//     not any numeric value.
//   - SyncStatusBadgeComponent with syncState='pending' renders
//     'pendente de sincronização'.
//   - UrgencyBadgeComponent with urgencia='emergencia' has class urgency--emergencia.
//   - CommandRowComponent emits @Output edit on edit button click.
// =============================================================================

// ---------------------------------------------------------------------------
// Helper: default ComandoRowVM
// ---------------------------------------------------------------------------

function buildRowVM(overrides?: Partial<ComandoRowVM>): ComandoRowVM {
  return {
    id: 'c1',
    rotulo: 'Preciso de ajuda',
    urgencia: 'rotina',
    primitivoPrincipalNome: 'Piscar olhos',
    primitivoSecundarioNome: null,
    primitivoPrincipalId: 'tg-piscar',
    primitivoSecundarioId: null,
    tempoMinimoGestoMs: 400,
    tempoMaximoGestoMs: 1500,
    sensibilidade: 'media',
    estado: 'drafted',
    syncState: 'pending-sync',
    testedState: 'not-tested',
    etag: 'etag-v1',
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// UrgencyBadgeComponent
// ---------------------------------------------------------------------------

describe('UrgencyBadgeComponent', () => {
  let fixture: ComponentFixture<UrgencyBadgeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UrgencyBadgeComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(UrgencyBadgeComponent);
    fixture.detectChanges();
  });

  afterEach(() => TestBed.resetTestingModule());

  it('deve renderizar classe urgency--emergencia para urgencia emergencia', () => {
    fixture.componentRef.setInput('urgencia', 'emergencia' as UrgenciaType);
    fixture.detectChanges();
    const span: HTMLElement = fixture.nativeElement.querySelector('.urgency--emergencia');
    expect(span).toBeTruthy();
  });

  it('deve renderizar classe urgency--rotina para urgencia rotina', () => {
    fixture.componentRef.setInput('urgencia', 'rotina' as UrgenciaType);
    fixture.detectChanges();
    const span: HTMLElement = fixture.nativeElement.querySelector('.urgency--rotina');
    expect(span).toBeTruthy();
  });

  it('deve exibir texto "Emergência" para urgencia emergencia', () => {
    fixture.componentRef.setInput('urgencia', 'emergencia' as UrgenciaType);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Emergência');
  });

  it('deve exibir texto "Rotina" para urgencia rotina', () => {
    fixture.componentRef.setInput('urgencia', 'rotina' as UrgenciaType);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Rotina');
  });
});

// ---------------------------------------------------------------------------
// SensitivitySelectorComponent
// ---------------------------------------------------------------------------

describe('SensitivitySelectorComponent', () => {
  let fixture: ComponentFixture<SensitivitySelectorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SensitivitySelectorComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(SensitivitySelectorComponent);
    fixture.componentRef.setInput('readonly', true);
    fixture.detectChanges();
  });

  afterEach(() => TestBed.resetTestingModule());

  it('deve renderizar label "Baixa" para sensibilidade baixa (sem valor numérico)', () => {
    fixture.componentRef.setInput('sensibilidade', 'baixa' as SensibilidadeType);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text.trim()).toBe('Baixa');
    expect(text).not.toMatch(/\d/);
  });

  it('deve renderizar label "Média" para sensibilidade media', () => {
    fixture.componentRef.setInput('sensibilidade', 'media' as SensibilidadeType);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent.trim()).toBe('Média');
  });

  it('deve renderizar label "Alta" para sensibilidade alta', () => {
    fixture.componentRef.setInput('sensibilidade', 'alta' as SensibilidadeType);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent.trim()).toBe('Alta');
  });

  it('deve emitir change ao clicar em opção quando readonly=false', () => {
    fixture.componentRef.setInput('readonly', false);
    fixture.componentRef.setInput('sensibilidade', 'media' as SensibilidadeType);
    fixture.detectChanges();

    const emitted: SensibilidadeType[] = [];
    fixture.componentInstance.selectionChange.subscribe((v: SensibilidadeType) => emitted.push(v));

    const buttons = fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>;
    // buttons in order: Baixa (0), Média (1), Alta (2)
    buttons[0].click();
    fixture.detectChanges();

    expect(emitted).toContain('baixa');
  });

  it('não deve renderizar botões quando readonly=true (modo display)', () => {
    fixture.componentRef.setInput('readonly', true);
    fixture.detectChanges();
    const buttons = fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>;
    expect(buttons.length).toBe(0);
  });
});

// ---------------------------------------------------------------------------
// SyncStatusBadgeComponent
// ---------------------------------------------------------------------------

describe('SyncStatusBadgeComponent', () => {
  let fixture: ComponentFixture<SyncStatusBadgeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SyncStatusBadgeComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(SyncStatusBadgeComponent);
    fixture.detectChanges();
  });

  afterEach(() => TestBed.resetTestingModule());

  it('deve renderizar "pendente de sincronização" quando syncState é pending', () => {
    fixture.componentRef.setInput('syncState', 'pending' as SyncStateDisplay);
    fixture.detectChanges();
    const span: HTMLElement = fixture.nativeElement.querySelector('.sync-status-badge--pending');
    expect(span).toBeTruthy();
    expect(span.textContent).toContain('pendente de sincronização');
  });

  it('deve renderizar "ativo no dispositivo" quando syncState é synced', () => {
    fixture.componentRef.setInput('syncState', 'synced' as SyncStateDisplay);
    fixture.detectChanges();
    const span: HTMLElement = fixture.nativeElement.querySelector('.sync-status-badge--synced');
    expect(span).toBeTruthy();
    expect(span.textContent).toContain('ativo no dispositivo');
  });

  it('não deve mostrar badge synced quando syncState é pending', () => {
    fixture.componentRef.setInput('syncState', 'pending' as SyncStateDisplay);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.sync-status-badge--synced')).toBeNull();
  });

  it('não deve mostrar badge pending quando syncState é synced', () => {
    fixture.componentRef.setInput('syncState', 'synced' as SyncStateDisplay);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.sync-status-badge--pending')).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// CommandRowComponent
// ---------------------------------------------------------------------------

describe('CommandRowComponent', () => {
  let fixture: ComponentFixture<CommandRowComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CommandRowComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(CommandRowComponent);
    fixture.componentRef.setInput('row', buildRowVM());
    fixture.detectChanges();
  });

  afterEach(() => TestBed.resetTestingModule());

  it('deve renderizar o rótulo do comando', () => {
    fixture.componentRef.setInput('row', buildRowVM({ rotulo: 'Água por favor' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Água por favor');
  });

  it('deve emitir output edit ao clicar no botão editar', () => {
    const row = buildRowVM();
    fixture.componentRef.setInput('row', row);
    fixture.detectChanges();

    const emitted: ComandoRowVM[] = [];
    fixture.componentInstance.edit.subscribe((r: ComandoRowVM) => emitted.push(r));

    const editBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.pac-command-row__btn-edit');
    expect(editBtn).toBeTruthy();
    editBtn.click();
    fixture.detectChanges();

    expect(emitted.length).toBe(1);
    expect(emitted[0].id).toBe(row.id);
  });

  it('deve emitir output retire ao clicar no botão aposentar', () => {
    const row = buildRowVM();
    fixture.componentRef.setInput('row', row);
    fixture.detectChanges();

    const emitted: ComandoRowVM[] = [];
    fixture.componentInstance.retire.subscribe((r: ComandoRowVM) => emitted.push(r));

    const retireBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.pac-command-row__btn-retire');
    expect(retireBtn).toBeTruthy();
    retireBtn.click();
    fixture.detectChanges();

    expect(emitted.length).toBe(1);
    expect(emitted[0].id).toBe(row.id);
  });

  it('deve mostrar botão recalibrate apenas para comandos publicados', () => {
    fixture.componentRef.setInput('row', buildRowVM({ estado: 'published' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.pac-command-row__btn-recalibrate')).toBeTruthy();
  });

  it('não deve mostrar botão recalibrate para comandos rascunho', () => {
    fixture.componentRef.setInput('row', buildRowVM({ estado: 'drafted' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.pac-command-row__btn-recalibrate')).toBeNull();
  });

  it('deve emitir output recalibrate ao clicar no botão recalibrar (publicado)', () => {
    const row = buildRowVM({ estado: 'published' });
    fixture.componentRef.setInput('row', row);
    fixture.detectChanges();

    const emitted: ComandoRowVM[] = [];
    fixture.componentInstance.recalibrate.subscribe((r: ComandoRowVM) => emitted.push(r));

    const recalibrateBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.pac-command-row__btn-recalibrate');
    recalibrateBtn.click();
    fixture.detectChanges();

    expect(emitted.length).toBe(1);
    expect(emitted[0].id).toBe(row.id);
  });

  it('deve mapear syncState pending-sync para badge pending', () => {
    fixture.componentRef.setInput('row', buildRowVM({ syncState: 'pending-sync' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.sync-status-badge--pending')).toBeTruthy();
  });

  it('deve mapear syncState active-on-device para badge synced', () => {
    fixture.componentRef.setInput('row', buildRowVM({ syncState: 'active-on-device' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.sync-status-badge--synced')).toBeTruthy();
  });

  it('deve renderizar descrição do gesto com secundário quando presente', () => {
    fixture.componentRef.setInput('row', buildRowVM({
      primitivoPrincipalNome: 'Piscar olhos',
      primitivoSecundarioNome: 'Mover cabeça',
    }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Piscar olhos + Mover cabeça');
  });

  it('deve renderizar apenas gesto principal quando sem gesto secundário', () => {
    fixture.componentRef.setInput('row', buildRowVM({
      primitivoPrincipalNome: 'Piscar olhos',
      primitivoSecundarioNome: null,
    }));
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Piscar olhos');
    expect(text).not.toContain(' + ');
  });
});
