import { expect, test } from '@playwright/test';
import { fakeApi } from './fake-api';
import { TEXT_PHOTO, viewerState } from './fakes/viewer';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

async function openPanel(page: import('@playwright/test').Page, assetId = TEXT_PHOTO) {
	const api = await fakeApi(page, { signedIn: true });
	await page.goto(`/?asset=${assetId}`);
	await expect(page.getByRole('dialog').first()).toBeVisible();
	await page.keyboard.press('i');
	return { api, panel: page.getByRole('complementary', { name: 'Información' }) };
}

test('shows the recognised text, folded, and copies it', async ({ page, context }) => {
	await context.grantPermissions(['clipboard-read', 'clipboard-write']);
	const { panel } = await openPanel(page);

	await expect(panel.getByRole('button', { name: 'Texto reconocido' })).toHaveAttribute(
		'aria-expanded',
		'true'
	);
	await expect(panel.getByText(/Café Central/)).toBeVisible();
	await panel.getByRole('button', { name: 'Copiar el texto' }).click();
	await expect(page.getByText('Texto copiado')).toBeVisible();
	expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(
		'Café Central\nMenú del día 12 €'
	);

	// Folding a section is remembered for the next photo.
	await panel.getByRole('button', { name: 'Texto reconocido' }).click();
	await expect(panel.getByText(/Café Central/)).toBeHidden();
});

test('links the objects and scenes to their explore pages', async ({ page }) => {
	const { panel } = await openPanel(page);

	const objects = panel.getByRole('list', { name: 'Objetos' });
	await expect(objects.getByRole('link')).toHaveText(['dog', 'person']);
	await expect(panel.getByRole('list', { name: 'Escenas' }).getByRole('link')).toHaveText([
		'beach'
	]);
	await objects.getByRole('link', { name: 'dog' }).click();
	await expect(page).toHaveURL(/\/explore\/objects\/dog$/);
});

test('opens a photo of the same day and of the same person', async ({ page }) => {
	const { panel } = await openPanel(page);

	const sameDay = panel.getByRole('list', { name: 'Del mismo día' });
	await expect(sameDay.getByRole('button')).toHaveCount(5);
	await sameDay.getByRole('button', { name: /IMG_202609_1\.jpg/ }).click();
	await expect(page).toHaveURL(/\?asset=2026-09-1$/);

	await expect(panel.getByRole('list', { name: 'Más de Ana' })).toBeVisible();
	await panel
		.getByRole('list', { name: 'Más de Ana' })
		.getByRole('button', { name: /IMG_202609_0\.jpg/ })
		.click();
	await expect(page).toHaveURL(/\?asset=2026-09-0$/);
});

test('suggests the original date and applies it, also to the file', async ({ page }) => {
	const { api, panel } = await openPanel(page);

	await panel.getByRole('button', { name: 'Buscar la fecha original' }).click();
	const found = panel.getByRole('list', { name: 'Fechas encontradas' });
	await expect(found.getByRole('listitem')).toHaveCount(2);
	await expect(found.getByRole('listitem').first()).toContainText('EXIF del archivo');
	await expect(found.getByRole('listitem').nth(1)).toContainText('Actual');
	await expect(panel.getByText('Ahora viene de: la fecha del archivo')).toBeVisible();

	await panel.getByRole('checkbox', { name: /Escribir también en el archivo/ }).check();
	await found.getByRole('button', { name: /^Aplicar .*EXIF del archivo/ }).click();

	await expect(panel.getByText('Guardado')).toBeVisible();
	expect(viewerState(api.state).dates).toEqual([
		{ assetId: TEXT_PHOTO, dateTaken: '2019-07-09T05:36:00.000Z', writeToFile: true }
	]);
});

test('shows where the photo was taken and opens the map there', async ({ page }) => {
	const { panel } = await openPanel(page);

	await expect(panel.getByRole('group', { name: 'Mapa del lugar de la foto' })).toBeVisible();
	await expect(panel.getByRole('link', { name: 'Barcelona' })).toHaveAttribute(
		'href',
		/openstreetmap\.org/
	);
	await panel.getByRole('link', { name: 'Ver en el mapa' }).click();
	await expect(page).toHaveURL(/\/map\?lat=41\.400000&lng=2\.170000&z=14$/);
});

test('reads the capture date in the app language and edits it on request', async ({ page }) => {
	const { api, panel } = await openPanel(page);

	await expect(panel.getByText('viernes, 25 de septiembre de 2026, 10:00')).toBeVisible();
	await expect(panel.locator('input[type="datetime-local"]')).toHaveCount(0);

	await panel.getByRole('button', { name: 'Cambiar la fecha' }).click();
	const field = panel.getByLabel('Fecha y hora');
	await expect(field).toBeFocused();
	await expect(panel.getByRole('button', { name: 'Guardar', exact: true })).toBeDisabled();
	// Escape leaves the field, not the viewer.
	await page.keyboard.press('Escape');
	await expect(field).toBeHidden();
	await expect(page.getByRole('dialog').first()).toBeVisible();
	await expect(panel.getByRole('button', { name: 'Cambiar la fecha' })).toBeFocused();

	await panel.getByRole('button', { name: 'Cambiar la fecha' }).click();
	await field.fill('2026-09-24T08:30');
	await panel.getByRole('button', { name: 'Guardar', exact: true }).click();

	await expect(panel.getByText('Guardado')).toBeVisible();
	await expect(field).toBeHidden();
	expect(viewerState(api.state).dates).toEqual([
		{ assetId: TEXT_PHOTO, dateTaken: '2026-09-24T08:30:00.000Z', writeToFile: false }
	]);
});
