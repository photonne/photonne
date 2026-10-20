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
			'Vídeos',
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

test('only the link of the page on screen is the current page', async ({ page }) => {
	await page.setViewportSize({ width: 1400, height: 900 });
	await fakeApi(page, { signedIn: true });
	await page.goto('/albums/album-1');

	const sidebar = page.getByRole('navigation', { name: 'Navegación principal' });
	await expect(sidebar.getByRole('link', { name: 'Vacaciones' })).toHaveAttribute(
		'aria-current',
		'page'
	);
	await expect(sidebar.getByRole('link', { name: 'Álbumes' })).toHaveAttribute(
		'aria-current',
		'true'
	);
	await expect(sidebar.getByRole('link', { name: 'Fotos' })).not.toHaveAttribute('aria-current');
});

test('the menu follows the window width until the user chooses', async ({ page }) => {
	await page.setViewportSize({ width: 1400, height: 900 });
	await fakeApi(page, { signedIn: true });
	await page.goto('/');

	await expect(page.getByRole('button', { name: 'Contraer el menú' })).toBeVisible();
	await page.setViewportSize({ width: 1000, height: 900 });
	await expect(page.getByRole('button', { name: 'Expandir el menú' })).toBeVisible();
	await page.setViewportSize({ width: 1400, height: 900 });
	await expect(page.getByRole('button', { name: 'Contraer el menú' })).toBeVisible();

	// Once chosen, the choice stays whatever the width.
	await page.getByRole('button', { name: 'Contraer el menú' }).click();
	await page.setViewportSize({ width: 1300, height: 900 });
	await expect(page.getByRole('button', { name: 'Expandir el menú' })).toBeVisible();
});

test('on a phone the three tabs of the native app replace the menu', async ({ page }) => {
	await page.setViewportSize({ width: 390, height: 800 });
	await fakeApi(page, { signedIn: true });
	await page.goto('/');

	await expect(page.getByRole('navigation', { name: 'Navegación principal' })).toBeHidden();
	const tabs = page.getByRole('navigation', { name: 'Pestañas principales' });
	await expect(tabs.getByRole('link')).toHaveCount(3);
	await expect(tabs.getByRole('link', { name: 'Fotos' })).toHaveAttribute('aria-current', 'page');

	// Colecciones gathers the collections, and stays current inside one.
	await tabs.getByRole('link', { name: 'Colecciones' }).click();
	await expect(page).toHaveURL(/\/collections$/);
	await page.getByRole('main').getByRole('link', { name: 'Álbumes' }).click();
	await expect(page).toHaveURL(/\/albums$/);
	await expect(tabs.getByRole('link', { name: 'Colecciones' })).toHaveAttribute(
		'aria-current',
		'true'
	);

	// Más holds the actions and the account, with the unread count.
	await tabs.getByRole('link', { name: /^Más/ }).click();
	await expect(page).toHaveURL(/\/more$/);
	await expect(page.getByRole('main').getByRole('link', { name: 'Subir' })).toBeVisible();
	await expect(page.getByRole('main').getByRole('link', { name: /Ajustes/ })).toBeVisible();
});

test('on a phone a selection takes the place of the tabs', async ({ page }) => {
	await page.setViewportSize({ width: 390, height: 800 });
	await fakeApi(page, { signedIn: true });
	await page.goto('/');

	const tabs = page.getByRole('navigation', { name: 'Pestañas principales' });
	await expect(tabs).toBeVisible();
	await page
		.getByRole('button', { name: /^Foto, / })
		.first()
		.click({ modifiers: ['ControlOrMeta'] });
	await expect(page.getByRole('toolbar', { name: /^1 seleccionad/ })).toBeVisible();
	await expect(tabs).not.toBeInViewport();

	await page.getByRole('button', { name: 'Quitar selección' }).click();
	await expect(tabs).toBeInViewport();
});

test('Ctrl+K goes anywhere, or searches the photos for the words', async ({ page }) => {
	await page.setViewportSize({ width: 1400, height: 900 });
	await fakeApi(page, { signedIn: true });
	await page.goto('/');
	await expect(page.getByRole('heading', { name: 'Fotos', level: 1 })).toBeVisible();

	await page.keyboard.press('ControlOrMeta+k');
	const palette = page.getByRole('dialog', { name: 'Ir a' });
	await expect(palette).toBeVisible();
	await palette.getByRole('combobox').fill('albu');
	await expect(palette.getByRole('option', { selected: true })).toContainText('Buscar «albu»');
	await page.keyboard.press('ArrowDown');
	await expect(palette.getByRole('option', { selected: true })).toContainText('Álbumes');
	await page.keyboard.press('Enter');
	await expect(page).toHaveURL(/\/albums$/);
	await expect(palette).toBeHidden();

	// The search capsule's hint opens it too; Enter right away searches.
	await page.getByRole('button', { name: 'Abrir la paleta de comandos' }).click();
	await palette.getByRole('combobox').fill('playa');
	await page.keyboard.press('Enter');
	await expect(page).toHaveURL(/\/search\?q=playa$/);
});
