import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';
import { linksState } from './fakes/links';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

const cells = (page: Page) => page.getByRole('button', { name: /^(Foto|Vídeo), / });

async function openTimeline(page: Page) {
	const api = await fakeApi(page, { signedIn: true });
	await page.goto('/');
	await expect(cells(page).first()).toBeVisible();
	return { api, links: linksState(api) };
}

async function select(page: Page, ...indexes: number[]) {
	for (const index of indexes)
		await cells(page)
			.nth(index)
			.click({ modifiers: ['ControlOrMeta'] });
}

const calls = (log: { call: string; body?: unknown }[], call: string) =>
	log.filter((entry) => entry.call === call);

test('shares the selection as a new album with a public link', async ({ page, context }) => {
	await context.grantPermissions(['clipboard-read', 'clipboard-write']);
	const { api, links } = await openTimeline(page);
	await select(page, 0, 3);

	await page.getByRole('toolbar').getByRole('button', { name: 'Compartir con un enlace' }).click();
	const dialog = page.getByRole('dialog', { name: 'Compartir 2 fotos' });
	const name = dialog.getByRole('textbox', { name: 'Nombre del álbum' });
	await expect(name).toBeFocused();
	await expect(name).toHaveValue(/^Compartidas el /);

	await name.fill('Playa con Lucía');
	await dialog.getByRole('button', { name: '1 semana' }).click();
	await dialog.getByRole('textbox', { name: 'Límite de visitas' }).fill('5');
	await dialog.getByRole('checkbox', { name: 'Permitir descargar' }).uncheck();
	await dialog.getByRole('button', { name: 'Crear enlace' }).click();

	const ready = page.getByRole('dialog', { name: 'Enlace listo' });
	const url = ready.getByRole('textbox', { name: 'Enlace público' });
	await expect(url).toHaveValue(/\/share\/nuevo4$/);
	await expect(url).toBeFocused();
	await expect(ready.getByRole('link', { name: 'Ver el álbum' })).toHaveAttribute(
		'href',
		/\/albums\/shared-1$/
	);

	expect(calls(links.log, 'POST /api/albums')[0].body).toEqual({ name: 'Playa con Lucía' });
	expect(api.added).toEqual(['2026-09-0', '2026-09-3']);
	expect(calls(links.log, 'POST /api/share')[0].body).toMatchObject({
		albumId: 'shared-1',
		maxViews: 5,
		allowDownload: false,
		allowUpload: false,
		password: null
	});
	// The selection was what got shared: it is done with.
	await expect(page.getByRole('toolbar')).toBeHidden();

	await ready.getByRole('button', { name: 'Copiar' }).click();
	await expect(page.getByText('Enlace copiado')).toBeVisible();
	expect(await page.evaluate(() => navigator.clipboard.readText())).toMatch(/\/share\/nuevo4$/);

	await ready.getByRole('button', { name: 'Cerrar', exact: true }).click();
	await expect(ready).toBeHidden();
});

test('S opens the dialog and asks for a name before creating anything', async ({ page }) => {
	const { links } = await openTimeline(page);
	await select(page, 1);

	await page.keyboard.press('s');
	const dialog = page.getByRole('dialog', { name: 'Compartir 1 foto' });
	await expect(dialog).toBeVisible();

	await dialog.getByRole('textbox', { name: 'Nombre del álbum' }).fill('   ');
	await dialog.getByRole('button', { name: 'Crear enlace' }).click();
	await expect(dialog.getByText('Escribe un nombre para el álbum.')).toBeVisible();
	await expect(dialog.getByRole('textbox', { name: 'Nombre del álbum' })).toBeFocused();
	expect(calls(links.log, 'POST /api/albums')).toHaveLength(0);

	// Escape closes the dialog and leaves the selection as it was.
	await page.keyboard.press('Escape');
	await expect(dialog).toBeHidden();
	await expect(page.getByRole('toolbar')).toContainText('1 seleccionado');
});

test('a failed link removes the album it had just created', async ({ page }) => {
	const { links } = await openTimeline(page);
	links.failShare = true;
	await select(page, 0);

	await page.getByRole('toolbar').getByRole('button', { name: 'Compartir con un enlace' }).click();
	const dialog = page.getByRole('dialog', { name: 'Compartir 1 foto' });
	await dialog.getByRole('button', { name: 'Crear enlace' }).click();

	await expect(page.getByText('No se ha podido crear el enlace.')).toBeVisible();
	await expect(dialog).toBeVisible();
	await expect.poll(() => calls(links.log, 'DELETE /api/albums/shared-1').length).toBe(1);
	await expect(page.getByRole('toolbar')).toBeVisible();
});

test('shares the photo open in the viewer', async ({ page }) => {
	const { links } = await openTimeline(page);
	await cells(page).nth(1).click();
	await expect(page).toHaveURL(/asset=/);

	await page.getByRole('button', { name: 'Compartir', exact: true }).click();
	const dialog = page.getByRole('dialog', { name: 'Compartir 1 foto' });
	await dialog.getByRole('button', { name: 'Crear enlace' }).click();
	await expect(page.getByRole('dialog', { name: 'Enlace listo' })).toBeVisible();
	expect(calls(links.log, 'POST /api/share')).toHaveLength(1);
});
