import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';
import { libraryState } from './fakes/library';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

const cells = (page: Page) => page.getByRole('button', { name: /^(Foto|Vídeo), / });

test('unarchives the selection and undoes it', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	await page.goto('/archive');

	await expect(page.getByRole('heading', { name: 'Archivo', level: 1 })).toBeVisible();
	await expect(cells(page)).toHaveCount(12);
	const first = await cells(page).first().getAttribute('data-id');

	await cells(page)
		.first()
		.click({ modifiers: ['ControlOrMeta'] });
	await page.getByRole('button', { name: 'Desarchivar', exact: true }).click();

	await expect(page.getByText('1 desarchivada')).toBeVisible();
	await expect(cells(page)).toHaveCount(11);
	expect(api.restored).toEqual([first]);
	// The selection bar goes with the photos it held.
	await expect(page.getByRole('toolbar')).toHaveCount(0);

	await page.getByRole('button', { name: 'Deshacer' }).click();

	await expect(cells(page)).toHaveCount(12);
	expect(api.removed).toEqual([first]);
});

test('unarchives everything after confirming', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	await page.goto('/archive');
	await expect(cells(page)).toHaveCount(12);

	await page.getByRole('button', { name: 'Desarchivar todo' }).click();
	const dialog = page.getByRole('dialog', { name: 'Desarchivar todo' });
	await expect(dialog).toContainText('volverán al timeline');
	await dialog.getByRole('button', { name: 'Cancelar' }).click();
	expect(libraryState(api.state).calls).toEqual([]);

	await page.getByRole('button', { name: 'Desarchivar todo' }).click();
	await dialog.getByRole('button', { name: 'Desarchivar todo' }).click();

	await expect(page.getByText('Todo desarchivado')).toBeVisible();
	await expect(page.getByText('No hay nada archivado.')).toBeVisible();
	expect(libraryState(api.state).calls).toEqual(['unarchive-all']);
	await expect(page.getByRole('button', { name: 'Desarchivar todo' })).toBeDisabled();
});

test('unarchives from the viewer and moves on to the next photo', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	await page.goto('/archive?asset=2024-07-0');

	const viewer = page.getByRole('dialog');
	await expect(viewer).toBeVisible();
	// The archive viewer doesn't offer archiving again.
	await expect(viewer.getByRole('button', { name: 'Archivar', exact: true })).toHaveCount(0);

	await viewer.getByRole('button', { name: 'Desarchivar' }).click();

	await expect(page).toHaveURL(/asset=2024-07-1/);
	await expect(page.getByText('1 desarchivada')).toBeVisible();
	expect(api.restored).toEqual(['2024-07-0']);

	await page.keyboard.press('Escape');
	await expect(cells(page)).toHaveCount(11);
});
