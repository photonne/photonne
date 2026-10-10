import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';
import { linksState } from './fakes/links';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

// In the fake library, 2026-09-1 is a DNG and 2026-09-2 a HEIC (fakes/links.ts).
const cells = (page: Page) => page.getByRole('button', { name: /^(Foto|Vídeo), / });

async function openTimeline(page: Page) {
	const api = await fakeApi(page, { signedIn: true });
	await page.goto('/');
	await expect(cells(page).first()).toBeVisible();
	return linksState(api);
}

async function select(page: Page, ...indexes: number[]) {
	for (const index of indexes)
		await cells(page)
			.nth(index)
			.click({ modifiers: ['ControlOrMeta'] });
}

const zipRequests = (log: { call: string; body?: unknown }[]) =>
	log.filter((entry) => entry.call === 'POST /api/assets/download-zip').map((entry) => entry.body);

const download = (page: Page) =>
	page.getByRole('toolbar').getByRole('button', { name: 'Descargar' });

test('a selection without RAW or HEIC downloads straight away', async ({ page }) => {
	const links = await openTimeline(page);
	await select(page, 3, 4);

	const file = page.waitForEvent('download');
	await download(page).click();

	expect((await file).suggestedFilename()).toMatch(/^photonne-.+\.zip$/);
	await expect(page.getByRole('dialog', { name: '¿Original o JPEG?' })).toHaveCount(0);
	expect(zipRequests(links.log)).toEqual([{ assetIds: ['2026-09-3', '2026-09-4'], format: null }]);
});

test('asks original or JPEG when the selection holds a RAW', async ({ page }) => {
	const links = await openTimeline(page);
	await select(page, 1, 3);

	await download(page).click();
	const dialog = page.getByRole('dialog', { name: '¿Original o JPEG?' });
	await expect(dialog).toContainText('1 de las 2 fotos es RAW o HEIC');
	const original = dialog.getByRole('button', { name: /^Original \(\.DNG\)/ });
	await expect(original).toBeFocused();
	expect(zipRequests(links.log)).toHaveLength(0);

	const file = page.waitForEvent('download');
	await dialog.getByRole('button', { name: /^JPEG/ }).click();

	expect((await file).suggestedFilename()).toMatch(/\.zip$/);
	await expect(dialog).toBeHidden();
	expect(zipRequests(links.log)).toEqual([
		{ assetIds: ['2026-09-1', '2026-09-3'], format: 'jpeg' }
	]);
});

test('one HEIC downloads as its own file in the format chosen with the keyboard', async ({
	page
}) => {
	await openTimeline(page);
	await select(page, 2);

	await download(page).click();
	const dialog = page.getByRole('dialog', { name: '¿Original o JPEG?' });
	await expect(dialog.getByRole('button', { name: /^Original \(\.HEIC\)/ })).toBeVisible();
	// A single photo needs no "how many of them" line.
	await expect(dialog).not.toContainText('fotos son');

	const file = page.waitForEvent('download');
	await page.keyboard.press('o');

	expect((await file).url()).toMatch(
		/\/api\/assets\/2026-09-2\/content\?download=true&format=original$/
	);
	await expect(dialog).toBeHidden();
	// The question doesn't touch the selection.
	await expect(page.getByRole('toolbar')).toBeVisible();
});

test('cancelling the question downloads nothing', async ({ page }) => {
	const links = await openTimeline(page);
	await select(page, 1, 2);

	await download(page).click();
	const dialog = page.getByRole('dialog', { name: '¿Original o JPEG?' });
	await expect(dialog).toContainText('Las 2 fotos son RAW o HEIC.');
	// Mixed extensions: plain "Original".
	await expect(dialog.getByRole('button', { name: /^Original/ })).not.toContainText('(.');

	await page.keyboard.press('Escape');
	await expect(dialog).toBeHidden();
	expect(zipRequests(links.log)).toHaveLength(0);
	await expect(page.getByRole('toolbar')).toBeVisible();
});
