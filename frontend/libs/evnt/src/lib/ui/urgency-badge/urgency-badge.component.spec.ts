import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UrgencyBadgeComponent } from './urgency-badge.component';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';

describe('UrgencyBadgeComponent', () => {
  let fixture: ComponentFixture<UrgencyBadgeComponent>;
  let debugElement: DebugElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UrgencyBadgeComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(UrgencyBadgeComponent);
    debugElement = fixture.debugElement;
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders a span with badge class (default urgencia=rotina)', () => {
    const span = debugElement.query(By.css('span'));
    expect(span).toBeTruthy();
    expect(span.nativeElement.className).toContain('badge-');
  });

  it('displays urgency text based on urgencia value', () => {
    const span = debugElement.query(By.css('span'));
    expect(span.nativeElement.textContent).toMatch(/Emergência|Rotina/);
  });
});
