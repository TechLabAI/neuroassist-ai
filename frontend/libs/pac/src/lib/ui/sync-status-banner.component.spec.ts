import { TestBed, ComponentFixture } from '@angular/core/testing';
import { SyncStatusBannerComponent, SyncStateBanner } from './sync-status-banner.component';

// =============================================================================
// SyncStatusBannerComponent — unit tests (PAC-FE-020 / P7 UX Polish)
// Uses createComponent(SyncStatusBannerComponent) + setInput() — the pattern
// that works in this ts-jest JIT environment for @Input() decorator inputs.
//
// ADR-005 acceptance criteria:
//   - syncState='pending' → .sync-status-banner element present with text
//     "pendente de sincronização"
//   - syncState='synced'  → banner element absent from DOM
//   - EARS orphan: "Saved comando edit not acknowledged stays pendente de
//     sincronização not ativo no dispositivo"
// =============================================================================

describe('SyncStatusBannerComponent', () => {
  let fixture: ComponentFixture<SyncStatusBannerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SyncStatusBannerComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(SyncStatusBannerComponent);
    fixture.detectChanges();
  });

  afterEach(() => TestBed.resetTestingModule());

  // -------------------------------------------------------------------------
  // ADR-005: honest-state — pending
  // -------------------------------------------------------------------------

  describe('syncState === pending', () => {
    beforeEach(() => {
      fixture.componentRef.setInput('syncState', 'pending' as SyncStateBanner);
      fixture.detectChanges();
    });

    it('deve renderizar o elemento .sync-status-banner no DOM', () => {
      const banner: HTMLElement = fixture.nativeElement.querySelector('.sync-status-banner');
      expect(banner).toBeTruthy();
    });

    it('deve exibir o texto "pendente de sincronização"', () => {
      const banner: HTMLElement = fixture.nativeElement.querySelector('.sync-status-banner');
      expect(banner.textContent).toContain('pendente de sincronização');
    });

    it('deve exibir informação sobre o dispositivo edge', () => {
      const banner: HTMLElement = fixture.nativeElement.querySelector('.sync-status-banner');
      expect(banner.textContent).toContain('dispositivo edge');
    });

    // EARS orphan: "Saved comando edit not acknowledged stays pendente de
    // sincronização not ativo no dispositivo"
    it('não deve exibir o texto "ativo no dispositivo" quando pending', () => {
      const banner: HTMLElement = fixture.nativeElement.querySelector('.sync-status-banner');
      expect(banner.textContent).not.toContain('ativo no dispositivo');
    });
  });

  // -------------------------------------------------------------------------
  // ADR-005: honest-state — synced (no banner)
  // -------------------------------------------------------------------------

  describe('syncState === synced', () => {
    beforeEach(() => {
      fixture.componentRef.setInput('syncState', 'synced' as SyncStateBanner);
      fixture.detectChanges();
    });

    it('não deve renderizar o banner no DOM quando synced', () => {
      const banner: HTMLElement | null = fixture.nativeElement.querySelector('.sync-status-banner');
      expect(banner).toBeNull();
    });

    it('deve renderizar template vazio (nenhum conteúdo visível)', () => {
      const text = (fixture.nativeElement.textContent as string).trim();
      expect(text).toBe('');
    });
  });

  // -------------------------------------------------------------------------
  // Transição de estado reativa
  // -------------------------------------------------------------------------

  describe('transição de estado reativa', () => {
    it('deve mostrar banner ao mudar de synced para pending', () => {
      fixture.componentRef.setInput('syncState', 'synced' as SyncStateBanner);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.sync-status-banner')).toBeNull();

      fixture.componentRef.setInput('syncState', 'pending' as SyncStateBanner);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.sync-status-banner')).toBeTruthy();
    });

    it('deve ocultar banner ao mudar de pending para synced', () => {
      fixture.componentRef.setInput('syncState', 'pending' as SyncStateBanner);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.sync-status-banner')).toBeTruthy();

      fixture.componentRef.setInput('syncState', 'synced' as SyncStateBanner);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.sync-status-banner')).toBeNull();
    });
  });
});
