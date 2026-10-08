import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

const cells = (page: Page) => page.getByRole('button', { name: /^(Foto|Vídeo), / });

async function openFirstPhoto(page: Page) {
	const api = await fakeApi(page, { signedIn: true });
	await page.goto('/');
	await cells(page).first().click();
	await expect(page.getByRole('dialog')).toBeVisible();
	return api;
}

test('opens a photo in the URL, walks with the arrows and closes back to the grid', async ({
	page
}) => {
	await openFirstPhoto(page);
	await expect(page).toHaveURL(/\?asset=2026-09-0$/);
	await expect(page.getByRole('dialog', { name: 'IMG_202609_0.jpg' })).toBeVisible();

	await page.keyboard.press('ArrowRight');
	await expect(page).toHaveURL(/\?asset=2026-09-1$/);
	await expect(page.getByRole('dialog', { name: 'IMG_202609_1.jpg' })).toBeVisible();

	await page.keyboard.press('Escape');
	await expect(page.getByRole('dialog')).toBeHidden();
	await expect(page).toHaveURL(/\/$/);
	await expect(cells(page).nth(1)).toBeFocused();
});

test('the browser Back button closes the viewer', async ({ page }) => {
	await openFirstPhoto(page);

	await page.goBack();

	await expect(page.getByRole('dialog')).toBeHidden();
	await expect(cells(page).first()).toBeVisible();
});

test('a link to a photo opens it directly', async ({ page }) => {
	await fakeApi(page, { signedIn: true });

	await page.goto('/?asset=2026-09-3');

	await expect(page.getByRole('dialog', { name: 'IMG_202609_3.jpg' })).toBeVisible();
	await page.getByRole('button', { name: 'Cerrar' }).click();
	await expect(page).toHaveURL(/\/$/);
});

test('marks a favorite', async ({ page }) => {
	await openFirstPhoto(page);

	await page.getByRole('button', { name: 'Añadir a favoritos' }).click();

	await expect(page.getByRole('button', { name: 'Quitar de favoritos' })).toHaveAttribute(
		'aria-pressed',
		'true'
	);
});

test('shows the info panel and saves a description', async ({ page }) => {
	const api = await openFirstPhoto(page);

	await page.keyboard.press('i');
	const panel = page.getByRole('complementary', { name: 'Información' });
	await expect(panel).toBeVisible();
	await expect(panel.getByText('Apple iPhone 15')).toBeVisible();
	await expect(panel.getByRole('link', { name: 'Barcelona' })).toBeVisible();

	await panel.getByLabel('Descripción').fill('Atardecer en la playa');
	await panel.getByLabel('Descripción').blur();

	await expect(panel.getByText('Guardado')).toBeVisible();
	expect(api.descriptions).toEqual(['Atardecer en la playa']);
});
