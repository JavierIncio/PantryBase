import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Unit } from '../../core/catalog/catalog.models';
import { CompletionDeduction } from '../../core/cooking/cooking.models';
import { ShortageDialog } from './shortage-dialog';

const UNITS: Unit[] = [
  { code: 'GRAM', name: 'Gram', category: 'WEIGHT', canonical: true },
  { code: 'UNIT', name: 'Unit', category: 'COUNT', canonical: true },
];

const PLAN: CompletionDeduction = {
  hasShortage: true,
  lines: [
    {
      ingredientId: 10,
      ingredientName: 'Eggs',
      unitCode: 'UNIT',
      needed: 3,
      available: 1,
      deducted: 1,
      remaining: 0,
    },
  ],
};

describe('ShortageDialog', () => {
  let http: HttpTestingController;
  let close: ReturnType<typeof vi.fn>;

  const configure = async () => {
    close = vi.fn();
    await TestBed.configureTestingModule({
      imports: [ShortageDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // The dialog is a question with two answers; the caller gave it the plan
        // and this spec answers with each of them in turn.
        { provide: MAT_DIALOG_DATA, useValue: PLAN },
        { provide: MatDialogRef, useValue: { close } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  };

  /** Creates the dialog, answers the unit catalog, and closes it. */
  const render = async (): Promise<ComponentFixture<ShortageDialog>> => {
    await configure();
    const fixture = TestBed.createComponent(ShortageDialog);
    fixture.detectChanges();
    http.expectOne('/api/units').flush(UNITS);
    fixture.detectChanges();
    return fixture;
  };

  const root = (fixture: ComponentFixture<ShortageDialog>): HTMLElement =>
    fixture.nativeElement as HTMLElement;

  /** Rendered text with the template's whitespace collapsed, icon ligatures included. */
  const text = (node: Element | null | undefined): string =>
    (node?.textContent ?? '').replace(/\s+/g, ' ').trim();

  afterEach(() => {
    const pending = http?.match(() => true) ?? [];
    expect(pending.map((req) => req.request.urlWithParams)).toEqual([]);
  });

  it('shows every short line with its unit-named figures', async () => {
    const fixture = await render();
    const line = root(fixture).querySelector('.shortage-line');

    expect(text(line?.querySelector('.line-name'))).toBe('Eggs');
    // The catalog turned the code UNIT into its name without a template knowing.
    expect(text(line?.querySelector('.line-figures'))).toBe(
      'need 3 Unit · have 1 Unit · will use 1 Unit · leaves 0 Unit',
    );
    expect(root(fixture).querySelector('.shortage-intro')).toBeTruthy();
  });

  it('answers the question with cancel as no', async () => {
    const fixture = await render();

    (root(fixture).querySelector('.cancel-shortage') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(close).toHaveBeenCalledExactlyOnceWith(false);
  });

  it('answers the question with "Cook anyway" as yes', async () => {
    const fixture = await render();

    (root(fixture).querySelector('.accept-shortage') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(close).toHaveBeenCalledExactlyOnceWith(true);
  });
});
