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
	// The place's name searches its photos; OpenStreetMap stays a link away.
	await expect(panel.getByRole('link', { name: 'OSM' })).toHaveAttribute(
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

test('resizes the panel by dragging or with the keyboard, and remembers it', async ({ page }) => {
	const { panel } = await openPanel(page);
	const handle = page.getByRole('separator', { name: 'Cambiar el ancho del panel' });
	await expect(handle).toHaveAttribute('aria-valuenow', '340');

	// The panel sits on the right: ArrowLeft widens it.
	await handle.focus();
	await page.keyboard.press('ArrowLeft');
	await page.keyboard.press('ArrowLeft');
	await expect(handle).toHaveAttribute('aria-valuenow', '372');
	await expect(page).toHaveURL(new RegExp(`asset=${TEXT_PHOTO}$`));

	const box = (await handle.boundingBox())!;
	await page.mouse.move(box.x + box.width / 2, box.y + 200);
	await page.mouse.down();
	await page.mouse.move(box.x + box.width / 2 - 100, box.y + 200, { steps: 4 });
	await page.mouse.up();
	await expect(handle).toHaveAttribute('aria-valuenow', '472');
	expect(Math.round((await panel.boundingBox())!.width)).toBe(472);

	// The panel stays open (remembered) once the photo loads: wait for it,
	// not toggle it (an "i" before it shows would close it).
	await page.reload();
	await expect(handle).toBeVisible();
	await expect(handle).toHaveAttribute('aria-valuenow', '472');

	// A narrower window caps it so the photo keeps most of the room.
	await page.setViewportSize({ width: 900, height: 800 });
	await expect(handle).toHaveAttribute('aria-valuemax', '540');
	await handle.focus();
	await page.keyboard.press('End');
	await expect(handle).toHaveAttribute('aria-valuenow', '540');
	await page.setViewportSize({ width: 800, height: 800 });
	await expect(handle).toHaveAttribute('aria-valuenow', '480');

	await handle.dblclick();
	await expect(handle).toHaveAttribute('aria-valuenow', '340');
});

test('the panel closes from its own X', async ({ page }) => {
	const { panel } = await openPanel(page);

	await panel.getByRole('button', { name: 'Cerrar la información' }).click();

	await expect(panel).toBeHidden();
	await expect(page.getByRole('button', { name: 'Información' })).toHaveAttribute(
		'aria-pressed',
		'false'
	);
});

test('links its tags, camera, folder, place and month to their photos', async ({ page }) => {
	const { panel } = await openPanel(page, '2026-09-0');

	await expect(panel.getByRole('link', { name: 'familia' })).toHaveAttribute(
		'href',
		'/explore/tags/familia'
	);
	await expect(panel.getByRole('link', { name: 'playa' })).toHaveAttribute(
		'href',
		'/explore/tags/playa'
	);
	await expect(panel.getByRole('link', { name: 'Apple iPhone 15' })).toHaveAttribute(
		'href',
		'/search?camera=iPhone+15&make=Apple'
	);
	await expect(panel.getByRole('link', { name: 'Barcelona' })).toHaveAttribute(
		'href',
		'/search?place=place-bcn&placeName=Barcelona'
	);
	await expect(panel.getByRole('link', { name: '/assets/users/ana/Camera' })).toHaveAttribute(
		'href',
		'/folders/folder-camera'
	);

	await panel.getByRole('link', { name: 'Ver ese mes en Fotos' }).click();
	await expect(page.getByRole('dialog')).toHaveCount(0);
	await expect(page).toHaveURL(/\/$/);
	await expect(page.getByRole('heading', { name: 'Septiembre de 2026' })).toBeInViewport();
});
