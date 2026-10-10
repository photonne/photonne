import { test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';
import { pinSearchFakes } from './fakes/search';

// Not an assertion: renders search, organize and utilities to files for
// visual review, wide and narrow.
test.skip(!process.env.SCREENSHOTS, 'only with SCREENSHOTS=1');
test.use({ locale: 'es-ES' });

const pages: [string, string, ((page: Page) => Promise<void>)?][] = [
	['search-discover', '/search'],
	['search-results', '/search?q=IMG_2026&object=dog'],
	[
		'search-filter-menu',
		'/search?q=IMG',
		async (page) => {
			await page.getByRole('button', { name: 'Personas' }).click();
		}
	],
	['organize', '/organize'],
	[
		'organize-review',
		'/organize',
		async (page) => {
			await page.getByRole('button', { name: 'Revisar y mover «Roma»' }).click();
		}
	],
	[
		'organize-rules',
		'/organize/rules',
		async (page) => {
			await page.getByRole('combobox', { name: 'Añadir condición…' }).selectOption('favorite');
			await page.getByRole('combobox', { name: 'Añadir condición…' }).selectOption('person');
			await page.waitForTimeout(500);
		}
	],
	['organize-excluded', '/organize/excluded'],
	['utilities', '/utilities'],
	[
		'utilities-duplicates',
		'/utilities/duplicates',
		async (page) => {
			await page.getByRole('button', { name: 'Conservar la más antigua en todos' }).click();
		}
	],
	['utilities-large-files', '/utilities/large-files'],
	[
		'utilities-locations',
		'/utilities/locations',
		async (page) => {
			await page.getByRole('button', { name: 'Expandir todo' }).click();
		}
	],
	['utilities-unsupported', '/utilities/unsupported']
];

for (const [width, height] of [
	[1280, 800],
	[900, 800]
]) {
	for (const [name, path, act] of pages) {
		test(`${name} ${width}`, async ({ page }) => {
			await page.setViewportSize({ width, height });
			const api = await fakeApi(page, { signedIn: true });
			await pinSearchFakes(page, api.state);
			await page.goto(path);
			await page.waitForTimeout(700);
			await act?.(page);
			await page.waitForTimeout(300);
			await page.screenshot({ path: `test-results/${name}-${width}.png` });
		});
	}
}
