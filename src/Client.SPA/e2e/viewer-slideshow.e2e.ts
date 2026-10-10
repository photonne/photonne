import { expect, test } from '@playwright/test';
import { fakeApi } from './fake-api';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

const cells = (page: import('@playwright/test').Page) =>
	page.getByRole('button', { name: /^(Foto|Vídeo), / });

test('plays a slideshow: advances by itself, pauses with Space, moves with the arrows and exits with Esc', async ({
	page
}) => {
	await fakeApi(page, { signedIn: true });
	await page.goto('/');
	await cells(page).first().click();
	await expect(page).toHaveURL(/\?asset=2026-09-0$/);

	await page.keyboard.press('s');
	const controls = page.getByRole('toolbar', { name: 'Controles de la presentación' });
	await expect(controls).toBeVisible();
	await expect(page.getByRole('button', { name: 'Cerrar' })).toBeHidden();

	await controls.getByRole('combobox', { name: 'Tiempo por foto' }).selectOption('3');
	await expect(page).toHaveURL(/\?asset=2026-09-1$/, { timeout: 6_000 });

	await page.keyboard.press(' ');
	await expect(controls.getByText('En pausa')).toBeVisible();
	await page.waitForTimeout(3_500);
	await expect(page).toHaveURL(/\?asset=2026-09-1$/);

	await page.keyboard.press('ArrowRight');
	await expect(page).toHaveURL(/\?asset=2026-09-2$/);
	await page.keyboard.press('ArrowLeft');
	await expect(page).toHaveURL(/\?asset=2026-09-1$/);

	await page.keyboard.press('Escape');
	await expect(controls).toBeHidden();
	await expect(page.getByRole('button', { name: 'Cerrar' })).toBeFocused();
	// Still in the viewer: the first Escape only ends the slideshow.
	await expect(page.getByRole('dialog', { name: 'IMG_202609_1.jpg' })).toBeVisible();
});
