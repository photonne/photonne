import { expect, test } from '@playwright/test';
import { fakeApi } from './fake-api';

test.use({ locale: 'es-ES' });

test('an address of the previous web client lands on its page', async ({ page }) => {
	await fakeApi(page, { signedIn: true });

	await page.goto('/favoritas');
	await expect(page).toHaveURL(/\/favorites$/);

	await page.goto('/buscar?q=playa');
	await expect(page).toHaveURL(/\/search\?q=playa$/);
});

test('an unknown address says so and leads back to the photos', async ({ page }) => {
	await fakeApi(page, { signedIn: true });

	await page.goto('/no-existe');
	await expect(page.getByRole('heading', { name: 'Esta página no existe' })).toBeVisible();

	await page.getByRole('link', { name: 'Ir a mis fotos' }).click();
	await expect(page.getByRole('heading', { name: 'Fotos' })).toBeVisible();
});

test('the menu folds into a rail of icons and stays as it was left', async ({ page }) => {
	await page.setViewportSize({ width: 1400, height: 900 });
	await fakeApi(page, { signedIn: true });
	await page.goto('/');

	const sidebar = page.getByRole('navigation', { name: 'Navegación principal' });
	const albums = sidebar.getByRole('link', { name: 'Álbumes' });
	const wide = (await sidebar.boundingBox())!.width;

	await page.getByRole('button', { name: 'Contraer el menú' }).click();
	await expect(page.getByRole('button', { name: 'Expandir el menú' })).toHaveAttribute(
		'aria-expanded',
		'false'
	);
	// Narrow, but every page is still one click away, by name.
	await expect.poll(async () => (await sidebar.boundingBox())!.width).toBeLessThan(wide / 2);
	await expect(albums).toHaveAttribute('title', 'Álbumes');

	await page.reload();
	await expect(page.getByRole('button', { name: 'Expandir el menú' })).toBeVisible();
	await albums.click();
	await expect(page).toHaveURL(/\/albums$/);

	await page.getByRole('button', { name: 'Expandir el menú' }).click();
	await expect(albums).not.toHaveAttribute('title');
});

test('on a narrow window the menu starts as a rail', async ({ page }) => {
	await page.setViewportSize({ width: 1000, height: 800 });
	await fakeApi(page, { signedIn: true });
	await page.goto('/');

	await expect(page.getByRole('button', { name: 'Expandir el menú' })).toBeVisible();
});

test('the menu follows the native app: photos, collections, actions', async ({ page }) => {
	await page.setViewportSize({ width: 1400, height: 900 });
	await fakeApi(page, { signedIn: true });
	await page.goto('/');

	const sidebar = page.getByRole('navigation', { name: 'Navegación principal' });
	await expect(sidebar.getByRole('heading')).toHaveText(['Colecciones', 'Fijados', 'Acciones']);
	// Pinned albums go right after the collections, as in the native app.
	const links = sidebar.getByRole('link');
	await expect
		.poll(async () => (await links.allTextContents()).map((text) => text.trim()))
		.toEqual([
			'Fotos',
			'Recuerdos',
			'Personas',
			'Favoritos',
			'Álbumes',
			'Carpetas',
			'Explorar',
			'Mapa',
			'Archivo',
			'Papelera',
			'Vacaciones',
			'Subir',
			'Organizar',
			'Mis enlaces',
			'Utilidades'
		]);
});
