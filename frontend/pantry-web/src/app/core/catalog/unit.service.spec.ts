import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Unit } from './catalog.models';
import { UnitService } from './unit.service';

/** The fixed catalog, in the order the API answers it (sorted by code). */
const UNITS: Unit[] = [
  { code: 'CUP', name: 'Cup', category: 'VOLUME', canonical: false },
  { code: 'GRAM', name: 'Gram', category: 'WEIGHT', canonical: true },
  { code: 'KG', name: 'Kilogram', category: 'WEIGHT', canonical: false },
  { code: 'UNIT', name: 'Unit', category: 'COUNT', canonical: true },
];

describe('UnitService', () => {
  let service: UnitService;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    service = TestBed.inject(UnitService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('publishes the catalog as a signal, loading while it is asked for', () => {
    service.load();

    expect(service.units()).toBeNull();
    expect(service.loading()).toBe(true);

    http.expectOne('/api/units').flush(UNITS);

    expect(service.units()).toEqual(UNITS);
    expect(service.loading()).toBe(false);
    expect(service.loadError()).toBeNull();
  });

  it('asks once however many consumers need the labels', () => {
    // Two screens, one table of twelve rows that cannot change while the app runs.
    service.load();
    service.load();

    expect(http.match('/api/units').length).toBe(1);
  });

  it('does not ask again what it already answered', () => {
    service.load();
    http.expectOne('/api/units').flush(UNITS);

    service.load();

    // A second call would be a request whose answer cannot have changed.
    expect(http.match(() => true)).toEqual([]);
  });

  it('does not ask twice while the first load is still in flight', () => {
    service.load();
    service.load();

    const pending = http.match('/api/units');
    expect(pending.length).toBe(1);
    pending[0].flush(UNITS);

    expect(service.label('GRAM')).toBe('Gram');
  });

  it('labels a code with the name the catalog gives it', () => {
    service.load();
    http.expectOne('/api/units').flush(UNITS);

    expect(service.label('GRAM')).toBe('Gram');
    expect(service.label('CUP')).toBe('Cup');
  });

  it('renders a code it does not know as itself instead of as nothing', () => {
    // Before the catalog is loaded, and if it never loads: a raw code in a row is
    // a fact the user can act on, a blank cell looks like a missing quantity.
    expect(service.label('FLOZ')).toBe('FLOZ');

    service.load();
    http.expectOne('/api/units').flush(UNITS);

    expect(service.label('FLOZ')).toBe('FLOZ');
  });

  it('keeps the codes usable and asks to try again when the catalog fails', () => {
    service.load();

    http.expectOne('/api/units').flush(
      {
        timestamp: '2026-10-05T09:00:00Z',
        status: 500,
        error: 'Internal Server Error',
        message: 'Database is down',
        path: '/api/units',
      },
      { status: 500, statusText: 'Server Error' },
    );

    // Labels are lost but the quantities are not, so this is not a dead end and it
    // is not retried on its own either.
    expect(service.loadError()).toBe('Database is down');
    expect(service.units()).toBeNull();
    expect(service.loading()).toBe(false);
    expect(service.label('GRAM')).toBe('GRAM');

    service.load();
    http.expectOne('/api/units').flush(UNITS);

    expect(service.label('GRAM')).toBe('Gram');
    expect(service.loadError()).toBeNull();
  });
});
