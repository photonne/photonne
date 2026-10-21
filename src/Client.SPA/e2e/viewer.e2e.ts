import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

const cells = (page: Page) => page.getByRole('button', { name: /^(Foto|Vídeo), / });

async function openFirstPhoto(page: Page) {
	const api = await fakeApi(page, { signedIn: true });
	await page.goto('/');
	await cells(page).first().click();
	await expect(page.getByRole('dialog')).toBeVisible();
	return api;
}

test('opens a photo in the URL, walks with the arrows and closes back to the grid', async ({
	page
}) => {
	await openFirstPhoto(page);
	await expect(page).toHaveURL(/\?asset=2026-09-0$/);
	await expect(page.getByRole('dialog', { name: 'IMG_202609_0.jpg' })).toBeVisible();

	await page.keyboard.press('ArrowRight');
	await expect(page).toHaveURL(/\?asset=2026-09-1$/);
	await expect(page.getByRole('dialog', { name: 'IMG_202609_1.jpg' })).toBeVisible();

	await page.keyboard.press('Escape');
	await expect(page.getByRole('dialog')).toBeHidden();
	await expect(page).toHaveURL(/\/$/);
	await expect(cells(page).nth(1)).toBeFocused();
});

test('the browser Back button closes the viewer', async ({ page }) => {
	await openFirstPhoto(page);

	await page.goBack();

	await expect(page.getByRole('dialog')).toBeHidden();
	await expect(cells(page).first()).toBeVisible();
});

test('a link to a photo opens it directly', async ({ page }) => {
	await fakeApi(page, { signedIn: true });

	await page.goto('/?asset=2026-09-3');

	await expect(page.getByRole('dialog', { name: 'IMG_202609_3.jpg' })).toBeVisible();
	await page.getByRole('button', { name: 'Cerrar' }).click();
	await expect(page).toHaveURL(/\/$/);
});

test('marks a favorite', async ({ page }) => {
	await openFirstPhoto(page);

	await page.getByRole('button', { name: 'Añadir a favoritos' }).click();

	await expect(page.getByRole('button', { name: 'Quitar de favoritos' })).toHaveAttribute(
		'aria-pressed',
		'true'
	);
});

test('shows the info panel and saves a description', async ({ page }) => {
	const api = await openFirstPhoto(page);

	await page.keyboard.press('i');
	const panel = page.getByRole('complementary', { name: 'Información' });
	await expect(panel).toBeVisible();
	await expect(panel.getByText('Apple iPhone 15')).toBeVisible();
	await expect(panel.getByRole('link', { name: 'Barcelona' })).toBeVisible();

	await panel.getByLabel('Descripción').fill('Atardecer en la playa');
	await panel.getByLabel('Descripción').blur();

	await expect(panel.getByText('Guardado')).toBeVisible();
	expect(api.descriptions).toEqual(['Atardecer en la playa']);
});

test('opens with the focus on the dialog, not on a button', async ({ page }) => {
	await openFirstPhoto(page);
	const viewer = page.getByRole('dialog', { name: 'IMG_202609_0.jpg' });

	await expect(viewer).toBeFocused();
	await page.keyboard.press('Tab');
	await expect(viewer.getByRole('button', { name: 'Cerrar' })).toBeFocused();
	// Shift+Tab wraps around inside the viewer.
	await page.keyboard.press('Shift+Tab');
	await expect(viewer.getByRole('button', { name: 'Siguiente' })).toBeFocused();
});

test('the "more" menu holds the other actions and works with the keyboard', async ({ page }) => {
	await openFirstPhoto(page);
	const viewer = page.getByRole('dialog', { name: 'IMG_202609_0.jpg' });
	const more = viewer.getByRole('button', { name: 'Más acciones' });

	await more.focus();
	await page.keyboard.press('Enter');
	const menu = viewer.getByRole('menu', { name: 'Más acciones' });
	await expect(more).toHaveAttribute('aria-expanded', 'true');
	await expect(menu.getByRole('menuitem')).toHaveText([
		/Añadir a un álbum/,
		/Descargar original/,
		/Presentación\s*S/,
		/Archivar/,
		/Mover a la papelera\s*Supr/,
		/^\s*Atajos de teclado\s*\?$/
	]);
	await expect(menu.getByRole('menuitemcheckbox', { name: 'Recuadros de caras' })).toHaveAttribute(
		'aria-checked',
		'false'
	);
	await expect(menu.getByRole('menuitem', { name: 'Añadir a un álbum' })).toBeFocused();
	await page.keyboard.press('End');
	await expect(menu.getByRole('menuitem', { name: 'Atajos de teclado' })).toBeFocused();
	await page.keyboard.press('ArrowDown');
	await expect(menu.getByRole('menuitem', { name: 'Añadir a un álbum' })).toBeFocused();

	// Escape closes the menu, not the viewer, and gives the focus back.
	await page.keyboard.press('Escape');
	await expect(menu).toBeHidden();
	await expect(more).toBeFocused();
	await expect(viewer).toBeVisible();

	// The shortcuts still work with the menu closed.
	await page.keyboard.press('Shift+F');
	await more.click();
	await expect(menu.getByRole('menuitemcheckbox', { name: 'Recuadros de caras' })).toHaveAttribute(
		'aria-checked',
		'true'
	);
});

test('a video sits below the bar and fits the screen, so its controls stay reachable', async ({
	page
}) => {
	await fakeApi(page, { signedIn: true });
	// A real portrait clip (1080×1920), taller than the screen.
	await page.route('**/api/assets/2026-09-5/content*', (route) =>
		route.fulfill({ path: new URL('./fakes/portrait.webm', import.meta.url).pathname })
	);
	await page.goto('/?asset=2026-09-5');

	const video = page.locator('video');
	await expect(video).toHaveJSProperty('videoHeight', 1920);
	const bar = (await page.getByRole('button', { name: 'Cerrar' }).boundingBox())!;
	const box = (await video.boundingBox())!;
	expect(box.y).toBeGreaterThanOrEqual(bar.y + bar.height);
	expect(box.y + box.height).toBeLessThanOrEqual(800);
});

test('when zoomed, a navigator frames the part on screen and moves the view there', async ({
	page
}) => {
	// A photo much larger than the screen, so a zoom has room to move.
	const big = `<svg xmlns="http://www.w3.org/2000/svg" width="3000" height="2000"><rect width="3000" height="2000" fill="#c87846"/><circle cx="2150" cy="550" r="250" fill="#f0eba0"/></svg>`;
	await fakeApi(page, { signedIn: true });
	// After the fake API's routes: the last one registered answers first.
	await page.route(/\/api\/assets\/2026-09-0\/(thumbnail|content)/, (route) =>
		route.fulfill({ contentType: 'image/svg+xml', body: big })
	);
	await page.goto('/?asset=2026-09-0');
	const photo = page.getByRole('dialog').locator('img').first();
	const navigator = page.locator('.navigator');
	await expect(photo).toBeVisible();
	await expect(navigator).toHaveCount(0);

	// Zoomed in: the navigator frames a part of the photo.
	const box = (await photo.boundingBox())!;
	await page.mouse.dblclick(box.x + box.width / 2, box.y + box.height / 2);
	await expect(navigator).toBeVisible();
	const frame = navigator.locator('.frame');
	expect(
		parseFloat((await frame.getAttribute('style'))!.match(/width: ([\d.]+)%/)![1])
	).toBeLessThan(60);

	// A click on its top left corner takes the view there.
	const map = (await navigator.locator('.map').boundingBox())!;
	await page.mouse.click(map.x + 2, map.y + 2);
	await expect(frame).toHaveAttribute('style', /left: 0%; top: 0%/);
	await expect(photo).toHaveAttribute('style', /translate\([1-9][\d.]*px, [1-9][\d.]*px\)/);

	// Back to the fitted photo: no navigator.
	await page.mouse.dblclick(box.x + box.width / 2, box.y + box.height / 2);
	await expect(navigator).toHaveCount(0);
});
