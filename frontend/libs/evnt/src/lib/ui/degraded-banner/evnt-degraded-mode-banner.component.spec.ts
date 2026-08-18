import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EvntDegradedModeBannerComponent } from './evnt-degraded-mode-banner.component';

describe('EvntDegradedModeBannerComponent', () => {
  let fixture: ComponentFixture<EvntDegradedModeBannerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EvntDegradedModeBannerComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(EvntDegradedModeBannerComponent);
    fixture.componentInstance.pollIntervalSec = 15;
    fixture.detectChanges();
  });

  it('displays the poll interval in seconds with degraded message', () => {
    const el: HTMLElement = fixture.nativeElement;

    const banner = el.querySelector('[data-testid="degraded-banner"]');
    expect(banner).toBeTruthy();
    expect(banner?.textContent).toContain('Modo degradado');
    expect(banner?.textContent).toContain('15s');
    expect(banner?.getAttribute('role')).toBe('status');
    expect(banner?.getAttribute('aria-live')).toBe('polite');
  });

  it('updates displayed interval when input changes', () => {
    // Use setInput to trigger OnPush change detection for @Input() property changes.
    fixture.componentRef.setInput('pollIntervalSec', 30);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const banner = el.querySelector('[data-testid="degraded-banner"]');
    expect(banner?.textContent).toContain('30s');
  });
});
