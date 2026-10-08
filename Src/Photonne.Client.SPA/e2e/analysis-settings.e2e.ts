import { expect, test } from '@playwright/test';
import { fakeApi } from './fake-api';
import { analysisState } from './fakes/analysis';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

test('lists my photos with pending or failed analysis and retries them', async ({ page }) => {
	const { state } = await fakeApi(page, { signedIn: true });

	await page.goto('/settings');
	await page
		.getByRole('navigation', { name: 'Ajustes' })
		.getByRole('link', { name: 'Análisis de fotos' })
		.click();

	await expect(page).toHaveURL(/\/settings\/analysis$/);
	await expect(page.getByRole('heading', { name: 'Análisis de fotos', level: 1 })).toBeVisible();
	await expect(page.getByText('1 foto procesándose')).toBeVisible();
	await expect(page.getByText('2 con errores')).toBeVisible();

	const list = page.getByRole('list', { name: 'Fotos pendientes o con errores' });
	const rows = list.locator(':scope > li');
	await expect(rows).toHaveCount(3);
	await expect(rows.first()).toContainText('Pendientes: 2 · En curso: 1 · Fallidas: 0');
	await expect(rows.first().getByRole('button')).toHaveCount(0);

	// One task of one photo.
	const second = rows.nth(1);
	await second.getByRole('button', { name: 'Reintentar Caras' }).click();
	await expect(page.getByText('Caras de «IMG_202609_1.jpg» vuelve a la cola')).toBeVisible();
	await expect(second.getByRole('button', { name: 'Reintentar Caras' })).toBeHidden();
	await expect(second.getByRole('button', { name: 'Reintentar Objetos' })).toBeVisible();
	expect(analysisState(state).calls).toEqual(['retry 2026-09-1 FaceRecognition']);

	// Everything that failed, photo by photo.
	await page.getByRole('button', { name: 'Reintentar todo lo fallido' }).click();
	await expect(page.getByText('Reintentando 2 fotos')).toBeVisible();
	await expect(page.getByText('0 con errores')).toBeVisible();
	await expect(list.getByRole('button', { name: /^Reintentar/ })).toHaveCount(0);
	expect(analysisState(state).calls.slice(1)).toEqual([
		'retry-all 2026-09-1',
		'retry-all 2026-08-3'
	]);
});

test('says so when nothing is pending', async ({ page }) => {
	const { state } = await fakeApi(page, { signedIn: true });
	analysisState(state).items = [];

	await page.goto('/settings/analysis');

	await expect(page.getByText('Todo está analizado.')).toBeVisible();
});
