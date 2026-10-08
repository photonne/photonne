import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';

// The map page (its fake lives with people's: e2e/fakes/people.ts).
test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

// A 1×1 PNG for every CARTO tile: no network in tests, and the CSP host is
// the only one the page may ask.
const PNG = Buffer.from(
	'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=',
	'base64'
);

async function openMap(page: Page, options: { empty?: boolean; url?: string } = {}) {
	const tiles: string[] = [];
	await page.route('https://*.basemaps.cartocdn.com/**', (route) => {
		tiles.push(route.request().url());
		return route.fulfill({ status: 200, contentType: 'image/png', body: PNG });
	});
	const api = await fakeApi(page, { signedIn: true });
	api.state.mapEmpty = options.empty ?? false;
	await page.goto(options.url ?? '/map');
	await expect(page.getByRole('heading', { name: 'Mapa', level: 1 })).toBeVisible();
	return { tiles };
}

const marker = (page: Page, name: RegExp) => page.getByRole('button', { name });

test('shows clusters with counts on CARTO raster tiles', async ({ page }) => {
	const { tiles } = await openMap(page);

	await expect(page.getByText('34 fotos con ubicación')).toBeVisible();
	await expect(marker(page, /^24 fotos, /)).toBeVisible();
	await expect(marker(page, /^9 fotos, /)).toBeVisible();
	await expect(marker(page, /^1 foto, /)).toBeVisible();
	await expect(marker(page, /^24 fotos, /)).toContainText('24');
	expect(tiles.length).toBeGreaterThan(0);
	expect(
		tiles.every((url) => /^https:\/\/[a-d]\.basemaps\.cartocdn\.com\/(light|dark)_all\//.test(url))
	).toBe(true);
	// The view goes to the URL, for reloads and links.
	await expect(page).toHaveURL(/[?&]lat=.*&lng=.*&z=\d+/);
});

test('a cluster opens its photos beside the map, and the viewer from there', async ({ page }) => {
	await openMap(page);

	await marker(page, /^24 fotos, /).click();

	const panel = page.getByRole('complementary', { name: '24 fotos aquí' });
	await expect(panel.getByRole('heading', { name: '24 fotos aquí' })).toBeVisible();
	const cells = panel.getByRole('button', { name: /^(Foto|Vídeo), / });
	// The grid is virtualized: what fits is drawn, newest first.
	await expect(cells.first()).toBeVisible();

	await cells.first().click();
	await expect(page.getByRole('dialog', { name: /^IMG_/ })).toBeVisible();
	await page.keyboard.press('Escape');
	await expect(page.getByRole('dialog', { name: /^IMG_/ })).toBeHidden();

	await panel.getByRole('button', { name: 'Cerrar el panel' }).click();
	await expect(panel).toBeHidden();
});

test('markers work from the keyboard; a single photo opens straight in the viewer', async ({
	page
}) => {
	await openMap(page);

	await marker(page, /^1 foto, /).focus();
	await page.keyboard.press('Enter');

	await expect(page.getByRole('dialog', { name: /^IMG_/ })).toBeVisible();
	await expect(page.getByRole('complementary', { name: '1 foto aquí' })).toBeAttached();
});

test('opens where the URL says', async ({ page }) => {
	await openMap(page, { url: '/map?lat=40.4168&lng=-3.7038&z=12' });

	await expect(marker(page, /^9 fotos, /)).toBeInViewport();
	await expect(page).toHaveURL(/z=12/);
});

test('without located photos it says so', async ({ page }) => {
	await openMap(page, { empty: true });

	await expect(page.getByRole('heading', { name: 'Sin fotos con ubicación' })).toBeVisible();
	await expect(page.getByText('0 fotos con ubicación')).toBeVisible();
	await expect(page.getByRole('button', { name: 'Ver todas' })).toBeDisabled();
});

test('tiles follow the colour scheme', async ({ page }) => {
	await page.emulateMedia({ colorScheme: 'dark' });
	const { tiles } = await openMap(page);
	await expect(marker(page, /^24 fotos, /)).toBeVisible();
	expect(tiles.some((url) => url.includes('/dark_all/'))).toBe(true);

	// The user's own theme wins over the system's.
	let before = tiles.length;
	await page.evaluate(() => (document.documentElement.dataset.theme = 'light'));
	await expect
		.poll(() => tiles.slice(before).some((url) => url.includes('/light_all/')))
		.toBe(true);
	await page.evaluate(() => delete document.documentElement.dataset.theme);

	before = tiles.length;
	await page.emulateMedia({ colorScheme: 'light' });
	await expect
		.poll(() => tiles.slice(before).some((url) => url.includes('/light_all/')))
		.toBe(true);
});
