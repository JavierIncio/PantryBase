import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PantryItem } from '../../core/pantry/pantry.models';
import { PantryItemRow } from './pantry-item-row';
import { PantryViewMode } from './pantry-helpers';

const MILK: PantryItem = {
  id: 7,
  fdcId: 171265,
  name: 'Milk, whole',
  category: 'Dairy and Egg Products',
  quantity: 244,
  unitCode: 'GRAM',
  location: 'Fridge door',
  note: 'Opened',
  version: 0,
  updatedAt: '2026-10-01T10:00:00Z',
};

/** A line with nothing left: the decrement control has nothing to take away. */
const SALT: PantryItem = {
  ...MILK,
  id: 9,
  name: 'Salt',
  category: 'Spices and Herbs',
  quantity: 0,
  unitCode: 'UNIT',
  location: null,
  note: null,
};

/** The one unit the fixture lines use; passed in already resolved by the page. */
const UNIT_LABEL = 'Gram';

/**
 * A page that projects a panel, standing in for `PantryPage`, so the test can
 * prove the row paints its slot where the pages put their editors.
 */
@Component({
  imports: [PantryItemRow],
  template: `
    <app-pantry-item-row
      [item]="item"
      unitLabel="Gram"
      viewMode="list"
      [busy]="false"
      [open]="false"
      [editing]="false"
      draft=""
    >
      <span class="projected-panel">Projected editor</span>
    </app-pantry-item-row>
  `,
})
class ProjectingHost {
  readonly item = MILK;
}

describe('PantryItemRow', () => {
  interface RowInputs {
    readonly item: PantryItem;
    readonly unitLabel: string;
    readonly viewMode: PantryViewMode;
    readonly busy: boolean;
    readonly open: boolean;
    readonly editing: boolean;
    readonly draft: string;
  }

  const DEFAULTS: RowInputs = {
    item: MILK,
    unitLabel: UNIT_LABEL,
    viewMode: 'list',
    busy: false,
    open: false,
    editing: false,
    draft: '',
  };

  const render = async (
    overrides: Partial<RowInputs> = {},
  ): Promise<ComponentFixture<PantryItemRow>> => {
    const inputs = { ...DEFAULTS, ...overrides };
    await TestBed.configureTestingModule({ imports: [PantryItemRow] }).compileComponents();

    const fixture = TestBed.createComponent(PantryItemRow);
    fixture.componentRef.setInput('item', inputs.item);
    fixture.componentRef.setInput('unitLabel', inputs.unitLabel);
    fixture.componentRef.setInput('viewMode', inputs.viewMode);
    fixture.componentRef.setInput('busy', inputs.busy);
    fixture.componentRef.setInput('open', inputs.open);
    fixture.componentRef.setInput('editing', inputs.editing);
    fixture.componentRef.setInput('draft', inputs.draft);
    fixture.detectChanges();
    return fixture;
  };

  const root = (fixture: ComponentFixture<PantryItemRow>): HTMLElement =>
    fixture.nativeElement as HTMLElement;

  const text = (node: Element | null | undefined): string =>
    (node?.textContent ?? '').replace(/\s+/g, ' ').trim();

  const button = (fixture: ComponentFixture<PantryItemRow>, selector: string): HTMLButtonElement =>
    root(fixture).querySelector(selector) as HTMLButtonElement;

  it('renders the name, quantity, unit and both details of the line', async () => {
    const fixture = await render();

    expect(text(root(fixture).querySelector('.item-name'))).toBe('Milk, whole');
    expect(text(root(fixture).querySelector('.item-amount'))).toBe('244');
    expect(text(root(fixture).querySelector('.item-unit'))).toBe(UNIT_LABEL);
    expect([...root(fixture).querySelectorAll('.item-detail')].map((node) => text(node))).toEqual([
      'place Fridge door',
      'notes Opened',
    ]);
  });

  it('carries `.pantry-item` on its host and no card state in list view', async () => {
    const fixture = await render();

    expect(root(fixture).classList).toContain('pantry-item');
    expect(root(fixture).classList).not.toContain('grid-card');
    // The category travels with the card in the grid; in the list the section
    // header already names it, so the row must not repeat it.
    expect(root(fixture).querySelector('.item-category')).toBeNull();
  });

  it('adds the card and category state in grid view', async () => {
    const fixture = await render({ viewMode: 'grid' });

    expect(root(fixture).classList).toContain('grid-card');
    expect(text(root(fixture).querySelector('.item-category'))).toBe('Dairy and Egg Products');
  });

  it('tints the host when the row is open', async () => {
    const fixture = await render({ open: true });

    expect(root(fixture).classList).toContain('pantry-item-open');
  });

  it('disables the decrement control on a zeroed line but not the increment', async () => {
    const fixture = await render({ item: SALT });

    expect(button(fixture, '.qty-decrement').disabled).toBe(true);
    expect(button(fixture, '.qty-increment').disabled).toBe(false);
  });

  it('emits the signed step of the line unit from the −/+ buttons', async () => {
    const fixture = await render();
    const steps: number[] = [];
    fixture.componentInstance.step.subscribe((delta) => steps.push(delta));

    button(fixture, '.qty-increment').click();
    button(fixture, '.qty-decrement').click();

    // GRAM steps by 10, and the sign is the whole meaning of the movement.
    expect(steps).toEqual([10, -10]);
  });

  it('names the step and unit in the stepper labels', async () => {
    const fixture = await render();

    expect(button(fixture, '.qty-increment').getAttribute('aria-label')).toBe(
      'Increase Milk, whole by 10 Gram',
    );
    expect(button(fixture, '.qty-decrement').getAttribute('aria-label')).toBe(
      'Decrease Milk, whole by 10 Gram',
    );
  });

  it('shows the editable field with the draft and the unit suffix while editing', async () => {
    const fixture = await render({ editing: true, draft: '250' });

    expect(root(fixture).querySelector('.inline-edit-trigger')).toBeNull();
    const input = root(fixture).querySelector('.qty-input') as HTMLInputElement;
    expect(input.value).toBe('250');
    expect(text(root(fixture).querySelector('.qty-unit-suffix'))).toBe(UNIT_LABEL);
  });

  it('asks for the editable field when the amount is clicked', async () => {
    const fixture = await render();
    const starts: number[] = [];
    fixture.componentInstance.startEdit.subscribe(() => starts.push(1));

    button(fixture, '.inline-edit-trigger').click();

    expect(starts.length).toBe(1);
  });

  it('emits the typed draft, then a commit on Enter', async () => {
    const fixture = await render({ editing: true, draft: '244' });
    const drafts: string[] = [];
    let commits = 0;
    fixture.componentInstance.draftChange.subscribe((value) => drafts.push(value));
    fixture.componentInstance.commitEdit.subscribe(() => (commits += 1));

    const input = root(fixture).querySelector('.qty-input') as HTMLInputElement;
    input.value = '250';
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));

    expect(drafts).toEqual(['250']);
    expect(commits).toBe(1);
  });

  it('abandons the draft on Escape and on blur', async () => {
    const fixture = await render({ editing: true, draft: '999' });
    let cancels = 0;
    fixture.componentInstance.cancelEdit.subscribe(() => (cancels += 1));

    const input = root(fixture).querySelector('.qty-input') as HTMLInputElement;
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    input.dispatchEvent(new Event('blur'));

    expect(cancels).toBe(2);
  });

  it('emits the panel the action asked for, and the remove separately', async () => {
    const fixture = await render();
    const panels: string[] = [];
    let removes = 0;
    fixture.componentInstance.panel.subscribe((kind) => panels.push(kind));
    fixture.componentInstance.remove.subscribe(() => (removes += 1));

    button(fixture, '.action-edit').click();
    button(fixture, '.action-adjust').click();
    button(fixture, '.action-history').click();
    button(fixture, '.action-delete').click();

    expect(panels).toEqual(['edit', 'adjust', 'history']);
    expect(removes).toBe(1);
  });

  it('disables every control while the row is busy', async () => {
    const fixture = await render({ busy: true });

    for (const control of root(fixture).querySelectorAll('button')) {
      expect((control as HTMLButtonElement).disabled).toBe(true);
    }
  });

  it('renders the projected panel after the row', async () => {
    await TestBed.configureTestingModule({ imports: [ProjectingHost] }).compileComponents();
    const fixture = TestBed.createComponent(ProjectingHost);
    fixture.detectChanges();

    const row = fixture.nativeElement.querySelector('app-pantry-item-row') as HTMLElement;
    const projected = row.querySelector('.projected-panel');
    expect(text(projected)).toBe('Projected editor');
    // The slot sits below the row, so a panel reads as belonging to the line
    // above it.
    const rowBox = row.querySelector('.item-row') as HTMLElement;
    expect(
      rowBox.compareDocumentPosition(projected as Node) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
  });
});
