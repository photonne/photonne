import { expect, test } from '@playwright/test';
import { fakeApi } from './fake-api';
import { LIVE_ID, viewerState } from './fakes/viewer';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

test('picks a frame of a Live Photo and saves it as a photo', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	await page.goto(`/?asset=${LIVE_ID}`);
	await expect(page.getByRole('dialog', { name: 'IMG_LIVE.HEIC' })).toBeVisible();

	await page.getByRole('button', { name: 'Más acciones' }).click();
	await page.getByRole('menuitem', { name: 'Elegir fotograma' }).click();
	const picker = page.getByRole('region', { name: 'Elegir fotograma' });
	await expect(picker.getByText('Fotograma 16 de 30')).toBeVisible();
	await expect(picker.getByRole('img', { name: 'Fotograma 16 de 30' })).toBeVisible();

	await page.keyboard.press('ArrowRight');
	await expect(picker.getByText('Fotograma 17 de 30')).toBeVisible();
	await picker.getByRole('button', { name: 'Ir al fotograma 1', exact: true }).click();
	await expect(picker.getByText('Fotograma 1 de 30')).toBeVisible();
	await picker.getByRole('slider', { name: 'Fotograma' }).fill('9');
	await expect(picker.getByText('Fotograma 10 de 30')).toBeVisible();

	await picker.getByRole('button', { name: 'Guardar como foto' }).click();
	await expect(page.getByText('Fotograma guardado como foto nueva')).toBeVisible();
	await expect(picker).toBeHidden();
	expect(viewerState(api.state).savedFrames).toEqual([9]);

	// The toast opens the new photo.
	await page.getByRole('button', { name: 'Abrir', exact: true }).click();
	await expect(page).toHaveURL(/\?asset=2026-09-1$/);
});

test('Escape closes the frame picker before the viewer', async ({ page }) => {
	await fakeApi(page, { signedIn: true });
	await page.goto(`/?asset=${LIVE_ID}`);
	await page.getByRole('button', { name: 'Más acciones' }).click();
	await page.getByRole('menuitem', { name: 'Elegir fotograma' }).click();
	const picker = page.getByRole('region', { name: 'Elegir fotograma' });
	await expect(picker).toBeVisible();

	await page.keyboard.press('Escape');
	await expect(picker).toBeHidden();
	await expect(page.getByRole('dialog', { name: 'IMG_LIVE.HEIC' })).toBeVisible();
});
