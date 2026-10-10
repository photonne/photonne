import { test } from '@playwright/test';
import { fakeAdminApi } from './fakes/admin';

// Not assertions: renders the admin pages to files for visual review.
test.skip(!process.env.SCREENSHOTS, 'only with SCREENSHOTS=1');
test.use({ locale: 'es-ES' });

const pages = ['', 'users', 'libraries', 'backup', 'shared-trash', 'system'];

for (const width of [1280, 900]) {
	for (const name of pages) {
		test(`admin ${name || 'dashboard'} at ${width}`, async ({ page }) => {
			await page.setViewportSize({ width, height: 1300 });
			await fakeAdminApi(page);
			await page.goto(`/admin/${name}`);
			await page.waitForTimeout(700);
			await page.screenshot({
				path: `test-results/admin-${name || 'dashboard'}-${width}.png`,
				fullPage: true
			});
		});
	}
}

test.describe('dark', () => {
	test.use({ colorScheme: 'dark' });
	for (const name of ['', 'users', 'libraries']) {
		test(`admin ${name || 'dashboard'} in dark mode`, async ({ page }) => {
			await page.setViewportSize({ width: 1280, height: 1300 });
			await fakeAdminApi(page);
			await page.goto(`/admin/${name}`);
			await page.waitForTimeout(700);
			await page.screenshot({ path: `test-results/admin-${name || 'dashboard'}-dark.png` });
		});
	}
});
