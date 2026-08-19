import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { HistoryTimelineComponent } from './history-timeline.component';
import { HistoryFacade } from '../hist.facade';
import { HistoryScreenVm, HistoryItemVm, ItemMarking } from '../hist.types';

// =============================================================================
// HistoryTimelineComponent spec — P2 (FE-HIST-002)
// Tests rendering and user interactions using a signal-based mock facade.
// All facade dependencies replaced with writable signal stubs.
// =============================================================================

// ---------------------------------------------------------------------------
// Mock facade factory — writable signal handles exposed as _underscore fields.
// ---------------------------------------------------------------------------

function createMockFacade() {
  const _screenVm = signal<HistoryScreenVm>({
    items: [],
    period: 'semana',
    status: 'idle',
    hasMore: false,
    error: null,
  });
  const _markings = signal<Map<string, ItemMarking>>(new Map());

  return {
    screenVm: _screenVm.asReadonly(),
    markings: _markings.asReadonly(),
    load: jest.fn().mockResolvedValue(undefined),
    setPeriod: jest.fn().mockResolvedValue(undefined),
    loadMore: jest.fn().mockResolvedValue(undefined),
    retry: jest.fn().mockResolvedValue(undefined),
    markFeedback: jest.fn().mockResolvedValue(undefined),
    // Writable handles for test mutations
    _screenVm,
    _markings,
  };
}

// ---------------------------------------------------------------------------
// Seed data helpers
// ---------------------------------------------------------------------------

function makeItem(id: string, verdict: 'real' | 'falso_positivo' | null = null): HistoryItemVm {
  return {
    id,
    factType: 'evento.confirmado',
    label: `Evento ${id}`,
    urgency: 'rotina',
    occurredAtLabel: '14h00',
    recordedAtIso: new Date().toISOString(),
    confirmationLine: `Confirmado por Test User às 14h00`,
    verdict,
    correctsRecordId: null,
  };
}

// ---------------------------------------------------------------------------
// Suite
// ---------------------------------------------------------------------------

describe('HistoryTimelineComponent', () => {
  let fixture: ComponentFixture<HistoryTimelineComponent>;
  let mockFacade: ReturnType<typeof createMockFacade>;

  beforeEach(async () => {
    mockFacade = createMockFacade();

    await TestBed.configureTestingModule({
      imports: [HistoryTimelineComponent],
      providers: [
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        { provide: HistoryFacade, useValue: mockFacade as any },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(HistoryTimelineComponent);
    fixture.detectChanges();
  });

  afterEach(() => TestBed.resetTestingModule());

  // -------------------------------------------------------------------------
  // Rendering: heading
  // -------------------------------------------------------------------------

  it('renders the "Histórico" heading', () => {
    const h1: HTMLElement | null = fixture.nativeElement.querySelector('h1');
    expect(h1).toBeTruthy();
    expect(h1?.textContent?.trim()).toBe('Histórico');
  });

  // -------------------------------------------------------------------------
  // Rendering: period tabs
  // -------------------------------------------------------------------------

  it('renders 3 period tabs (Hoje, Semana, Mês)', () => {
    const tabs: NodeListOf<HTMLButtonElement> = fixture.nativeElement.querySelectorAll('[role="tab"]');
    expect(tabs.length).toBe(3);

    const labels = Array.from(tabs).map((t) => t.textContent?.trim());
    expect(labels).toContain('Hoje');
    expect(labels).toContain('Semana');
    expect(labels).toContain('Mês');
  });

  // -------------------------------------------------------------------------
  // Rendering: skeleton when loading + no items
  // -------------------------------------------------------------------------

  it('shows loading skeleton when status is loading and items is empty', () => {
    mockFacade._screenVm.set({
      items: [],
      period: 'semana',
      status: 'loading',
      hasMore: false,
      error: null,
    });
    fixture.detectChanges();

    const skeleton: HTMLElement | null = fixture.nativeElement.querySelector('[aria-busy="true"]');
    expect(skeleton).toBeTruthy();
    expect(skeleton?.getAttribute('aria-label')).toBe('Carregando histórico');
  });

  // -------------------------------------------------------------------------
  // Rendering: error state with retry button
  // -------------------------------------------------------------------------

  it('shows error alert and "Tentar novamente" button when status is error', () => {
    mockFacade._screenVm.set({
      items: [],
      period: 'semana',
      status: 'error',
      hasMore: false,
      error: { code: 'HTTP_500', message: 'Server error' },
    });
    fixture.detectChanges();

    const alert: HTMLElement | null = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert).toBeTruthy();

    const retryBtn = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((b) => b.textContent?.trim() === 'Tentar novamente');
    expect(retryBtn).toBeTruthy();
  });

  // -------------------------------------------------------------------------
  // Rendering: empty state
  // -------------------------------------------------------------------------

  it('shows empty state message when status is empty', () => {
    mockFacade._screenVm.set({
      items: [],
      period: 'semana',
      status: 'empty',
      hasMore: false,
      error: null,
    });
    fixture.detectChanges();

    const text: string = fixture.nativeElement.textContent ?? '';
    expect(text).toContain('Nenhum evento neste período');
  });

  // -------------------------------------------------------------------------
  // Rendering: items list when loaded
  // -------------------------------------------------------------------------

  it('shows item labels when status is loaded and items are present', () => {
    mockFacade._screenVm.set({
      items: [makeItem('001'), makeItem('002')],
      period: 'semana',
      status: 'loaded',
      hasMore: false,
      error: null,
    });
    fixture.detectChanges();

    const content: string = fixture.nativeElement.textContent ?? '';
    expect(content).toContain('Evento 001');
    expect(content).toContain('Evento 002');
  });

  // -------------------------------------------------------------------------
  // Interaction: retry button calls facade.retry()
  // -------------------------------------------------------------------------

  it('clicking "Tentar novamente" calls facade.retry()', () => {
    mockFacade._screenVm.set({
      items: [],
      period: 'semana',
      status: 'error',
      hasMore: false,
      error: { code: 'UNKNOWN', message: 'Error' },
    });
    fixture.detectChanges();

    const retryBtn = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((b) => b.textContent?.trim() === 'Tentar novamente');

    expect(retryBtn).toBeTruthy();
    retryBtn?.click();

    expect(mockFacade.retry).toHaveBeenCalledTimes(1);
  });

  // -------------------------------------------------------------------------
  // Interaction: period tab click calls facade.setPeriod()
  // -------------------------------------------------------------------------

  it('clicking "Hoje" tab calls facade.setPeriod("hoje")', () => {
    fixture.detectChanges();

    const tabs: NodeListOf<HTMLButtonElement> = fixture.nativeElement.querySelectorAll('[role="tab"]');
    // periodos order: ['hoje', 'semana', 'mes'] — first tab is 'Hoje'
    const hojeTab = Array.from(tabs).find((t) => t.textContent?.trim() === 'Hoje');
    expect(hojeTab).toBeTruthy();

    hojeTab?.click();

    expect(mockFacade.setPeriod).toHaveBeenCalledWith('hoje');
  });
});
