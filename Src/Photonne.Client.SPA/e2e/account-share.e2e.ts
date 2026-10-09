import { expect, test } from '@playwright/test';
import { fakeApi } from './fake-api';
import { accountState } from './fakes/account';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

test('a visitor without a session browses a shared album and its viewer', async ({ page }) => {
	await fakeApi(page, { signedIn: false });

	await page.goto('/share/boda');
	await expect(page.getByRole('heading', { name: 'Boda de Marta y Joan', level: 1 })).toBeVisible();
	await expect(page.getByText('14 fotos y vídeos')).toBeVisible();
	await expect(page.getByText(/Caduca el 31 de diciembre de 2026/)).toBeVisible();
	await expect(page.getByRole('button', { name: 'Descargar todo' })).toBeVisible();
	// No session: the public page, with a way to sign in and come back here.
	await expect(page.getByRole('navigation', { name: 'Principal' })).toHaveCount(0);
	await expect(page.getByRole('link', { name: 'Iniciar sesión' })).toHaveAttribute(
		'href',
		'/login?returnTo=%2Fshare%2Fboda'
	);

	const grid = page.getByRole('list', { name: 'Boda de Marta y Joan' });
	const cells = grid.getByRole('button', { name: /^(Foto|Vídeo), / });
	await expect(cells).toHaveCount(14);

	// Keyboard: into the grid, along a row, open.
	await cells.first().focus();
	await page.keyboard.press('ArrowRight');
	await expect(cells.nth(1)).toBeFocused();
	await page.keyboard.press('Enter');

	const viewer = page.getByRole('dialog', { name: 'BODA_2.jpg' });
	await expect(viewer).toBeVisible();
	await expect(page).toHaveURL(/asset=/);
	await expect(viewer).toContainText('2 de 14');
	await expect(viewer.getByRole('link', { name: 'Descargar BODA_2.jpg' })).toHaveAttribute(
		'href',
		/\/api\/share\/boda\/asset\/.+\/content\?download=true$/
	);

	await page.keyboard.press('ArrowRight');
	await expect(page.getByRole('dialog', { name: 'BODA_3.jpg' })).toBeVisible();
	await page.keyboard.press('Escape');
	await expect(page.getByRole('dialog')).toHaveCount(0);
	await expect(cells.nth(2)).toBeFocused();
});

test('a password-protected link asks for the password', async ({ page }) => {
	await fakeApi(page);

	await page.goto('/share/privado');
	await expect(page.getByRole('heading', { name: 'Enlace protegido' })).toBeVisible();
	const field = page.getByLabel('Contraseña', { exact: true });
	await field.fill('otra');
	await page.getByRole('button', { name: 'Ver el álbum' }).click();
	await expect(page.getByRole('alert')).toHaveText('La contraseña no es correcta.');

	await field.fill('clave');
	await page.getByRole('button', { name: 'Ver el álbum' }).click();
	await expect(page.getByRole('heading', { name: 'Boda de Marta y Joan', level: 1 })).toBeVisible();
	// This link doesn't allow downloads.
	await expect(page.getByRole('button', { name: 'Descargar todo' })).toHaveCount(0);
	// Media carry the password.
	await expect(page.locator('main img').first()).toHaveAttribute('src', /pw=clave/);

	// A reload in the same tab doesn't ask again.
	await page.reload();
	await expect(page.getByRole('heading', { name: 'Boda de Marta y Joan', level: 1 })).toBeVisible();
});

test.describe('links that no longer work', () => {
	for (const [token, title] of [
		['caducado', 'Este enlace ha caducado'],
		['agotado', 'Este enlace ya no admite más visitas'],
		['no-existe', 'Enlace no encontrado']
	]) {
		test(token, async ({ page }) => {
			await fakeApi(page);
			await page.goto(`/share/${token}`);
			await expect(page.getByRole('heading', { name: title })).toBeVisible();
		});
	}
});

test('a guest adds photos to a photo-request album', async ({ page }) => {
	const { state } = await fakeApi(page);

	await page.goto('/share/boda');
	const card = page.getByRole('region', { name: '¿Tienes fotos de esto?' });
	await card.getByLabel('Tu nombre (opcional)').fill('Laia');
	// Guests can't pick folders.
	await expect(card.getByRole('button', { name: 'Elegir carpeta' })).toHaveCount(0);
	await card.locator('input[type="file"]').setInputFiles([
		{ name: 'invitada-1.jpg', mimeType: 'image/jpeg', buffer: Buffer.from('a') },
		{ name: 'invitada-2.jpg', mimeType: 'image/jpeg', buffer: Buffer.from('b') }
	]);

	await expect(card.getByText('¡Gracias! Se han añadido 2 archivos al álbum.')).toBeVisible();
	expect(accountState(state).guestUploads).toEqual([
		{ token: 'boda', name: 'Laia', pw: null, file: 'invitada-1.jpg' },
		{ token: 'boda', name: 'Laia', pw: null, file: 'invitada-2.jpg' }
	]);
});

test('a signed-in user sees the shared album inside the app', async ({ page }) => {
	await fakeApi(page, { signedIn: true });

	await page.goto('/share/boda');

	await expect(page.getByRole('heading', { name: 'Boda de Marta y Joan', level: 1 })).toBeVisible();
	await expect(page.getByRole('navigation', { name: 'Principal' })).toBeVisible();
	await expect(page.getByRole('link', { name: 'Iniciar sesión' })).toHaveCount(0);
});
