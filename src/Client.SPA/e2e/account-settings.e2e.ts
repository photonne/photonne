import { expect, test } from '@playwright/test';
import { fakeApi } from './fake-api';
import { accountState } from './fakes/account';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

test('settings open on the profile and move between sections', async ({ page }) => {
	await fakeApi(page, { signedIn: true });

	await page.goto('/settings');
	await expect(page).toHaveURL(/\/settings\/profile$/);
	const nav = page.getByRole('navigation', { name: 'Ajustes' });
	await expect(nav.getByRole('link', { name: 'Perfil' })).toHaveAttribute('aria-current', 'page');

	await nav.getByRole('link', { name: 'Almacenamiento' }).click();
	await expect(page.getByRole('heading', { name: 'Almacenamiento', level: 1 })).toBeVisible();
	await expect(page.getByRole('meter', { name: 'Espacio usado' })).toHaveAttribute(
		'aria-valuenow',
		'95'
	);
	await expect(page.getByText(/casi lleno/)).toBeVisible();
	await expect(page.getByRole('row', { name: /NAS familiar/ })).toBeVisible();
});

test('edits the profile and confirms a username change', async ({ page }) => {
	const { state } = await fakeApi(page, { signedIn: true });

	await page.goto('/settings/profile');
	const save = page.getByRole('button', { name: 'Guardar cambios' });
	await expect(page.getByLabel('Nombre de usuario')).toHaveValue('ana');
	await expect(save).toBeDisabled();

	await page.getByLabel('Correo electrónico').fill('taken@photonne.test');
	await save.click();
	await expect(page.getByRole('alert')).toHaveText('Ese correo ya está en uso.');

	await page.getByLabel('Correo electrónico').fill('ana@photonne.test');
	await page.getByLabel('Nombre', { exact: true }).fill('Ana');
	await page.getByLabel('Nombre de usuario').fill('ana garcia');
	await expect(page.getByText(/solo puede tener letras/)).toBeVisible();
	await expect(save).toBeDisabled();

	await page.getByLabel('Nombre de usuario').fill('ana.garcia');
	await save.click();
	const dialog = page.getByRole('dialog', { name: '¿Cambiar el nombre de usuario?' });
	await expect(dialog).toContainText('1234 archivos se actualizarán');
	await dialog.getByRole('button', { name: 'Cambiar nombre' }).click();

	await expect(page.getByText('Cambios guardados')).toBeVisible();
	expect(accountState(state).profile).toMatchObject({
		username: 'ana.garcia',
		firstName: 'Ana',
		email: 'ana@photonne.test'
	});
	// The account menu follows the new name.
	await expect(page.getByRole('banner').getByText('ana.garcia')).toBeVisible();
});

test('changes the password, checking the rules as it is typed', async ({ page }) => {
	const { state } = await fakeApi(page, { signedIn: true });

	await page.goto('/settings/security');
	const submit = page.getByRole('button', { name: 'Cambiar contraseña' });
	const rules = page.getByRole('list', { name: 'Requisitos de la contraseña' });

	await page.getByLabel('Contraseña actual').fill('mala');
	await page.getByLabel('Nueva contraseña', { exact: true }).fill('corta');
	await expect(rules.getByRole('listitem').filter({ hasText: '8 caracteres' })).toContainText(
		'(pendiente)'
	);
	await expect(submit).toBeDisabled();

	await page.getByLabel('Nueva contraseña', { exact: true }).fill('Nueva-Clave-2026');
	await page.getByLabel('Repite la nueva contraseña').fill('Nueva-Clave-2025');
	await page.getByLabel('Repite la nueva contraseña').blur();
	await expect(page.getByText('Las contraseñas no coinciden.')).toBeVisible();

	await page.getByLabel('Repite la nueva contraseña').fill('Nueva-Clave-2026');
	await submit.click();
	await expect(page.getByRole('alert')).toHaveText('La contraseña actual no es correcta.');

	await page.getByLabel('Contraseña actual').fill('secreto');
	await page.getByRole('button', { name: 'Mostrar contraseña' }).first().click();
	await expect(page.getByLabel('Contraseña actual')).toHaveAttribute('type', 'text');
	await submit.click();
	await expect(page.getByText('Contraseña cambiada')).toBeVisible();
	await expect(page.getByLabel('Contraseña actual')).toHaveValue('');
	expect(accountState(state).passwordChanges).toEqual([
		{ currentPassword: 'secreto', newPassword: 'Nueva-Clave-2026' }
	]);
});

test('deletes the account after confirming the password', async ({ page }) => {
	const { state } = await fakeApi(page, { signedIn: true });

	await page.goto('/settings/security');
	await page.getByRole('button', { name: 'Eliminar mi cuenta…' }).click();
	const dialog = page.getByRole('dialog', { name: 'Eliminar la cuenta' });
	await dialog.getByLabel('Contraseña', { exact: true }).fill('nope');
	await dialog.getByRole('button', { name: 'Eliminar definitivamente' }).click();
	await expect(dialog.getByRole('alert')).toHaveText('La contraseña no es correcta.');

	await dialog.getByLabel('Contraseña', { exact: true }).fill('secreto');
	await dialog.getByRole('button', { name: 'Eliminar definitivamente' }).click();
	await expect(page).toHaveURL(/\/login/);
	expect(accountState(state).accountDeleted).toBe(true);
});

test('chooses the theme and the language', async ({ page }) => {
	await fakeApi(page, { signedIn: true });

	await page.goto('/settings/appearance');
	await page.getByRole('radio', { name: /Oscuro/ }).check();
	await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
	expect(await page.evaluate(() => localStorage.getItem('photonne.theme'))).toBe('dark');

	await page.getByRole('radio', { name: /Del sistema/ }).check();
	await expect(page.locator('html')).not.toHaveAttribute('data-theme');

	await page.getByRole('radio', { name: 'English' }).check();
	await expect(
		page.getByRole('heading', { name: 'Appearance and language', level: 1 })
	).toBeVisible();
	await expect(page.getByRole('radio', { name: 'English' })).toBeChecked();
});

test('hides a shared folder from the timeline, with undo', async ({ page }) => {
	const { state } = await fakeApi(page, { signedIn: true });
	const shared = (id: string, path: string, excluded: boolean) => ({
		id,
		path,
		name: path.split('/').at(-1),
		parentFolderId: null,
		createdAt: '2026-01-01T00:00:00Z',
		assetCount: 120,
		firstAssetId: null,
		previewAssetIds: [],
		isShared: true,
		isOwner: false,
		canWrite: false,
		canDelete: false,
		sharedWithCount: 2,
		externalLibraryId: null,
		excludedFromDiscovery: excluded,
		isPinned: false,
		pinnedAt: null,
		subFolders: []
	});
	await page.route('**/api/folders', (route) =>
		route.fulfill({
			json: [
				shared('f-fam', '/assets/shared/Familia', false),
				shared('f-old', '/assets/shared/Familia/Antiguas', true)
			]
		})
	);

	await page.goto('/settings/shared-folders');
	await expect(page.getByText('1 oculta de tus fotos')).toBeVisible();
	const familia = page.getByRole('switch', { name: /^Familia/ });
	await expect(familia).toBeChecked();
	await expect(page.getByRole('switch', { name: /^Antiguas/ })).not.toBeChecked();

	await familia.uncheck();
	await expect(page.getByText('«Familia» ya no aparece en tus fotos')).toBeVisible();
	await page.getByRole('button', { name: 'Deshacer' }).click();
	await expect
		.poll(() => accountState(state).discovery)
		.toEqual([
			{ folderId: 'f-fam', included: false },
			{ folderId: 'f-fam', included: true }
		]);
});
