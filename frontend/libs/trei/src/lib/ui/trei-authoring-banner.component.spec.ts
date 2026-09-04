import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TreiAuthoringBannerComponent } from './trei-authoring-banner.component';

describe('TreiAuthoringBannerComponent', () => {
  let fixture: ComponentFixture<TreiAuthoringBannerComponent>;
  let nativeEl: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TreiAuthoringBannerComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(TreiAuthoringBannerComponent);
    fixture.detectChanges();
    nativeEl = fixture.nativeElement as HTMLElement;
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the authoring-banner test id', () => {
    const banner = nativeEl.querySelector('[data-testid="authoring-banner"]');
    expect(banner).not.toBeNull();
  });

  it('should have correct aria-label for accessibility', () => {
    const banner = nativeEl.querySelector('[data-testid="authoring-banner"]');
    expect(banner?.getAttribute('aria-label')).toContain('não é o app do paciente');
  });

  it('should contain the authoring label text', () => {
    expect(nativeEl.textContent).toContain('Treinamento');
    expect(nativeEl.textContent).toContain('não é o app do paciente');
  });
});
