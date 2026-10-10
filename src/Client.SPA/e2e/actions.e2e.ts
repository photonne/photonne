import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

const cells = (page: Page) => page.getByRole('button', { name: /^(Foto|Vídeo), / });

async function openTimeline(page: Page) {
	const api = await fakeApi(page, { signedIn: true });
	await page.goto('/');
	await expect(cells(page).first()).toBeVisible();
	return api;
}

async function select(page: Page, ...indexes: number[]) {
	for (const index of indexes)
		await cells(page)
			.nth(index)
			.click({ modifiers: ['ControlOrMeta'] });
}

test('trashes the selection and undoes it', async ({ page }) => {
	const api = await openTimeline(page);
	const first = await cells(page).nth(0).getAttribute('data-id');
	await select(page, 0, 1);

	await page.getByRole('button', { name: 'Mover a la papelera' }).click();

	await expect(page.getByText('2 movidas a la papelera')).toBeVisible();
	await expect(page.locator(`[data-id="${first}"]`)).toHaveCount(0);
	expect(api.removed).toHaveLength(2);

	await page.getByRole('button', { name: 'Deshacer' }).click();

	await expect(page.locator(`[data-id="${first}"]`)).toBeVisible();
	expect(api.restored).toEqual(api.removed);
});

test('Delete trashes the selection', async ({ page }) => {
	const api = await openTimeline(page);
	await select(page, 2);

	await page.keyboard.press('Delete');

	await expect(page.getByText('1 movida a la papelera')).toBeVisible();
	expect(api.removed).toHaveLength(1);
});

test('adds the selection to an album from the picker', async ({ page }) => {
	const api = await openTimeline(page);
	await select(page, 0, 3);

	await page.getByRole('button', { name: 'Añadir a un álbum' }).click();
	await page
		.getByRole('dialog')
		.getByRole('button', { name: /Vacaciones/ })
		.click();

	await expect(page.getByText('2 añadidas a «Vacaciones»')).toBeVisible();
	expect(api.added).toHaveLength(2);
	await expect(page.getByRole('toolbar')).toBeHidden();
});

test('drops a photo on a pinned album', async ({ page }) => {
	const api = await openTimeline(page);

	await cells(page)
		.nth(4)
		.dragTo(page.getByRole('link', { name: 'Vacaciones' }));

	await expect(page.getByText('1 añadida a «Vacaciones»')).toBeVisible();
	expect(api.added).toEqual(['2026-09-4']);
});

test('trashing from the viewer moves on to the next photo', async ({ page }) => {
	const api = await openTimeline(page);
	await cells(page).nth(0).click();
	await expect(page.getByRole('dialog', { name: 'IMG_202609_0.jpg' })).toBeVisible();

	await page.getByRole('dialog').getByRole('button', { name: 'Más acciones' }).click();
	await page.getByRole('dialog').getByRole('menuitem', { name: 'Mover a la papelera' }).click();

	await expect(page.getByRole('dialog', { name: 'IMG_202609_1.jpg' })).toBeVisible();
	expect(api.removed).toEqual(['2026-09-0']);
});
