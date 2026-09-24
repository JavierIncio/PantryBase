import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Shell } from './shell';
import { AuthService } from '../auth/auth.service';
import { SessionState } from '../auth/session.state';
import { UserResponse } from '../auth/auth.models';

const USER: UserResponse = {
  id: 1,
  username: 'ada',
  email: 'ada@example.com',
  firstName: null,
  lastName: null,
  roles: ['USER'],
};

describe('Shell', () => {
  let authStub: { logout: ReturnType<typeof vi.fn> };
  let session: SessionState;

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

    authStub = { logout: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [Shell],
      providers: [
        { provide: AuthService, useValue: authStub },
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();

    session = TestBed.inject(SessionState);
  });

  /** Opens the user menu in the toolbar and returns the rendered menu items. */
  const openUserMenu = async (fixture: ComponentFixture<Shell>): Promise<HTMLElement[]> => {
    (fixture.nativeElement.querySelector('.shell-user-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    return Array.from(document.querySelectorAll('.mat-mdc-menu-item')) as HTMLElement[];
  };

  const menuItemByText = (items: HTMLElement[], text: string) =>
    items.find((item) => item.textContent?.includes(text));

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

  it('shows the signed-in username in the toolbar user area', () => {
    session.restore(USER);
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();

    const toolbar = fixture.nativeElement.querySelector('mat-toolbar') as HTMLElement;
    expect(toolbar.textContent).toContain('ada');
  });

  it('falls back to a neutral label when no user is loaded', () => {
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();

    const toolbar = fixture.nativeElement.querySelector('mat-toolbar') as HTMLElement;
    expect(toolbar.textContent).toContain('Cuenta');
  });

  it('navigates to the profile route from the user menu', async () => {
    session.restore(USER);
    const fixture = TestBed.createComponent(Shell);
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();

    const items = await openUserMenu(fixture);
    const profileItem = menuItemByText(items, 'Perfil');
    expect(profileItem).toBeTruthy();
    // Profile keeps the neutral menu-item look (no destructive marker).
    expect(profileItem?.classList).not.toContain('logout-item');
    (profileItem as HTMLAnchorElement).click();
    fixture.detectChanges();

    // `routerLink` resolves the route into a UrlTree and passes navigation
    // extras alongside, so compare the tree and ignore the extras object.
    expect(router.navigateByUrl).toHaveBeenCalledWith(
      router.createUrlTree(['/profile']),
      expect.anything(),
    );
  });

  it('calls AuthService.logout and navigates to /login when Cerrar sesión is clicked', async () => {
    session.restore(USER);
    authStub.logout.mockReturnValue(of(undefined));
    const fixture = TestBed.createComponent(Shell);
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();

    const items = await openUserMenu(fixture);
    const logoutItem = menuItemByText(items, 'Cerrar sesión');
    // Destructive affordance: the item carries the styling hook used by the
    // global `.shell-user-menu .logout-item` rules in styles.scss.
    expect(logoutItem?.classList).toContain('logout-item');
    (logoutItem as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(authStub.logout).toHaveBeenCalledTimes(1);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('drops the local session and navigates anyway when the logout request fails', async () => {
    session.setAccessToken('access-token');
    session.restore(USER);
    authStub.logout.mockReturnValue(throwError(() => new Error('network down')));
    const fixture = TestBed.createComponent(Shell);
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();

    const items = await openUserMenu(fixture);
    const logoutItem = menuItemByText(items, 'Cerrar sesión');
    (logoutItem as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(session.isAuthenticated()).toBe(false);
    expect(session.user()).toBeNull();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });
});
