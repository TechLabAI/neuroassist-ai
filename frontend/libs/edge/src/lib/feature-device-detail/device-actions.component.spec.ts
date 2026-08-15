// =============================================================================
// device-actions.component.spec.ts — FE-EDGE-013 DoD
//
// Covers:
//   - Actions container is HIDDEN (not rendered) when canManage is false
//   - Actions container is visible when canManage is true
//   - Emits `unpairAction` when the Despareador button is clicked
//   - Emits `retireAction` when the Aposentar button is clicked
//   - "Reparear" link present when canManage is true
// =============================================================================

import { TestBed, ComponentFixture } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { DeviceActionsComponent } from './device-actions.component';

describe('DeviceActionsComponent', () => {
  let fixture: ComponentFixture<DeviceActionsComponent>;
  let component: DeviceActionsComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DeviceActionsComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(DeviceActionsComponent);
    component = fixture.componentInstance;
  });

  // ---------------------------------------------------------------------------
  // Visibility — DoD P3: hidden (not disabled) when canManage is false
  // ---------------------------------------------------------------------------

  it('does NOT render the actions container when canManage is false', () => {
    fixture.componentRef.setInput('canManage', false);
    fixture.detectChanges();

    const container = fixture.nativeElement.querySelector('[data-testid="device-actions-container"]');
    expect(container).toBeNull();
  });

  it('renders the actions container when canManage is true', () => {
    fixture.componentRef.setInput('canManage', true);
    fixture.detectChanges();

    const container = fixture.nativeElement.querySelector('[data-testid="device-actions-container"]');
    expect(container).not.toBeNull();
  });

  it('buttons are absent from the DOM when canManage is false (not merely disabled)', () => {
    fixture.componentRef.setInput('canManage', false);
    fixture.detectChanges();

    const buttons = fixture.nativeElement.querySelectorAll('button');
    expect(buttons.length).toBe(0);
  });

  // ---------------------------------------------------------------------------
  // Outputs
  // ---------------------------------------------------------------------------

  describe('when canManage is true', () => {
    beforeEach(() => {
      fixture.componentRef.setInput('canManage', true);
      fixture.detectChanges();
    });

    it('emits unpairAction when the Despareador button is clicked', () => {
      const unpairSpy = jest.fn();
      component.unpairAction.subscribe(unpairSpy);

      const buttons = fixture.debugElement.queryAll(By.css('button'));
      // Despareador is the first button
      const unpairBtn = buttons.find((b) =>
        (b.nativeElement as HTMLElement).textContent?.includes('Despareador'),
      );
      expect(unpairBtn).toBeTruthy();
      unpairBtn!.nativeElement.click();
      fixture.detectChanges();

      expect(unpairSpy).toHaveBeenCalledTimes(1);
    });

    it('emits retireAction when the Aposentar button is clicked', () => {
      const retireSpy = jest.fn();
      component.retireAction.subscribe(retireSpy);

      const buttons = fixture.debugElement.queryAll(By.css('button'));
      const retireBtn = buttons.find((b) =>
        (b.nativeElement as HTMLElement).textContent?.includes('Aposentar'),
      );
      expect(retireBtn).toBeTruthy();
      retireBtn!.nativeElement.click();
      fixture.detectChanges();

      expect(retireSpy).toHaveBeenCalledTimes(1);
    });

    it('renders a "Reparear" navigation link', () => {
      const link = fixture.nativeElement.querySelector('a[routerLink]');
      expect(link).not.toBeNull();
      expect((link as HTMLElement).textContent).toContain('Reparear');
    });
  });
});
