import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EvntFeedSkeletonComponent } from './evnt-feed-skeleton.component';

describe('EvntFeedSkeletonComponent', () => {
  let fixture: ComponentFixture<EvntFeedSkeletonComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EvntFeedSkeletonComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(EvntFeedSkeletonComponent);
    fixture.detectChanges();
  });

  it('renders 3 skeleton rows with aria-busy', () => {
    const el: HTMLElement = fixture.nativeElement;

    const container = el.querySelector('[aria-busy="true"]');
    expect(container).toBeTruthy();
    expect(container?.getAttribute('aria-label')).toBe('Carregando notificações');

    const rows = el.querySelectorAll('.skeleton-row');
    expect(rows.length).toBe(3);
  });
});
