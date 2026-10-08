import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';
import { albumsApi } from './fakes/albums';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

async function open(page: Page) {
	const api = await fakeApi(page, { signedIn: true });
	const state = await albumsApi(page, api.state);
	await page.goto('/folders');
	await expect(page.getByRole('heading', { name: 'Carpetas', level: 1 })).toBeVisible();
	return state;
}

const calls = (state: { log: { call: string }[] }, call: string) =>
	state.log.filter((entry) => entry.call === call);
const main = (page: Page) => page.locator('.main');
const names = (page: Page) => main(page).locator('li .name');
const bar = (page: Page) => page.getByRole('toolbar');

test('searches at any depth, filters by scope, sorts and remembers the view', async ({ page }) => {
	await open(page);
	await expect(names(page)).toHaveText(['Camera', 'Documentos', 'Familia']);

	await page.getByRole('searchbox', { name: 'Buscar carpetas' }).fill('2025');
	const results = page.getByRole('list', { name: 'Carpetas encontradas' });
	await expect(results.getByRole('link')).toHaveCount(1);
	await expect(results.getByRole('link', { name: /2025/ })).toContainText('Camera');

	await page.getByRole('searchbox', { name: 'Buscar carpetas' }).fill('');
	await page.getByRole('radio', { name: /Compartidas/ }).check();
	await expect(names(page)).toHaveText(['Familia']);
	await page.getByRole('radio', { name: /Personales/ }).check();
	await expect(names(page)).toHaveText(['Camera', 'Documentos']);
	await page.getByRole('radio', { name: /Externas/ }).check();
	await expect(page.getByText('Ninguna carpeta coincide con el filtro.')).toBeVisible();
	await page.getByRole('radio', { name: /Todas/ }).check();

	await page.getByRole('combobox', { name: 'Ordenar por' }).selectOption('count');
	await page.getByRole('button', { name: 'Orden ascendente' }).click();
	await expect(names(page)).toHaveText(['Camera', 'Familia', 'Documentos']);

	await page.getByRole('radio', { name: 'Lista' }).check();
	await expect(main(page).locator('ul.list')).toBeVisible();

	await page.reload();
	await expect(page.getByRole('radio', { name: 'Lista' })).toBeChecked();
	await expect(page.getByRole('combobox', { name: 'Ordenar por' })).toHaveValue('count');
	// The scope isn't remembered: a filter restored later would look like missing folders.
	await expect(page.getByRole('radio', { name: /Todas/ })).toBeChecked();
});

test('deletes several folders after confirming, only if all can be deleted', async ({ page }) => {
	const state = await open(page);

	await page.getByRole('button', { name: 'Seleccionar «Camera»' }).click();
	await main(page)
		.getByRole('link', { name: /Familia/ })
		.click({ modifiers: ['ControlOrMeta'] });
	await expect(bar(page).getByText('2 seleccionados')).toBeVisible();
	await expect(bar(page).getByRole('button', { name: 'Eliminar' })).toBeDisabled();

	await main(page)
		.getByRole('link', { name: /Familia/ })
		.click();
	await page.getByRole('button', { name: 'Seleccionar «Documentos»' }).click();
	await bar(page).getByRole('button', { name: 'Eliminar' }).click();

	const dialog = page.getByRole('dialog', { name: '¿Eliminar 2 carpetas?' });
	await expect(dialog).toContainText('irán a la papelera');
	await dialog.getByRole('button', { name: 'Eliminar' }).click();

	await expect(page.getByText('2 carpetas eliminadas')).toBeVisible();
	expect(calls(state, 'DELETE /api/folders/folder-1')).toHaveLength(1);
	expect(calls(state, 'DELETE /api/folders/folder-3')).toHaveLength(1);
	await expect(names(page)).toHaveText(['Familia']);
	await expect(bar(page)).toBeHidden();
});

test('moves the selected folders into another one', async ({ page }) => {
	const state = await open(page);

	await page.getByRole('button', { name: 'Seleccionar «Documentos»' }).click();
	await bar(page).getByRole('button', { name: 'Mover' }).click();

	const dialog = page.getByRole('dialog', { name: 'Mover 1 carpeta' });
	await expect(dialog.getByRole('combobox', { name: 'Ubicación' })).toHaveValue('');
	await expect(dialog.getByRole('button', { name: 'Mover' })).toBeDisabled();
	// A folder can't go inside itself: Documentos isn't offered.
	await expect(dialog.getByRole('option', { name: /Documentos/ })).toHaveCount(0);
	await dialog.getByRole('combobox', { name: 'Ubicación' }).selectOption({ label: 'Camera' });
	await dialog.getByRole('button', { name: 'Mover' }).click();

	await expect(page.getByText('1 carpeta movida a «Camera»')).toBeVisible();
	expect(calls(state, 'PUT /api/folders/folder-3')[0].body).toEqual({
		name: 'Documentos',
		parentFolderId: 'folder-1'
	});
	await expect(names(page)).toHaveText(['Camera', 'Familia']);
	await page.keyboard.press('Escape');
});
