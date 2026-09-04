import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EmptySessionStateComponent } from './empty-session-state.component';

describe('EmptySessionStateComponent', () => {
  let fixture: ComponentFixture<EmptySessionStateComponent>;
  let component: EmptySessionStateComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmptySessionStateComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(EmptySessionStateComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render the empty state container', () => {
    const el = fixture.nativeElement.querySelector('[data-testid="empty-state"]') as HTMLElement;
    expect(el).toBeTruthy();
  });

  it('should emit sessionStart when the CTA button is clicked', () => {
    const spy = jest.spyOn(component.sessionStart, 'emit');
    const btn = fixture.nativeElement.querySelector('[data-testid="start-session-btn"]') as HTMLButtonElement;
    btn.click();
    expect(spy).toHaveBeenCalledTimes(1);
  });
});
