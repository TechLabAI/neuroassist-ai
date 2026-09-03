import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { TreiRootComponent } from './trei-root.component';

// =============================================================================
// TreiRootComponent — unit tests (FE-TREI-002 / P1 policy)
// Minimal smoke: component renders, router-outlet is present.
// =============================================================================

describe('TreiRootComponent', () => {
  let fixture: ComponentFixture<TreiRootComponent>;
  let compiled: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TreiRootComponent, RouterModule.forRoot([])],
    }).compileComponents();

    fixture = TestBed.createComponent(TreiRootComponent);
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;
  });

  it('should create the component', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render a router-outlet element', () => {
    const outlet = compiled.querySelector('router-outlet');
    expect(outlet).not.toBeNull();
  });

  // P6 (TREI-FE-021): TreiAuthoringBannerComponent was extracted to TreiRootComponent
  // so that it renders on every TREI child route (training session + proposal review)
  // without being duplicated in individual page components (INV-6).
  it('should render the authoring banner (data-testid="authoring-banner")', () => {
    const banner = compiled.querySelector('[data-testid="authoring-banner"]');
    expect(banner).not.toBeNull();
    expect(banner?.textContent).toContain('não é o app do paciente');
  });
});
