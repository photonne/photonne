import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';
import { linksState } from './fakes/links';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

async function openLinks(page: Page) {
	const api = await fakeApi(page, { signedIn: true });
	await page.goto('/links');
	await expect(page.getByRole('heading', { level: 1, name: 'Mis enlaces' })).toBeVisible();
	return linksState(api);
}

const row = (page: Page, name: string) =>
	page.getByRole('list', { name: 'Mis enlaces' }).getByRole('listitem', { name, exact: true });

test('the sidebar leads to every link with its state and views', async ({ page }) => {
	await fakeApi(page, { signedIn: true });
	await page.goto('/');
	await page
		.getByRole('navigation', { name: 'Navegación principal' })
		.getByRole('link', { name: 'Mis enlaces' })
		.click();
	await expect(page).toHaveURL(/\/links$/);

	await expect(page.getByText('2 enlaces activos · 5 visitas')).toBeVisible();

	const album = row(page, 'Vacaciones');
	await expect(album.getByRole('link', { name: 'Vacaciones' })).toHaveAttribute(
		'href',
		/\/albums\/album-1$/
	);
	const badges = album.getByRole('list', { name: 'Estado del enlace' });
	await expect(badges).toContainText('Activo');
	await expect(badges).toContainText('Con contraseña');
	await expect(badges).toContainText('4 de 10 visitas');
	await expect(album.getByRole('textbox', { name: 'Enlace público' })).toHaveValue(
		'https://fotos.example/share/boda'
	);
	await expect(album.getByRole('link', { name: 'Abrir' })).toHaveAttribute(
		'href',
		'https://fotos.example/share/boda'
	);
	await expect(album.locator('img')).toHaveAttribute('src', /2026-09-0\/thumbnail/);

	const photo = row(page, 'IMG_202609_5.jpg');
	await expect(photo).toContainText('Foto');
	await expect(photo.getByRole('list', { name: 'Estado del enlace' })).toContainText(
		'Sin descargas'
	);
	// A path from the server is completed with this origin.
	await expect(photo.getByRole('textbox', { name: 'Enlace público' })).toHaveValue(
		/^http:\/\/localhost:\d+\/share\/perro$/
	);

	await expect(
		row(page, 'Cumpleaños').getByRole('list', { name: 'Estado del enlace' })
	).toContainText('Caducado');
});

test('filters by name', async ({ page }) => {
	await openLinks(page);

	await page.getByRole('searchbox', { name: 'Buscar por nombre' }).fill('cumpleanos');
	await expect(page.getByRole('list', { name: 'Mis enlaces' }).getByRole('heading')).toHaveText([
		'Cumpleaños'
	]);

	await page.getByRole('searchbox', { name: 'Buscar por nombre' }).fill('nada');
	await expect(page.getByText('Ningún enlace coincide con la búsqueda.')).toBeVisible();
});

test('copies, edits and revokes a link', async ({ page, context }) => {
	await context.grantPermissions(['clipboard-read', 'clipboard-write']);
	const links = await openLinks(page);
	const album = row(page, 'Vacaciones');

	await album.getByRole('button', { name: 'Copiar' }).click();
	await expect(page.getByText('Enlace copiado')).toBeVisible();
	expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(
		'https://fotos.example/share/boda'
	);

	await album.getByRole('button', { name: 'Editar' }).click();
	await expect(album.getByRole('radio', { name: 'Mantener la actual' })).toBeChecked();
	await expect(album.getByRole('textbox', { name: 'Límite de visitas' })).toHaveValue('10');
	await album.getByRole('radio', { name: 'Quitar la contraseña' }).check();
	await album.getByRole('textbox', { name: 'Límite de visitas' }).fill('');
	await album.getByRole('button', { name: 'Guardar' }).click();

	await expect(page.getByText('Enlace actualizado')).toBeVisible();
	expect(links.log.find((entry) => entry.call === 'PATCH /api/share/boda')?.body).toMatchObject({
		password: '',
		maxViews: null,
		allowDownload: true
	});
	const badges = album.getByRole('list', { name: 'Estado del enlace' });
	await expect(badges).not.toContainText('Con contraseña');
	await expect(badges).toContainText('4 visitas');

	await album.getByRole('button', { name: 'Revocar' }).click();
	const confirm = page.getByRole('dialog', { name: '¿Revocar el enlace?' });
	await expect(confirm.getByRole('button', { name: 'Cancelar' })).toBeFocused();
	await confirm.getByRole('button', { name: 'Revocar' }).click();

	await expect(page.getByText('Enlace revocado')).toBeVisible();
	await expect(album).toHaveCount(0);
	expect(links.log.some((entry) => entry.call === 'DELETE /api/share/boda')).toBe(true);
});

test('says what to do when there are no links', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	linksState(api).links = [];
	await page.goto('/links');

	await expect(page.getByText('Aún no has creado enlaces')).toBeVisible();
	await expect(page.getByRole('main').getByRole('searchbox')).toHaveCount(0);
});
