import { expect, test } from '@playwright/test';
import { fakeApi } from './fake-api';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

test('a person shows who they have the most photos with, and opens those photos', async ({
	page
}) => {
	await fakeApi(page, { signedIn: true });
	await page.goto('/people/person-ana');
	await expect(page.getByRole('heading', { name: 'Ana', level: 1 })).toBeVisible();

	const together = page.getByRole('region', { name: 'Personas con más fotos juntas' });
	const cards = together.getByRole('link');
	// Only the pairs: "through the years" belongs to Recuerdos.
	await expect(cards).toHaveCount(2);
	await expect(cards.first()).toHaveAccessibleName(/^Luis\s+42 fotos$/);
	await expect(cards.nth(1)).toContainText('Marta');

	await cards.first().click();

	await expect(page).toHaveURL(/\/memories\/pair-luis$/);
	await expect(page.getByRole('heading', { name: 'Ana y Luis', level: 1 })).toBeVisible();
});

test('without companions the row is not there', async ({ page }) => {
	await fakeApi(page, { signedIn: true });
	await page.goto('/people/person-luis');
	await expect(page.getByRole('heading', { name: 'Luis', level: 1 })).toBeVisible();

	await expect(page.getByRole('region', { name: 'Personas con más fotos juntas' })).toHaveCount(0);
});
