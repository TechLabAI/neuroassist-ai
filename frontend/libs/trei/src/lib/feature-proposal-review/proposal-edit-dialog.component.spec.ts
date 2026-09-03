import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SimpleChange } from '@angular/core';
import { ProposalEditDialogComponent } from './proposal-edit-dialog.component';

// Helper: properly trigger @Input() + ngOnChanges in TestBed
// (setting component.prop directly doesn't trigger ngOnChanges in Ivy tests)
function setInput(
  component: ProposalEditDialogComponent,
  key: keyof ProposalEditDialogComponent,
  value: unknown,
  firstChange = true,
): void {
  const prev = firstChange ? undefined : component[key];
  (component as Record<string, unknown>)[key as string] = value;
  component.ngOnChanges({
    [key]: new SimpleChange(prev, value, firstChange),
  });
}

describe('ProposalEditDialogComponent', () => {
  let fixture: ComponentFixture<ProposalEditDialogComponent>;
  let component: ProposalEditDialogComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProposalEditDialogComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ProposalEditDialogComponent);
    component = fixture.componentInstance;
    // Do NOT call detectChanges here; each test is responsible for setup.
  });

  it('should create', () => {
    setInput(component, 'draftText', '');
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should not render dialog when isOpen is false', () => {
    setInput(component, 'draftText', 'Test');
    component.isOpen = false;
    fixture.detectChanges();
    const dialog = fixture.nativeElement.querySelector('[data-testid="proposal-edit-dialog"]') as HTMLElement;
    expect(dialog).toBeNull();
  });

  it('should render dialog when isOpen is true', () => {
    setInput(component, 'draftText', 'Test');
    component.isOpen = true;
    fixture.detectChanges();
    const dialog = fixture.nativeElement.querySelector('[data-testid="proposal-edit-dialog"]') as HTMLElement;
    expect(dialog).toBeTruthy();
  });

  it('should initialize textarea with draftText', () => {
    setInput(component, 'draftText', 'Quero água');
    component.isOpen = true;
    fixture.detectChanges();
    const textarea = fixture.nativeElement.querySelector('[data-testid="edit-textarea"]') as HTMLTextAreaElement;
    expect(textarea.value).toBe('Quero água');
  });

  it('should emit save with trimmed text when Save is clicked', () => {
    setInput(component, 'draftText', 'Quero água');
    component.isOpen = true;
    fixture.detectChanges();

    const saveEvents: string[] = [];
    component.save.subscribe((t: string) => saveEvents.push(t));

    const btn = fixture.nativeElement.querySelector('[data-testid="save-btn"]') as HTMLButtonElement;
    btn.click();

    expect(saveEvents).toHaveLength(1);
    expect(saveEvents[0]).toBe('Quero água');
  });

  it('should emit dismiss when Cancel is clicked', () => {
    setInput(component, 'draftText', 'Original');
    component.isOpen = true;
    fixture.detectChanges();

    const dismissed = jest.fn();
    component.dismiss.subscribe(dismissed);

    const cancelBtn = fixture.nativeElement.querySelector('[data-testid="cancel-btn"]') as HTMLButtonElement;
    cancelBtn.click();

    expect(dismissed).toHaveBeenCalledTimes(1);
  });

  it('should emit dismiss when backdrop is clicked', () => {
    setInput(component, 'draftText', 'Original');
    component.isOpen = true;
    fixture.detectChanges();

    const dismissed = jest.fn();
    component.dismiss.subscribe(dismissed);

    const backdrop = fixture.nativeElement.querySelector('[data-testid="dialog-backdrop"]') as HTMLElement;
    backdrop.click();

    expect(dismissed).toHaveBeenCalledTimes(1);
  });

  it('should disable Save button when text is empty', () => {
    setInput(component, 'draftText', '');
    component.isOpen = true;
    fixture.detectChanges();

    const saveBtn = fixture.nativeElement.querySelector('[data-testid="save-btn"]') as HTMLButtonElement;
    expect(saveBtn.disabled).toBe(true);
  });

  it('should reset editing text when draftText input changes', () => {
    setInput(component, 'draftText', 'Original');
    component.isOpen = true;
    fixture.detectChanges();

    // Simulate a parent updating the draftText input
    setInput(component, 'draftText', 'Novo texto', false);
    fixture.detectChanges();

    const textarea = fixture.nativeElement.querySelector('[data-testid="edit-textarea"]') as HTMLTextAreaElement;
    expect(textarea.value).toBe('Novo texto');
  });
});
