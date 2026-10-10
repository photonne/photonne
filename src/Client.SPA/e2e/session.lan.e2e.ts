import { expect, test } from '@playwright/test';
import { fakeApi } from './fake-api';

test.use({ locale: 'es-ES' });

test('opens over plain http on the local network', async ({ page }) => {
	await fakeApi(page, { signedIn: true });

	await page.goto('/');

	expect(await page.evaluate(() => isSecureContext)).toBe(false);
	await expect(page.getByRole('heading', { name: 'Fotos' })).toBeVisible();
});

test('copies a new link without the Clipboard API', async ({ page }) => {
	await fakeApi(page, { signedIn: true });
	await page.goto('/');
	expect(await page.evaluate(() => 'clipboard' in navigator)).toBe(false);

	await page
		.getByRole('button', { name: /^(Foto|Vídeo), / })
		.first()
		.click({ modifiers: ['ControlOrMeta'] });
	await page.getByRole('toolbar').getByRole('button', { name: 'Compartir con un enlace' }).click();
	await page.getByRole('button', { name: 'Crear enlace' }).click();
	const ready = page.getByRole('dialog', { name: 'Enlace listo' });
	await ready.getByRole('button', { name: 'Copiar' }).click();

	await expect(page.getByText('Enlace copiado')).toBeVisible();
});
