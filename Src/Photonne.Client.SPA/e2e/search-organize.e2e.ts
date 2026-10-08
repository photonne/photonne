import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';
import { pinSearchFakes } from './fakes/search';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

const cells = (page: Page) => page.getByRole('button', { name: /^(Foto|Vídeo), / });

interface Recorded {
	excludes?: { assetIds: string[]; excluded: boolean }[];
	moves?: { assetIds: string[]; targetFolderId: string; organizeByCaptureYear: boolean }[];
	ruleMoves?: { rule: unknown; targetFolderId: string; organizeByCaptureYear: boolean }[];
	organize?: { inbox: Set<string>; excluded: Set<string> };
}

async function open(page: Page, path: string) {
	const api = await fakeApi(page, { signedIn: true });
	await pinSearchFakes(page, api.state);
	await page.goto(path);
	return api.state as Recorded;
}

async function select(page: Page, ...indexes: number[]) {
	for (const index of indexes)
		await cells(page)
			.nth(index)
			.click({ modifiers: ['ControlOrMeta'] });
}

test('the inbox shows what is pending and sets photos aside with undo', async ({ page }) => {
	const state = await open(page, '/organize');
	const tabs = page.getByRole('navigation', { name: 'Secciones de organizar' });

	await expect(page.getByText('32 pendientes · agosto de 2026 – septiembre de 2026')).toBeVisible();
	await expect(tabs.getByRole('link', { name: 'Bandeja 32' })).toHaveAttribute(
		'aria-current',
		'page'
	);
	const first = await cells(page).nth(0).getAttribute('data-id');
	await select(page, 0, 1);

	await page.getByRole('button', { name: 'Apartar de la bandeja' }).click();

	await expect(page.getByText('2 apartadas')).toBeVisible();
	await expect(page.locator(`[data-id="${first}"]`)).toHaveCount(0);
	await expect(tabs.getByRole('link', { name: 'Apartadas 2' })).toBeVisible();
	expect(state.excludes).toEqual([{ assetIds: expect.any(Array), excluded: true }]);

	await page.getByRole('button', { name: 'Deshacer' }).click();

	await expect(page.locator(`[data-id="${first}"]`)).toBeVisible();
	expect(state.excludes?.[1]).toEqual({ assetIds: state.excludes?.[0].assetIds, excluded: false });
});

test('moves the selection to a folder and takes it out of the inbox', async ({ page }) => {
	const state = await open(page, '/organize');
	const first = await cells(page).nth(0).getAttribute('data-id');
	await select(page, 0);

	await page.getByRole('button', { name: 'Mover a una carpeta' }).click();
	const dialog = page.getByRole('dialog', { name: 'Mover a una carpeta' });
	await dialog.getByRole('button', { name: 'Camera' }).click();
	await dialog.getByRole('checkbox', { name: 'Organizar por año de captura' }).check();
	await dialog.getByRole('button', { name: 'Mover', exact: true }).click();

	await expect(page.getByText('1 movida a «Camera» (2026: 1)')).toBeVisible();
	await expect(page.locator(`[data-id="${first}"]`)).toHaveCount(0);
	expect(state.moves).toEqual([
		{
			sourceFolderId: null,
			targetFolderId: 'folder-1',
			assetIds: [first],
			organizeByCaptureYear: true
		}
	]);
});

test('reviews a suggestion, leaves a photo out and moves the rest', async ({ page }) => {
	const state = await open(page, '/organize');

	await page.getByRole('button', { name: 'Revisar y mover «Roma»' }).click();
	const review = page.getByRole('dialog', { name: 'Roma' });
	await expect(review.getByText('Se moverán 6 de 6.')).toBeVisible();
	await review.getByRole('checkbox', { name: 'Foto 1 de 2026' }).uncheck();
	await expect(review.getByText('Se moverán 5 de 6.')).toBeVisible();
	await review.getByRole('button', { name: 'Elegir destino' }).click();

	const picker = page.getByRole('dialog', { name: 'Mover a una carpeta' });
	await picker.getByRole('button', { name: 'Camera' }).click();
	await picker.getByRole('button', { name: 'Mover', exact: true }).click();

	await expect(page.getByText('5 movidas a «Camera»')).toBeVisible();
	expect(state.moves?.[0].assetIds).toHaveLength(5);
	expect(state.moves?.[0].assetIds).not.toContain('2026-08-0');
	// The suggestion now offers only what is left.
	await expect(
		page.getByRole('article', { name: 'Roma' }).getByText('Viaje · 1 foto')
	).toBeVisible();
});

test('dismissing a suggestion sets it aside', async ({ page }) => {
	const state = await open(page, '/organize');

	await page.getByRole('button', { name: /^Descartar «Roma»/ }).click();

	await expect(page.getByText('6 apartadas')).toBeVisible();
	await expect(page.getByRole('article', { name: 'Roma' })).toHaveCount(0);
	expect(state.excludes?.[0]).toEqual({ assetIds: expect.any(Array), excluded: true });
	expect(state.excludes?.[0].assetIds).toHaveLength(6);
});

test('a rule previews its matches, is reviewed and moves on the server', async ({ page }) => {
	const state = await open(page, '/organize/rules');

	await expect(page.getByText('Completa una condición para ver qué coincide.')).toBeVisible();
	await page.getByRole('combobox', { name: 'Añadir condición…' }).selectOption('favorite');

	await expect(page.getByText('4 fotos pendientes coinciden')).toBeVisible();
	const move = page.getByRole('button', { name: 'Revisar y mover' });
	await expect(move).toBeDisabled();

	await page
		.getByRole('combobox', { name: 'Carpeta de destino' })
		.selectOption({ label: 'Camera' });
	await move.click();

	const review = page.getByRole('dialog', { name: 'Revisar antes de mover a «Camera»' });
	await expect(review.getByText('Se moverán 4 de 4.')).toBeVisible();
	await review.getByRole('button', { name: 'Mover a una carpeta' }).click();

	await expect(page.getByText('4 movidas a «Camera» (2026: 4)')).toBeVisible();
	expect(state.ruleMoves).toEqual([
		{
			rule: { type: 'favorite', value: true },
			targetFolderId: 'folder-1',
			organizeByCaptureYear: true
		}
	]);
	await expect(page.getByText('0 fotos pendientes coinciden')).toBeVisible();
});

test('a reviewed rule with photos left out moves exactly the kept ones', async ({ page }) => {
	const state = await open(page, '/organize/rules');
	const add = page.getByRole('combobox', { name: 'Añadir condición…' });

	await add.selectOption('favorite');
	await add.selectOption('mediaType');
	await page.getByRole('combobox', { name: 'Cumplir' }).selectOption('OR');
	await page.getByRole('checkbox', { name: 'Excluir las que la cumplen' }).nth(1).check();
	await expect(page.getByText(/fotos pendientes coinciden/)).toBeVisible();
	await page
		.getByRole('combobox', { name: 'Carpeta de destino' })
		.selectOption({ label: 'Camera' });
	await page.getByRole('checkbox', { name: 'Organizar en subcarpetas por año' }).uncheck();
	await page.getByRole('button', { name: 'Revisar y mover' }).click();

	const review = page.getByRole('dialog');
	await review.getByRole('checkbox', { name: 'Mover todas las de 2026' }).uncheck();
	await expect(review.getByRole('button', { name: 'Mover a una carpeta' })).toBeDisabled();
	await review.getByRole('checkbox', { name: 'Foto 2 de 2026' }).check();
	await review.getByRole('button', { name: 'Mover a una carpeta' }).click();

	await expect(page.getByText('1 movida a «Camera»')).toBeVisible();
	expect(state.ruleMoves).toBeUndefined();
	expect(state.moves).toEqual([
		expect.objectContaining({ assetIds: [expect.any(String)], organizeByCaptureYear: false })
	]);
});

test('set-aside photos go back to the inbox', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	await pinSearchFakes(page, api.state);
	api.state.organize = {
		inbox: new Set(['2026-09-0']),
		excluded: new Set(['2026-08-1', '2026-08-2'])
	};
	await page.goto('/organize/excluded');

	await expect(cells(page)).toHaveCount(2);
	await select(page, 0);
	await page.getByRole('button', { name: 'Devolver a la bandeja' }).click();

	await expect(page.getByText('1 devuelta a la bandeja')).toBeVisible();
	await expect(cells(page)).toHaveCount(1);
	await expect(
		page.getByRole('navigation', { name: 'Secciones de organizar' }).getByRole('link', {
			name: 'Bandeja 2'
		})
	).toBeVisible();
});
