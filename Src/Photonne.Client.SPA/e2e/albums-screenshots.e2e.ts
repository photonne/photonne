import { test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';
import { albumsApi } from './fakes/albums';

// Not assertions: renders the albums and folders screens to files for visual
// review, wide and narrow.
test.skip(!process.env.SCREENSHOTS, 'only with SCREENSHOTS=1');
test.use({ locale: 'es-ES' });

const widths = [1280, 900];

async function open(page: Page, path: string, width: number) {
	await page.setViewportSize({ width, height: 800 });
	const api = await fakeApi(page, { signedIn: true });
	await albumsApi(page, api.state);
	await page.goto(path);
	await page.waitForTimeout(600);
}

for (const width of widths) {
	test(`albums ${width}`, async ({ page }) => {
		await open(page, '/albums', width);
		await page.screenshot({ path: `test-results/shots/albums-${width}.png` });

		await page.getByRole('button', { name: 'Álbum inteligente' }).click();
		const dialog = page.getByRole('dialog');
		await dialog.getByLabel('Nombre').fill('Lucía en la playa');
		await dialog.getByRole('button', { name: 'Añadir condición' }).click();
		await page.getByRole('menuitem', { name: 'Personas' }).click();
		await dialog.getByRole('checkbox', { name: /Lucía/ }).check();
		await dialog.getByRole('button', { name: 'Añadir condición' }).click();
		await page.getByRole('menuitem', { name: 'Fechas' }).click();
		await page.waitForTimeout(800);
		await page.screenshot({ path: `test-results/shots/smart-${width}.png` });
	});

	test(`album ${width}`, async ({ page }) => {
		await open(page, '/albums/album-1', width);
		await page.screenshot({ path: `test-results/shots/album-${width}.png` });
		await page
			.getByRole('button', { name: /^Foto, / })
			.first()
			.click({ modifiers: ['ControlOrMeta'] });
		await page.screenshot({ path: `test-results/shots/album-selection-${width}.png` });
		await page.keyboard.press('Escape');
		await page.getByRole('button', { name: 'Compartir', exact: true }).click();
		await page.screenshot({ path: `test-results/shots/share-people-${width}.png` });
		await page.getByRole('tab', { name: 'Enlaces públicos' }).click();
		await page.getByRole('button', { name: 'Nuevo enlace' }).click();
		await page.screenshot({ path: `test-results/shots/share-links-${width}.png` });
		await page.keyboard.press('Escape');
		await page.getByRole('button', { name: 'Más acciones' }).click();
		await page.screenshot({ path: `test-results/shots/album-menu-${width}.png` });
	});

	test(`folders ${width}`, async ({ page }) => {
		await open(page, '/folders/folder-1', width);
		await page.screenshot({ path: `test-results/shots/folder-${width}.png` });
		await page.getByRole('button', { name: 'Nueva subcarpeta' }).click();
		await page.screenshot({ path: `test-results/shots/folder-new-${width}.png` });
	});
}
