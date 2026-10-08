import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';
import { albumsApi } from './fakes/albums';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

const cells = (page: Page) => page.getByRole('button', { name: /^(Foto|Vídeo), / });

async function open(page: Page, path: string) {
	const api = await fakeApi(page, { signedIn: true });
	const albums = await albumsApi(page, api.state);
	await page.goto(path);
	return albums;
}

const calls = (state: { log: { call: string }[] }, call: string) =>
	state.log.filter((entry) => entry.call === call);

test.describe('albums list', () => {
	test('shows pinned albums apart and filters by owner and text', async ({ page }) => {
		await open(page, '/albums');

		await expect(page.getByRole('heading', { name: 'Álbumes', level: 1 })).toBeVisible();
		const pinned = page.getByRole('region', { name: 'Fijados' });
		await expect(pinned.getByRole('link', { name: /Vacaciones/ })).toBeVisible();
		const rest = page.getByRole('region', { name: 'Álbumes' });
		await expect(rest.getByRole('link')).toHaveCount(2);

		const main = page.getByRole('main');
		await page.getByRole('radio', { name: /Compartidos conmigo/ }).check();
		await expect(main.getByRole('link', { name: /Boda de Lucía/ })).toBeVisible();
		await expect(main.getByRole('link', { name: /Perros/ })).toBeHidden();
		await expect(main.getByRole('link', { name: /Boda de Lucía/ })).toContainText(
			'Compartido contigo'
		);

		await page.getByRole('radio', { name: /Todos/ }).check();
		await page.getByRole('searchbox', { name: 'Buscar álbumes' }).fill('perr');
		await expect(main.getByRole('link', { name: /Perros/ })).toBeVisible();
		await expect(main.getByRole('link', { name: /Vacaciones/ })).toBeHidden();

		await page.getByRole('searchbox', { name: 'Buscar álbumes' }).fill('nada parecido');
		await expect(page.getByText('Ningún álbum coincide con el filtro.')).toBeVisible();
	});

	test('sorts by name', async ({ page }) => {
		await open(page, '/albums');
		await page.getByRole('combobox', { name: 'Ordenar por' }).selectOption('name');
		await page.getByRole('button', { name: 'Orden descendente' }).click();

		const names = page.getByRole('region', { name: 'Álbumes' }).locator('.name');
		await expect(names).toHaveText(['Boda de Lucía', 'Perros']);
	});

	test('pins an album to the sidebar', async ({ page }) => {
		const state = await open(page, '/albums');

		await page.getByRole('button', { name: 'Fijar «Boda de Lucía»' }).click();

		await expect(page.getByText('«Boda de Lucía» fijado')).toBeVisible();
		expect(calls(state, 'PUT /api/albums/album-3/pin')).toHaveLength(1);
		await expect(
			page.getByRole('navigation', { name: 'Navegación principal' }).getByRole('link', {
				name: 'Boda de Lucía'
			})
		).toBeVisible();
		await expect(page.getByRole('region', { name: 'Fijados' }).getByRole('link')).toHaveCount(2);
	});

	test('creates an album and opens it', async ({ page }) => {
		const state = await open(page, '/albums');

		await page.getByRole('button', { name: 'Nuevo álbum' }).click();
		const dialog = page.getByRole('dialog', { name: 'Nuevo álbum' });
		await dialog.getByLabel('Nombre').fill('Navidad');
		await dialog.getByLabel('Descripción (opcional)').fill('En casa de los abuelos');
		await dialog.getByRole('button', { name: 'Crear' }).click();

		await expect(page.getByRole('heading', { name: 'Navidad', level: 1 })).toBeVisible();
		await expect(page).toHaveURL(/\/albums\/album-new-4$/);
		expect(calls(state, 'POST /api/albums')[0].body).toMatchObject({
			name: 'Navidad',
			description: 'En casa de los abuelos',
			smartRule: null
		});
	});
});

test.describe('album detail', () => {
	test('shows the photos in album order with the description', async ({ page }) => {
		await open(page, '/albums/album-1');

		await expect(page.getByRole('heading', { name: 'Vacaciones', level: 1 })).toBeVisible();
		await expect(page.getByText('Verano en la costa')).toBeVisible();
		await expect(cells(page)).toHaveCount(6);
		await expect(cells(page).first()).toHaveAttribute('data-id', '2026-09-0');
	});

	test('sorts the photos by date and back', async ({ page }) => {
		await open(page, '/albums/album-1');
		await expect(cells(page)).toHaveCount(6);

		await page.getByRole('combobox', { name: 'Orden de las fotos' }).selectOption('oldest');
		// Fake capture days run 28, 27… so the last added is the oldest.
		await expect(cells(page).first()).toHaveAttribute('data-id', '2026-09-5');

		await page.getByRole('combobox', { name: 'Orden de las fotos' }).selectOption('album');
		await expect(cells(page).first()).toHaveAttribute('data-id', '2026-09-0');
	});

	test('removes photos from the album and undoes it', async ({ page }) => {
		const state = await open(page, '/albums/album-1');
		await expect(cells(page)).toHaveCount(6);

		await cells(page)
			.nth(0)
			.click({ modifiers: ['ControlOrMeta'] });
		await cells(page)
			.nth(1)
			.click({ modifiers: ['ControlOrMeta'] });
		await page.getByRole('button', { name: 'Quitar del álbum' }).click();

		await expect(page.getByText('2 quitadas del álbum')).toBeVisible();
		await expect(cells(page)).toHaveCount(4);
		await expect(page.getByRole('toolbar')).toBeHidden();
		expect(
			state.log.filter((e) => e.call.startsWith('DELETE /api/albums/album-1/assets/'))
		).toHaveLength(2);

		await page.getByRole('button', { name: 'Deshacer' }).click();
		await expect(cells(page)).toHaveCount(6);
		expect(calls(state, 'POST /api/albums/album-1/assets/batch')[0].body).toEqual({
			assetIds: ['2026-09-0', '2026-09-1']
		});
	});

	test('sets a photo as the cover', async ({ page }) => {
		const state = await open(page, '/albums/album-1');

		await cells(page)
			.nth(3)
			.click({ modifiers: ['ControlOrMeta'] });
		await page.getByRole('button', { name: 'Usar como portada' }).click();

		await expect(page.getByText('Portada actualizada')).toBeVisible();
		expect(calls(state, 'PUT /api/albums/album-1/cover')[0].body).toEqual({
			assetId: '2026-09-3'
		});

		// Also from the viewer, on the open photo.
		await page.keyboard.press('Escape');
		await cells(page).nth(2).click();
		await page.getByRole('dialog').getByRole('button', { name: 'Usar como portada' }).click();
		await expect.poll(() => calls(state, 'PUT /api/albums/album-1/cover').length).toBe(2);
		expect(calls(state, 'PUT /api/albums/album-1/cover')[1].body).toEqual({
			assetId: '2026-09-2'
		});
	});

	test('edits the name and description', async ({ page }) => {
		const state = await open(page, '/albums/album-1');

		await page.getByRole('button', { name: 'Más acciones' }).click();
		await page.getByRole('menuitem', { name: 'Editar álbum' }).click();
		const dialog = page.getByRole('dialog', { name: 'Editar álbum' });
		await expect(dialog.getByLabel('Nombre')).toHaveValue('Vacaciones');
		await dialog.getByLabel('Nombre').fill('Vacaciones 2026');
		await dialog.getByRole('button', { name: 'Guardar' }).click();

		await expect(page.getByRole('heading', { name: 'Vacaciones 2026', level: 1 })).toBeVisible();
		expect(calls(state, 'PUT /api/albums/album-1')[0].body).toMatchObject({
			name: 'Vacaciones 2026',
			smartRule: null
		});
	});

	test('deletes the album after confirming', async ({ page }) => {
		const state = await open(page, '/albums/album-1');

		await page.getByRole('button', { name: 'Más acciones' }).click();
		await page.getByRole('menuitem', { name: 'Eliminar álbum' }).click();
		const confirm = page.getByRole('dialog', { name: '¿Eliminar el álbum?' });
		await expect(confirm).toContainText('Las fotos no se borran');
		await confirm.getByRole('button', { name: 'Cancelar' }).click();
		expect(calls(state, 'DELETE /api/albums/album-1')).toHaveLength(0);

		await page.getByRole('button', { name: 'Más acciones' }).click();
		await page.getByRole('menuitem', { name: 'Eliminar álbum' }).click();
		await page.getByRole('dialog').getByRole('button', { name: 'Eliminar álbum' }).click();

		await expect(page).toHaveURL(/\/albums$/);
		await expect(page.getByText('Álbum «Vacaciones» eliminado')).toBeVisible();
		await expect(page.getByRole('main').getByRole('link', { name: /Vacaciones/ })).toBeHidden();
	});

	test('leaves an album shared with me, which only offers what a guest can do', async ({
		page
	}) => {
		const state = await open(page, '/albums/album-3');
		await expect(cells(page)).toHaveCount(3);

		await cells(page)
			.first()
			.click({ modifiers: ['ControlOrMeta'] });
		await expect(page.getByRole('button', { name: 'Quitar del álbum' })).toBeHidden();
		await expect(page.getByRole('button', { name: 'Mover a la papelera' })).toBeHidden();
		await page.keyboard.press('Escape');

		await page.getByRole('button', { name: 'Más acciones' }).click();
		await expect(page.getByRole('menuitem', { name: 'Editar álbum' })).toBeHidden();
		await page.getByRole('menuitem', { name: 'Salir del álbum' }).click();
		await page.getByRole('dialog').getByRole('button', { name: 'Salir del álbum' }).click();

		await expect(page).toHaveURL(/\/albums$/);
		expect(calls(state, 'POST /api/albums/album-3/leave')).toHaveLength(1);
	});

	test('keyboard: the actions menu opens and runs with the keyboard', async ({ page }) => {
		await open(page, '/albums/album-1');
		const more = page.getByRole('button', { name: 'Más acciones' });
		await more.focus();
		await page.keyboard.press('ArrowDown');
		await expect(page.getByRole('menuitem', { name: 'Editar álbum' })).toBeFocused();
		await page.keyboard.press('ArrowUp');
		await expect(page.getByRole('menuitem', { name: 'Eliminar álbum' })).toBeFocused();
		await page.keyboard.press('Escape');
		await expect(page.getByRole('menu')).toBeHidden();
		await expect(more).toBeFocused();
	});

	test('a missing album says so', async ({ page }) => {
		await open(page, '/albums/nope');
		await expect(page.getByText('Este álbum no existe o no tienes acceso.')).toBeVisible();
	});
});

test.describe('smart albums', () => {
	test('creates one from conditions with a live preview', async ({ page }) => {
		const state = await open(page, '/albums');

		await page.getByRole('button', { name: 'Álbum inteligente' }).click();
		const dialog = page.getByRole('dialog', { name: 'Nuevo álbum inteligente' });
		await dialog.getByLabel('Nombre').fill('Lucía');
		await expect(dialog.getByRole('button', { name: 'Crear' })).toBeDisabled();

		await dialog.getByRole('button', { name: 'Añadir condición' }).click();
		await page.getByRole('menuitem', { name: 'Personas' }).click();
		await dialog.getByRole('checkbox', { name: /Lucía/ }).check();
		await expect(dialog.getByText('7 fotos cumplen las condiciones')).toBeVisible();

		await dialog.getByRole('button', { name: 'Añadir condición' }).click();
		await page.getByRole('menuitem', { name: 'Tipo de archivo' }).click();
		await dialog.getByRole('combobox', { name: 'Tipo de archivo' }).selectOption('Video');

		await dialog.getByRole('button', { name: 'Crear' }).click();

		await expect(page.getByRole('heading', { name: 'Lucía', level: 1 })).toBeVisible();
		expect(calls(state, 'POST /api/albums')[0].body).toMatchObject({
			name: 'Lucía',
			smartRule: {
				op: 'AND',
				conditions: [
					{ type: 'person', personIds: ['person-1'], match: 'any' },
					{ type: 'mediaType', mediaType: 'Video' }
				]
			}
		});
	});

	test('edits a rule and keeps the conditions it cannot show', async ({ page }) => {
		const state = await open(page, '/albums/album-2');
		await expect(page.getByText('Inteligente', { exact: true })).toBeVisible();

		await page.getByRole('button', { name: 'Más acciones' }).click();
		await page.getByRole('menuitem', { name: 'Editar álbum' }).click();
		const dialog = page.getByRole('dialog', { name: 'Editar álbum' });
		await expect(dialog.getByText(/1 condición más que aquí no se muestra/)).toBeVisible();
		await expect(dialog.getByRole('button', { name: 'Quitar dog' })).toBeVisible();

		await dialog.getByRole('combobox', { name: 'Las fotos deben cumplir' }).selectOption('OR');
		await dialog.getByRole('button', { name: 'Guardar' }).click();

		await expect(page.getByText('Cambios guardados')).toBeVisible();
		expect(calls(state, 'PUT /api/albums/album-2')[0].body).toMatchObject({
			smartRule: {
				op: 'OR',
				conditions: [
					{ type: 'object', labels: ['dog'], match: 'any' },
					{ type: 'not', condition: { type: 'mediaType', mediaType: 'Video' } }
				]
			}
		});
	});

	test('offers no removal: a rule decides what is inside', async ({ page }) => {
		await open(page, '/albums/album-2');
		await cells(page)
			.first()
			.click({ modifiers: ['ControlOrMeta'] });
		await expect(page.getByRole('button', { name: 'Usar como portada' })).toBeVisible();
		await expect(page.getByRole('button', { name: 'Quitar del álbum' })).toBeHidden();
	});
});

test.describe('sharing', () => {
	test('shares with a person, changes the access and stops sharing', async ({ page }) => {
		const state = await open(page, '/albums/album-1');

		await page.getByRole('button', { name: 'Compartir', exact: true }).click();
		const dialog = page.getByRole('dialog', { name: 'Compartir «Vacaciones»' });
		await expect(dialog.getByRole('list', { name: 'Personas con acceso' })).toContainText('luis');

		await dialog.getByRole('combobox', { name: 'Elige a quién…' }).selectOption('user-marta');
		await dialog.getByRole('combobox', { name: 'Permiso', exact: true }).selectOption('contribute');
		await dialog.getByRole('button', { name: 'Compartir', exact: true }).click();
		await expect(page.getByText('Compartido con marta')).toBeVisible();
		expect(calls(state, 'POST /api/albums/album-1/permissions')[0].body).toEqual({
			userId: 'user-marta',
			canRead: true,
			canWrite: true,
			canDelete: false,
			canManagePermissions: false
		});

		await dialog.getByRole('combobox', { name: 'Permiso de luis' }).selectOption('manage');
		await expect.poll(() => calls(state, 'POST /api/albums/album-1/permissions').length).toBe(2);

		await dialog.getByRole('button', { name: 'Dejar de compartir con marta' }).click();
		await expect(page.getByText('Ya no se comparte con marta')).toBeVisible();
		await expect(dialog.getByRole('list', { name: 'Personas con acceso' })).not.toContainText(
			'marta'
		);
	});

	test('creates, edits and revokes a public link', async ({ page, context }) => {
		await context.grantPermissions(['clipboard-read', 'clipboard-write']);
		const state = await open(page, '/albums/album-1');

		await page.getByRole('button', { name: 'Compartir', exact: true }).click();
		const dialog = page.getByRole('dialog', { name: 'Compartir «Vacaciones»' });
		await dialog.getByRole('tab', { name: 'Enlaces públicos' }).click();
		await expect(dialog.getByText('No hay enlaces activos.')).toBeVisible();

		await dialog.getByRole('button', { name: 'Nuevo enlace' }).click();
		await dialog.getByRole('button', { name: '1 semana' }).click();
		await dialog.getByRole('radio', { name: 'Con contraseña' }).check();
		await dialog.getByRole('textbox', { name: 'Límite de visitas' }).fill('0');
		await dialog.getByRole('button', { name: 'Crear enlace' }).click();
		await expect(dialog.getByText('El límite de visitas debe ser')).toBeVisible();

		await dialog.getByRole('textbox', { name: 'Límite de visitas' }).fill('10');
		await dialog.getByRole('button', { name: 'Crear enlace' }).click();
		await expect(dialog.getByText('Escribe la contraseña.')).toBeVisible();
		await dialog.getByPlaceholder('Contraseña').fill('secreto');
		await dialog.getByRole('button', { name: 'Crear enlace' }).click();

		await expect(page.getByText('Enlace creado y copiado')).toBeVisible();
		await expect(dialog.getByRole('textbox', { name: 'Enlace público' })).toHaveValue(
			/\/share\/tok1$/
		);
		await expect(dialog).toContainText('0 de 10 visitas');
		await expect(dialog).toContainText('Con contraseña');
		expect(await page.evaluate(() => navigator.clipboard.readText())).toMatch(/\/share\/tok1$/);
		expect(calls(state, 'POST /api/share')[0].body).toMatchObject({
			albumId: 'album-1',
			password: 'secreto',
			maxViews: 10,
			allowDownload: true,
			allowUpload: false
		});

		await dialog.getByRole('button', { name: 'Editar' }).click();
		await dialog.getByRole('radio', { name: 'Quitar la contraseña' }).check();
		await dialog.getByRole('checkbox', { name: 'Permitir descargar' }).uncheck();
		await dialog.getByRole('button', { name: 'Guardar' }).click();
		await expect(page.getByText('Enlace actualizado')).toBeVisible();
		await expect(dialog).toContainText('Sin descargas');
		expect(calls(state, 'PATCH /api/share/tok1')[0].body).toMatchObject({
			password: '',
			allowDownload: false,
			maxViews: 10
		});

		await dialog.getByRole('button', { name: 'Revocar' }).click();
		const confirm = dialog.getByRole('group', { name: '¿Revocar el enlace?' });
		await expect(confirm.getByRole('button', { name: 'Cancelar' })).toBeFocused();
		await confirm.getByRole('button', { name: 'Revocar' }).click();
		await expect(page.getByText('Enlace revocado')).toBeVisible();
		await expect(dialog.getByText('No hay enlaces activos.')).toBeVisible();
	});
});
