import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

const cells = (page: Page) => page.getByRole('button', { name: /^(Foto|Vídeo), / });
const zoom = (page: Page) => page.getByRole('slider', { name: 'Tamaño de las miniaturas' });

async function openTimeline(page: Page) {
	const monthRequests: string[] = [];
	page.on('request', (request) => {
		const match = /\/api\/assets\/timeline\/buckets\/(\d{4}-\d{2})$/.exec(request.url());
		if (match) monthRequests.push(match[1]);
	});
	const api = await fakeApi(page, { signedIn: true });
	await page.goto('/');
	await expect(page.getByRole('heading', { name: 'Septiembre de 2026' })).toBeVisible();
	return { monthRequests, state: api.state };
}

test('zooms with the keys and the control, splits days at large sizes and remembers it', async ({
	page
}) => {
	await openTimeline(page);
	await expect(zoom(page)).toHaveAttribute('aria-valuetext', 'Medianas, por meses');
	await expect(page.getByRole('heading', { level: 3 })).toHaveCount(0);
	const mediumHeight = (await cells(page).first().boundingBox())!.height;

	await page.keyboard.press('+');

	await expect(zoom(page)).toHaveAttribute('aria-valuetext', 'Grandes, por días');
	await expect(
		page.getByRole('status').filter({ hasText: 'Vista: Grandes, por días' })
	).toBeAttached();
	await expect(
		page.getByRole('heading', { name: 'lunes, 28 de septiembre', level: 3 })
	).toBeVisible();
	expect((await cells(page).first().boundingBox())!.height).toBeGreaterThan(mediumHeight);

	// The whole day can be selected from its header.
	await page.getByRole('button', { name: 'Seleccionar lunes, 28 de septiembre' }).click();
	await expect(page.getByText('1 seleccionado', { exact: true })).toBeVisible();
	await page.keyboard.press('Escape');

	await page.reload();
	await expect(zoom(page)).toHaveAttribute('aria-valuetext', 'Grandes, por días');
	await expect(
		page.getByRole('heading', { name: 'lunes, 28 de septiembre', level: 3 })
	).toBeVisible();

	await page.getByRole('button', { name: 'Miniaturas más pequeñas' }).click();
	await page.getByRole('button', { name: 'Miniaturas más pequeñas' }).click();
	await expect(zoom(page)).toHaveAttribute('aria-valuetext', 'Pequeñas, por meses');
	await expect(page.getByRole('heading', { level: 3 })).toHaveCount(0);
	expect((await cells(page).first().boundingBox())!.height).toBeLessThan(mediumHeight);
});

test('Ctrl + wheel zooms around the photo under the pointer', async ({ page }) => {
	await openTimeline(page);
	const grid = page.getByRole('region', { name: 'Fotos' });
	await grid.hover();
	await page.mouse.wheel(0, 1600);
	await expect(page.getByRole('heading', { name: 'Junio de 2026' })).toBeVisible();

	const target = cells(page).nth(30);
	const id = await target.getAttribute('data-id');
	const box = (await target.boundingBox())!;
	const point = { x: box.x + box.width / 2, y: box.y + box.height / 2 };
	await page.mouse.move(point.x, point.y);

	await page.keyboard.down('Control');
	await page.mouse.wheel(0, 120);
	await page.keyboard.up('Control');

	await expect(zoom(page)).toHaveAttribute('aria-valuetext', 'Pequeñas, por meses');
	// Rows reflow, so the photo may move sideways, but its row stays under the pointer.
	const after = (await page.locator(`[data-id="${id}"]`).boundingBox())!;
	expect(after.y).toBeLessThanOrEqual(point.y + 2);
	expect(after.y + after.height).toBeGreaterThanOrEqual(point.y - 2);
});

test('the year view samples each year and a photo opens its month', async ({ page }) => {
	const { state } = await openTimeline(page);

	await page.keyboard.press('-');
	await page.keyboard.press('-');

	await expect(zoom(page)).toHaveAttribute('aria-valuetext', 'Años');
	await expect(page.getByRole('heading', { name: /^2026/ })).toContainText('92 elementos');
	await expect(page.getByRole('heading', { name: /^2023/ })).toContainText('120 elementos');
	expect((state.yearSamples as number[]).every((n) => n > 0 && n <= 100)).toBe(true);
	// Sampled photos only navigate: no selection checks.
	await expect(page.getByRole('button', { name: /^Seleccionar / })).toHaveCount(0);

	await page
		.getByRole('button', { name: /^Ver julio de 2024/ })
		.first()
		.click();

	await expect(zoom(page)).toHaveAttribute('aria-valuetext', 'Pequeñas, por meses');
	await expect(page.getByRole('heading', { name: 'Julio de 2024' })).toBeInViewport();
	await expect(page.locator('[data-id^="2024-07-"]:focus')).toHaveCount(1);
});

test('goes to a date with G, loading that month', async ({ page }) => {
	const { monthRequests } = await openTimeline(page);
	expect(monthRequests).not.toContain('2023-03');

	await page.keyboard.press('g');
	const dialog = page.getByRole('dialog', { name: 'Ir a una fecha' });
	await expect(dialog).toBeVisible();
	await expect(dialog.getByRole('combobox', { name: 'Año' })).toHaveValue('2026');
	await expect(dialog.getByRole('button', { name: 'julio de 2026, sin fotos' })).toBeDisabled();

	await dialog.getByRole('combobox', { name: 'Año' }).selectOption('2023');
	await dialog.getByRole('button', { name: 'marzo de 2023, 120 elementos' }).click();

	await expect(dialog).toBeHidden();
	await expect(page.getByRole('heading', { name: 'Marzo de 2023' })).toBeInViewport();
	await expect(page.getByRole('button', { name: /de marzo de 2023$/ }).first()).toBeVisible();
	expect(monthRequests).toContain('2023-03');

	// The toolbar button opens it too, on the year now on screen.
	await page.getByRole('button', { name: 'Ir a una fecha' }).click();
	await expect(dialog.getByRole('combobox', { name: 'Año' })).toHaveValue('2023');
	await page.keyboard.press('Escape');
	await expect(dialog).toBeHidden();
});
