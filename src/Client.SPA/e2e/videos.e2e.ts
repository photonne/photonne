import { expect, test } from '@playwright/test';
import { fakeApi } from './fake-api';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

test('the sidebar opens every video and nothing else', async ({ page }) => {
	await fakeApi(page, { signedIn: true });

	await page.goto('/');
	await page.getByRole('link', { name: 'Vídeos' }).click();

	await expect(page).toHaveURL(/\/videos$/);
	await expect(page.getByRole('heading', { name: 'Vídeos', level: 1 })).toBeVisible();
	await expect(page.getByRole('button', { name: /^Vídeo, / }).first()).toBeVisible();
	await expect(page.getByRole('button', { name: /^Foto, / })).toHaveCount(0);
});
