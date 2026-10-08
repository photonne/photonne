import { test } from '@playwright/test';
import { fakeApi } from './fake-api';

// Not assertions: renders the administration operations pages to files for
// visual review, wide and narrow, light and dark.
test.skip(!process.env.SCREENSHOTS, 'only with SCREENSHOTS=1');
test.use({ locale: 'es-ES' });

const pages = [
	['tasks', '/admin/tasks'],
	['failures', '/admin/tasks/failures'],
	['maintenance', '/admin/maintenance'],
	['duplicates', '/admin/maintenance/duplicates'],
	['settings-server', '/admin/settings/server'],
	['settings-faces', '/admin/settings/faces'],
	['settings-trash', '/admin/settings/trash'],
	['settings-nightly', '/admin/settings/nightly']
] as const;

for (const [name, path] of pages) {
	for (const [width, height] of [
		[1280, 2200],
		[900, 2200]
	]) {
		test(`${name} at ${width}`, async ({ page }) => {
			await page.setViewportSize({ width, height });
			await fakeApi(page, { signedIn: true, role: 'Admin' });
			await page.goto(path);
			await page.waitForTimeout(900);
			await page.screenshot({ path: `test-results/adminops-${name}-${width}.png`, fullPage: true });
		});
	}
}

test('duplicates review, dark', async ({ page }) => {
	await page.emulateMedia({ colorScheme: 'dark' });
	await page.setViewportSize({ width: 1280, height: 800 });
	await fakeApi(page, { signedIn: true, role: 'Admin' });
	await page.goto('/admin/maintenance/duplicates');
	await page.getByLabel(/Escanear el disco/).check();
	await page.getByRole('button', { name: 'Empezar' }).click();
	await page.waitForTimeout(900);
	await page.screenshot({
		path: 'test-results/adminops-duplicates-review-dark.png',
		fullPage: true
	});
});

test('maintenance, dark', async ({ page }) => {
	await page.emulateMedia({ colorScheme: 'dark' });
	await page.setViewportSize({ width: 1280, height: 800 });
	await fakeApi(page, { signedIn: true, role: 'Admin' });
	await page.goto('/admin/maintenance');
	await page.waitForTimeout(900);
	await page.screenshot({ path: 'test-results/adminops-maintenance-dark.png', fullPage: true });
});
