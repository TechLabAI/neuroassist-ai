import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EventTimestampComponent, formatTimestamp } from './event-timestamp.component';
import { DebugElement } from '@angular/core';

describe('formatTimestamp', () => {
  it('produces a string starting with "às " followed by a time pattern', () => {
    // Use a fixed UTC time. The formatted hour varies by the test runner's
    // local timezone, so we verify the prefix and shape, not the exact value.
    const result = formatTimestamp('2026-07-28T14:32:00Z');
    expect(result).toMatch(/^às \d{1,2}[h:]\d{2}$/);
  });
});

describe('EventTimestampComponent', () => {
  let fixture: ComponentFixture<EventTimestampComponent>;
  let debugElement: DebugElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EventTimestampComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(EventTimestampComponent);
    debugElement = fixture.debugElement;
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders a <time> element', () => {
    const time = debugElement.nativeElement.querySelector('time');
    expect(time).toBeTruthy();
  });

  it('renders formatted timestamp with "às" prefix', () => {
    const time = debugElement.nativeElement.querySelector('time');
    const text = time?.textContent?.trim() || '';
    expect(text).toMatch(/^às/);
  });

  it('time element has datetime attribute', () => {
    const time = debugElement.nativeElement.querySelector('time');
    expect(time.getAttribute('datetime')).toBeTruthy();
  });
});
