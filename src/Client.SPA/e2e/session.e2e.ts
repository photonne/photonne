import { expect, test } from '@playwright/test';
import { fakeApi } from './fake-api';

test.use({ locale: 'es-ES' });

test('sends a visitor to the login page and back after signing in', async ({ page }) => {
	await fakeApi(page);

	await page.goto('/');
	await expect(page).toHaveURL(/\/login\?returnTo=%2F$/);

	await page.getByLabel('Usuario o correo').fill('ana');
	await page.getByLabel('Contraseña').fill('mal');
	await page.getByRole('button', { name: 'Entrar' }).click();
	await expect(page.getByRole('alert')).toHaveText('Usuario o contraseña incorrectos.');

	await page.getByLabel('Contraseña').fill('secreto');
	await page.getByRole('button', { name: 'Entrar' }).click();

	await expect(page).toHaveURL(/\/$/);
	await expect(page.getByRole('heading', { name: 'Fotos' })).toBeVisible();
	await expect(page.getByText('Septiembre de 2026')).toBeVisible();
});

test('restores a session from the refresh cookie', async ({ page }) => {
	await fakeApi(page, { signedIn: true });

	await page.goto('/');

	await expect(page.getByRole('heading', { name: 'Fotos' })).toBeVisible();
	await expect(page.getByRole('link', { name: 'Fotos' })).toHaveAttribute('aria-current', 'page');
});

test('signs out', async ({ page }) => {
	await fakeApi(page, { signedIn: true });
	await page.goto('/');

	await page.getByLabel('Cuenta').click();
	await page.getByRole('button', { name: 'Cerrar sesión' }).click();

	await expect(page).toHaveURL(/\/login$/);
});

test('offers a retry when the server is unreachable', async ({ page }) => {
	await fakeApi(page, { offline: true });

	await page.goto('/');

	await expect(
		page.getByRole('heading', { name: 'No se puede conectar con el servidor' })
	).toBeVisible();
	await expect(page.getByRole('button', { name: 'Reintentar' })).toBeVisible();
});

test('a proxy saying the server is down also offers a retry, not the login', async ({ page }) => {
	await fakeApi(page, { signedIn: true });
	// Registered after the fake, so it wins: nginx & co. answer 502 while the server is down.
	await page.route('**/api/auth/refresh', (route) => route.fulfill({ status: 502, body: '' }));

	await page.goto('/');

	await expect(page.getByRole('button', { name: 'Reintentar' })).toBeVisible();
	await page.unroute('**/api/auth/refresh');
	await page.getByRole('button', { name: 'Reintentar' }).click();
	await expect(page.getByRole('heading', { name: 'Fotos' })).toBeVisible();
});
