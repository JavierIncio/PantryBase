import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { Shell } from './shell';

describe('Shell', () => {
  beforeEach(async () => {
    // jsdom does not implement matchMedia; stub it for the CDK BreakpointObserver.
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: (query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => undefined,
        removeListener: () => undefined,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
        dispatchEvent: () => false,
      }),
    });

    await TestBed.configureTestingModule({
      imports: [Shell],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('renders the brand in the toolbar and all main navigation entries', () => {
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();

    const toolbar = fixture.nativeElement.querySelector('mat-toolbar') as HTMLElement;
    expect(toolbar.textContent).toContain('PantryBase');

    const navList = fixture.nativeElement.querySelector('mat-nav-list') as HTMLElement;
    const links = navList.querySelectorAll('a');
    expect(links.length).toBe(5);
    for (const label of ['Pantry', 'Recipes', 'Cooking', 'Social', 'Profile']) {
      expect(navList.textContent).toContain(label);
    }
  });
});
