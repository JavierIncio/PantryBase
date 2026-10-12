import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { UnitService } from '../../core/catalog/unit.service';
import { RecipeAvailability } from '../../core/recipes/recipe-matching';
import { StartCookingDialog } from './start-cooking-dialog';

const AVAILABILITY: RecipeAvailability = {
  covered: [],
  low: [
    {
      ingredientId: 1,
      ingredientName: 'Flour',
      neededAmount: 200,
      neededUnitCode: 'GRAM',
      availableAmount: 40,
      availableUnitCode: 'GRAM',
      fullyCovered: false,
    },
  ],
  missing: [],
  uncatalogued: [],
  hasGaps: true,
};

describe('StartCookingDialog', () => {
  let fixture: ComponentFixture<StartCookingDialog>;
  let close: ReturnType<typeof vi.fn>;

  const render = async (): Promise<ComponentFixture<StartCookingDialog>> => {
    close = vi.fn();
    await TestBed.configureTestingModule({
      imports: [StartCookingDialog],
      providers: [
        { provide: UnitService, useValue: { label: (code: string) => code } },
        { provide: MAT_DIALOG_DATA, useValue: AVAILABILITY },
        { provide: MatDialogRef, useValue: { close } },
      ],
    }).compileComponents();

    const created = TestBed.createComponent(StartCookingDialog);
    created.detectChanges();
    return created;
  };

  const button = (selector: string): HTMLButtonElement =>
    fixture.nativeElement.querySelector(selector) as HTMLButtonElement;

  it('renders the shared availability panel with the gaps', async () => {
    fixture = await render();

    expect(fixture.nativeElement.querySelector('app-recipe-availability-panel')).toBeTruthy();
    expect(fixture.nativeElement.querySelectorAll('.availability-group-title').length).toBe(1);
  });

  it('answers false on cancel and true on start anyway', async () => {
    fixture = await render();

    button('.cancel-start-cooking').click();
    expect(close).toHaveBeenCalledWith(false);

    button('.confirm-start-cooking').click();
    expect(close).toHaveBeenCalledWith(true);
  });
});
