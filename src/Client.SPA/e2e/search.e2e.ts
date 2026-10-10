import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';
import { pinSearchFakes } from './fakes/search';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

const cells = (page: Page) => page.getByRole('button', { name: /^(Foto|Vídeo), / });

async function open(page: Page, path: string) {
	const api = await fakeApi(page, { signedIn: true });
	await pinSearchFakes(page, api.state);
	await page.goto(path);
	return api.state as { searches?: string[]; semanticSearches?: string[]; semanticOff?: boolean };
}

test('the top bar search opens the results with the words in the URL', async ({ page }) => {
	const state = await open(page, '/');

	await page.getByRole('search').getByRole('searchbox', { name: 'Buscar' }).fill('IMG_202608');
	await page.keyboard.press('Enter');

	await expect(page).toHaveURL(/\/search\?q=IMG_202608$/);
	await expect(page.getByRole('heading', { name: 'Resultados', level: 1 })).toBeVisible();
	await expect(cells(page)).toHaveCount(9);
	await expect(page.getByRole('searchbox', { name: 'Buscar en tu biblioteca' })).toHaveValue(
		'IMG_202608'
	);
	expect(state.searches?.at(-1)).toContain('q=IMG_202608');
});

test('filters go into the URL and Back takes them off again', async ({ page }) => {
	const state = await open(page, '/search?q=IMG_2026');
	await expect(cells(page).first()).toBeVisible();

	await page.getByRole('button', { name: 'Objetos' }).click();
	await page.getByRole('checkbox', { name: 'Dog' }).check();

	await expect(page).toHaveURL(/object=dog/);
	// The menu stays open to tick more.
	await expect(page.getByRole('checkbox', { name: 'Dog' })).toBeChecked();
	await page.keyboard.press('Escape');
	const chips = page.getByRole('list', { name: 'Filtros activos' });
	await expect(chips.getByText('Dog')).toBeVisible();
	expect(state.searches?.at(-1)).toContain('objectLabel=dog');

	await page.getByRole('button', { name: 'Personas' }).click();
	await page.getByRole('checkbox', { name: 'Lucía' }).check();
	await page.keyboard.press('Escape');
	await expect(chips.getByText('Lucía')).toBeVisible();
	await expect(page).toHaveURL(/person=11111111-0000-0000-0000-000000000001/);

	await page.goBack();
	await expect(page).not.toHaveURL(/person=/);
	await expect(chips.getByText('Dog')).toBeVisible();
	await expect(chips.getByText('Lucía')).toHaveCount(0);

	await page.getByRole('button', { name: 'Quitar el filtro Dog' }).click();
	await expect(page).toHaveURL(/\/search\?q=IMG_2026$/);
	await expect(page.getByRole('list', { name: 'Filtros activos' })).toHaveCount(0);
});

test('dates and text in the image narrow the search', async ({ page }) => {
	const state = await open(page, '/search?q=IMG');

	await page.getByRole('button', { name: 'Fechas' }).click();
	await page.getByLabel('Desde').fill('2026-06-01');
	await page.getByLabel('Hasta').fill('2026-08-31');
	await page.keyboard.press('Escape');
	await expect(page).toHaveURL(/from=2026-06-01&to=2026-08-31/);
	await expect(page.getByText('Del 1 de junio de 2026 al 31 de agosto de 2026')).toBeVisible();
	await expect(cells(page).first()).toBeVisible();
	expect(state.searches?.at(-1)).toContain(
		'from=2026-06-01T00%3A00%3A00Z&to=2026-08-31T00%3A00%3A00Z'
	);

	await page.getByRole('button', { name: 'Texto en la imagen' }).click();
	await page.getByRole('searchbox', { name: 'Texto en la imagen' }).fill('factura');
	await page.getByRole('button', { name: 'Aplicar' }).click();
	await expect(page).toHaveURL(/ocr=factura/);
	await expect(cells(page)).toHaveCount(3);
	expect(state.searches?.at(-1)).toContain('textQuery=factura');
});

test('semantic search shows the closest photos', async ({ page }) => {
	const state = await open(page, '/search?q=perro+en+la+playa&sem=1');

	await expect(cells(page)).toHaveCount(7);
	await expect(page.getByRole('switch', { name: 'Semántica' })).toBeChecked();
	await expect(page.getByRole('button', { name: 'Objetos' })).toBeDisabled();
	expect(state.semanticSearches).toEqual(['perro en la playa']);
});

test('without ML the semantic search falls back to the text search', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	await pinSearchFakes(page, api.state);
	api.state.semanticOff = true;

	await page.goto('/search?q=IMG_202608&sem=1');

	await expect(
		page.getByText('La búsqueda semántica no está disponible ahora mismo')
	).toBeVisible();
	await expect(cells(page)).toHaveCount(9);
	expect(api.state.searches).toEqual([expect.stringContaining('q=IMG_202608')]);
});

test('turning semantic on and off keeps the words', async ({ page }) => {
	await open(page, '/search?q=perro');

	await page.getByRole('switch', { name: 'Semántica' }).check();
	await expect(page).toHaveURL(/q=perro&sem=1/);
	await expect(cells(page)).toHaveCount(7);

	await page.goBack();
	await expect(page.getByRole('switch', { name: 'Semántica' })).not.toBeChecked();
	await expect(page.getByText('Sin resultados.')).toBeVisible();
});

test('the empty search offers people, scenes and objects', async ({ page }) => {
	const state = await open(page, '/search');

	await expect(page.getByRole('heading', { name: 'Buscar', level: 1 })).toBeVisible();
	await expect(page.getByRole('searchbox', { name: 'Buscar en tu biblioteca' })).toBeFocused();
	await expect(page.getByRole('link', { name: 'Lucía' })).toBeVisible();

	await page.getByRole('link', { name: /Beach/ }).click();

	await expect(page).toHaveURL(/\/search\?scene=beach$/);
	await expect(cells(page).first()).toBeVisible();
	expect(state.searches?.at(-1)).toContain('sceneLabel=beach');
});

test('a result opens in the viewer and Back returns to the results', async ({ page }) => {
	await open(page, '/search?q=IMG_202608');

	await cells(page).first().click();
	await expect(page.getByRole('dialog')).toBeVisible();
	await expect(page).toHaveURL(/q=IMG_202608&asset=/);

	await page.goBack();
	await expect(page.getByRole('dialog')).toHaveCount(0);
	await expect(cells(page)).toHaveCount(9);
});
