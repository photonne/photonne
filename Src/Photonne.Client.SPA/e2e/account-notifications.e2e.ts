import { expect, test } from '@playwright/test';
import { fakeApi } from './fake-api';
import { accountState } from './fakes/account';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

test('lists notifications, filters unread ones and marks them read', async ({ page }) => {
	const { state } = await fakeApi(page, { signedIn: true });
	const account = accountState(state);

	await page.goto('/notifications');
	await expect(page.getByRole('heading', { name: 'Notificaciones', level: 1 })).toBeVisible();

	const list = page.getByRole('list', { name: 'Notificaciones' });
	await expect(list.getByRole('listitem')).toHaveCount(20);
	await expect(page.getByText('Página 1 de 2')).toBeVisible();
	await expect(list.getByRole('listitem').nth(1)).toContainText('3 avisos agrupados');

	// Only the unread ones.
	await page.getByRole('button', { name: /No leídas/ }).click();
	await expect(page).toHaveURL(/filter=unread/);
	await expect(list.getByRole('listitem')).toHaveCount(2);

	// One by its own button.
	await page
		.getByRole('button', { name: 'Marcar como leída: Fallo al generar miniaturas' })
		.click();
	await expect(list.getByRole('listitem')).toHaveCount(1);
	expect(account.notifications.find((n) => n.id === 'n-2')?.isRead).toBe(true);

	// The rest at once.
	await page.getByRole('button', { name: 'Marcar todas como leídas' }).click();
	await expect(page.getByText('No hay notificaciones sin leer.')).toBeVisible();
	await expect(page.getByRole('button', { name: 'Marcar todas como leídas' })).toBeDisabled();
	expect(account.notifications.every((n) => n.isRead)).toBe(true);
});

test('opening a notification marks it read and follows its link', async ({ page }) => {
	const { state } = await fakeApi(page, { signedIn: true });

	await page.goto('/notifications');
	await page.getByRole('link', { name: 'Álbum compartido visitado' }).click();

	await expect(page).toHaveURL(/\/albums\/album-1$/);
	expect(accountState(state).notifications.find((n) => n.id === 'n-1')?.isRead).toBe(true);
});

test('pages through older notifications', async ({ page }) => {
	await fakeApi(page, { signedIn: true });

	await page.goto('/notifications');
	await page.getByRole('button', { name: 'Siguiente' }).click();
	await expect(page).toHaveURL(/page=2/);
	await expect(page.getByText('Página 2 de 2')).toBeVisible();
	await expect(
		page.getByRole('list', { name: 'Notificaciones' }).getByRole('listitem')
	).toHaveCount(5);
	await expect(page.getByRole('button', { name: 'Siguiente' })).toBeDisabled();
});
