import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';
import { libraryState } from './fakes/library';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

const cells = (page: Page) => page.getByRole('button', { name: /^(Foto|Vídeo), / });

test('groups the feed into today, this month and through the years', async ({ page }) => {
	await fakeApi(page, { signedIn: true });
	await page.goto('/memories');

	await expect(page.getByRole('heading', { name: 'Recuerdos', level: 1 })).toBeVisible();
	const headings = page.getByRole('main').getByRole('heading', { level: 2 });
	await expect(headings).toHaveText([
		'Un día como hoy',
		'Este mes en otros años',
		'A lo largo de los años'
	]);
	const today = page.getByRole('region', { name: 'Un día como hoy' }).getByRole('link');
	// Newest year first.
	await expect(today).toHaveText([/Hace 2 años/, /Hace 3 años/]);
	// A person's card says just the name.
	await expect(
		page.getByRole('region', { name: 'A lo largo de los años' }).getByRole('link')
	).toHaveText(/^\s*Martina\s+6 fotos/);
	// Themes live in Explorar, not here.
	await expect(page.getByRole('link', { name: /playa/ })).toHaveCount(0);
});

test('opens a memory and plays it as a story', async ({ page }) => {
	await fakeApi(page, { signedIn: true });
	await page.goto('/memories');
	await page.getByRole('link', { name: /Hace 2 años/ }).click();

	await expect(page.getByRole('heading', { name: 'Hace 2 años', level: 1 })).toBeVisible();
	await expect(page.getByText('8 de octubre de 2024', { exact: true })).toBeVisible();
	await expect(cells(page)).toHaveCount(6);

	await page.getByRole('button', { name: 'Reproducir' }).click();
	const story = page.getByRole('dialog', { name: 'Hace 2 años' });
	await expect(story).toBeVisible();
	await expect(story.getByText('1 de 6')).toBeVisible();
	await expect(story.getByRole('button', { name: 'Pausar' })).toBeFocused();

	await page.keyboard.press('ArrowRight');
	await expect(story.getByText('2 de 6')).toBeVisible();
	await page.keyboard.press('k');
	await expect(story.getByRole('button', { name: 'Reanudar' })).toBeVisible();
	await page.keyboard.press('End');
	await expect(story.getByText('6 de 6')).toBeVisible();

	await page.keyboard.press('Escape');
	await expect(story).toBeHidden();
	await expect(page.getByRole('button', { name: 'Reproducir' })).toBeFocused();
	await expect(page).toHaveURL(/\/memories\/mem-today-1$/);
});

test('shows the photos of this day when no memory has been generated yet', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	libraryState(api.state).memories = [];
	await page.goto('/memories');

	await page.getByRole('link', { name: /Un día como hoy/ }).click();

	await expect(page.getByRole('heading', { name: 'Un día como hoy', level: 1 })).toBeVisible();
	await expect(cells(page)).toHaveCount(5);
	await page.getByRole('link', { name: 'Recuerdos' }).last().click();
	await expect(page).toHaveURL(/\/memories$/);
});

test('says when there are no memories at all', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	libraryState(api.state).memories = [];
	libraryState(api.state).onThisDay = [];
	await page.goto('/memories');

	await expect(page.getByText('Todavía no hay recuerdos.')).toBeVisible();
});

test('says when a memory no longer exists', async ({ page }) => {
	await fakeApi(page, { signedIn: true });
	await page.goto('/memories/gone');

	await expect(page.getByText('Este recuerdo ya no existe.')).toBeVisible();
});
