import { expect, test } from '@playwright/test';
import { fakeApi } from './fake-api';

test.use({ locale: 'es-ES' });

test('an address of the previous web client lands on its page', async ({ page }) => {
	await fakeApi(page, { signedIn: true });

	await page.goto('/favoritas');
	await expect(page).toHaveURL(/\/favorites$/);

	await page.goto('/buscar?q=playa');
	await expect(page).toHaveURL(/\/search\?q=playa$/);
});

test('an unknown address says so and leads back to the photos', async ({ page }) => {
	await fakeApi(page, { signedIn: true });

	await page.goto('/no-existe');
	await expect(page.getByRole('heading', { name: 'Esta página no existe' })).toBeVisible();

	await page.getByRole('link', { name: 'Ir a mis fotos' }).click();
	await expect(page.getByRole('heading', { name: 'Fotos' })).toBeVisible();
});
