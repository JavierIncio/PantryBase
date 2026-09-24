import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ProfilePage } from './profile-page';
import { ProfileService } from '../../core/user/profile.service';
import { Allergen, AllergyExclusions, UserPreferences } from '../../core/user/profile.models';
import { AuthService } from '../../core/auth/auth.service';
import { SessionState } from '../../core/auth/session.state';
import { UserResponse } from '../../core/auth/auth.models';

const USER: UserResponse = {
  id: 1,
  username: 'ada',
  email: 'ada@example.com',
  firstName: null,
  lastName: null,
  roles: ['USER'],
};

const USER_NAMED: UserResponse = {
  id: 1,
  username: 'ada',
  email: 'ada@example.com',
  firstName: 'Ada',
  lastName: 'Lovelace',
  roles: ['USER'],
};

const PREFERENCES: UserPreferences = {
  filterMode: 'LAX',
  coverageThreshold: 65,
  diet: 'LOW_CARB',
};

// Catalog in the API-driven order (sorted by name).
const CATALOG: Allergen[] = [
  { id: 1, code: 'CELERY', name: 'Celery' },
  { id: 2, code: 'CRUSTACEAN', name: 'Crustacean' },
  { id: 3, code: 'EGG', name: 'Egg' },
  { id: 4, code: 'FISH', name: 'Fish' },
  { id: 5, code: 'GLUTEN', name: 'Gluten' },
  { id: 6, code: 'LUPIN', name: 'Lupin' },
  { id: 7, code: 'MILK', name: 'Milk' },
  { id: 8, code: 'MOLLUSC', name: 'Mollusc' },
  { id: 9, code: 'MUSTARD', name: 'Mustard' },
  { id: 10, code: 'NUT', name: 'Nut' },
  { id: 11, code: 'PEANUT', name: 'Peanut' },
  { id: 12, code: 'SESAME', name: 'Sesame' },
  { id: 13, code: 'SOY', name: 'Soy' },
  { id: 14, code: 'SULFITE', name: 'Sulphite' },
];

const EXCLUSIONS: AllergyExclusions = {
  exclusions: [CATALOG[10]], // PEANUT
};

/** Backend error body wrapped in an HttpErrorResponse, as the interceptor flow yields. */
const failedRequest = (message: string, status = 400) =>
  throwError(
    () =>
      new HttpErrorResponse({
        status,
        statusText: status === 500 ? 'Server Error' : 'Bad Request',
        error: {
          timestamp: '2026-01-01T00:00:00Z',
          status,
          error: 'Error',
          message,
          path: '/api/users/preferences',
        },
      }),
  );

type ServiceStub = {
  getPreferences: ReturnType<typeof vi.fn>;
  updatePreferences: ReturnType<typeof vi.fn>;
  getAllergenCatalog: ReturnType<typeof vi.fn>;
  getAllergyExclusions: ReturnType<typeof vi.fn>;
  updateAllergyExclusions: ReturnType<typeof vi.fn>;
  updateProfile: ReturnType<typeof vi.fn>;
};

const saveButton = (fixture: ComponentFixture<ProfilePage>, cls: string) =>
  fixture.nativeElement.querySelector(`button.${cls}`) as HTMLButtonElement;

describe('ProfilePage', () => {
  let service: ServiceStub;
  let authStub: { changePassword: ReturnType<typeof vi.fn> };
  let snackBar: { open: ReturnType<typeof vi.fn> };

  /** Default backend answers used by every test unless overridden. */
  const configure = () => {
    service = {
      getPreferences: vi.fn(),
      updatePreferences: vi.fn(),
      getAllergenCatalog: vi.fn(),
      getAllergyExclusions: vi.fn(),
      updateAllergyExclusions: vi.fn(),
      updateProfile: vi.fn(),
    };
    authStub = { changePassword: vi.fn() };
    snackBar = { open: vi.fn() };
    service.getPreferences.mockReturnValue(of(PREFERENCES));
    service.getAllergenCatalog.mockReturnValue(of(CATALOG));
    service.getAllergyExclusions.mockReturnValue(of(EXCLUSIONS));

    return TestBed.configureTestingModule({
      imports: [ProfilePage],
      providers: [
        { provide: ProfileService, useValue: service },
        { provide: AuthService, useValue: authStub },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
  };

  it('shows the signed-in user email and renders both cards with loaded data', async () => {
    await configure();
    await TestBed.compileComponents();
    TestBed.inject(SessionState).restore(USER);

    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    const component = fixture.componentInstance;

    expect(host.querySelector('.profile-email')?.textContent).toContain('ada@example.com');
    // Preferences loaded into the form controls the selects bind to; the
    // threshold readout is plain markup, so it shows through in the text.
    expect(component.prefsForm.controls.filterMode.value).toBe('LAX');
    expect(component.prefsForm.controls.coverageThreshold.value).toBe(65);
    expect(component.prefsForm.controls.diet.value).toBe('LOW_CARB');
    expect(host.querySelectorAll('mat-select').length).toBe(2);
    expect(host.querySelector('.threshold-value')?.textContent).toContain('65%');

    // Checklist built from the catalog, with the stored exclusion pre-checked.
    const checkboxes = host.querySelectorAll('mat-checkbox');
    expect(checkboxes.length).toBe(CATALOG.length);
    const peanutIndex = CATALOG.findIndex((allergen) => allergen.code === 'PEANUT');
    expect(component.exclusions.at(peanutIndex).value).toBe(true);
    expect((checkboxes[peanutIndex] as HTMLElement).className).toContain(
      'mat-mdc-checkbox-checked',
    );
    const fishIndex = CATALOG.findIndex((allergen) => allergen.code === 'FISH');
    expect(component.exclusions.at(fishIndex).value).toBe(false);
  });

  it('keeps all Save buttons disabled while nothing has changed', async () => {
    await configure();
    await TestBed.compileComponents();
    TestBed.inject(SessionState).restore(USER);

    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();

    expect(saveButton(fixture, 'save-preferences').disabled).toBe(true);
    expect(saveButton(fixture, 'save-exclusions').disabled).toBe(true);
    expect(saveButton(fixture, 'save-identity').disabled).toBe(true);
    // The password form starts empty: required fields keep Save disabled.
    expect(saveButton(fixture, 'save-password').disabled).toBe(true);
  });

  it('prefills the identity form from the session profile', async () => {
    await configure();
    await TestBed.compileComponents();
    TestBed.inject(SessionState).restore(USER_NAMED);

    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const host = fixture.nativeElement as HTMLElement;

    // Null names arrive as empty inputs; username comes from the session.
    expect(component.identityForm.controls.username.value).toBe('ada');
    expect(component.identityForm.controls.firstName.value).toBe('Ada');
    expect(component.identityForm.controls.lastName.value).toBe('Lovelace');
    expect(host.querySelector('.identity-card')).toBeTruthy();
    expect(saveButton(fixture, 'save-identity').disabled).toBe(true);
  });

  it('sends the full identity payload inline and notifies when a name is cleared', async () => {
    await configure();
    await TestBed.compileComponents();
    TestBed.inject(SessionState).restore(USER_NAMED);
    service.updateProfile.mockReturnValue(of(USER_NAMED));

    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    // Clearing a name must map to `null` (the backend clears it); a shortest
    // valid new username is sent as its literal value.
    component.identityForm.patchValue({ username: 'ada42', firstName: '', lastName: 'Lovelace' });
    fixture.detectChanges();

    const button = saveButton(fixture, 'save-identity');
    expect(button.disabled).toBe(false);
    button.click();

    expect(service.updateProfile).toHaveBeenCalledWith({
      username: 'ada42',
      firstName: null,
      lastName: 'Lovelace',
    });
    expect(snackBar.open).toHaveBeenCalledWith('Perfil actualizado', 'OK', { duration: 3000 });
  });

  it('encodes an emptied username as null (backend keeps the current one)', async () => {
    await configure();
    await TestBed.compileComponents();
    TestBed.inject(SessionState).restore(USER_NAMED);
    service.updateProfile.mockReturnValue(of(USER_NAMED));

    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    // The username field has no `required` validator: emptying it is a valid
    // "keep current" no-op per the backend contract.
    component.identityForm.patchValue({ username: '', firstName: 'Ada' });
    fixture.detectChanges();

    expect(saveButton(fixture, 'save-identity').disabled).toBe(false);
    saveButton(fixture, 'save-identity').click();

    expect(service.updateProfile).toHaveBeenCalledWith({
      username: null,
      firstName: 'Ada',
      lastName: 'Lovelace',
    });
  });

  it('restores the session profile on success so the whole app updates', async () => {
    await configure();
    await TestBed.compileComponents();
    const session = TestBed.inject(SessionState);
    session.restore(USER_NAMED);
    service.updateProfile.mockReturnValue(of({ ...USER_NAMED, firstName: 'A.' }));

    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.identityForm.patchValue({ firstName: 'A.' });
    fixture.detectChanges();
    saveButton(fixture, 'save-identity').click();
    fixture.detectChanges();

    expect(session.user()?.firstName).toBe('A.');
    // Re-baselined form: Save disabled again and pristine.
    expect(component.identityForm.pristine).toBe(true);
    expect(saveButton(fixture, 'save-identity').disabled).toBe(true);
  });

  it('shows the backend message on the username field when a 400 reports it taken', async () => {
    await configure();
    await TestBed.compileComponents();
    TestBed.inject(SessionState).restore(USER_NAMED);
    service.updateProfile.mockReturnValue(failedRequest('Username already exists'));

    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const host = fixture.nativeElement as HTMLElement;

    component.identityForm.patchValue({ username: 'ada42' });
    fixture.detectChanges();
    saveButton(fixture, 'save-identity').click();
    fixture.detectChanges();

    expect(component.identityForm.controls.username.hasError('taken')).toBe(true);
    expect(host.querySelector('.identity-card mat-error')?.textContent).toContain(
      'Username already exists',
    );
    // The edited values survive the failed save so the user can retry or revert.
    expect(component.identityForm.controls.username.value).toBe('ada42');
    expect(snackBar.open).not.toHaveBeenCalled();
  });

  it('disables Save while the username violates the length constraints', async () => {
    await configure();
    await TestBed.compileComponents();
    TestBed.inject(SessionState).restore(USER_NAMED);

    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const username = component.identityForm.controls.username;

    username.setValue('a'.repeat(21));
    fixture.detectChanges();
    expect(saveButton(fixture, 'save-identity').disabled).toBe(true);
  });

  it('saves the edited preferences with the full shape and disables Save again', async () => {
    await configure();
    await TestBed.compileComponents();
    service.updatePreferences.mockReturnValue(of(PREFERENCES));

    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    // Programmatic changes do not mark the form dirty (only real user edits
    // through the value accessor do), so mirror the accessor's markAsDirty.
    component.prefsForm.patchValue({
      filterMode: 'STRICT',
      coverageThreshold: 90,
      diet: 'HIGH_PROTEIN',
    });
    component.prefsForm.markAsDirty();
    fixture.detectChanges();

    const button = saveButton(fixture, 'save-preferences');
    expect(button.disabled).toBe(false);
    button.click();

    expect(service.updatePreferences).toHaveBeenCalledWith({
      filterMode: 'STRICT',
      coverageThreshold: 90,
      diet: 'HIGH_PROTEIN',
    });
    expect(snackBar.open).toHaveBeenCalledWith('Preferences saved', 'OK', { duration: 3000 });

    fixture.detectChanges();
    expect(saveButton(fixture, 'save-preferences').disabled).toBe(true);
    expect(component.prefsForm.dirty).toBe(false);
  });

  it('shows the backend message when saving preferences fails and keeps the edits', async () => {
    await configure();
    await TestBed.compileComponents();
    service.updatePreferences.mockReturnValue(failedRequest('Invalid coverage threshold'));

    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.prefsForm.patchValue({ filterMode: 'STRICT' });
    component.prefsForm.markAsDirty();
    fixture.detectChanges();
    saveButton(fixture, 'save-preferences').click();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.save-error')?.textContent).toContain('Invalid coverage threshold');
    expect(snackBar.open).not.toHaveBeenCalled();
    // The edit survives the failed save, so the user can retry or revert.
    expect(component.prefsForm.controls.filterMode.value).toBe('STRICT');
    expect(saveButton(fixture, 'save-preferences').disabled).toBe(false);
  });

  it('saves the full uppercase selection and re-syncs the checklist to the server state', async () => {
    await configure();
    await TestBed.compileComponents();
    service.updateAllergyExclusions.mockReturnValue(of(EXCLUSIONS));

    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const fishIndex = CATALOG.findIndex((allergen) => allergen.code === 'FISH');

    component.exclusions.at(fishIndex).setValue(true); // add FISH to the stored PEANUT
    component.exclusions.markAsDirty();
    fixture.detectChanges();

    const button = saveButton(fixture, 'save-exclusions');
    expect(button.disabled).toBe(false);
    button.click();

    expect(service.updateAllergyExclusions).toHaveBeenCalledWith(['FISH', 'PEANUT']);
    expect(snackBar.open).toHaveBeenCalledWith('Allergy exclusions saved', 'OK', {
      duration: 3000,
    });

    fixture.detectChanges();
    expect(saveButton(fixture, 'save-exclusions').disabled).toBe(true);
    // Server acknowledged only PEANUT, so FISH is unchecked again.
    expect(component.exclusions.at(fishIndex).value).toBe(false);
    expect(component.exclusions.at(10).value).toBe(true);
  });

  it('shows the backend message when saving exclusions fails and keeps the selection', async () => {
    await configure();
    await TestBed.compileComponents();
    service.updateAllergyExclusions.mockReturnValue(failedRequest('Unknown allergen code: peanut'));

    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const fishIndex = CATALOG.findIndex((allergen) => allergen.code === 'FISH');

    component.exclusions.at(fishIndex).setValue(true);
    component.exclusions.markAsDirty();
    fixture.detectChanges();
    saveButton(fixture, 'save-exclusions').click();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.save-error')?.textContent).toContain(
      'Unknown allergen code: peanut',
    );
    expect(snackBar.open).not.toHaveBeenCalled();
    expect(component.exclusions.at(fishIndex).value).toBe(true);
  });

  it('shows a load error with a Retry button that re-fetches preferences', async () => {
    await configure();
    await TestBed.compileComponents();
    service.getPreferences
      .mockReturnValueOnce(failedRequest('Server exploded', 500))
      .mockReturnValue(of(PREFERENCES));

    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;

    const retry = host.querySelector('button.retry-preferences') as HTMLButtonElement;
    expect(retry?.textContent).toContain('Retry');
    expect(host.textContent).toContain('Server exploded');

    retry.click();
    fixture.detectChanges();

    expect(service.getPreferences).toHaveBeenCalledTimes(2);
    expect(host.querySelector('.save-preferences')).toBeTruthy();
    expect(fixture.componentInstance.prefsForm.controls.filterMode.value).toBe('LAX');
  });

  it('shows a spinner while the preferences are still loading', async () => {
    await configure();
    await TestBed.compileComponents();
    const pending = new Subject<UserPreferences>();
    service.getPreferences.mockReturnValue(pending);

    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('.preferences-card .card-status mat-spinner')).toBeTruthy();

    pending.next(PREFERENCES);
    pending.complete();
    fixture.detectChanges();

    expect(host.querySelector('.preferences-card .save-preferences')).toBeTruthy();
  });

  /**
   * The slider's underlying `<input>` thumb: MatSlider reflects its `[disabled]`
   * input by propagating it to the thumb, which lands on this element.
   */
  const thresholdInput = (fixture: ComponentFixture<ProfilePage>) =>
    fixture.nativeElement.querySelector('.threshold-slider input') as HTMLInputElement;

  it('locks the coverage threshold at 100 and disables the slider when the mode turns strict', async () => {
    await configure();
    await TestBed.compileComponents();

    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const threshold = component.prefsForm.controls.coverageThreshold;

    component.prefsForm.controls.filterMode.setValue('STRICT');
    fixture.detectChanges();

    expect(threshold.value).toBe(100);
    expect(thresholdInput(fixture).disabled).toBe(true);
    // Domino effect: the flip also normalizes a previous divergent threshold.
    component.prefsForm.controls.filterMode.setValue('LAX');
    fixture.detectChanges();
    expect(thresholdInput(fixture).disabled).toBe(false);
    expect(threshold.value).toBe(100); // preserved, the user can lower it now
  });

  it('switches the filter mode to strict when the coverage threshold reaches 100', async () => {
    await configure();
    await TestBed.compileComponents();

    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const mode = component.prefsForm.controls.filterMode;

    expect(mode.value).toBe('LAX');
    component.prefsForm.controls.coverageThreshold.setValue(100);
    fixture.detectChanges();

    expect(mode.value).toBe('STRICT');
    expect(thresholdInput(fixture).disabled).toBe(true);
  });

  it('normalizes a strict response with a threshold below 100 to a pristine 100% form', async () => {
    await configure();
    await TestBed.compileComponents();
    service.getPreferences.mockReturnValue(
      of({ filterMode: 'STRICT', coverageThreshold: 80, diet: 'LOW_CARB' }),
    );

    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const host = fixture.nativeElement as HTMLElement;

    expect(component.prefsForm.controls.coverageThreshold.value).toBe(100);
    expect(component.prefsForm.controls.filterMode.value).toBe('STRICT');
    expect(host.querySelector('.threshold-value')?.textContent).toContain('100%');
    // Normalizing before reset keeps the load pristine, so Save stays disabled.
    expect(component.prefsForm.pristine).toBe(true);
    expect(saveButton(fixture, 'save-preferences').disabled).toBe(true);
    expect(component.prefsForm.getRawValue().coverageThreshold).toBe(100);
  });

  it('changes the password and resets the card on success', async () => {
    await configure();
    await TestBed.compileComponents();
    authStub.changePassword.mockReturnValue(of(undefined));

    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.passwordForm.setValue({
      currentPassword: 'oldpass',
      newPassword: 'newpass42',
      confirmPassword: 'newpass42',
    });
    fixture.detectChanges();
    saveButton(fixture, 'save-password').click();

    // confirmPassword is client-only: the request carries the two real fields.
    expect(authStub.changePassword).toHaveBeenCalledWith('oldpass', 'newpass42');
    expect(snackBar.open).toHaveBeenCalledWith('Contraseña actualizada', 'OK', { duration: 3000 });

    fixture.detectChanges();
    // Reset cleared the secrets and re-disabled Save via the required fields.
    expect(component.passwordForm.getRawValue()).toEqual({
      currentPassword: '',
      newPassword: '',
      confirmPassword: '',
    });
    expect(component.passwordForm.pristine).toBe(true);
    expect(saveButton(fixture, 'save-password').disabled).toBe(true);
    expect(component.passwordSaveError()).toBeNull();
  });

  it('sends an empty currentPassword as the first password for OAuth users', async () => {
    await configure();
    await TestBed.compileComponents();
    authStub.changePassword.mockReturnValue(of(undefined));

    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.passwordForm.setValue({
      currentPassword: '',
      newPassword: 'firstpass42',
      confirmPassword: 'firstpass42',
    });
    fixture.detectChanges();
    saveButton(fixture, 'save-password').click();

    expect(authStub.changePassword).toHaveBeenCalledWith('', 'firstpass42');
  });

  it('shows a 400/409 password error inside the card and keeps the edits', async () => {
    await configure();
    await TestBed.compileComponents();
    authStub.changePassword.mockReturnValue(failedRequest('Current password does not match'));

    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const host = fixture.nativeElement as HTMLElement;

    component.passwordForm.setValue({
      currentPassword: 'wrongpass',
      newPassword: 'newpass42',
      confirmPassword: 'newpass42',
    });
    fixture.detectChanges();
    saveButton(fixture, 'save-password').click();
    fixture.detectChanges();

    expect(host.querySelector('.password-card .save-error')?.textContent).toContain(
      'Current password does not match',
    );
    expect(snackBar.open).not.toHaveBeenCalled();
    // The typed values survive the failed save so the user can fix and retry.
    expect(component.passwordForm.controls.currentPassword.value).toBe('wrongpass');
    expect(component.passwordForm.controls.newPassword.value).toBe('newpass42');
    expect(saveButton(fixture, 'save-password').disabled).toBe(false);
  });

  it('keeps Save disabled while the passwords differ', async () => {
    await configure();
    await TestBed.compileComponents();

    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.passwordForm.setValue({
      currentPassword: 'oldpass',
      newPassword: 'newpass42',
      confirmPassword: 'nope',
    });
    fixture.detectChanges();

    // Group mismatch invalidates the whole form; the button never fires.
    expect(saveButton(fixture, 'save-password').disabled).toBe(true);
    saveButton(fixture, 'save-password').click();
    expect(authStub.changePassword).not.toHaveBeenCalled();
  });
});
