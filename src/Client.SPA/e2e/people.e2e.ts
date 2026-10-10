import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';
import { peopleState } from './fakes/people';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

const grid = (page: Page) => page.getByRole('list', { name: 'Personas' });
const card = (page: Page, name: string) =>
	grid(page).getByRole('link', { name: new RegExp(`^${name}`) });

async function openPeople(page: Page) {
	const api = await fakeApi(page, { signedIn: true });
	await page.goto('/people');
	await expect(page.getByRole('heading', { name: 'Personas', level: 1 })).toBeVisible();
	return api;
}

test('lists people, searches, sorts and shows the hidden ones', async ({ page }) => {
	await openPeople(page);

	await expect(page.getByText('5 personas')).toBeVisible();
	const links = grid(page).getByRole('link');
	await expect(links).toHaveCount(5);
	// Most faces first; Ana has suggestions waiting.
	await expect(links.first()).toHaveAccessibleName('Ana, 14 caras, 3 sugerencias por revisar');
	await expect(grid(page).getByText('Sin nombre')).toHaveCount(2);

	await page.getByRole('searchbox', { name: 'Buscar personas' }).fill('jose');
	await expect(links).toHaveCount(1);
	await expect(links.first()).toContainText('José');

	await page.getByRole('searchbox', { name: 'Buscar personas' }).fill('nadie');
	await expect(page.getByText('Nadie coincide con «nadie».')).toBeVisible();
	await page.getByRole('searchbox', { name: 'Buscar personas' }).fill('');

	await page.getByLabel('Ordenar').selectOption('name');
	await expect(links.first()).toContainText('Ana');
	await expect(links.nth(1)).toContainText('José');

	await page.getByLabel('Ordenar').selectOption('unnamed');
	await expect(links.first()).toContainText('Sin nombre');

	await page.getByLabel('Mostrar ocultas').check();
	await expect(card(page, 'Marta')).toBeVisible();
	await expect(card(page, 'Marta')).toHaveAccessibleName(
		'Marta, 4 caras, 1 sugerencia por revisar, Oculta'
	);
	await expect(page.getByText('6 personas')).toBeVisible();
});

test('moves between people with the arrow keys', async ({ page }) => {
	await openPeople(page);
	await card(page, 'Ana').focus();

	await page.keyboard.press('ArrowRight');
	await expect(card(page, 'Luis')).toBeFocused();
	await page.keyboard.press('End');
	await expect(card(page, 'José')).toBeFocused();
	await page.keyboard.press('Home');
	await expect(card(page, 'Ana')).toBeFocused();

	await page.keyboard.press('Enter');
	await expect(page).toHaveURL(/\/people\/person-ana$/);
});

test('merges the selected people into the one kept', async ({ page }) => {
	const api = await openPeople(page);

	await card(page, 'Ana').click({ modifiers: ['ControlOrMeta'] });
	await page.getByRole('checkbox', { name: 'Seleccionar a Luis' }).click();
	await expect(page.getByText('2 seleccionados')).toBeVisible();

	await page.getByRole('button', { name: 'Fusionar', exact: true }).click();
	const dialog = page.getByRole('dialog', { name: 'Fusionar personas' });
	// The named person with most faces is kept by default.
	await expect(dialog.getByRole('radio', { name: /Ana/ })).toBeChecked();
	await dialog.getByRole('button', { name: 'Fusionar' }).click();

	await expect(page.getByText('1 persona fusionada con Ana')).toBeVisible();
	await expect(card(page, 'Luis')).toHaveCount(0);
	await expect(card(page, 'Ana')).toHaveAccessibleName(/^Ana, 23 caras/);
	expect(peopleState(api.state).people.map((p) => p.id)).not.toContain('person-luis');
});

test('hides a person and undoes it', async ({ page }) => {
	const api = await openPeople(page);

	await card(page, 'Luis').click({ modifiers: ['ControlOrMeta'] });
	await page.getByRole('button', { name: 'Ocultar' }).click();

	await expect(page.getByText('1 persona oculta')).toBeVisible();
	await expect(card(page, 'Luis')).toHaveCount(0);
	expect(peopleState(api.state).people.find((p) => p.id === 'person-luis')?.isHidden).toBe(true);

	await page.getByRole('button', { name: 'Deshacer' }).click();
	await expect(card(page, 'Luis')).toBeVisible();
});

test('renames from the selection, Escape clears it', async ({ page }) => {
	await openPeople(page);

	const unnamed = grid(page)
		.getByRole('link', { name: /^Sin nombre/ })
		.first();
	await unnamed.click({ modifiers: ['ControlOrMeta'] });
	await page.getByRole('button', { name: 'Cambiar nombre' }).click();
	const dialog = page.getByRole('dialog', { name: 'Cambiar nombre' });
	await dialog.getByLabel('Nombre').fill('Carmen');
	await dialog.getByRole('button', { name: 'Guardar' }).click();

	await expect(page.getByText('Nombre guardado')).toBeVisible();
	await expect(card(page, 'Carmen')).toBeVisible();

	await card(page, 'Carmen').click({ modifiers: ['ControlOrMeta'] });
	await expect(page.getByText('1 seleccionado')).toBeVisible();
	await page.keyboard.press('Escape');
	await expect(page.getByText('1 seleccionado')).toHaveCount(0);
});

test('shows face recognition progress and queues the pending photos', async ({ page }) => {
	const api = await openPeople(page);

	await page.getByRole('button', { name: 'Reconocimiento facial' }).click();
	const dialog = page.getByRole('dialog', { name: 'Reconocimiento facial' });
	await expect(dialog.getByText('Por analizar')).toBeVisible();
	await expect(dialog.getByText('840')).toBeVisible();

	await dialog.getByRole('button', { name: 'Analizar las pendientes' }).click();
	await expect(page.getByText('12 fotos en cola para analizar')).toBeVisible();

	await dialog.getByRole('button', { name: 'Reagrupar caras' }).click();
	await expect(page.getByText('Caras reagrupadas: 2 personas nuevas')).toBeVisible();
	expect(peopleState(api.state).backfills).toBe(1);
	expect(peopleState(api.state).reclusters).toBe(1);
});

test('a person page shows their photos, renames and removes photos from them', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	await page.goto('/people/person-ana');

	await expect(page.getByRole('heading', { name: 'Ana', level: 1 })).toBeVisible();
	const cells = page.getByRole('button', { name: /^(Foto|Vídeo), / });
	await expect(cells).toHaveCount(14);
	await expect(
		page
			.getByRole('navigation', { name: 'Secciones de la persona' })
			.getByRole('link', { name: 'Fotos' })
	).toHaveAttribute('aria-current', 'page');

	await page.getByRole('button', { name: 'Cambiar nombre' }).click();
	await page.getByRole('dialog').getByLabel('Nombre').fill('Ana María');
	await page.getByRole('dialog').getByRole('button', { name: 'Guardar' }).click();
	await expect(page.getByRole('heading', { name: 'Ana María', level: 1 })).toBeVisible();

	await cells.nth(0).click({ modifiers: ['ControlOrMeta'] });
	await cells.nth(1).click({ modifiers: ['ControlOrMeta'] });
	await page.getByRole('button', { name: 'Quitar de esta persona' }).click();
	const confirm = page.getByRole('dialog', { name: 'Quitar fotos de Ana María' });
	await expect(confirm).toContainText('2 fotos');
	await confirm.getByRole('button', { name: 'Quitar de esta persona' }).click();

	await expect(page.getByText('2 fotos quitadas de Ana María')).toBeVisible();
	await expect(cells).toHaveCount(12);
	expect(peopleState(api.state).unlinked).toHaveLength(2);

	// The viewer opens over the person's photos.
	await cells.nth(0).click();
	await expect(page.getByRole('dialog', { name: /^IMG_/ })).toBeVisible();
});

test('merges a person with another from their page and lands on the kept one', async ({ page }) => {
	await fakeApi(page, { signedIn: true });
	await page.goto('/people/person-x1');
	await expect(page.getByRole('heading', { name: 'Sin nombre', level: 1 })).toBeVisible();

	await page.getByRole('button', { name: 'Fusionar con…' }).click();
	const picker = page.getByRole('dialog', { name: 'Fusionar con…' });
	await picker.getByRole('searchbox').fill('luis');
	await picker.getByRole('button', { name: /Luis/ }).click();

	const merge = page.getByRole('dialog', { name: 'Fusionar personas' });
	await merge.getByText('Luis').click();
	await merge.getByRole('button', { name: 'Fusionar' }).click();

	await expect(page).toHaveURL(/\/people\/person-luis$/);
	await expect(page.getByRole('heading', { name: 'Luis', level: 1 })).toBeVisible();
	await expect(page.getByText('15 caras')).toBeVisible();
});

test('manages faces: cover, not this person (with undo), move to a new person', async ({
	page
}) => {
	const api = await fakeApi(page, { signedIn: true });
	await page.goto('/people/person-ana/faces');

	const faces = page
		.getByRole('list', { name: 'Caras' })
		.getByRole('button', { name: /^Cara \d+/ });
	await expect(faces).toHaveCount(14);
	await expect(faces.first()).toHaveAccessibleName('Cara 1, portada');

	await faces.nth(2).click();
	await page.getByRole('button', { name: 'Usar como portada' }).click();
	await expect(page.getByText('Portada actualizada')).toBeVisible();
	await expect(faces.nth(2)).toHaveAccessibleName('Cara 3, portada');

	await faces.nth(4).click();
	await faces.nth(6).click({ modifiers: ['Shift'] });
	await expect(page.getByText('3 seleccionados')).toBeVisible();
	await page.getByRole('button', { name: 'No es esta persona' }).click();
	await expect(page.getByText('3 caras quitadas')).toBeVisible();
	await expect(faces).toHaveCount(11);
	await page.getByRole('button', { name: 'Deshacer' }).click();
	await expect(faces).toHaveCount(14);

	await faces.nth(13).click();
	await page.getByRole('button', { name: 'Mover a otra persona' }).click();
	const picker = page.getByRole('dialog', { name: 'Mover a otra persona' });
	await picker.getByRole('searchbox').fill('Pedro');
	await picker.getByRole('button', { name: 'Nueva persona: «Pedro»' }).click();
	await expect(page.getByText('1 cara movida a Pedro')).toBeVisible();
	await expect(faces).toHaveCount(13);
	expect(peopleState(api.state).people.some((p) => p.name === 'Pedro')).toBe(true);

	// Enter on a face opens its photo.
	await faces.first().focus();
	await page.keyboard.press('Enter');
	await expect(page.getByRole('dialog', { name: /^IMG_/ })).toBeVisible();
});

test('rejects a face that is not a face', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	await page.goto('/people/person-luis/faces');
	const faces = page
		.getByRole('list', { name: 'Caras' })
		.getByRole('button', { name: /^Cara \d+/ });
	await expect(faces).toHaveCount(9);

	await faces.nth(1).click();
	await page.getByRole('button', { name: 'No es una cara' }).click();

	await expect(page.getByText('1 cara descartada')).toBeVisible();
	await expect(faces).toHaveCount(8);
	expect(peopleState(api.state).faces.filter((f) => f.rejected)).toHaveLength(1);
});

test('reviews suggestions one by one with the keyboard and all at once', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	await page.goto('/people/person-ana/suggestions');

	await expect(page.getByRole('link', { name: /Sugerencias/ })).toContainText('3');
	const cards = page.getByRole('group', { name: /^Cara sugerida/ });
	await expect(cards).toHaveCount(3);

	await cards.first().focus();
	await page.keyboard.press('a');
	await expect(cards).toHaveCount(2);
	// Focus stays in place, on the next suggestion.
	await expect(cards.first()).toBeFocused();
	await page.keyboard.press('d');
	await expect(cards).toHaveCount(1);
	await expect(page.getByText('1 cara confirmada')).toBeVisible();
	await expect(page.getByText('1 sugerencia descartada')).toBeVisible();

	await page.getByRole('button', { name: 'Aceptar todas' }).click();
	const confirm = page.getByRole('dialog', { name: 'Aceptar todas' });
	await expect(confirm).toContainText('Se asignará 1 cara sugerida a Ana.');
	await confirm.getByRole('button', { name: 'Aceptar todas' }).click();

	await expect(page.getByText('No hay sugerencias pendientes.')).toBeVisible();
	const faces = peopleState(api.state).faces;
	expect(faces.filter((f) => f.personId === 'person-ana')).toHaveLength(16);
});

test('accepts or dismisses a suggestion with its buttons', async ({ page }) => {
	await fakeApi(page, { signedIn: true });
	await page.goto('/people/person-marta/suggestions');
	await expect(page.getByRole('group', { name: /^Cara sugerida/ })).toHaveCount(1);

	await page.getByRole('button', { name: 'Es Marta', exact: true }).click();

	await expect(page.getByText('No hay sugerencias pendientes.')).toBeVisible();
	await expect(page.getByText('5 caras')).toBeVisible();
});

test('an unknown person shows a way back', async ({ page }) => {
	await fakeApi(page, { signedIn: true });
	await page.goto('/people/nobody');

	await expect(page.getByText('Esta persona no existe o ya no está disponible.')).toBeVisible();
	await page.getByRole('link', { name: 'Volver a Personas' }).click();
	await expect(page).toHaveURL(/\/people$/);
});
