import { expect, test } from '@playwright/test';
import { fakeApi } from './fake-api';
import { peopleState } from './fakes/people';

// Reduced motion opens the constellation still, at the present.
test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 }, reducedMotion: 'reduce' });

test('draws a star per named person, fixed where their last photo is', async ({ page }) => {
	await page.clock.setFixedTime('2026-10-11T12:00:00Z');
	await fakeApi(page, { signedIn: true });
	await page.goto('/people');
	await page.getByRole('link', { name: 'Constelación' }).click();

	await expect(page.getByRole('heading', { name: 'Constelación', level: 1 })).toBeVisible();
	await expect(page.getByText('2026', { exact: true })).toBeVisible();
	// Ana and Luis reach today; José's last photo is from 2016. Marta is hidden.
	await expect(page.getByText('Contigo: 2 · Fijas: 1')).toBeVisible();
	const stars = page
		.getByRole('list', { name: 'Personas de la constelación' })
		.getByRole('listitem');
	await expect(stars).toHaveText([
		'José, Sin clasificar: de 2009 a 2016',
		'Ana, Sin clasificar: desde 2012',
		'Luis, Amistades: desde 2015'
	]);

	// The legend hides a whole relationship.
	const legend = page.getByRole('group', { name: 'Mostrar u ocultar por relación' });
	await expect(legend.getByRole('button', { name: 'Sin clasificar' })).toHaveAttribute(
		'title',
		/^2 personas sin clasificar/
	);
	await legend.getByRole('button', { name: 'Amistades' }).click();
	await expect(page.getByText('Contigo: 1 · Fijas: 1')).toBeVisible();
	await expect(stars).toHaveCount(2);
	await legend.getByRole('button', { name: 'Amistades' }).click();

	// Back at the first photo only José is there, and it plays from there.
	await page.getByRole('slider', { name: 'Año' }).press('Home');
	await expect(page.getByText('2009', { exact: true })).toBeVisible();
	await expect(page.getByText('Contigo: 1 · Fijas: 0')).toBeVisible();
	await page.getByRole('button', { name: 'Reproducir' }).click();
	await expect(page.getByRole('button', { name: 'Pausar' })).toBeVisible();
});

test('a relationship given on the person page colors their star', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	peopleState(api.state).people[0].relationshipCategory = 'family';
	peopleState(api.state).people[0].relationshipLabel = 'Madre';
	await page.goto('/people/constellation');

	await expect(
		page.getByRole('list', { name: 'Personas de la constelación' }).getByRole('listitem').nth(1)
	).toHaveText('Ana, Familia · Madre: desde 2012');
	await expect(page.getByRole('button', { name: 'Familia' })).toHaveAttribute(
		'aria-pressed',
		'true'
	);
});

test('without named people it says how to get stars', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	for (const person of peopleState(api.state).people) person.name = null;
	await page.goto('/people/constellation');

	await expect(page.getByText('Aún no hay estrellas')).toBeVisible();
	await expect(page.getByRole('link', { name: 'Volver a Personas' })).toBeVisible();
});
