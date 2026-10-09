import { expect, test } from '@playwright/test';
import { admin, fakeAdminApi } from './fakes/admin';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 900 } });

test.describe('dashboard', () => {
	test('shows the server at a glance and links into each section', async ({ page }) => {
		await fakeAdminApi(page);
		await page.goto('/admin');

		await expect(
			page.getByRole('heading', { name: 'Panel de administración', level: 1 })
		).toBeVisible();
		const totals = page.getByRole('region', { name: 'Totales' });
		await expect(totals.getByText('48.210')).toBeVisible();
		await expect(totals.getByText('612 GB')).toBeVisible();

		const status = page.getByRole('region', { name: 'Estado del servidor' });
		await expect(status.getByText('v1.8.2')).toBeVisible();
		await expect(status.getByText('Disponible la v1.9.0')).toBeVisible();
		await expect(status.getByText('12 caducados')).toBeVisible();
		await expect(status.getByText('99,9 %')).toBeVisible();

		// The growth chart is a slider over the months.
		const chart = page.getByRole('slider', { name: /Crecimiento de la biblioteca/ });
		await chart.focus();
		await page.keyboard.press('Home');
		await expect(chart).toHaveAttribute('aria-valuenow', '0');

		await page.getByRole('button', { name: 'Ver los 6 archivos sin indexar' }).click();
		await expect(page.getByText('/mnt/nas/fotos/2019/IMG_0001.HEIC')).toBeVisible();
		await expect(page.getByText('Se muestran los primeros 3.')).toBeVisible();

		await expect(page.getByRole('cell', { name: /Luis Martín/ })).toBeVisible();

		await page.getByRole('link', { name: /Usuarios Crear cuentas/ }).click();
		await expect(page).toHaveURL(/\/admin\/users$/);
	});
});

test.describe('users', () => {
	test('searches, filters and sorts the table', async ({ page }) => {
		await fakeAdminApi(page);
		await page.goto('/admin/users');

		const rows = page.getByRole('row');
		await expect(rows).toHaveCount(5); // header + 4
		await expect(page.getByText('4 de 4 usuarios')).toBeVisible();

		await page.getByRole('searchbox', { name: 'Buscar por nombre, usuario o correo' }).fill('ruiz');
		await expect(rows).toHaveCount(2);
		await expect(page.getByRole('button', { name: 'Marta Ruiz' })).toBeVisible();
		await page.getByRole('searchbox', { name: 'Buscar por nombre, usuario o correo' }).fill('');

		await page.getByRole('button', { name: /^Inactivos/ }).click();
		await expect(rows).toHaveCount(2);
		await expect(page.getByRole('button', { name: 'Jorge', exact: true })).toBeVisible();
		await page.getByRole('button', { name: /^Todos/ }).click();

		await page.getByRole('button', { name: 'Almacenamiento' }).click();
		await expect(page.getByRole('columnheader', { name: 'Almacenamiento' })).toHaveAttribute(
			'aria-sort',
			'descending'
		);
		await expect(rows.nth(1)).toContainText('Ana García');
		await expect(rows.nth(2)).toContainText('Luis Martín');
	});

	test('creates a user following the password rules', async ({ page }) => {
		const api = await fakeAdminApi(page);
		await page.goto('/admin/users');

		await page.getByRole('button', { name: 'Nuevo usuario' }).click();
		const dialog = page.getByRole('dialog', { name: 'Nuevo usuario' });
		// New accounts start from the server defaults (10 GB quota).
		await expect(dialog.getByLabel('Cuota de almacenamiento')).toHaveValue('10');

		await dialog.getByLabel('Nombre de usuario').fill('pablo');
		await dialog.getByLabel('Correo electrónico').fill('pablo@photonne.test');
		await dialog.getByLabel('Contraseña', { exact: true }).fill('corta');
		await dialog.getByRole('button', { name: 'Crear usuario' }).click();
		await expect(dialog.getByText('La contraseña no cumple todas las reglas.')).toBeVisible();

		await dialog.getByLabel('Contraseña', { exact: true }).fill('Secreto-2026');
		await dialog.getByLabel('Nombre', { exact: true }).fill('Pablo');
		await dialog.getByLabel('Rol').selectOption('Admin');
		await dialog.getByRole('button', { name: 'Crear usuario' }).click();

		await expect(page.getByText('Se ha creado «pablo»')).toBeVisible();
		await expect(page.getByRole('button', { name: 'Pablo', exact: true })).toBeVisible();
		const sent = admin(api.state).sent.find((r) => r.method === 'POST' && r.path === '/api/users');
		expect(sent?.body).toMatchObject({
			username: 'pablo',
			role: 'Admin',
			isActive: true,
			storageQuotaBytes: 10 * 1024 ** 3
		});
	});

	test('renames a user after showing what changes', async ({ page }) => {
		const api = await fakeAdminApi(page);
		await page.goto('/admin/users');

		await page.getByRole('button', { name: 'Editar a marta' }).click();
		const dialog = page.getByRole('dialog', { name: 'Editar usuario' });
		await dialog.getByLabel('Nombre de usuario').fill('marta.ruiz');
		await dialog.getByLabel('Cuota de almacenamiento').selectOption('unlimited');
		await dialog.getByRole('button', { name: 'Guardar' }).click();

		const confirm = page.getByRole('dialog', { name: 'Confirmar el cambio de nombre' });
		await expect(confirm.getByText('/assets/users/marta.ruiz')).toBeVisible();
		await expect(confirm.getByText('1520')).toBeVisible();
		await confirm.getByRole('button', { name: 'Renombrar y guardar' }).click();

		await expect(page.getByText('Se han guardado los cambios de «marta.ruiz»')).toBeVisible();
		const sent = admin(api.state).sent.find((r) => r.method === 'PUT');
		expect(sent?.body).toMatchObject({ username: 'marta.ruiz', storageQuotaBytes: -1 });
	});

	test('deactivates with undo, resets a password, promotes and deletes', async ({ page }) => {
		const api = await fakeAdminApi(page);
		await page.goto('/admin/users');

		// The primary admin (me) can't be deleted or deactivated.
		await expect(page.getByRole('button', { name: 'Eliminar a ana' })).toHaveCount(0);
		await expect(page.getByRole('button', { name: 'Desactivar a ana' })).toHaveCount(0);

		await page.getByRole('button', { name: 'Desactivar a marta' }).click();
		await expect(page.getByText('«marta» ya no puede entrar')).toBeVisible();
		await expect(page.getByRole('button', { name: 'Activar a marta' })).toBeVisible();
		await page.getByRole('button', { name: 'Deshacer' }).click();
		await expect(page.getByRole('button', { name: 'Desactivar a marta' })).toBeVisible();

		await page.getByRole('button', { name: 'Restablecer la contraseña de luis' }).click();
		const reset = page.getByRole('dialog', { name: 'Restablecer contraseña' });
		await reset.getByLabel('Nueva contraseña').fill('Otra-Clave-9');
		await reset.getByLabel('Repite la contraseña').fill('Otra-Clave-8');
		await expect(reset.getByText('Las contraseñas no coinciden.')).toBeVisible();
		await expect(reset.getByRole('button', { name: 'Restablecer' })).toBeDisabled();
		await reset.getByLabel('Repite la contraseña').fill('Otra-Clave-9');
		await reset.getByRole('button', { name: 'Restablecer' }).click();
		await expect(page.getByText('Se ha cambiado la contraseña de «luis»')).toBeVisible();

		await page.getByRole('button', { name: 'Hacer a luis administrador principal' }).click();
		await page.getByRole('dialog').getByRole('button', { name: 'Transferir' }).click();
		await expect(page.getByText('«luis» es ahora el administrador principal')).toBeVisible();
		// No longer primary: I can't hand it over again.
		await expect(page.getByRole('button', { name: /administrador principal$/ })).toHaveCount(0);

		await page.getByRole('button', { name: 'Eliminar a jorge' }).click();
		const confirm = page.getByRole('dialog', { name: 'Eliminar usuario' });
		await expect(confirm.getByText('¿Eliminar la cuenta de «jorge»?')).toBeVisible();
		await confirm.getByRole('button', { name: 'Eliminar' }).click();
		await expect(page.getByText('Se ha eliminado «jorge»')).toBeVisible();
		await expect(page.getByRole('button', { name: 'Jorge', exact: true })).toHaveCount(0);
		expect(admin(api.state).sent.map((r) => `${r.method} ${r.path}`)).toEqual([
			'PUT /api/users/00000000-0000-0000-0000-000000000003',
			'PUT /api/users/00000000-0000-0000-0000-000000000003',
			'POST /api/users/00000000-0000-0000-0000-000000000002/reset-password',
			'POST /api/users/00000000-0000-0000-0000-000000000002/promote-to-primary',
			'DELETE /api/users/00000000-0000-0000-0000-000000000004'
		]);
	});
});

test.describe('external libraries', () => {
	test('creates a library and follows its first scan', async ({ page }) => {
		const api = await fakeAdminApi(page);
		await page.goto('/admin/libraries');

		await expect(page.getByRole('heading', { name: 'NAS fotos' })).toBeVisible();
		await page.getByRole('button', { name: 'Nueva biblioteca' }).click();
		const dialog = page.getByRole('dialog', { name: 'Nueva biblioteca externa' });
		await dialog.getByLabel('Nombre').fill('Viajes');
		await dialog.getByLabel('Ruta en el servidor').fill('/srv/no-existe');
		await dialog.getByLabel('Escaneo automático').selectOption('weekly');
		await dialog.getByRole('button', { name: 'Crear biblioteca' }).click();
		await expect(dialog.getByText('Esa carpeta no existe en el servidor.')).toBeVisible();

		await dialog.getByLabel('Ruta en el servidor').fill('/mnt/viajes');
		await dialog.getByRole('button', { name: 'Crear biblioteca' }).click();

		await expect(page.getByText('«Viajes» escaneada: 78 indexados')).toBeVisible();
		const card = page.getByRole('article', { name: 'Viajes' });
		await expect(card.getByText('Cada semana')).toBeVisible();
		const created = admin(api.state).sent.findLast((r) => r.method === 'POST');
		expect(created?.body).toMatchObject({
			name: 'Viajes',
			path: '/mnt/viajes',
			importSubfolders: true,
			cronSchedule: '@weekly'
		});
	});

	test('edits, shares and deletes a library', async ({ page }) => {
		const api = await fakeAdminApi(page);
		await page.goto('/admin/libraries');

		await page.getByRole('button', { name: 'Editar «Archivo escaneado»' }).click();
		const edit = page.getByRole('dialog', { name: 'Editar biblioteca externa' });
		await expect(edit.getByLabel('Escaneo automático')).toHaveValue('manual');
		await edit.getByLabel('Escaneo automático').selectOption('daily');
		await edit.getByLabel('Incluir las subcarpetas').uncheck();
		await edit.getByRole('button', { name: 'Guardar' }).click();
		await expect(
			page.getByText('Se han guardado los cambios de «Archivo escaneado»')
		).toBeVisible();
		expect(admin(api.state).sent.at(-1)?.body).toMatchObject({
			cronSchedule: '@daily',
			importSubfolders: false
		});

		await page.getByRole('button', { name: 'Quién puede ver «NAS fotos»' }).click();
		const access = page.getByRole('dialog', { name: 'Acceso a «NAS fotos»' });
		await expect(access.getByText('marta@photonne.test')).toBeVisible();
		await access.getByLabel('Dar acceso a').selectOption({ label: 'luis (luis@photonne.test)' });
		await access.getByRole('button', { name: 'Dar acceso' }).click();
		await expect(page.getByText('luis ya puede ver «NAS fotos»')).toBeVisible();
		await access.getByRole('button', { name: 'Quitar el acceso a marta' }).click();
		await expect(
			access.getByRole('list', { name: 'Usuarios con acceso' }).getByText('marta@photonne.test')
		).toHaveCount(0);
		await access.getByRole('button', { name: 'Cerrar', exact: true }).click();

		await page.getByRole('button', { name: 'Eliminar «NAS fotos»' }).click();
		await page
			.getByRole('dialog', { name: 'Eliminar biblioteca' })
			.getByRole('button', { name: 'Eliminar' })
			.click();
		await expect(page.getByText('Se ha eliminado «NAS fotos»')).toBeVisible();
		await expect(page.getByRole('heading', { name: 'NAS fotos' })).toHaveCount(0);
	});

	test('re-attaches to a scan already running on the server', async ({ page }) => {
		const api = await fakeAdminApi(page);
		admin(api.state).tasks = [
			{
				id: 'task-scan-1',
				type: 'LibraryScan',
				status: 'Running',
				percentage: 30,
				lastMessage: 'Indexando…',
				startedAt: '2026-10-08T09:00:00Z',
				finishedAt: null,
				parameters: { libraryId: 'lib-2' }
			}
		];
		await page.goto('/admin/libraries');

		await expect(page.getByText('«Archivo escaneado» escaneada: 78 indexados')).toBeVisible();
	});
});

test.describe('backup', () => {
	test('downloads a backup of the chosen level', async ({ page }) => {
		const api = await fakeAdminApi(page);
		await page.goto('/admin/backup');

		await page.getByRole('radio', { name: /Completa \(con IA\)/ }).check();
		const download = page.waitForEvent('download');
		await page.getByRole('button', { name: 'Descargar copia' }).click();
		expect((await download).suggestedFilename()).toBe('photonne_backup_full_20261008_100000.json');
		expect(admin(api.state).backupLevel).toBe('full');
	});

	test('restores a backup only after reading it and confirming', async ({ page }) => {
		const api = await fakeAdminApi(page);
		await page.goto('/admin/backup');

		const restore = page.getByRole('button', { name: 'Restaurar copia…' });
		await expect(restore).toBeDisabled();

		await page.locator('input[type=file]').setInputFiles({
			name: 'copia.json',
			mimeType: 'application/json',
			buffer: Buffer.from(
				JSON.stringify({
					version: '3.0',
					createdAt: '2026-10-01T10:00:00Z',
					includesLibrary: true,
					includesMlData: false,
					users: [{}, {}],
					folders: [{}],
					externalLibraries: [],
					assets: [{}, {}, {}],
					albums: [{}]
				})
			)
		});
		await expect(page.getByText('copia.json')).toBeVisible();
		await expect(page.getByText('Esencial', { exact: true }).last()).toBeVisible();

		await restore.click();
		const confirm = page.getByRole('dialog', { name: 'Restaurar la base de datos' });
		const go = confirm.getByRole('button', { name: 'Restaurar' });
		await expect(go).toBeDisabled();
		await confirm.getByLabel('Entiendo que los datos actuales se borran').check();
		await go.click();

		await expect(page.getByRole('heading', { name: 'Base de datos restaurada' })).toBeVisible();
		expect(admin(api.state).restoredFile).toBe('copia.json');
	});

	test('refuses a file that is not a backup', async ({ page }) => {
		await fakeAdminApi(page);
		await page.goto('/admin/backup');

		await page.locator('input[type=file]').setInputFiles({
			name: 'notas.json',
			mimeType: 'application/json',
			buffer: Buffer.from('{"hola": 1}')
		});
		await expect(
			page.getByText('El archivo no es una copia de seguridad válida de Photonne.')
		).toBeVisible();
		await expect(page.getByRole('button', { name: 'Restaurar copia…' })).toBeDisabled();
	});
});

test.describe('shared trash', () => {
	test('filters by who deleted, restores and purges', async ({ page }) => {
		const api = await fakeAdminApi(page);
		await page.goto('/admin/shared-trash');

		await expect(
			page.getByRole('heading', { name: 'Papelera compartida', level: 1 })
		).toBeVisible();
		const cells = page.getByRole('button', { name: /^(Foto|Vídeo), / });
		await expect(cells).toHaveCount(6);

		await page.getByRole('button', { name: /^marta/ }).click();
		await expect(cells).toHaveCount(3);
		await page.getByRole('button', { name: 'Todos' }).click();
		await expect(cells).toHaveCount(6);

		await cells.first().click({ modifiers: ['ControlOrMeta'] });
		await page.getByRole('button', { name: 'Restaurar' }).click();
		await expect(page.getByText('1 restaurado a su carpeta')).toBeVisible();
		await expect(cells).toHaveCount(5);

		await cells.nth(0).click({ modifiers: ['ControlOrMeta'] });
		await cells.nth(1).click({ modifiers: ['ControlOrMeta'] });
		await page.getByRole('button', { name: 'Eliminar para siempre' }).click();
		const confirm = page.getByRole('dialog', { name: 'Eliminar para siempre' });
		await expect(confirm.getByText('Se borrarán 2 archivos del disco.')).toBeVisible();
		await confirm.getByRole('button', { name: 'Eliminar para siempre' }).click();
		await expect(page.getByText('2 eliminados para siempre')).toBeVisible();
		await expect(cells).toHaveCount(3);

		// From the viewer: restoring moves on to the next photo.
		await cells.first().click();
		await expect(page).toHaveURL(/\?asset=/);
		const shown = new URL(page.url()).searchParams.get('asset');
		await page.getByRole('button', { name: 'Restaurar' }).click();
		await expect(cells).toHaveCount(2);
		await expect(page).not.toHaveURL(new RegExp(`asset=${shown}`));

		const paths = admin(api.state).sent.map((r) => r.path);
		expect(paths).toEqual([
			'/api/assets/shared-trash/restore',
			'/api/assets/shared-trash/purge',
			'/api/assets/shared-trash/restore'
		]);
	});
});

test.describe('system', () => {
	test('shows versions, release notes and credits, and checks again on demand', async ({
		page
	}) => {
		await fakeAdminApi(page);
		await page.goto('/admin/system');

		await expect(page.getByText('Hay una versión nueva: v1.9.0.')).toBeVisible();
		await expect(page.getByText('Mejoras en el reconocimiento facial')).toBeVisible();
		await expect(page.getByRole('link', { name: 'Ver en GitHub' })).toHaveAttribute(
			'href',
			/releases\/tag\/v1\.9\.0/
		);
		await expect(page.getByText('v1.5.0')).toBeVisible();
		await expect(page.getByText('Desactivado. Es una instalación normal.')).toBeVisible();
		await expect(page.getByText(/Contiene datos de GeoNames/)).toBeVisible();

		const refreshed = page.waitForRequest((r) =>
			r.url().includes('/api/admin/version?refresh=true')
		);
		await page.getByRole('button', { name: 'Comprobar ahora' }).click();
		await refreshed;
	});
});
