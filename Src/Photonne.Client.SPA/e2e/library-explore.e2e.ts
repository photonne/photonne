import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';
import { libraryState } from './fakes/library';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

const cells = (page: Page) => page.getByRole('button', { name: /^(Foto|Vídeo), / });

test('shows themes, scenes, objects and tags', async ({ page }) => {
	await fakeApi(page, { signedIn: true });
	await page.goto('/explore');

	await expect(page.getByRole('heading', { name: 'Explorar', level: 1 })).toBeVisible();
	await expect(page.getByRole('main').getByRole('heading', { level: 2 })).toHaveText([
		'Viajes',
		'Días de playa',
		'Escenas',
		'Objetos',
		'Tus etiquetas'
	]);
	// A theme's periods, newest first, named by their year.
	await expect(page.getByRole('region', { name: 'Días de playa' }).getByRole('link')).toHaveText([
		'2024',
		'2023'
	]);
	await expect(page.getByRole('region', { name: 'Escenas' }).getByRole('listitem')).toHaveCount(3);
	await expect(page.getByRole('link', { name: 'familia' })).toBeVisible();
});

test('opens the photos of a scene', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	await page.goto('/explore');

	await page.getByRole('link', { name: /Beach/ }).click();

	await expect(page.getByRole('heading', { name: 'Beach', level: 1 })).toBeVisible();
	await expect(cells(page)).toHaveCount(14);
	const search = libraryState(api.state).searches.at(-1)!;
	expect(search.getAll('sceneLabel')).toEqual(['beach']);

	await page.getByRole('link', { name: 'Escenas' }).click();
	await expect(page.getByRole('heading', { name: 'Escenas', level: 1 })).toBeVisible();
});

test('searches and sorts every object', async ({ page }) => {
	await fakeApi(page, { signedIn: true });
	await page.goto('/explore/objects');

	const tiles = page.getByRole('list', { name: 'Objetos' }).getByRole('link');
	await expect(tiles).toHaveText([/Dog/, /Bicycle/]);

	await page.getByLabel('Ordenar').selectOption('name');
	await expect(tiles).toHaveText([/Bicycle/, /Dog/]);

	await page.getByRole('searchbox', { name: 'Buscar en Objetos' }).fill('do');
	await expect(tiles).toHaveText([/Dog/]);

	await page.getByRole('searchbox', { name: 'Buscar en Objetos' }).fill('zebra');
	await expect(page.getByText('Nada coincide con la búsqueda.')).toBeVisible();

	// The sort is remembered.
	await page.reload();
	await expect(page.getByLabel('Ordenar')).toHaveValue('name');
});

test('finds photos by the text in them', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	await page.goto('/explore');

	await page.getByRole('searchbox', { name: 'Buscar texto en las fotos' }).fill('menú del día');
	await page.keyboard.press('Enter');

	await expect(page).toHaveURL(/\/explore\/text\?q=men%C3%BA%20del%20d%C3%ADa$/);
	await expect(page.getByRole('heading', { name: '«menú del día»', level: 1 })).toBeVisible();
	await expect(cells(page)).toHaveCount(14);
	expect(libraryState(api.state).searches.at(-1)!.get('textQuery')).toBe('menú del día');

	await page.getByRole('searchbox', { name: 'Buscar texto en las fotos' }).fill('nada');
	await page.keyboard.press('Enter');
	await expect(page.getByText('Ninguna foto contiene ese texto.')).toBeVisible();
});

test('opens the photos of a tag', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	await page.goto('/explore');

	await page.getByRole('link', { name: 'viaje' }).click();

	await expect(page.getByRole('heading', { name: '#viaje', level: 1 })).toBeVisible();
	await expect(cells(page)).toHaveCount(14);
	expect(libraryState(api.state).searches.at(-1)!.get('q')).toBe('viaje');
});

test('opens a theme in full and one of its memories', async ({ page }) => {
	await fakeApi(page, { signedIn: true });
	await page.goto('/explore/themes/scene%3Abeach');

	await expect(page.getByRole('heading', { name: 'Días de playa', level: 1 })).toBeVisible();
	await page.getByRole('link', { name: /2023/ }).click();

	await expect(
		page.getByRole('heading', { name: 'Días de playa de 2023', level: 1 })
	).toBeVisible();
	// Back goes to Explorar, where themes live.
	await expect(page.getByRole('navigation', { name: 'Ruta de navegación' })).toContainText(
		'Explorar'
	);
});
