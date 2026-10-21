import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

const cells = (page: Page) => page.getByRole('button', { name: /^(Foto|Vídeo), / });
const cellHeight = async (page: Page) => (await cells(page).first().boundingBox())!.height;
// The size and the layout live in the chrome's "view options" menu; the
// grouping, with the dates and the order, in its "filters" menu.
const options = (page: Page) => page.getByRole('dialog', { name: 'Opciones de visualización' });
const filters = (page: Page) => page.getByRole('dialog', { name: 'Filtros' });
const size = (page: Page) =>
	options(page).getByRole('slider', { name: 'Tamaño de las miniaturas' });

async function openOptions(page: Page) {
	if (!(await options(page).isVisible()))
		await page.getByRole('button', { name: 'Opciones de visualización' }).click();
	await expect(options(page)).toBeVisible();
}

async function openFilters(page: Page) {
	if (!(await filters(page).isVisible()))
		await page.getByRole('button', { name: 'Filtros' }).click();
	await expect(filters(page)).toBeVisible();
}

async function closeMenus(page: Page) {
	await page.keyboard.press('Escape');
	await expect(options(page)).toBeHidden();
	await expect(filters(page)).toBeHidden();
}

async function group(page: Page, name: string) {
	await openFilters(page);
	await filters(page).getByRole('radio', { name }).check();
	await closeMenus(page);
}

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

test('resizes in five steps with the keys, the buttons and the slider, and remembers it', async ({
	page
}) => {
	await openTimeline(page);
	const medium = await cellHeight(page);

	await page.keyboard.press('+');
	await expect(page.getByRole('status').filter({ hasText: 'Tamaño: Grande' })).toBeAttached();
	await expect.poll(() => cellHeight(page)).toBeGreaterThan(medium);
	await openOptions(page);
	await expect(size(page)).toHaveValue('75');
	await expect(size(page)).toHaveAttribute('aria-valuetext', 'Grande');

	await options(page)
		.getByRole('button', { name: /^Miniaturas más pequeñas/ })
		.click();
	await options(page)
		.getByRole('button', { name: /^Miniaturas más pequeñas/ })
		.click();
	await expect(size(page)).toHaveValue('25');
	await expect.poll(() => cellHeight(page)).toBeLessThan(medium);

	await size(page).fill('0');
	await expect(size(page)).toHaveValue('0');
	await expect(
		options(page).getByRole('button', { name: /^Miniaturas más pequeñas/ })
	).toBeDisabled();
	const smallest = await cellHeight(page);
	expect(smallest).toBeLessThan(medium / 1.5);
	await closeMenus(page);
	// Grouping doesn't change with the size.
	await openFilters(page);
	await expect(filters(page).getByRole('radio', { name: 'Por meses' })).toBeChecked();

	await page.reload();
	await openOptions(page);
	await expect(size(page)).toHaveValue('0');
	await expect.poll(() => cellHeight(page)).toBeLessThan(medium / 1.5);
});

test('groups by day at any size, from the filters', async ({ page }) => {
	await openTimeline(page);
	await expect(page.getByRole('heading', { level: 3 })).toHaveCount(0);

	await openFilters(page);
	await filters(page).getByRole('radio', { name: 'Por días' }).check();
	await expect(page.getByRole('status').filter({ hasText: 'Agrupadas Por días' })).toBeAttached();
	await closeMenus(page);
	await expect(
		page.getByRole('heading', { name: 'lunes, 28 de septiembre', level: 3 })
	).toBeVisible();

	// Small thumbnails keep their days.
	await openOptions(page);
	await size(page).fill('25');
	await closeMenus(page);
	await expect(
		page.getByRole('heading', { name: 'lunes, 28 de septiembre', level: 3 })
	).toBeVisible();

	// The whole day can be selected from its header.
	await page.getByRole('button', { name: 'Seleccionar lunes, 28 de septiembre' }).click();
	await expect(page.getByText('1 seleccionado', { exact: true })).toBeVisible();
	await page.keyboard.press('Escape');

	await page.reload();
	await openFilters(page);
	await expect(filters(page).getByRole('radio', { name: 'Por días' })).toBeChecked();
	await closeMenus(page);
	await openOptions(page);
	await expect(size(page)).toHaveValue('25');
});

test('carries a level saved by the old zoom over', async ({ page }) => {
	await page.addInitScript(() => {
		if (!localStorage.getItem('photonne.timeline.size'))
			localStorage.setItem('photonne.timeline.zoom', 'large');
	});
	await openTimeline(page);
	await openOptions(page);
	await expect(size(page)).toHaveValue('75');
	await closeMenus(page);
	await openFilters(page);
	await expect(filters(page).getByRole('radio', { name: 'Por días' })).toBeChecked();
});

test('Ctrl + wheel resizes a whole step around the photo under the pointer', async ({ page }) => {
	await openTimeline(page);
	const grid = page.getByRole('region', { name: 'Fotos' });
	await grid.hover();
	await page.mouse.wheel(0, 1600);
	await expect(page.getByRole('heading', { name: 'Junio de 2026' })).toBeVisible();

	// A photo wholly inside the grid's viewport (not under the chrome),
	// among the rendered ones: the grid only renders what's near the viewport.
	const area = (await grid.boundingBox())!;
	const rendered = await cells(page).count();
	let index = 0;
	let box = (await cells(page).nth(index).boundingBox())!;
	while (
		index < rendered - 1 &&
		(box.y < area.y + 120 || box.y + box.height > area.y + area.height)
	) {
		box = (await cells(page).nth(++index).boundingBox())!;
	}
	const target = cells(page).nth(index);
	const id = await target.getAttribute('data-id');
	const point = { x: box.x + box.width / 2, y: box.y + box.height / 2 };
	await page.mouse.move(point.x, point.y);

	await page.keyboard.down('Control');
	await page.mouse.wheel(0, 120);
	await page.keyboard.up('Control');

	// The grid reflows, so the photo may move sideways, but it stays under the pointer.
	const after = (await page.locator(`[data-id="${id}"]`).boundingBox())!;
	expect(after.y).toBeLessThanOrEqual(point.y + 2);
	expect(after.y + after.height).toBeGreaterThanOrEqual(point.y - 2);
	await openOptions(page);
	await expect(size(page)).toHaveValue('25');
});

test('the year view samples each year and a photo opens its month', async ({ page }) => {
	const { state } = await openTimeline(page);

	await group(page, 'Por años');
	await expect(page.getByRole('heading', { name: /^2026/ })).toContainText('92 elementos');
	await expect(page.getByRole('heading', { name: /^2023/ })).toContainText('120 elementos');
	expect((state.yearSamples as number[]).every((n) => n > 0 && n <= 100)).toBe(true);
	// Sampled photos only navigate: no selection checks.
	await expect(page.getByRole('button', { name: /^Seleccionar / })).toHaveCount(0);

	// The slider sizes the samples too, and they stay on screen meanwhile.
	const sample = page.getByRole('button', { name: /^Ver / }).first();
	const sampleHeight = async () => (await sample.boundingBox())!.height;
	const before = await sampleHeight();
	await openOptions(page);
	await size(page).fill('100');
	await closeMenus(page);
	await expect.poll(sampleHeight).toBeGreaterThan(before);
	await expect(page.getByRole('heading', { name: /^2026/ })).toContainText('92 elementos');

	await page
		.getByRole('button', { name: /^Ver julio de 2024/ })
		.first()
		.click();

	await expect(page.getByRole('heading', { name: 'Julio de 2024' })).toBeInViewport();
	await expect(page.locator('[data-id^="2024-07-"]:focus')).toHaveCount(1);
	await openFilters(page);
	await expect(filters(page).getByRole('radio', { name: 'Por meses' })).toBeChecked();
});

test('on a phone the view options open as a sheet', async ({ page }) => {
	await page.setViewportSize({ width: 390, height: 800 });
	await openTimeline(page);
	await openOptions(page);
	const sheet = (await options(page).boundingBox())!;
	expect(sheet.y + sheet.height).toBeGreaterThan(790);
	await options(page)
		.getByRole('button', { name: /^Miniaturas más grandes/ })
		.click();
	await expect(page.getByRole('status').filter({ hasText: 'Tamaño: Grande' })).toBeAttached();
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

	// The filters menu opens it too, on the year now on screen.
	await openFilters(page);
	await filters(page)
		.getByRole('button', { name: /^Ir a una fecha/ })
		.click();
	await expect(dialog.getByRole('combobox', { name: 'Año' })).toHaveValue('2023');
	await page.keyboard.press('Escape');
	await expect(dialog).toBeHidden();
});
