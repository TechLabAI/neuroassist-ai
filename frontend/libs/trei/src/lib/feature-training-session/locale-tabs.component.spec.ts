import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocaleTabsComponent } from './locale-tabs.component';

describe('LocaleTabsComponent', () => {
  let fixture: ComponentFixture<LocaleTabsComponent>;
  let component: LocaleTabsComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LocaleTabsComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(LocaleTabsComponent);
    component = fixture.componentInstance;
    component.locales = ['pt', 'en'];
    component.activeLocale = 'pt';
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render one tab button per locale', () => {
    const tabs = fixture.nativeElement.querySelectorAll('[role="tab"]');
    expect(tabs.length).toBe(2);
  });

  it('should mark the active locale tab with aria-selected=true', () => {
    const ptTab = fixture.nativeElement.querySelector('[data-testid="locale-tab-pt"]') as HTMLButtonElement;
    expect(ptTab.getAttribute('aria-selected')).toBe('true');

    const enTab = fixture.nativeElement.querySelector('[data-testid="locale-tab-en"]') as HTMLButtonElement;
    expect(enTab.getAttribute('aria-selected')).toBe('false');
  });

  it('should emit localeChange when a tab is clicked', () => {
    const spy = jest.spyOn(component.localeChange, 'emit');
    const enTab = fixture.nativeElement.querySelector('[data-testid="locale-tab-en"]') as HTMLButtonElement;
    enTab.click();
    expect(spy).toHaveBeenCalledWith('en');
  });
});
