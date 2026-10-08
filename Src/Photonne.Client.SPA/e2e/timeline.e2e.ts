import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

async function openTimeline(page: Page) {
	const monthRequests: string[] = [];
	page.on('request', (request) => {
		const match = /\/api\/assets\/timeline\/buckets\/(\d{4}-\d{2})$/.exec(request.url());
		if (match) monthRequests.push(match[1]);
	});
	await fakeApi(page, { signedIn: true });
	await page.goto('/');
	await expect(page.getByRole('heading', { name: 'Septiembre de 2026' })).toBeVisible();
	return monthRequests;
}

const cells = (page: Page) => page.getByRole('button', { name: /^(Foto|Vídeo), / });

test('loads months only as they come near the viewport', async ({ page }) => {
	const requested = await openTimeline(page);

	expect(requested).toContain('2026-09');
	expect(requested).not.toContain('2023-03');

	await page.getByRole('slider', { name: 'Saltar a una fecha' }).press('End');

	await expect(page.getByRole('button', { name: /de marzo de 2023$/ }).first()).toBeVisible();
	expect(requested).toContain('2023-03');
});

test('selects with Ctrl, Shift and clears with Escape', async ({ page }) => {
	await openTimeline(page);

	await cells(page)
		.nth(0)
		.click({ modifiers: ['ControlOrMeta'] });
	await expect(page.getByText('1 seleccionado', { exact: true })).toBeVisible();

	// Once selecting, a plain click toggles instead of opening.
	await cells(page).nth(1).click();
	await expect(page.getByText('2 seleccionados')).toBeVisible();

	// Shift extends from the anchor (the last toggled cell, the 2nd) to the 5th.
	await cells(page)
		.nth(4)
		.click({ modifiers: ['Shift'] });
	await expect(page.getByText('5 seleccionados')).toBeVisible();

	await page.keyboard.press('Escape');
	await expect(page.getByRole('toolbar')).toBeHidden();
});

test('moves focus with the arrows and selects with Space', async ({ page }) => {
	await openTimeline(page);

	await cells(page).nth(0).focus();
	await page.keyboard.press('ArrowRight');
	await expect(cells(page).nth(1)).toBeFocused();

	await page.keyboard.press('Space');
	await expect(page.getByText('1 seleccionado', { exact: true })).toBeVisible();
	await expect(cells(page).nth(1)).toHaveAttribute('aria-pressed', 'true');

	await page.keyboard.press('Shift+ArrowRight');
	await expect(page.getByText('2 seleccionados')).toBeVisible();
});

test('selects a whole month from its header', async ({ page }) => {
	await openTimeline(page);

	await page.getByRole('button', { name: 'Seleccionar septiembre de 2026' }).click();

	await expect(page.getByText('23 seleccionados')).toBeVisible();
});

test('paints a selection by dragging across the cells', async ({ page }) => {
	await openTimeline(page);
	const first = (await cells(page).nth(0).boundingBox())!;
	const third = (await cells(page).nth(2).boundingBox())!;

	// Press on the first cell's check circle (top-left corner) and sweep right.
	await page.mouse.move(first.x + 18, first.y + 18);
	await page.mouse.down();
	await page.mouse.move(first.x + first.width / 2, first.y + 40, { steps: 4 });
	await page.mouse.move(third.x + third.width / 2, third.y + 40, { steps: 8 });
	await page.mouse.up();

	await expect(page.getByText('3 seleccionados')).toBeVisible();
});
