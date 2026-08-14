import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmationTrailComponent } from './confirmation-trail.component';

describe('ConfirmationTrailComponent', () => {
  let fixture: ComponentFixture<ConfirmationTrailComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConfirmationTrailComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ConfirmationTrailComponent);
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders nothing when confirmadoPor is null (default — no confirmation yet)', () => {
    // Default inputs: confirmadoPor=null, confirmadoAt=null
    const trail = fixture.nativeElement.querySelector('[data-testid="confirmation-trail"]');
    expect(trail).toBeNull();
  });
});
