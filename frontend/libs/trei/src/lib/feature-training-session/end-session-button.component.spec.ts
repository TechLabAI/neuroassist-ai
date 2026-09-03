import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EndSessionButtonComponent } from './end-session-button.component';

describe('EndSessionButtonComponent', () => {
  let fixture: ComponentFixture<EndSessionButtonComponent>;
  let component: EndSessionButtonComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EndSessionButtonComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(EndSessionButtonComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should emit end event when button is clicked', () => {
    const spy = jest.spyOn(component.end, 'emit');
    const btn = fixture.nativeElement.querySelector('[data-testid="end-session-btn"]') as HTMLButtonElement;
    btn.click();
    expect(spy).toHaveBeenCalledTimes(1);
  });
});
