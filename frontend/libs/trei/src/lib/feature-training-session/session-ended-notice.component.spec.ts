import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SessionEndedNoticeComponent } from './session-ended-notice.component';

describe('SessionEndedNoticeComponent', () => {
  let fixture: ComponentFixture<SessionEndedNoticeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SessionEndedNoticeComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(SessionEndedNoticeComponent);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the session-ended testid with correct text', () => {
    const el = fixture.nativeElement.querySelector('[data-testid="session-ended-notice"]') as HTMLElement;
    expect(el).toBeTruthy();
    // INV-4: wording must communicate that proposals are retained as drafts
    expect(el.textContent).toContain('rascunhos');
  });
});
