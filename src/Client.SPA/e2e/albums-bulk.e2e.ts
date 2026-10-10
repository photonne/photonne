import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';
import { albumsApi } from './fakes/albums';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

async function open(page: Page) {
	const api = await fakeApi(page, { signedIn: true });
	const albums = await albumsApi(page, api.state);
	await page.goto('/albums');
	await expect(page.getByRole('heading', { name: 'Álbumes', level: 1 })).toBeVisible();
	return albums;
}

const calls = (state: { log: { call: string }[] }, call: string) =>
	state.log.filter((entry) => entry.call === call);
const card = (page: Page, name: string) =>
	page.getByRole('main').getByRole('link', { name: new RegExp(name) });
const bar = (page: Page) => page.getByRole('toolbar');

test('selects albums with Ctrl, Shift and the check, and Escape clears', async ({ page }) => {
	await open(page);

	await card(page, 'Vacaciones').click({ modifiers: ['ControlOrMeta'] });
	await expect(bar(page).getByText('1 seleccionado', { exact: true })).toBeVisible();
	await expect(page).toHaveURL(/\/albums$/);

	// Once selecting, a plain click selects instead of opening.
	await card(page, 'Perros').click();
	await expect(bar(page).getByText('2 seleccionados')).toBeVisible();
	await expect(bar(page).getByRole('button', { name: 'Eliminar' })).toBeEnabled();
	await expect(bar(page).getByRole('button', { name: 'Salir' })).toBeHidden();

	// Shift extends from the last one toggled to Boda de Lucía, someone else's album.
	await card(page, 'Boda de Lucía').click({ modifiers: ['Shift'] });
	await expect(bar(page).getByText('3 seleccionados')).toBeVisible();
	await expect(bar(page).getByRole('button', { name: 'Eliminar' })).toBeDisabled();
	await expect(bar(page).getByRole('button', { name: 'Salir' })).toBeDisabled();

	await page.getByRole('button', { name: 'Seleccionar «Perros»' }).click();
	await expect(page.getByRole('button', { name: 'Seleccionar «Perros»' })).toHaveAttribute(
		'aria-pressed',
		'false'
	);
	await expect(bar(page).getByText('2 seleccionados')).toBeVisible();

	await page.keyboard.press('Escape');
	await expect(bar(page)).toBeHidden();
});

test('deletes several albums after confirming', async ({ page }) => {
	const state = await open(page);

	await page.getByRole('button', { name: 'Seleccionar «Vacaciones»' }).click();
	await page.getByRole('button', { name: 'Seleccionar «Perros»' }).click();
	await bar(page).getByRole('button', { name: 'Eliminar' }).click();

	const dialog = page.getByRole('dialog', { name: '¿Eliminar 2 álbumes?' });
	await expect(dialog).toContainText('Las fotos no se borran');
	await dialog.getByRole('button', { name: 'Eliminar' }).click();

	await expect(page.getByText('2 álbumes eliminados')).toBeVisible();
	expect(calls(state, 'DELETE /api/albums/album-1')).toHaveLength(1);
	expect(calls(state, 'DELETE /api/albums/album-2')).toHaveLength(1);
	await expect(card(page, 'Vacaciones')).toBeHidden();
	await expect(card(page, 'Perros')).toBeHidden();
	await expect(card(page, 'Boda de Lucía')).toBeVisible();
	await expect(bar(page)).toBeHidden();
});

test('leaves an album shared with me', async ({ page }) => {
	const state = await open(page);

	await page.getByRole('button', { name: 'Seleccionar «Boda de Lucía»' }).click();
	await expect(bar(page).getByRole('button', { name: 'Eliminar' })).toBeDisabled();
	await bar(page).getByRole('button', { name: 'Salir' }).click();
	await page
		.getByRole('dialog', { name: '¿Salir de 1 álbum?' })
		.getByRole('button', { name: 'Salir' })
		.click();

	await expect(page.getByText('Has salido de 1 álbum')).toBeVisible();
	expect(calls(state, 'POST /api/albums/album-3/leave')).toHaveLength(1);
	await expect(card(page, 'Boda de Lucía')).toBeHidden();
});

test('switches to a list grouped by year and remembers it', async ({ page }) => {
	await open(page);

	await page.getByRole('radio', { name: 'Lista' }).check();
	await page.getByRole('switch', { name: 'Agrupar por año' }).check();

	await expect(page.getByRole('region', { name: 'Álbumes creados en 2026' })).toBeVisible();
	const older = page.getByRole('region', { name: 'Álbumes creados en 2025' });
	await expect(older.getByRole('link')).toHaveCount(1);
	await expect(older.getByRole('link', { name: /Boda de Lucía/ })).toBeVisible();
	await expect(page.locator('ul.rows').first()).toBeVisible();

	await page.reload();
	await expect(page.getByRole('radio', { name: 'Lista' })).toBeChecked();
	await expect(page.getByRole('switch', { name: 'Agrupar por año' })).toBeChecked();
	await expect(page.getByRole('region', { name: 'Álbumes creados en 2025' })).toBeVisible();
});
