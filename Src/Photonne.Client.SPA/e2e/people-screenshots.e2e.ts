import { test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';

// Not assertions: renders the people and map pages to files for visual review.
test.skip(!process.env.SCREENSHOTS, 'only with SCREENSHOTS=1');
test.use({ locale: 'es-ES' });

const PNG = Buffer.from(
	'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=',
	'base64'
);

async function shoot(page: Page, name: string) {
	await page.waitForTimeout(700);
	await page.screenshot({ path: `test-results/${name}.png` });
}

for (const [label, width, scheme] of [
	['wide', 1280, 'light'],
	['narrow', 900, 'light'],
	['dark', 1280, 'dark']
] as const) {
	test(`people ${label}`, async ({ page }) => {
		await page.setViewportSize({ width, height: 800 });
		await page.emulateMedia({ colorScheme: scheme });
		await page.route('https://*.basemaps.cartocdn.com/**', (route) =>
			route.fulfill({ status: 200, contentType: 'image/png', body: PNG })
		);
		await fakeApi(page, { signedIn: true });

		await page.goto('/people');
		await page.getByRole('link', { name: /^Ana/ }).waitFor();
		await shoot(page, `people-${label}`);
		await page.getByRole('link', { name: /^Luis/ }).click({ modifiers: ['ControlOrMeta'] });
		await page.getByRole('link', { name: /^José/ }).click({ modifiers: ['ControlOrMeta'] });
		await page.getByRole('button', { name: 'Fusionar', exact: true }).click();
		await shoot(page, `people-merge-${label}`);
		await page.keyboard.press('Escape');
		await page.keyboard.press('Escape');
		await page.getByRole('button', { name: 'Reconocimiento facial' }).click();
		await shoot(page, `people-recognition-${label}`);
		await page.keyboard.press('Escape');

		await page.goto('/people/person-ana');
		await page.getByRole('heading', { name: 'Ana' }).waitFor();
		await shoot(page, `person-photos-${label}`);

		await page.goto('/people/person-ana/faces');
		await page.getByRole('button', { name: /^Cara 3/ }).click();
		await page.getByRole('button', { name: /^Cara 5/ }).click();
		await shoot(page, `person-faces-${label}`);

		await page.goto('/people/person-ana/suggestions');
		await page.getByRole('group').first().focus();
		await shoot(page, `person-suggestions-${label}`);

		await page.goto('/map');
		await page.getByRole('button', { name: /^24 fotos/ }).waitFor();
		await shoot(page, `map-${label}`);
		await page.getByRole('button', { name: /^24 fotos/ }).click();
		await shoot(page, `map-panel-${label}`);
	});
}
