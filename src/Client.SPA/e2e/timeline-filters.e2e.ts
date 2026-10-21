import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

const filters = (page: Page) => page.getByRole('dialog', { name: 'Filtros' });
const months = (page: Page) => page.getByRole('heading', { level: 2, name: /de 20\d\d$/ });

async function openTimeline(page: Page) {
	await fakeApi(page, { signedIn: true });
	await page.goto('/');
	await expect(page.getByRole('heading', { name: 'Septiembre de 2026' })).toBeVisible();
}

async function openFilters(page: Page) {
	if (!(await filters(page).isVisible()))
		await page.getByRole('button', { name: 'Filtros' }).click();
	await expect(filters(page)).toBeVisible();
}

test('shows a range of days, oldest first, and clears it', async ({ page }) => {
	await openTimeline(page);
	await openFilters(page);
	await filters(page).getByRole('radio', { name: 'Un rango' }).check();
	await filters(page).getByLabel('Desde').fill('2026-08-25');
	await filters(page).getByLabel('Hasta').fill('2026-09-27');
	await filters(page).getByLabel('Hasta').dispatchEvent('change');
	await filters(page).getByRole('radio', { name: 'Más antiguas' }).check();
	await page.keyboard.press('Escape');

	// Only those days, in the two months they span, the oldest first.
	await expect(months(page)).toHaveText([/agosto de 2026/i, /septiembre de 2026/i]);
	await expect(page.getByRole('heading', { name: 'Fotos', level: 1 })).toContainText(
		'26 elementos · Del 25 de agosto de 2026 al 27 de septiembre de 2026'
	);
	const first = page.getByRole('button', { name: /^(Foto|Vídeo), / }).first();
	await expect(first).toHaveAccessibleName(/25 de agosto de 2026$/);
	// The filters' button says something is on.
	await expect(page.getByRole('button', { name: 'Filtros' }).locator('.dot')).toBeVisible();

	await openFilters(page);
	await filters(page).getByRole('button', { name: 'Quitar filtros' }).click();
	await page.keyboard.press('Escape');
	await expect(months(page).first()).toHaveText(/septiembre de 2026/i);
	await expect(page.getByRole('heading', { name: 'Fotos', level: 1 })).toContainText(
		'327 elementos · 2023 – 2026'
	);
});

test('shows one day', async ({ page }) => {
	await openTimeline(page);
	await openFilters(page);
	await filters(page).getByRole('radio', { name: 'Un día' }).check();
	await filters(page).getByLabel('Día', { exact: true }).fill('2026-08-27');
	await filters(page).getByLabel('Día', { exact: true }).dispatchEvent('change');
	await page.keyboard.press('Escape');

	await expect(months(page)).toHaveText([/agosto de 2026/i]);
	const cells = page.getByRole('button', { name: /^(Foto|Vídeo), / });
	await expect(cells).toHaveCount(1);
	await expect(cells.first()).toHaveAccessibleName(/27 de agosto de 2026$/);
});

test('says when the days chosen have no photos, with a way back', async ({ page }) => {
	await openTimeline(page);
	await openFilters(page);
	await filters(page).getByRole('radio', { name: 'Un día' }).check();
	await filters(page).getByLabel('Día', { exact: true }).fill('2026-07-15');
	await filters(page).getByLabel('Día', { exact: true }).dispatchEvent('change');
	await page.keyboard.press('Escape');

	await expect(page.getByText('No hay fotos en esas fechas.')).toBeVisible();
	await page.getByRole('button', { name: 'Quitar filtros' }).click();
	await expect(page.getByRole('heading', { name: 'Septiembre de 2026' })).toBeVisible();
});

test('remembers the order', async ({ page }) => {
	await openTimeline(page);
	await openFilters(page);
	await filters(page).getByRole('radio', { name: 'Más antiguas' }).check();
	await page.keyboard.press('Escape');
	await expect(months(page).first()).toHaveText(/marzo de 2023/i);

	await page.reload();
	await expect(months(page).first()).toHaveText(/marzo de 2023/i);
});
