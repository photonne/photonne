import { expect, test } from '@playwright/test';
import { fakeApi } from './fake-api';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

test('lists favorites and drops the ones that lose the heart', async ({ page }) => {
	await fakeApi(page, { signedIn: true });

	await page.goto('/favorites');

	await expect(page.getByRole('heading', { name: 'Favoritos', level: 1 })).toBeVisible();
	const cells = page.getByRole('button', { name: /^(Foto|Vídeo), / });
	const before = await cells.count();
	expect(before).toBeGreaterThan(0);

	await cells.first().click({ modifiers: ['ControlOrMeta'] });
	await page.getByRole('button', { name: 'Favorito', exact: true }).click();

	await expect(page.getByText('1 quitada de favoritas')).toBeVisible();
	await expect(cells).toHaveCount(before - 1);
});
