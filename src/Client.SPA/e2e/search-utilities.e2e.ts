import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';
import { pinSearchFakes } from './fakes/search';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

async function open(page: Page, path: string) {
	const api = await fakeApi(page, { signedIn: true });
	await pinSearchFakes(page, api.state);
	await page.goto(path);
	return api;
}

test('the hub shows live figures and leads to each tool', async ({ page }) => {
	await open(page, '/utilities');

	const duplicates = page.getByRole('link', { name: /Duplicados/ });
	await expect(duplicates).toContainText('2 grupos · 8,6 MB recuperables');
	await expect(page.getByRole('link', { name: /Archivos no compatibles/ })).toContainText(
		'3 archivos'
	);

	await duplicates.click();
	await expect(page.getByRole('heading', { name: 'Duplicados', level: 1 })).toBeVisible();
	await page.getByRole('link', { name: 'Utilidades' }).last().click();
	await expect(page).toHaveURL(/\/utilities$/);
});

test('keeps the oldest copy of each group and trashes the rest after confirming', async ({
	page
}) => {
	const api = await open(page, '/utilities/duplicates');

	await expect(page.getByText('2 grupos · hasta 8,6 MB recuperables')).toBeVisible();
	const trash = page.getByRole('button', { name: 'Mover 3 a la papelera' });
	await expect(
		page.getByRole('button', { name: 'Mover a la papelera', exact: true })
	).toBeDisabled();

	await page.getByRole('button', { name: 'Conservar la más antigua en todos' }).click();
	await expect(page.getByText('3 marcadas (8,6 MB)')).toBeVisible();
	await trash.click();

	const dialog = page.getByRole('dialog', { name: '¿Mover las copias a la papelera?' });
	await expect(dialog).toContainText('Se moverán 3 copias (8,6 MB) a la papelera.');
	await dialog.getByRole('button', { name: 'Mover 3 a la papelera' }).click();

	await expect(page.getByText('3 movidas a la papelera')).toBeVisible();
	expect(api.removed.sort()).toEqual(['2026-06-50', '2026-09-20', '2026-09-21']);
	await expect(page.getByText('No hay copias exactas en tu biblioteca.')).toBeVisible();

	await page.getByRole('button', { name: 'Deshacer' }).click();
	await expect(page.getByText('2 grupos · hasta 8,6 MB recuperables')).toBeVisible();
	expect(api.restored.sort()).toEqual(api.removed.sort());
});

test('a group always keeps one copy', async ({ page }) => {
	await open(page, '/utilities/duplicates');
	const group = page.getByRole('region', { name: 'Grupo 2' });

	await group.getByRole('checkbox', { name: /en \/assets\/users\/ana\/Camera$/ }).check();
	await group.getByRole('checkbox', { name: /en \/assets\/users\/ana\/Backup$/ }).click();

	await expect(page.getByText('Cada grupo conserva al menos una copia.')).toBeVisible();
	await expect(group.getByRole('checkbox', { name: /Backup$/ })).not.toBeChecked();

	await group
		.getByRole('button', { name: /^Conservar solo .* en \/assets\/users\/ana\/Backup$/ })
		.click();
	await expect(group.getByRole('checkbox', { name: /Camera$/ })).toBeChecked();
	await expect(group.getByRole('checkbox', { name: /Backup$/ })).not.toBeChecked();
	await expect(page.getByRole('button', { name: 'Mover 1 a la papelera' })).toBeEnabled();
});

test('a duplicate opens in the viewer', async ({ page }) => {
	await open(page, '/utilities/duplicates');

	await page.getByRole('button', { name: 'Ver IMG_202609_0.jpg' }).first().click();

	await expect(page.getByRole('dialog')).toBeVisible();
	await expect(page).toHaveURL(/\/utilities\/duplicates\?asset=2026-09-0$/);
	await page.keyboard.press('Escape');
	await expect(page.getByRole('dialog')).toHaveCount(0);
});

test('large files can be picked and trashed, with undo', async ({ page }) => {
	const api = await open(page, '/utilities/large-files');

	await expect(page.getByRole('row')).toHaveCount(51);
	await page.getByRole('combobox', { name: 'Mostrar' }).selectOption('25');
	await expect(page.getByRole('row')).toHaveCount(26);
	await expect(page.getByText('Los 25 elementos que más ocupan')).toBeVisible();

	await page.getByRole('checkbox', { name: 'Seleccionar IMG_202609_0.jpg' }).check();
	await page.getByRole('checkbox', { name: 'Seleccionar IMG_202609_1.jpg' }).check();
	await page.getByRole('button', { name: 'Mover 2 a la papelera' }).click();

	await expect(page.getByText('2 movidas a la papelera')).toBeVisible();
	expect(api.removed).toEqual(['2026-09-0', '2026-09-1']);
	await page.getByRole('button', { name: 'Deshacer' }).click();
	await expect.poll(() => api.restored).toEqual(['2026-09-0', '2026-09-1']);
});

test('locations expand, collapse and filter the folder tree', async ({ page }) => {
	await open(page, '/utilities/locations');

	await expect(page.getByText('6 carpetas · 135 archivos')).toBeVisible();
	await expect(page.getByRole('link', { name: 'Viajes' })).toHaveCount(0);

	await page.getByRole('button', { name: 'Mostrar u ocultar las subcarpetas de ana' }).click();
	await expect(page.getByRole('link', { name: 'Viajes' })).toBeVisible();
	await expect(page.getByRole('link', { name: 'Roma' })).toHaveCount(0);

	await page.getByRole('button', { name: 'Expandir todo' }).click();
	await expect(page.getByRole('link', { name: 'Roma' })).toBeVisible();

	await page.getByRole('button', { name: 'Contraer todo' }).click();
	await expect(page.getByRole('link', { name: 'Viajes' })).toHaveCount(0);

	await page.getByRole('searchbox', { name: 'Filtrar carpetas' }).fill('lisboa');
	await expect(page.getByRole('link', { name: 'Lisboa' })).toBeVisible();
	await expect(page.getByRole('link', { name: 'Camera' })).toHaveCount(0);
	await expect(page.getByRole('link', { name: 'Lisboa' })).toHaveAttribute(
		'href',
		'/folders/folder-lisboa'
	);
});

test('unsupported files can be downloaded and deleted after confirming', async ({ page }) => {
	const api = await open(page, '/utilities/unsupported');

	await expect(page.getByRole('row')).toHaveCount(4);
	await expect(page.getByRole('button', { name: 'Borrar proyecto.psd del disco' })).toHaveCount(0);

	const download = page.waitForEvent('download');
	await page.getByRole('button', { name: 'Descargar notas.txt' }).click();
	expect((await download).suggestedFilename()).toBe('notas.txt');

	await page.getByRole('button', { name: 'Borrar notas.txt del disco' }).click();
	const dialog = page.getByRole('dialog', { name: '¿Borrar el archivo para siempre?' });
	await expect(dialog).toContainText('«notas.txt» se borrará del disco.');
	await dialog.getByRole('button', { name: 'Borrar para siempre' }).click();

	await expect(page.getByText('«notas.txt» borrado')).toBeVisible();
	await expect(page.getByRole('row')).toHaveCount(3);
	expect(api.state.deletedUnsupported).toEqual(['file-1']);
});
